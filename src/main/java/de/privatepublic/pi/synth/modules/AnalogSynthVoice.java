package de.privatepublic.pi.synth.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.modules.fx.MultiModeFilter;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.EnvADSR.State;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.modules.osc.BlepOscillator;

public class AnalogSynthVoice {

	private final EnvADSR env1 = new EnvADSR(P.ENV_CONF_AMP);
	private final EnvADSR env2 = new EnvADSR(P.ENV_CONF_MOD_ENV);
	private final LFO lfo = new LFO(env2);
	
	private final BlepOscillator osc1 = new BlepOscillator(BlepOscillator.Mode.PRIMARY, env1, env2, lfo);
	private final BlepOscillator osc2 = new BlepOscillator(BlepOscillator.Mode.SECONDARY, env1, env2, lfo);
	private final BlepOscillator oscSub = new BlepOscillator(BlepOscillator.Mode.SUB, env1, env2, lfo);
	
	private final MultiModeFilter filter;
	
	private final float[] am_buffer = new float[P.SAMPLE_BUFFER_SIZE];
	private final boolean[] syncBuffer = new boolean[P.SAMPLE_BUFFER_SIZE];
	
	private static final float MAX_GAIN = 5;
	private static final float NOISE_SCALE = (2.0f / 0xffffffff) / 4294967296.0f;
	private int noiseX1 = (int) 0x67452301;
	private int noiseX2 = (int) 0xefcdab89;
	
	public long lastTriggered = 0;
	public int lastMidiNote = 0;
	
	private float velocityFactor = 1;
	private float outVolume = 0;
	
	private float gain, gainDry, gainWet;
	
	public AnalogSynthVoice() {
		filter = new MultiModeFilter(env1, env2, lfo);
	}
	
	public float captureWeight(long timestamp) {
		if (env1.state==State.REST) {
			return 0;
		}
		if (env1.state==State.RELEASE) {
			return env1.outValue;
		}
		// use hold time
		return timestamp-lastTriggered;
	}
	
	public boolean isActive() {
		return (env1.state!=EnvADSR.State.REST);
	}
	
	public boolean isIdle() {
		return (env1.state==EnvADSR.State.REST);
	}
	
	public boolean isHeld() {
		return (env1.state==EnvADSR.State.HOLD 
				|| env1.state==EnvADSR.State.ATTACK 
				|| env1.state==EnvADSR.State.DECAY 
				|| env1.state==EnvADSR.State.DECAY_LOOP);
	}
	
	public void trigger(final int midiNote, final float frequency, final float velocity, final long timestamp) {
		lastMidiNote = midiNote;
		lastTriggered = timestamp;
		osc1.trigger(frequency, velocity);
		osc2.trigger(frequency, velocity);
		oscSub.trigger(frequency, velocity);
		filter.trigger(frequency, velocity);
		env1.noteOn(velocity);
		env2.noteOn(velocity);
		lfo.resetDelay();
		if (P.IS[P.MOD_LFO_RESET]) {
			lfo.resetPhase();
		}
		velocityFactor = (1-P.VAL[P.MOD_VEL_VOL_AMOUNT])+(P.VAL[P.MOD_VEL_VOL_AMOUNT]*velocity);
		AnalogSynth.lastTriggeredFrequency = frequency;
	}
	
	public void triggerFreq(final int midiNote, final float frequency, final float velocity, final long timestamp) {
		lastMidiNote = midiNote;
		lastTriggered = timestamp;
		osc1.trigger(frequency, velocity);
		osc2.trigger(frequency, velocity);
		oscSub.trigger(frequency, velocity);
		AnalogSynth.lastTriggeredFrequency = frequency;
	}
	
	public void noteOff(float velocity) {
		env1.noteOff(velocity);
		env2.noteOff(velocity);
	}
	
	
	public void process(final float[] buffer, final int startPos) {
		osc1.controlTick();
		osc2.controlTick();
		oscSub.controlTick();
		filter.controlTick();
		env1.controlTick();
		env2.controlTick();
		lfo.controlTick();
		gain = 1f + MAX_GAIN*P.VALX[P.OVERDRIVE];
		gainDry = P.VALMIXHIGH[P.OVERDRIVE];
		gainWet = P.VALMIXLOW[P.OVERDRIVE];
		final boolean filter1on = P.IS[P.FILTER1_ON];
		final float osc1Vol = P.VALX[P.OSC1_VOLUME];
		final float osc2Vol = Math.max(
				P.VALX[P.OSC2_VOLUME]
				+env2.outValue*P.VALXC[P.MOD_ENV2_OSC2_VOL_AMOUNT]
				+lfo.lfoAmountAdd(P.VALXC[P.MOD_OSC2_VOL_AMOUNT])
						, 0);
		final float oscSubVol = P.VALX[P.OSC_SUB_VOLUME];
		final float noiseLevel = Math.max(P.VALX[P.OSC_NOISE_LEVEL] + env2.outValue*P.VALXC[P.MOD_ENV2_NOISE_AMOUNT], 0);
		final float volume = velocityFactor*env1.outValue*(1+lfo.lfoAmountAdd(P.VAL[P.MOD_VOL_AMOUNT]));
		final float volumeIncrement = (volume-outVolume)/P.CONTROL_BUFFER_SIZE;
		for (int i=0;i<P.CONTROL_BUFFER_SIZE;i++) {
			final int pos = i+startPos;
			noiseX2 += noiseX1;
			noiseX1 ^= noiseX2;
			float noise_val = noiseLevel * (noiseX2 * NOISE_SCALE);
			float val = osc1.process(i, osc1Vol, syncBuffer, am_buffer);
			val += osc2.process(i, osc2Vol, syncBuffer, am_buffer);
			val += oscSub.process(i, oscSubVol, syncBuffer, am_buffer);
			val += noise_val;
			if (filter1on)
				val = filter.processSample(val);
			else
				filter.processSample(val);
			
			// post filter drive
			float x = val*gain;
			float x2 = x*x;
			float y = x * ( 27 + x2 ) / ( 27 + 9 * x2 );
			
			buffer[pos] += (val*gainDry + y*gainWet) * outVolume;
			am_buffer[pos] = 0;
			outVolume += volumeIncrement;
		}
		outVolume = volume;
	}
	
	


	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(AnalogSynthVoice.class);
}
