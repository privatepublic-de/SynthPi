package de.privatepublic.pi.synth.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.modules.fx.MultiModeFilter;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.EnvADSR.State;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.modules.osc.AdditiveOscillator;
import de.privatepublic.pi.synth.modules.osc.BlepOscillator;
import de.privatepublic.pi.synth.modules.osc.ExciterOscillator;
import de.privatepublic.pi.synth.modules.osc.IOscillator;
import de.privatepublic.pi.synth.modules.osc.WaveTableOscillator;

public class AnalogSynthVoice {

	/**
	 * Toggles the buffer-rate oscillator path for WAVETABLE mode.
	 * Flip this and observe {@code SynthPiAudioClient.LOAD} to A/B the win.
	 */
	public static boolean USE_BUFFER_PATH = true;

	private final EnvADSR envelope;
	private final EnvADSR modEnvelope;
	/**
	 * Per-voice second mod envelope, parallel to {@link #modEnvelope}. Triggers
	 * with the amp envelope; advances per sample inside the chunk loop. Read by
	 * the BlepOscillator (Phase 5) via env2.outValue and the new MOD_ENV2_*
	 * mod-target depths in P. Existing oscillators don't read env2 — adding it
	 * here is inert behavior-wise; it's just kept up to date so it's ready when
	 * Blep mode lands.
	 */
	private final EnvADSR env2 = new EnvADSR(P.ENV_CONF_MOD_ENV2);
	/**
	 * Per-voice LFO, shared by all oscillator modes. Each voice accumulates its
	 * own phase independently of {@link LFO#GLOBAL}, so polyphonic notes diverge
	 * naturally over time. Reset to phase 0 on note-on when
	 * {@link P#MOD_LFO_RESET} is enabled.
	 */
	private final LFO lfo = new LFO();

	// All oscillator types receive the per-voice lfo (declared above in source
	// order so it is already initialized when these field initializers run).
	private final WaveTableOscillator osc1_va = new WaveTableOscillator(IOscillator.PRIMARY_OSC, lfo);
	private final WaveTableOscillator osc2_va = new WaveTableOscillator(IOscillator.SECONDARY_OSC, lfo);
	private final AdditiveOscillator osc1_add = new AdditiveOscillator(IOscillator.PRIMARY_OSC, lfo);
	private final AdditiveOscillator osc2_add = new AdditiveOscillator(IOscillator.SECONDARY_OSC, lfo);
	private final ExciterOscillator osc1_pluck = new ExciterOscillator(IOscillator.PRIMARY_OSC, lfo);
	private final ExciterOscillator osc2_pluck = new ExciterOscillator(IOscillator.SECONDARY_OSC, lfo);
	private final BlepOscillator osc1_blep = new BlepOscillator(IOscillator.PRIMARY_OSC, env2, lfo);
	private final BlepOscillator osc2_blep = new BlepOscillator(IOscillator.SECONDARY_OSC, env2, lfo);
	
	private final MultiModeFilter filter1 = new MultiModeFilter(
			P.FILTER1_FREQ, 
			P.FILTER1_RESONANCE, 
			P.MOD_FILTER1_AMOUNT, 
			P.FILTER1_ENV_DEPTH, 
			0, 
			P.FILTER1_TRACK_KEYBOARD, 
			P.FILTER1_ENV_VELOCITY_SENS,
			P.FILTER1_OVERLOAD,
			P.ENV_CONF_FILTER1
		);
	private final MultiModeFilter filter2 = new MultiModeFilter(
			P.FILTER2_FREQ, 
			P.FILTER2_RESONANCE, 
			P.MOD_FILTER2_AMOUNT, 
			P.FILTER2_ENV_DEPTH, 
			1, 
			P.FILTER2_TRACK_KEYBOARD, 
			P.FILTER2_ENV_VELOCITY_SENS,
			P.FILTER2_OVERLOAD,
			P.ENV_CONF_FILTER2
		);
	
	private final float[] am_buffer = new float[P.SAMPLE_BUFFER_SIZE];
	private final boolean[] syncBuffer = new boolean[P.SAMPLE_BUFFER_SIZE];
	private final float[] modEnvBuf = new float[P.SAMPLE_BUFFER_SIZE];
	private final float[] osc1OutBuf = new float[P.SAMPLE_BUFFER_SIZE];
	private final float[] osc2OutBuf = new float[P.SAMPLE_BUFFER_SIZE];
	private final float[] osc1MixBuf = new float[P.SAMPLE_BUFFER_SIZE];
	private final float[] osc2MixBuf = new float[P.SAMPLE_BUFFER_SIZE];
	private final float[] envBuf = new float[P.SAMPLE_BUFFER_SIZE];
	
	private static final float NOISE_SCALE = (2.0f / 0xffffffff) / 4294967296.0f;
	private int noiseX1 = (int) 0x67452301;
	private int noiseX2 = (int) 0xefcdab89;
	
	public long lastTriggered = 0;
	public int lastMidiNote = 0;
	// Per-note modulation sources, valid regardless of oscillator mode.
	private float keyNorm = 0f;
	private float noteVelocity = 0f;

	private float pendingFreq;
	private float pendingVelocity;
	private final Runnable pendingTrigger = new Runnable() {
		@Override
		public void run() {
			final IOscillator osc1;
			final IOscillator osc2;
			switch (P.VAL_OSCILLATOR_MODE) {
			case ADDITIVE: osc1=osc1_add; osc2=osc2_add; break;
			case EXITER:   osc1=osc1_pluck; osc2=osc2_pluck; break;
			case BLEP:     osc1=osc1_blep; osc2=osc2_blep; break;
			case WAVETABLE: default: osc1=osc1_va; osc2=osc2_va; break;
			}
			noteVelocity = pendingVelocity;
			final float octs = (float)(Math.log(pendingFreq / 440.0) / Math.log(2.0)) / 4f;
			keyNorm = octs < -1f ? -1f : (octs > 1f ? 1f : octs);
			osc1.trigger(pendingFreq, pendingVelocity);
			osc2.trigger(pendingFreq, pendingVelocity);
			filter1.trigger(pendingFreq, pendingVelocity);
			filter2.trigger(pendingFreq, pendingVelocity);
			envelope.noteOn(pendingVelocity);
			modEnvelope.noteOn(P.IS[P.MOD_ENV1_VEL_SENS] ? pendingVelocity : 1);
			env2.noteOn(P.IS[P.MOD_ENV2_VEL_SENS] ? pendingVelocity : 1);
			if (P.IS[P.MOD_LFO_RESET]) lfo.reset();
			AnalogSynth.lastTriggeredFrequency = pendingFreq;
		}
	};
	
	private RouteFilters filtersAllOff = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final int startPos, final float[] outL, final float[] outR) {
			filter1.processSample(in1, sampleNo);
			filter2.processSample(in2, sampleNo);
			outL[startPos+sampleNo] += (in1 + in2*width)*env;
			outR[startPos+sampleNo] += (in1*width + in2)*env;
		}
		@Override
		public void processBuffer(final int chunkLen, final float[] in1, final float[] in2, final float[] envBuf, final float width, final int startPos, final float[] outL, final float[] outR) {
			for (int sampleNo=0; sampleNo<chunkLen; sampleNo++) {
				final float a = in1[sampleNo];
				final float b = in2[sampleNo];
				filter1.processSample(a, sampleNo);
				filter2.processSample(b, sampleNo);
				final float env = envBuf[sampleNo];
				outL[startPos+sampleNo] += (a + b*width)*env;
				outR[startPos+sampleNo] += (a*width + b)*env;
			}
		}
	};
	
	private RouteFilters filtersSerial1On2Off = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final int startPos, final float[] outL, final float[] outR) {
			filter2.processSample(in2, sampleNo);
			final float val = filter1.processSample(in1+in2, sampleNo);
			// serial is mono only
			outL[startPos+sampleNo] += val*env;
			outR[startPos+sampleNo] += val*env;
		}
		@Override
		public void processBuffer(final int chunkLen, final float[] in1, final float[] in2, final float[] envBuf, final float width, final int startPos, final float[] outL, final float[] outR) {
			for (int sampleNo=0; sampleNo<chunkLen; sampleNo++) {
				final float b = in2[sampleNo];
				filter2.processSample(b, sampleNo);
				final float val = filter1.processSample(in1[sampleNo]+b, sampleNo);
				final float scaled = val*envBuf[sampleNo];
				outL[startPos+sampleNo] += scaled;
				outR[startPos+sampleNo] += scaled;
			}
		}
	};
	
	private RouteFilters filtersSerial1On2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final int startPos, final float[] outL, final float[] outR) {
			// serial is mono only
			final float val = filter2.processSample(filter1.processSample(in1+in2, sampleNo), sampleNo);
			outL[startPos+sampleNo] += val*env;
			outR[startPos+sampleNo] += val*env;
		}
		@Override
		public void processBuffer(final int chunkLen, final float[] in1, final float[] in2, final float[] envBuf, final float width, final int startPos, final float[] outL, final float[] outR) {
			for (int sampleNo=0; sampleNo<chunkLen; sampleNo++) {
				final float val = filter2.processSample(filter1.processSample(in1[sampleNo]+in2[sampleNo], sampleNo), sampleNo);
				final float scaled = val*envBuf[sampleNo];
				outL[startPos+sampleNo] += scaled;
				outR[startPos+sampleNo] += scaled;
			}
		}
	};
	
	private RouteFilters filtersSerial1Off2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final int startPos, final float[] outL, final float[] outR) {
			filter1.processSample(in1, sampleNo);
			final float val = filter2.processSample(in1+in2, sampleNo);
			// serial is mono only
			outL[startPos+sampleNo] += val*env;
			outR[startPos+sampleNo] += val*env;
		}
		@Override
		public void processBuffer(final int chunkLen, final float[] in1, final float[] in2, final float[] envBuf, final float width, final int startPos, final float[] outL, final float[] outR) {
			for (int sampleNo=0; sampleNo<chunkLen; sampleNo++) {
				final float a = in1[sampleNo];
				filter1.processSample(a, sampleNo);
				final float val = filter2.processSample(a+in2[sampleNo], sampleNo);
				final float scaled = val*envBuf[sampleNo];
				outL[startPos+sampleNo] += scaled;
				outR[startPos+sampleNo] += scaled;
			}
		}
	};
	
	private RouteFilters filtersParallel1On2Off = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final int startPos, final float[] outL, final float[] outR) {
			filter2.processSample(in2, sampleNo);
			final float val = in1+in2;
			final float filtered1 = filter1.processSample(val, sampleNo)*P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			outL[startPos+sampleNo] += (filtered1 + in2*width)*env;
			outR[startPos+sampleNo] += (filtered1*width + in2)*env;
		}
		@Override
		public void processBuffer(final int chunkLen, final float[] in1, final float[] in2, final float[] envBuf, final float width, final int startPos, final float[] outL, final float[] outR) {
			final float mixHigh = P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<chunkLen; sampleNo++) {
				final float a = in1[sampleNo];
				final float b = in2[sampleNo];
				filter2.processSample(b, sampleNo);
				final float filtered1 = filter1.processSample(a+b, sampleNo) * mixHigh;
				final float env = envBuf[sampleNo];
				outL[startPos+sampleNo] += (filtered1 + b*width)*env;
				outR[startPos+sampleNo] += (filtered1*width + b)*env;
			}
		}
	};
	
	private RouteFilters filtersParallel1On2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final int startPos, final float[] outL, final float[] outR) {
			final float val = in1+in2;
			final float filtered1 = filter1.processSample(val, sampleNo)*P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			final float filtered2 = filter2.processSample(val, sampleNo)*P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			outL[startPos+sampleNo] += (filtered1 + filtered2*width)*env;
			outR[startPos+sampleNo] += (filtered1*width + filtered2)*env;
		}
		@Override
		public void processBuffer(final int chunkLen, final float[] in1, final float[] in2, final float[] envBuf, final float width, final int startPos, final float[] outL, final float[] outR) {
			final float mixHigh = P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			final float mixLow = P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<chunkLen; sampleNo++) {
				final float val = in1[sampleNo] + in2[sampleNo];
				final float filtered1 = filter1.processSample(val, sampleNo) * mixHigh;
				final float filtered2 = filter2.processSample(val, sampleNo) * mixLow;
				final float env = envBuf[sampleNo];
				outL[startPos+sampleNo] += (filtered1 + filtered2*width)*env;
				outR[startPos+sampleNo] += (filtered1*width + filtered2)*env;
			}
		}
	};
	
	private RouteFilters filtersParallel1Off2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final int startPos, final float[] outL, final float[] outR) {
			filter1.processSample(in1, sampleNo);
			final float val = in1+in2;
			final float filtered2 = filter2.processSample(val, sampleNo)*P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			outL[startPos+sampleNo] += (in1 + filtered2*width)*env;
			outR[startPos+sampleNo] += (in1*width + filtered2)*env;
		}
		@Override
		public void processBuffer(final int chunkLen, final float[] in1, final float[] in2, final float[] envBuf, final float width, final int startPos, final float[] outL, final float[] outR) {
			final float mixLow = P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<chunkLen; sampleNo++) {
				final float a = in1[sampleNo];
				final float b = in2[sampleNo];
				filter1.processSample(a, sampleNo);
				final float filtered2 = filter2.processSample(a+b, sampleNo) * mixLow;
				final float env = envBuf[sampleNo];
				outL[startPos+sampleNo] += (a + filtered2*width)*env;
				outR[startPos+sampleNo] += (a*width + filtered2)*env;
			}
		}
	};
	
	private RouteFilters filtersPerOsc1On2Off = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final int startPos, final float[] outL, final float[] outR) {
			filter2.processSample(in2, sampleNo);
			final float filtered1 = filter1.processSample(in1, sampleNo)*P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			outL[startPos+sampleNo] += (filtered1 + in2*width)*env;
			outR[startPos+sampleNo] += (filtered1*width + in2)*env;
		}
		@Override
		public void processBuffer(final int chunkLen, final float[] in1, final float[] in2, final float[] envBuf, final float width, final int startPos, final float[] outL, final float[] outR) {
			final float mixHigh = P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<chunkLen; sampleNo++) {
				final float a = in1[sampleNo];
				final float b = in2[sampleNo];
				filter2.processSample(b, sampleNo);
				final float filtered1 = filter1.processSample(a, sampleNo) * mixHigh;
				final float env = envBuf[sampleNo];
				outL[startPos+sampleNo] += (filtered1 + b*width)*env;
				outR[startPos+sampleNo] += (filtered1*width + b)*env;
			}
		}
	};
	
	private RouteFilters filtersPerOsc1On2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final int startPos, final float[] outL, final float[] outR) {
			final float filtered1 = filter1.processSample(in1, sampleNo)*P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			final float filtered2 = filter2.processSample(in2, sampleNo)*P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			outL[startPos+sampleNo] += (filtered1 + filtered2*width)*env;
			outR[startPos+sampleNo] += (filtered1*width + filtered2)*env;
		}
		@Override
		public void processBuffer(final int chunkLen, final float[] in1, final float[] in2, final float[] envBuf, final float width, final int startPos, final float[] outL, final float[] outR) {
			final float mixHigh = P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			final float mixLow = P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<chunkLen; sampleNo++) {
				final float filtered1 = filter1.processSample(in1[sampleNo], sampleNo) * mixHigh;
				final float filtered2 = filter2.processSample(in2[sampleNo], sampleNo) * mixLow;
				final float env = envBuf[sampleNo];
				outL[startPos+sampleNo] += (filtered1 + filtered2*width)*env;
				outR[startPos+sampleNo] += (filtered1*width + filtered2)*env;
			}
		}
	};
	
	private RouteFilters filtersPerOsc1Off2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final int startPos, final float[] outL, final float[] outR) {
			filter1.processSample(in1, sampleNo);
			final float filtered2 = filter2.processSample(in2, sampleNo)*P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			outL[startPos+sampleNo] += (in1 + filtered2*width)*env;
			outR[startPos+sampleNo] += (in1*width + filtered2)*env;
		}
		@Override
		public void processBuffer(final int chunkLen, final float[] in1, final float[] in2, final float[] envBuf, final float width, final int startPos, final float[] outL, final float[] outR) {
			final float mixLow = P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<chunkLen; sampleNo++) {
				final float a = in1[sampleNo];
				filter1.processSample(a, sampleNo);
				final float filtered2 = filter2.processSample(in2[sampleNo], sampleNo) * mixLow;
				final float env = envBuf[sampleNo];
				outL[startPos+sampleNo] += (a + filtered2*width)*env;
				outR[startPos+sampleNo] += (a*width + filtered2)*env;
			}
		}
	};
	
	
	public AnalogSynthVoice() {
		envelope = new EnvADSR(P.ENV_CONF_AMP);
		modEnvelope = new EnvADSR(P.ENV_CONF_MOD_ENV);
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
		noteVelocity = velocity;
		final float octaves = (float)(Math.log(frequency / 440.0) / Math.log(2.0)) / 4f;
		keyNorm = octaves < -1f ? -1f : (octaves > 1f ? 1f : octaves);
		if (envelope.state==State.REST) {
			final IOscillator osc1;
			final IOscillator osc2;
			switch (P.VAL_OSCILLATOR_MODE) {
			case ADDITIVE:
				osc1=osc1_add;
				osc2=osc2_add;
				break;
			case EXITER:
				osc1=osc1_pluck;
				osc2=osc2_pluck;
				break;
			case BLEP:
				osc1=osc1_blep;
				osc2=osc2_blep;
				break;
			case WAVETABLE:
			default:
				osc1=osc1_va;
				osc2=osc2_va;
				break;
			}
			osc1.trigger(frequency, velocity);
			osc2.trigger(frequency, velocity);
			filter1.trigger(frequency, velocity);
			filter2.trigger(frequency, velocity);
			envelope.noteOn(velocity);
			modEnvelope.noteOn(P.IS[P.MOD_ENV1_VEL_SENS] ? velocity : 1);
			env2.noteOn(P.IS[P.MOD_ENV2_VEL_SENS] ? velocity : 1);
			if (P.IS[P.MOD_LFO_RESET]) lfo.reset();
			AnalogSynth.lastTriggeredFrequency = frequency;
		}
		else {
			pendingFreq = frequency;
			pendingVelocity = velocity;
			envelope.queueNoteOn(velocity, pendingTrigger);
		}
	}

	public void triggerFreq(final int midiNote, final float frequency, final float velocity, final long timestamp) {
		lastMidiNote = midiNote;
		lastTriggered = timestamp;
		final IOscillator osc1;
		final IOscillator osc2;
		switch (P.VAL_OSCILLATOR_MODE) {
		case ADDITIVE:
			osc1=osc1_add;
			osc2=osc2_add;
			break;
		case EXITER:
			osc1=osc1_pluck;
			osc2=osc2_pluck;
			break;
		case BLEP:
			osc1=osc1_blep;
			osc2=osc2_blep;
			break;
		case WAVETABLE:
		default:
			osc1=osc1_va;
			osc2=osc2_va;
			break;
		}
		osc1.trigger(frequency, velocity);
		osc2.trigger(frequency, velocity);
		AnalogSynth.lastTriggeredFrequency = frequency;
	}
	
	public void noteOff() {
		envelope.noteOff();
		modEnvelope.noteOff();
		env2.noteOff();
		filter1.noteOff();
		filter2.noteOff();
	}
	
	private float noiseLevel = 0;
	private float noiseModAmount = 0;
	
	public void process(final float[][] outbuffers, final int startPos, final int nframes) {
		if (envelope.state == State.REST) {
			return;
		}
		final boolean filter1on = P.IS[P.FILTER1_ON];
		final boolean filter2on = P.IS[P.FILTER2_ON];
		final IOscillator osc1;
		final IOscillator osc2;
		switch (P.VAL_OSCILLATOR_MODE) {
		case ADDITIVE:
			osc1=osc1_add;
			osc2=osc2_add;
			break;
		case EXITER:
			osc1=osc1_pluck;
			osc2=osc2_pluck;
			break;
		case BLEP:
			osc1=osc1_blep;
			osc2=osc2_blep;
			break;
		case WAVETABLE:
		default:
			osc1=osc1_va;
			osc2=osc2_va;
			break;
		}
		// Render per-voice LFO for this chunk before any bufferedValueAt() reads below.
		lfo.renderBuffer(nframes);
		final float env2OutForMix = env2.outValue;
		// Modulate the OSC 1/2 mix position so both oscillators move complementarily.
		// All MOD_*_OSC2VOL depth params shift the mix knob rather than scaling OSC2 alone.
		final float mixMod = lfo.bufferedValueAt(0) * P.MOD_AMOUNT_COMBINED * P.VALXC[P.MOD_LFO_OSC2VOL_AMOUNT]
				+ modEnvelope.outValue * P.VALXC[P.MOD_ENV1_OSC2VOL_AMOUNT]
				+ env2OutForMix * P.VALXC[P.MOD_ENV2_OSC2_VOL_AMOUNT]
				+ P.CHANNEL_PRESSURE * P.VALXC[P.MOD_PRESS_OSC2VOL_AMOUNT]
				+ keyNorm * P.VALXC[P.MOD_KEY_OSC2VOL_AMOUNT]
				+ noteVelocity * P.VALXC[P.MOD_VEL_OSC2VOL_AMOUNT]
				+ P.VAL[P.MOD_WHEEL] * P.VALXC[P.MOD_WHEEL_OSC2VOL_AMOUNT];
		final float mixPos = Math.max(0f, Math.min(1f, P.VAL[P.OSC_1_2_MIX] + mixMod));
		// Equal-power crossfade — same formula as P.setDirectly VALMIXLOW/VALMIXHIGH
		final float mixC = (mixPos - 0.5f) * 2f;
		final float mixX2 = Math.abs(mixC) - 1f;
		final float mixCv = Math.signum(mixC) * (1f - mixX2 * mixX2 * mixX2 * mixX2);
		final float gain = P.VALX[P.OSC_GAIN] * .75f;
		final float osc2Vol = (float) Math.sqrt(0.5 * (1.0 + mixCv)) * gain;
		final float osc1Vol = (float) Math.sqrt(0.5 * (1.0 - mixCv)) * gain;
		noiseLevel = P.VALX[P.OSC_NOISE_LEVEL];
		noiseModAmount = P.VALXC[P.MOD_ENV1_NOISE_AMOUNT];
		// Hoist extra noise sources (control-rate; LFO is sampled per-sample in the loop)
		final float lfoNoiseAmt = P.VALXC[P.MOD_LFO_NOISE_AMOUNT];
		final float env2NoiseAmt = P.VALXC[P.MOD_ENV2_NOISE_AMOUNT];
		final float pressNoiseAmt = P.VALXC[P.MOD_PRESS_NOISE_AMOUNT];
		final float keyNoiseBase = keyNorm * P.VALXC[P.MOD_KEY_NOISE_AMOUNT];
		final float velNoiseBase = noteVelocity * P.VALXC[P.MOD_VEL_NOISE_AMOUNT];
		final float wheelNoiseBase = P.VAL[P.MOD_WHEEL] * P.VALXC[P.MOD_WHEEL_NOISE_AMOUNT];
		final float pressNoiseBase = P.CHANNEL_PRESSURE * pressNoiseAmt;
		// Per-voice effective LFO amount: base knob + additive modulations, clamped to [0, 1].
		// Written to P.MOD_AMOUNT_COMBINED so all oscillator inner loops (which hoist modAmount
		// at the start of processBuffer) pick up the correct per-voice value.
		P.MOD_AMOUNT_COMBINED = Math.max(0f, Math.min(1f,
				P.VAL[P.MOD_AMOUNT_BASE]
				+ modEnvelope.outValue * P.VALXC[P.MOD_ENV1_LFOAMT_AMOUNT]
				+ env2OutForMix       * P.VALXC[P.MOD_ENV2_LFOAMT_AMOUNT]
				+ P.CHANNEL_PRESSURE  * P.VALXC[P.MOD_PRESS_LFOAMT_AMOUNT]
				+ P.VAL[P.MOD_WHEEL]  * P.VALXC[P.MOD_WHEEL_LFOAMT_AMOUNT]
				+ keyNorm             * P.VALXC[P.MOD_KEY_LFOAMT_AMOUNT]
				+ noteVelocity        * P.VALXC[P.MOD_VEL_LFOAMT_AMOUNT]));
		final float modAmount = P.MOD_AMOUNT_COMBINED;
		RouteFilters filterRoute = filtersAllOff;
		if (filter1on || filter2on) {
			switch(P.VAL_FILTER_ROUTING) {
			case SERIAL:
				if (filter1on && filter2on) {
					filterRoute = filtersSerial1On2On;
				}
				else {
					if (filter1on) {
						filterRoute = filtersSerial1On2Off;
					}
					else {
						filterRoute = filtersSerial1Off2On;
					}
				}
				break;
			case PARALLEL:
				if (filter1on && filter2on) {
					filterRoute = filtersParallel1On2On;
				}
				else {
					if (filter1on) {
						filterRoute = filtersParallel1On2Off;
					}
					else {
						filterRoute = filtersParallel1Off2On;
					}
				}
				break;
			case PEROSC:
				if (filter1on && filter2on) {
					filterRoute = filtersPerOsc1On2On;
				}
				else {
					if (filter1on) {
						filterRoute = filtersPerOsc1On2Off;
					}
					else {
						filterRoute = filtersPerOsc1Off2On;
					}
				}
				break;
			}
		}
		float osc1_val;
		float osc2_val;
		float noise_val;
		final float[] outL = outbuffers[0];
		final float[] outR = outbuffers[1];
		final float width = 1-P.VAL[P.SPREAD];
		final float modVol = P.VALXC[P.MOD_VOL_AMOUNT];
		final float modEnv1Out = modEnvelope.outValue;
		filter1.updateFreqResponse(modEnv1Out, env2OutForMix, keyNorm, noteVelocity);
		filter2.updateFreqResponse(modEnv1Out, env2OutForMix, keyNorm, noteVelocity);

		if (USE_BUFFER_PATH) {
			// Pre-render mod envelope and advance env2; snapshot env2 for oscillator use.
			for (int i=0; i<nframes; i++) {
				modEnvBuf[i] = modEnvelope.nextValue();
				env2.nextValue();
			}
			final float env2Snap = env2.outValue;
			// Push per-note state into the active oscillator pair so their inner loops
			// can read env2Val, keyNorm, noteVelocity without changing call signatures.
			switch (P.VAL_OSCILLATOR_MODE) {
			case ADDITIVE:
				osc1_add.env2Val = env2Snap; osc2_add.env2Val = env2Snap;
				osc1_add.processBuffer1st(nframes, osc1Vol, syncBuffer, am_buffer, modEnvBuf, osc1OutBuf);
				osc2_add.processBuffer2nd(nframes, osc2Vol, syncBuffer, am_buffer, modEnvBuf, osc2OutBuf);
				break;
			case EXITER:
				osc1_pluck.env2Val = env2Snap; osc2_pluck.env2Val = env2Snap;
				osc1_pluck.processBuffer1st(nframes, osc1Vol, syncBuffer, am_buffer, modEnvBuf, osc1OutBuf);
				osc2_pluck.processBuffer2nd(nframes, osc2Vol, syncBuffer, am_buffer, modEnvBuf, osc2OutBuf);
				break;
			case BLEP:
				// BlepOscillator reads env2 directly via its injected reference; env2Val unused.
				osc1_blep.processBuffer1st(nframes, osc1Vol, syncBuffer, am_buffer, modEnvBuf, osc1OutBuf);
				osc2_blep.processBuffer2nd(nframes, osc2Vol, syncBuffer, am_buffer, modEnvBuf, osc2OutBuf);
				break;
			case WAVETABLE:
			default:
				osc1_va.env2Val = env2Snap; osc2_va.env2Val = env2Snap;
				osc1_va.processBuffer1st(nframes, osc1Vol, syncBuffer, am_buffer, modEnvBuf, osc1OutBuf);
				osc2_va.processBuffer2nd(nframes, osc2Vol, syncBuffer, am_buffer, modEnvBuf, osc2OutBuf);
				break;
			}
			// Pre-mix noise (all sources) and envelope*LFO into working buffers.
			final float env2NoiseTerm = env2Snap * env2NoiseAmt;
			for (int i=0; i<nframes; i++) {
				noiseX2 += noiseX1;
				noiseX1 ^= noiseX2;
				final float lfoNoiseTerm = lfo.bufferedValueAt(i) * modAmount * lfoNoiseAmt;
				noise_val = Math.max(0f, noiseLevel
						+ modEnvBuf[i]*noiseModAmount
						+ env2NoiseTerm
						+ lfoNoiseTerm
						+ pressNoiseBase
						+ keyNoiseBase
						+ velNoiseBase
						+ wheelNoiseBase) * (noiseX2 * NOISE_SCALE) * .5f;
				osc1MixBuf[i] = osc1OutBuf[i] + noise_val;
				osc2MixBuf[i] = osc2OutBuf[i] + noise_val;
				envBuf[i] = envelope.nextValue() * (1 + lfo.bufferedValueAt(i) * modAmount * modVol
						+ P.VAL[P.MOD_WHEEL] * P.VALXC[P.MOD_WHEEL_VOL_AMOUNT]);
			}
			filterRoute.processBuffer(nframes, osc1MixBuf, osc2MixBuf, envBuf, width, startPos, outL, outR);
			// am_buffer is fully overwritten by OSC1 each buffer, no per-sample reset needed.
		}
		else {
			for (int i=0;i<nframes;i++) {
				modEnvelope.nextValue();
				env2.nextValue();
				noiseX2 += noiseX1;
				noiseX1 ^= noiseX2;
				final float lfoNoiseTerm = lfo.bufferedValueAt(i) * modAmount * lfoNoiseAmt;
				noise_val = Math.max(0f, noiseLevel
						+ modEnvelope.outValue*noiseModAmount
						+ env2.outValue*env2NoiseAmt
						+ lfoNoiseTerm
						+ pressNoiseBase
						+ keyNoiseBase
						+ velNoiseBase
						+ wheelNoiseBase) * (noiseX2 * NOISE_SCALE) * .5f;
				osc1_val = osc1.processSample1st(i, osc1Vol, syncBuffer, am_buffer, modEnvelope) + noise_val;
				osc2_val = osc2.processSample2nd(i, osc2Vol, syncBuffer, am_buffer, modEnvelope) + noise_val;
				filterRoute.process(osc1_val, osc2_val, envelope.nextValue()*(1+lfo.bufferedValueAt(i) * modAmount * modVol
						+ P.VAL[P.MOD_WHEEL] * P.VALXC[P.MOD_WHEEL_VOL_AMOUNT]), width, i, startPos, outL, outR);
				am_buffer[i] = 0;
			}
		}
		P.MOD_ENV1_GLOBAL = modEnvelope.outValue;
		P.MOD_ENV2_GLOBAL = env2.outValue;
		P.KEY_NORM_GLOBAL = keyNorm;
		lfo.nextBufferSlice(nframes);
	}
	
	
	@SuppressWarnings("unused")
	private static abstract class RouteFilters {

		private MultiModeFilter filter1;
		private MultiModeFilter filter2;

		public RouteFilters(MultiModeFilter filter1, MultiModeFilter filter2) {
			this.filter1 = filter1;
			this.filter2 = filter2;
		}

		/**
		 * Per-sample variant. {@code sampleNo} is the index within the current chunk
		 * (0..chunkLen-1); {@code startPos} is the chunk's offset into the full audio
		 * buffer, so writes to {@code outL/outR} use {@code startPos+sampleNo}.
		 */
		public abstract void process(float in1, float in2, float env, float width, int sampleNo, int startPos, float[] outL, float[] outR);

		/**
		 * Buffer-rate variant of {@link #process}. Reads pre-mixed oscillator+noise
		 * inputs and a pre-rendered envelope buffer (all indexed 0..chunkLen-1, since
		 * those are per-voice working buffers). Output buffers are full-size; indexing
		 * uses {@code startPos+sampleNo}.
		 */
		public abstract void processBuffer(int chunkLen, float[] in1, float[] in2, float[] envBuf, float width, int startPos, float[] outL, float[] outR);

	}

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(AnalogSynthVoice.class);
}
