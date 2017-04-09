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

	private final EnvADSR envelope;
	private final EnvADSR modEnvelope = new EnvADSR(P.ENV_CONF_MOD_ENV);
	
	private final IOscillator osc1 = new BlepOscillator(Mode.PRIMARY, modEnvelope);
	private final IOscillator osc2 = new BlepOscillator(Mode.SECONDARY, modEnvelope);
	private final IOscillator oscSub = new BlepOscillator(Mode.SUB, modEnvelope);
	
	private final MultiModeFilter filter1;
	
	private final float[] am_buffer = new float[P.SAMPLE_BUFFER_SIZE];
	private final boolean[] syncBuffer = new boolean[P.SAMPLE_BUFFER_SIZE];
	
	private static final float NOISE_SCALE = (2.0f / 0xffffffff) / 4294967296.0f;
	private int noiseX1 = (int) 0x67452301;
	private int noiseX2 = (int) 0xefcdab89;
	
	public long lastTriggered = 0;
	public int lastMidiNote = 0;
	
	
	
	public AnalogSynthVoice() {
		envelope = new EnvADSR(P.ENV_CONF_AMP);
		filter1 = new MultiModeFilter(
				P.FILTER1_FREQ, 
				P.FILTER1_RESONANCE, 
				P.MOD_FILTER1_AMOUNT, 
				P.FILTER1_ENV_DEPTH, 
				0, 
				P.FILTER1_TRACK_KEYBOARD, 
				P.FILTER1_ENV_VELOCITY_SENS,
				P.FILTER1_OVERLOAD,
				modEnvelope
			);
	}
	
	public float captureWeight(long timestamp) {
		if (envelope.state==State.REST) {
			return 0;
		}
		if (envelope.state==State.RELEASE) {
			return envelope.outValue;
		}
		if (envelope.state==State.QUEUE) {
			return Float.MAX_VALUE; // don't use queued voice
		}
		// use hold time
		return timestamp-lastTriggered;
	}
	
	public boolean isActive() {
		return (envelope.state!=EnvADSR.State.REST);
	}
	
	public boolean isIdle() {
		return (envelope.state==EnvADSR.State.REST);
	}
	
	public boolean isHeld() {
		return (envelope.state==EnvADSR.State.HOLD 
				|| envelope.state==EnvADSR.State.ATTACK 
				|| envelope.state==EnvADSR.State.DECAY 
				|| envelope.state==EnvADSR.State.DECAY_LOOP);
	}
	
	public void trigger(final int midiNote, final float frequency, final float velocity, final long timestamp) {
		lastMidiNote = midiNote;
		lastTriggered = timestamp;
		if (envelope.state==State.REST) {
			osc1.trigger(frequency, velocity);
			osc2.trigger(frequency, velocity);
			oscSub.trigger(frequency, velocity);
			filter1.trigger(frequency, velocity);
			envelope.noteOn(velocity);
			modEnvelope.noteOn(1);
			AnalogSynth.lastTriggeredFrequency = frequency;
		}
		else {
			envelope.queueNoteOn(velocity, new Runnable() {
				@Override
				public void run() {
					osc1.trigger(frequency, velocity);
					osc2.trigger(frequency, velocity);
					oscSub.trigger(frequency, velocity);
					filter1.trigger(frequency, velocity);
					envelope.noteOn(velocity);
					modEnvelope.noteOn(1);
					AnalogSynth.lastTriggeredFrequency = frequency;
				}
			});
		}
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
		envelope.noteOff();
		modEnvelope.noteOff();
		filter1.noteOff();
	}
	
	private float noiseLevel = 0;
	private float noiseModAmount = 0;
	
	public void process(final float[] buffer, final int startPos) {
		envelope.controlTick();
		modEnvelope.controlTick();
		osc1.controlTick();
		osc2.controlTick();
		oscSub.controlTick();
		filter1.controlTick();
		final boolean filter1on = P.IS[P.FILTER1_ON];
		final float osc1Vol = P.VALX[P.OSC1_VOLUME];
		final float osc2Vol = P.VALX[P.OSC2_VOLUME];
		final float oscSubVol = P.VALX[P.OSC_SUB_VOLUME];
		noiseLevel = P.VALX[P.OSC_NOISE_LEVEL];
		noiseModAmount = P.VALXC[P.MOD_ENV1_NOISE_AMOUNT];
		float val;
		float noise_val;
		final float modVol = P.VAL[P.MOD_VOL_AMOUNT];
		for (int i=0;i<P.CONTROL_BUFFER_SIZE;i++) {
			final int pos = i+startPos;
			noiseX2 += noiseX1;
			noiseX1 ^= noiseX2;
			noise_val = (noiseLevel + modEnvelope.outValue*noiseModAmount) * (noiseX2 * NOISE_SCALE) * .5f;
			val = osc1.process(i, osc1Vol, syncBuffer, am_buffer);
			val += osc2.process(i, osc2Vol, syncBuffer, am_buffer);
			val += oscSub.process(i, oscSubVol, syncBuffer, am_buffer);
			val += noise_val;
			if (filter1on)
				val = filter1.processSample(val);
			else
				filter1.processSample(val);
			val *= envelope.outValue*(1+LFO.lfoAmountAdd(modVol));
			buffer[pos] += val;
			am_buffer[pos] = 0;
		}
	}
	
	


	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(AnalogSynthVoice.class);
}
