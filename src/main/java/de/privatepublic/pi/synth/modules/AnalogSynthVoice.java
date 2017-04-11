package de.privatepublic.pi.synth.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.modules.fx.MultiModeFilter;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.EnvADSR.State;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.modules.osc.BlepOscillator;
import de.privatepublic.pi.synth.modules.osc.IOscillator;
import de.privatepublic.pi.synth.modules.osc.IOscillator.Mode;

public class AnalogSynthVoice {

	private final EnvADSR env1 = new EnvADSR(P.ENV_CONF_AMP);
	private final EnvADSR env2 = new EnvADSR(P.ENV_CONF_MOD_ENV);
	
	private final IOscillator osc1 = new BlepOscillator(Mode.PRIMARY, env1, env2);
	private final IOscillator osc2 = new BlepOscillator(Mode.SECONDARY, env1, env2);
	private final IOscillator oscSub = new BlepOscillator(Mode.SUB, env1, env2);
	
	private final MultiModeFilter filter;
	
	private final float[] am_buffer = new float[P.SAMPLE_BUFFER_SIZE];
	private final boolean[] syncBuffer = new boolean[P.SAMPLE_BUFFER_SIZE];
	
	private static final float NOISE_SCALE = (2.0f / 0xffffffff) / 4294967296.0f;
	private int noiseX1 = (int) 0x67452301;
	private int noiseX2 = (int) 0xefcdab89;
	
	public long lastTriggered = 0;
	public int lastMidiNote = 0;
	
	
	
	public AnalogSynthVoice() {
		filter = new MultiModeFilter(env1, env2);
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
		env1.noteOn();
		env2.noteOn();
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
	
	public void noteOff() {
		env1.noteOff();
		env2.noteOff();
	}
	
	
	public void process(final float[] buffer, final int startPos) {
		env1.controlTick();
		env2.controlTick();
		osc1.controlTick();
		osc2.controlTick();
		oscSub.controlTick();
		filter.controlTick();
		final boolean filter1on = P.IS[P.FILTER1_ON];
		final float osc1Vol = P.VALX[P.OSC1_VOLUME];
		final float osc2Vol = P.VALX[P.OSC2_VOLUME];
		final float oscSubVol = P.VALX[P.OSC_SUB_VOLUME];
		final float noiseLevel = P.VALX[P.OSC_NOISE_LEVEL] + env1.outValue*(P.VALXC[P.MOD_ENV1_NOISE_AMOUNT]+env2.outValue*P.VALXC[P.MOD_ENV2_NOISE_AMOUNT])+LFO.lfoAmountAdd(P.VALXC[P.MOD_NOISE_AMOUNT]);
		final float modVol = P.VAL[P.MOD_VOL_AMOUNT];
		final float volume = env1.outValue*P.VALXC[P.MOD_ENV1_VOL_AMOUNT]*(1+LFO.lfoAmountAdd(modVol))+env2.outValue*P.VALXC[P.MOD_ENV2_VOL_AMOUNT]*(1+LFO.lfoAmountAdd(modVol));
		for (int i=0;i<P.CONTROL_BUFFER_SIZE;i++) {
			final int pos = i+startPos;
			noiseX2 += noiseX1;
			noiseX1 ^= noiseX2;
			float noise_val = noiseLevel * (noiseX2 * NOISE_SCALE) * .5f;
			float val = osc1.process(i, osc1Vol, syncBuffer, am_buffer);
			val += osc2.process(i, osc2Vol, syncBuffer, am_buffer);
			val += oscSub.process(i, oscSubVol, syncBuffer, am_buffer);
			val += noise_val;
			if (filter1on)
				val = filter.processSample(val);
			else
				filter.processSample(val);
			buffer[pos] += val * volume;
			am_buffer[pos] = 0;
		}
	}
	
	


	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(AnalogSynthVoice.class);
}
