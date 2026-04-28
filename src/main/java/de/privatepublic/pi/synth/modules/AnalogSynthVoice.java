package de.privatepublic.pi.synth.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.modules.fx.MultiModeFilter;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.EnvADSR.State;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.modules.osc.AdditiveOscillator;
import de.privatepublic.pi.synth.modules.osc.ExciterOscillator;
import de.privatepublic.pi.synth.modules.osc.IOscillator;
import de.privatepublic.pi.synth.modules.osc.WaveTableOscillator;

public class AnalogSynthVoice {

	/**
	 * Toggles the buffer-rate oscillator path for VIRTUAL_ANALOG mode.
	 * Flip this and observe {@code SynthPiAudioClient.LOAD} to A/B the win.
	 */
	public static boolean USE_BUFFER_PATH = true;

	private final EnvADSR envelope;
	private final EnvADSR modEnvelope;

	private final WaveTableOscillator osc1_va = new WaveTableOscillator(IOscillator.PRIMARY_OSC);
	private final WaveTableOscillator osc2_va = new WaveTableOscillator(IOscillator.SECONDARY_OSC);
	private final AdditiveOscillator osc1_add = new AdditiveOscillator(IOscillator.PRIMARY_OSC);
	private final AdditiveOscillator osc2_add = new AdditiveOscillator(IOscillator.SECONDARY_OSC);
	private final ExciterOscillator osc1_pluck = new ExciterOscillator(IOscillator.PRIMARY_OSC);
	private final ExciterOscillator osc2_pluck = new ExciterOscillator(IOscillator.SECONDARY_OSC);
	
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
	
	private RouteFilters filtersAllOff = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final float[] outL, final float[] outR) {
			filter1.processSample(in1, sampleNo);
			filter2.processSample(in2, sampleNo);
			outL[sampleNo] += (in1 + in2*width)*env;
			outR[sampleNo] += (in1*width + in2)*env;
		}
		@Override
		public void processBuffer(final int nframes, final float[] in1, final float[] in2, final float[] envBuf, final float width, final float[] outL, final float[] outR) {
			for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
				final float a = in1[sampleNo];
				final float b = in2[sampleNo];
				filter1.processSample(a, sampleNo);
				filter2.processSample(b, sampleNo);
				final float env = envBuf[sampleNo];
				outL[sampleNo] += (a + b*width)*env;
				outR[sampleNo] += (a*width + b)*env;
			}
		}
	};
	
	private RouteFilters filtersSerial1On2Off = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final float[] outL, final float[] outR) {
			filter2.processSample(in2, sampleNo);
			final float val = filter1.processSample(in1+in2, sampleNo);
			// serial is mono only
			outL[sampleNo] += val*env;
			outR[sampleNo] += val*env;
		}
		@Override
		public void processBuffer(final int nframes, final float[] in1, final float[] in2, final float[] envBuf, final float width, final float[] outL, final float[] outR) {
			for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
				final float b = in2[sampleNo];
				filter2.processSample(b, sampleNo);
				final float val = filter1.processSample(in1[sampleNo]+b, sampleNo);
				final float scaled = val*envBuf[sampleNo];
				outL[sampleNo] += scaled;
				outR[sampleNo] += scaled;
			}
		}
	};
	
	private RouteFilters filtersSerial1On2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final float[] outL, final float[] outR) {
			// serial is mono only
			final float val = filter2.processSample(filter1.processSample(in1+in2, sampleNo), sampleNo);
			outL[sampleNo] += val*env;
			outR[sampleNo] += val*env;
		}
		@Override
		public void processBuffer(final int nframes, final float[] in1, final float[] in2, final float[] envBuf, final float width, final float[] outL, final float[] outR) {
			for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
				final float val = filter2.processSample(filter1.processSample(in1[sampleNo]+in2[sampleNo], sampleNo), sampleNo);
				final float scaled = val*envBuf[sampleNo];
				outL[sampleNo] += scaled;
				outR[sampleNo] += scaled;
			}
		}
	};
	
	private RouteFilters filtersSerial1Off2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final float[] outL, final float[] outR) {
			filter1.processSample(in1, sampleNo);
			final float val = filter2.processSample(in1+in2, sampleNo);
			// serial is mono only
			outL[sampleNo] += val*env;
			outR[sampleNo] += val*env;
		}
		@Override
		public void processBuffer(final int nframes, final float[] in1, final float[] in2, final float[] envBuf, final float width, final float[] outL, final float[] outR) {
			for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
				final float a = in1[sampleNo];
				filter1.processSample(a, sampleNo);
				final float val = filter2.processSample(a+in2[sampleNo], sampleNo);
				final float scaled = val*envBuf[sampleNo];
				outL[sampleNo] += scaled;
				outR[sampleNo] += scaled;
			}
		}
	};
	
	private RouteFilters filtersParallel1On2Off = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final float[] outL, final float[] outR) {
			filter2.processSample(in2, sampleNo);
			final float val = in1+in2;
			final float filtered1 = filter1.processSample(val, sampleNo)*P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			outL[sampleNo] += (filtered1 + in2*width)*env;
			outR[sampleNo] += (filtered1*width + in2)*env;
		}
		@Override
		public void processBuffer(final int nframes, final float[] in1, final float[] in2, final float[] envBuf, final float width, final float[] outL, final float[] outR) {
			final float mixHigh = P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
				final float a = in1[sampleNo];
				final float b = in2[sampleNo];
				filter2.processSample(b, sampleNo);
				final float filtered1 = filter1.processSample(a+b, sampleNo) * mixHigh;
				final float env = envBuf[sampleNo];
				outL[sampleNo] += (filtered1 + b*width)*env;
				outR[sampleNo] += (filtered1*width + b)*env;
			}
		}
	};
	
	private RouteFilters filtersParallel1On2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final float[] outL, final float[] outR) {
			final float val = in1+in2;
			final float filtered1 = filter1.processSample(val, sampleNo)*P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			final float filtered2 = filter2.processSample(val, sampleNo)*P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			outL[sampleNo] += (filtered1 + filtered2*width)*env;
			outR[sampleNo] += (filtered1*width + filtered2)*env;
		}
		@Override
		public void processBuffer(final int nframes, final float[] in1, final float[] in2, final float[] envBuf, final float width, final float[] outL, final float[] outR) {
			final float mixHigh = P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			final float mixLow = P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
				final float val = in1[sampleNo] + in2[sampleNo];
				final float filtered1 = filter1.processSample(val, sampleNo) * mixHigh;
				final float filtered2 = filter2.processSample(val, sampleNo) * mixLow;
				final float env = envBuf[sampleNo];
				outL[sampleNo] += (filtered1 + filtered2*width)*env;
				outR[sampleNo] += (filtered1*width + filtered2)*env;
			}
		}
	};
	
	private RouteFilters filtersParallel1Off2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final float[] outL, final float[] outR) {
			filter1.processSample(in1, sampleNo);
			final float val = in1+in2;
			final float filtered2 = filter2.processSample(val, sampleNo)*P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			outL[sampleNo] += (in1 + filtered2*width)*env;
			outR[sampleNo] += (in1*width + filtered2)*env;
		}
		@Override
		public void processBuffer(final int nframes, final float[] in1, final float[] in2, final float[] envBuf, final float width, final float[] outL, final float[] outR) {
			final float mixLow = P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
				final float a = in1[sampleNo];
				final float b = in2[sampleNo];
				filter1.processSample(a, sampleNo);
				final float filtered2 = filter2.processSample(a+b, sampleNo) * mixLow;
				final float env = envBuf[sampleNo];
				outL[sampleNo] += (a + filtered2*width)*env;
				outR[sampleNo] += (a*width + filtered2)*env;
			}
		}
	};
	
	private RouteFilters filtersPerOsc1On2Off = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final float[] outL, final float[] outR) {
			filter2.processSample(in2, sampleNo);
			final float filtered1 = filter1.processSample(in1, sampleNo)*P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			outL[sampleNo] += (filtered1 + in2*width)*env;
			outR[sampleNo] += (filtered1*width + in2)*env;
		}
		@Override
		public void processBuffer(final int nframes, final float[] in1, final float[] in2, final float[] envBuf, final float width, final float[] outL, final float[] outR) {
			final float mixHigh = P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
				final float a = in1[sampleNo];
				final float b = in2[sampleNo];
				filter2.processSample(b, sampleNo);
				final float filtered1 = filter1.processSample(a, sampleNo) * mixHigh;
				final float env = envBuf[sampleNo];
				outL[sampleNo] += (filtered1 + b*width)*env;
				outR[sampleNo] += (filtered1*width + b)*env;
			}
		}
	};
	
	private RouteFilters filtersPerOsc1On2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final float[] outL, final float[] outR) {
			final float filtered1 = filter1.processSample(in1, sampleNo)*P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			final float filtered2 = filter2.processSample(in2, sampleNo)*P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			outL[sampleNo] += (filtered1 + filtered2*width)*env;
			outR[sampleNo] += (filtered1*width + filtered2)*env;
		}
		@Override
		public void processBuffer(final int nframes, final float[] in1, final float[] in2, final float[] envBuf, final float width, final float[] outL, final float[] outR) {
			final float mixHigh = P.VALMIXHIGH[P.FILTER_PARALLEL_MIX];
			final float mixLow = P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
				final float filtered1 = filter1.processSample(in1[sampleNo], sampleNo) * mixHigh;
				final float filtered2 = filter2.processSample(in2[sampleNo], sampleNo) * mixLow;
				final float env = envBuf[sampleNo];
				outL[sampleNo] += (filtered1 + filtered2*width)*env;
				outR[sampleNo] += (filtered1*width + filtered2)*env;
			}
		}
	};
	
	private RouteFilters filtersPerOsc1Off2On = new RouteFilters(filter1, filter2) {
		@Override
		public void process(final float in1, final float in2, final float env, final float width, final int sampleNo, final float[] outL, final float[] outR) {
			filter1.processSample(in1, sampleNo);
			final float filtered2 = filter2.processSample(in2, sampleNo)*P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			outL[sampleNo] += (in1 + filtered2*width)*env;
			outR[sampleNo] += (in1*width + filtered2)*env;
		}
		@Override
		public void processBuffer(final int nframes, final float[] in1, final float[] in2, final float[] envBuf, final float width, final float[] outL, final float[] outR) {
			final float mixLow = P.VALMIXLOW[P.FILTER_PARALLEL_MIX];
			for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
				final float a = in1[sampleNo];
				filter1.processSample(a, sampleNo);
				final float filtered2 = filter2.processSample(in2[sampleNo], sampleNo) * mixLow;
				final float env = envBuf[sampleNo];
				outL[sampleNo] += (a + filtered2*width)*env;
				outR[sampleNo] += (a*width + filtered2)*env;
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
			case VIRTUAL_ANALOG:
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
			modEnvelope.noteOn(1);
			AnalogSynth.lastTriggeredFrequency = frequency;
		}
		else {
			envelope.queueNoteOn(velocity, new Runnable() {
				@Override
				public void run() {
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
					case VIRTUAL_ANALOG:
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
					modEnvelope.noteOn(1);
					AnalogSynth.lastTriggeredFrequency = frequency;
				}
			});
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
		case VIRTUAL_ANALOG:
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
		filter1.noteOff();
		filter2.noteOff();
	}
	
	private float noiseLevel = 0;
	private float noiseModAmount = 0;
	
	public void process(final float[][] outbuffers, final int nframes) {
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
		case VIRTUAL_ANALOG:
		default:
			osc1=osc1_va;
			osc2=osc2_va;
			break;
		}
		final float osc1Vol = P.VALMIXHIGH[P.OSC_1_2_MIX]*P.VALX[P.OSC_GAIN]*.75f;
		final float osc2Vol = P.VALMIXLOW[P.OSC_1_2_MIX]*P.VALX[P.OSC_GAIN]*.75f;
		noiseLevel = P.VALX[P.OSC_NOISE_LEVEL];
		noiseModAmount = P.VALXC[P.MOD_ENV1_NOISE_AMOUNT];
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
		final float modVol = P.VAL[P.MOD_VOL_AMOUNT];
		filter1.updateFreqResponse();
		filter2.updateFreqResponse();

		if (USE_BUFFER_PATH) {
			// Pre-render mod envelope so both oscillators see identical trajectory.
			for (int i=0; i<nframes; i++) {
				modEnvBuf[i] = modEnvelope.nextValue();
			}
			// Mode-specific dispatch: one buffer call per oscillator, statically typed.
			switch (P.VAL_OSCILLATOR_MODE) {
			case ADDITIVE:
				osc1_add.processBuffer1st(nframes, osc1Vol, syncBuffer, am_buffer, modEnvBuf, osc1OutBuf);
				osc2_add.processBuffer2nd(nframes, osc2Vol, syncBuffer, am_buffer, modEnvBuf, osc2OutBuf);
				break;
			case EXITER:
				osc1_pluck.processBuffer1st(nframes, osc1Vol, syncBuffer, am_buffer, modEnvBuf, osc1OutBuf);
				osc2_pluck.processBuffer2nd(nframes, osc2Vol, syncBuffer, am_buffer, modEnvBuf, osc2OutBuf);
				break;
			case VIRTUAL_ANALOG:
			default:
				osc1_va.processBuffer1st(nframes, osc1Vol, syncBuffer, am_buffer, modEnvBuf, osc1OutBuf);
				osc2_va.processBuffer2nd(nframes, osc2Vol, syncBuffer, am_buffer, modEnvBuf, osc2OutBuf);
				break;
			}
			// Pre-mix noise into oscillator buffers and pre-render envelope*LFO into envBuf,
			// so the filter route runs as a single buffer-rate dispatch instead of per sample.
			for (int i=0; i<nframes; i++) {
				noiseX2 += noiseX1;
				noiseX1 ^= noiseX2;
				noise_val = (noiseLevel + modEnvBuf[i]*noiseModAmount) * (noiseX2 * NOISE_SCALE) * .5f;
				osc1MixBuf[i] = osc1OutBuf[i] + noise_val;
				osc2MixBuf[i] = osc2OutBuf[i] + noise_val;
				envBuf[i] = envelope.nextValue() * (1 + LFO.lfoAmountAdd(i, modVol));
			}
			filterRoute.processBuffer(nframes, osc1MixBuf, osc2MixBuf, envBuf, width, outL, outR);
			// am_buffer is fully overwritten by OSC1 each buffer, no per-sample reset needed.
		}
		else {
			for (int i=0;i<nframes;i++) {
				modEnvelope.nextValue();
				noiseX2 += noiseX1;
				noiseX1 ^= noiseX2;
				noise_val = (noiseLevel + modEnvelope.outValue*noiseModAmount) * (noiseX2 * NOISE_SCALE) * .5f;
				osc1_val = osc1.processSample1st(i, osc1Vol, syncBuffer, am_buffer, modEnvelope) + noise_val;
				osc2_val = osc2.processSample2nd(i, osc2Vol, syncBuffer, am_buffer, modEnvelope) + noise_val;
				filterRoute.process(osc1_val, osc2_val, envelope.nextValue()*(1+LFO.lfoAmountAdd(i, modVol)), width, i, outL, outR);
				am_buffer[i] = 0;
			}
		}
	}
	
	
	@SuppressWarnings("unused")	
	private static abstract class RouteFilters {
		
		private MultiModeFilter filter1;
		private MultiModeFilter filter2;
		
		public RouteFilters(MultiModeFilter filter1, MultiModeFilter filter2) {
			this.filter1 = filter1;
			this.filter2 = filter2;
		}		
		
//		public abstract float process(float in1, float in2, int sampleNo);
		public abstract void process(float in1, float in2, float env, float width, int sampleNo, float[] outL, float[] outR);

		/**
		 * Buffer-rate variant of {@link #process}. Reads pre-mixed oscillator+noise
		 * inputs and a pre-rendered envelope buffer. Each subclass runs its own tight
		 * monomorphic loop, so the call site in the voice's mix loop becomes one
		 * vcall per buffer instead of one per sample.
		 */
		public abstract void processBuffer(int nframes, float[] in1, float[] in2, float[] envBuf, float width, float[] outL, float[] outR);

	}

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(AnalogSynthVoice.class);
}
