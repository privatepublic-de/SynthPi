package de.privatepublic.pi.synth.modules.osc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.IPitchBendReceiver;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.util.FastCalc;

public class WaveTableOscillator extends OscillatorBase implements IPitchBendReceiver {

	private float tableIndex = 0;
	private final int wavesetparamindex;
	private final LFO lfo;
//	private static float[] ignoreBuffer = new float[P.SAMPLE_BUFFER_SIZE];

	public WaveTableOscillator(boolean primaryOrSecondary, LFO lfo) {
		super(primaryOrSecondary);
		this.lfo = lfo;
		wavesetparamindex = primaryOrSecondary?P.OSC1_WAVE_SET:P.OSC2_WAVE_SET;
		MidiHandler.registerReceiver(this);
	}
	

	private boolean ampmod;
	
	
	@Override
	public float processSample1st(final int sampleNo, final float vol, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
		if (effectiveFrequency!=targetFrequency) {
			if (effectiveFrequency<targetFrequency) {
				effectiveFrequency += glideStepSize;
			}
			else if (effectiveFrequency>targetFrequency) {
				effectiveFrequency -= glideStepSize;
			}
			if (Math.abs(effectiveFrequency-targetFrequency)<glideStepSize) {
				effectiveFrequency = targetFrequency;
			}
		}
		final float freq = effectiveFrequency*LFO.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR;
		final float waveform = P.VAL[P.OSC1_WAVE] + LFO.lfoAmountAdd(sampleNo, P.VALXC[P.MOD_WAVE1_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_WAVE_AMOUNT]);
		final float waveformpos;
		if (waveform>1) {
			waveformpos = WaveTables.WAVE_INDEX_MAX;
		}
		else if (waveform<0) {
			waveformpos = 0;
		}
		else {
			waveformpos = waveform * WaveTables.WAVE_INDEX_MAX;
		}
		final int waveformBase = (int)waveformpos;
		final float waveformFract = waveformpos-waveformBase;
		final int wavesetindex = (int)(P.VAL[wavesetparamindex]*WaveTables.WAVE_SET_COUNT);// TODO calculate in P.set
		final int octindex = WaveTables.OCT_INDEXES_FOR_FREQUENCY[(int)freq];
		final int indexBase = (int)tableIndex;
		final float indexFrac = tableIndex-indexBase;
		final float[] wave1 =   WaveTables.WAVES[wavesetindex][waveformBase][octindex];
		float val = wave1[indexBase];
		val += ((wave1[indexBase+1]-val)*indexFrac);
		if (waveformBase<WaveTables.WAVE_INDEX_MAX) {
			final float[] wave2 =   WaveTables.WAVES[wavesetindex][waveformBase+1][octindex];
			float val2 = wave2[indexBase];
			val2 += ((wave2[indexBase+1]-val2)*indexFrac);
			val = (val*(1-waveformFract) + val2*waveformFract);
		}
		am_buffer[sampleNo] = val;
		tableIndex += WaveTables.TABLE_INCREMENT*freq;
		if (tableIndex >= WaveTables.TABLE_LENGTH) {
			tableIndex -= WaveTables.TABLE_LENGTH;
			syncOnFrameBuffer[sampleNo] = true;
		}
		else {
			syncOnFrameBuffer[sampleNo] = false;
		}
		return val*vol;
	}
	
	/**
	 * Buffer-rate variant of {@link #processSample1st}. Runs its own per-sample loop
	 * with every {@code P.*} read hoisted, so the call site in the voice loop becomes
	 * a single monomorphic dispatch per audio buffer instead of one virtual call per
	 * sample. Caller must pre-render {@code modEnvBuf[i] = modEnvelope.nextValue()} so
	 * both oscillators see the same trajectory.
	 */
	public void processBuffer1st(final int nframes, final float vol, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final float[] modEnvBuf, final float[] outBuf) {
		// Hoist parameter reads — these don't change within an audio buffer.
		final float pitchBend = P.PITCH_BEND_FACTOR;
		final float pitchDepth = P.VALXC[P.MOD_PITCH_AMOUNT];
		final float pitchModEnvDepth = P.VALXC[P.MOD_ENV1_PITCH_AMOUNT];
		final float pitchModEnv2Depth = P.VALXC[P.MOD_ENV2_PITCH_AMOUNT];
		final float pitchModPressDepth = P.VALXC[P.MOD_PRESS_PITCH_AMOUNT];
		final float pitchModKeyDepth = P.VALXC[P.MOD_KEY_PITCH1_AMOUNT];
		final float wfBase = P.VAL[P.OSC1_WAVE];
		final float wfDepth = P.VALXC[P.MOD_WAVE1_AMOUNT];
		final float wfModEnvDepth = P.VALXC[P.MOD_ENV1_WAVE_AMOUNT];
		final float wfEnv2Depth = P.VALXC[P.MOD_ENV2_WAVE1_AMOUNT];
		final float wfPressDepth = P.VALXC[P.MOD_PRESS_WAVE1_AMOUNT];
		final float wfKeyDepth = P.VALXC[P.MOD_KEY_WAVE1_AMOUNT];
		final float wfVelDepth = P.VALXC[P.MOD_VEL_WAVE1_AMOUNT];
		final float modAmount = P.MOD_AMOUNT_COMBINED;
		final float env2v = env2Val;
		final float pressure = P.CHANNEL_PRESSURE;
		final float kn = keyNorm;
		final float nv = noteVelocity;
		final float wh = P.VAL[P.MOD_WHEEL];
		final float wfWheelDepth = P.VALXC[P.MOD_WHEEL_WAVE1_AMOUNT];
		final float pitchWheelDepth = P.VALXC[P.MOD_WHEEL_PITCH1_AMOUNT];
		final int wavesetindex = (int)(P.VAL[wavesetparamindex]*WaveTables.WAVE_SET_COUNT);
		final float[][][] wavesetTable = WaveTables.WAVES[wavesetindex];
		final int waveIndexMax = WaveTables.WAVE_INDEX_MAX;
		final int tableLen = WaveTables.TABLE_LENGTH;
		final float tableInc = WaveTables.TABLE_INCREMENT;
		final int[] octIndexFor = WaveTables.OCT_INDEXES_FOR_FREQUENCY;

		// Pull mutable instance state into locals so the JIT can keep them in registers.
		float effFreq = effectiveFrequency;
		float idx = tableIndex;
		final float targetFreq = targetFrequency;
		final float glide = glideStepSize;

		for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
			if (effFreq != targetFreq) {
				if (effFreq < targetFreq) effFreq += glide;
				else if (effFreq > targetFreq) effFreq -= glide;
				if (Math.abs(effFreq - targetFreq) < glide) effFreq = targetFreq;
			}
			final float lfoVal = lfo.bufferedValueAt(sampleNo);
			final float modEnvVal = modEnvBuf[sampleNo];
			final float freq = effFreq * ((1 - lfoVal*modAmount*pitchDepth)
					+ modEnvVal*pitchModEnvDepth + env2v*pitchModEnv2Depth
					+ pressure*pitchModPressDepth + kn*pitchModKeyDepth + wh*pitchWheelDepth) * pitchBend;
			final float waveform = wfBase + (lfoVal*modAmount*wfDepth) + modEnvVal*wfModEnvDepth
					+ env2v*wfEnv2Depth + pressure*wfPressDepth + kn*wfKeyDepth + nv*wfVelDepth + wh*wfWheelDepth;
			final float waveformpos;
			if (waveform > 1)      waveformpos = waveIndexMax;
			else if (waveform < 0) waveformpos = 0;
			else                   waveformpos = waveform * waveIndexMax;
			final int waveformBase = (int)waveformpos;
			final float waveformFract = waveformpos - waveformBase;
			final int octindex = octIndexFor[(int)freq];
			final int indexBase = (int)idx;
			final float indexFrac = idx - indexBase;
			final float[] wave1 = wavesetTable[waveformBase][octindex];
			float val = wave1[indexBase];
			val += ((wave1[indexBase+1] - val) * indexFrac);
			if (waveformBase < waveIndexMax) {
				final float[] wave2 = wavesetTable[waveformBase+1][octindex];
				float val2 = wave2[indexBase];
				val2 += ((wave2[indexBase+1] - val2) * indexFrac);
				val = val*(1-waveformFract) + val2*waveformFract;
			}
			am_buffer[sampleNo] = val;
			idx += tableInc * freq;
			if (idx >= tableLen) {
				idx -= tableLen;
				syncOnFrameBuffer[sampleNo] = true;
			}
			else {
				syncOnFrameBuffer[sampleNo] = false;
			}
			outBuf[sampleNo] = val * vol;
		}

		effectiveFrequency = effFreq;
		tableIndex = idx;
	}

	/**
	 * Buffer-rate variant of {@link #processSample2nd}. See {@link #processBuffer1st}.
	 * Reads {@code am_buffer} and {@code syncOnFrameBuffer} that were filled by the
	 * companion OSC1 call earlier in the same audio buffer.
	 */
	public void processBuffer2nd(final int nframes, final float vol, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final float[] modEnvBuf, final float[] outBuf) {
		final float pitchBend = P.PITCH_BEND_FACTOR;
		final float pitchDepth = P.VALXC[P.MOD_PITCH_AMOUNT];
		final float pitchModEnvDepth = P.VALXC[P.MOD_ENV1_PITCH_AMOUNT];
		final float pitchModEnv2Depth = P.VALXC[P.MOD_ENV2_PITCH_AMOUNT];
		final float pitchModPressDepth = P.VALXC[P.MOD_PRESS_PITCH_AMOUNT];
		final float pitchModKeyDepth = P.VALXC[P.MOD_KEY_PITCH1_AMOUNT];
		final float pitch2Depth = P.VALXC[P.MOD_PITCH2_AMOUNT];
		final float pitch2ModEnvDepth = P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT];
		final float pitch2ModEnv2Depth = P.VALXC[P.MOD_ENV2_PITCH2_AMOUNT];
		final float pitch2ModPressDepth = P.VALXC[P.MOD_PRESS_PITCH2_AMOUNT];
		final float pitch2ModKeyDepth = P.VALXC[P.MOD_KEY_PITCH2_AMOUNT];
		final float wfBase = P.VAL[P.OSC2_WAVE];
		final float wfDepth = P.VALXC[P.MOD_WAVE2_AMOUNT];
		final float wfModEnvDepth = P.VALXC[P.MOD_ENV1_WAVE2_AMOUNT];
		final float wfEnv2Depth = P.VALXC[P.MOD_ENV2_WAVE2_AMOUNT];
		final float wfPressDepth = P.VALXC[P.MOD_PRESS_WAVE2_AMOUNT];
		final float wfKeyDepth = P.VALXC[P.MOD_KEY_WAVE2_AMOUNT];
		final float wfVelDepth = P.VALXC[P.MOD_VEL_WAVE2_AMOUNT];
		final float modAmount = P.MOD_AMOUNT_COMBINED;
		final float ampModAmount = P.VALC[P.MOD_ENV1_AM_AMOUNT];
		final float lfoRingAmt = P.VALXC[P.MOD_LFO_RING_AMOUNT];
		final float env2RingAmt = P.VALXC[P.MOD_ENV2_RING_AMOUNT];
		final float pressRingAmt = P.VALXC[P.MOD_PRESS_RING_AMOUNT];
		final float keyRingAmt = P.VALXC[P.MOD_KEY_RING_AMOUNT];
		final float velRingAmt = P.VALXC[P.MOD_VEL_RING_AMOUNT];
		final float wheelRingAmt = P.VALXC[P.MOD_WHEEL_RING_AMOUNT];
		final float env2v = env2Val;
		final float pressure = P.CHANNEL_PRESSURE;
		final float kn = keyNorm;
		final float nv = noteVelocity;
		final float wh = P.VAL[P.MOD_WHEEL];
		final float pitchWheelDepth  = P.VALXC[P.MOD_WHEEL_PITCH1_AMOUNT];
		final float pitch2WheelDepth = P.VALXC[P.MOD_WHEEL_PITCH2_AMOUNT];
		final float wfWheelDepth     = P.VALXC[P.MOD_WHEEL_WAVE2_AMOUNT];
		final float osc2AmBase = P.VAL[P.OSC2_AM];
		final boolean osc2AmIs = P.IS[P.OSC2_AM];
		final boolean osc2SyncIs = P.IS[P.OSC2_SYNC];
		final int wavesetindex = (int)(P.VAL[wavesetparamindex]*WaveTables.WAVE_SET_COUNT);
		final float[][][] wavesetTable = WaveTables.WAVES[wavesetindex];
		final int waveIndexMax = WaveTables.WAVE_INDEX_MAX;
		final int tableLen = WaveTables.TABLE_LENGTH;
		final float tableInc = WaveTables.TABLE_INCREMENT;
		final int[] octIndexFor = WaveTables.OCT_INDEXES_FOR_FREQUENCY;

		float effFreq = effectiveFrequency;
		float idx = tableIndex;
		boolean ampmodLocal = ampmod;
		final float targetFreq = targetFrequency;
		final float glide = glideStepSize;

		for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
			if (ampmodLocal && !osc2AmIs) {
				effFreq = targetFreq;
			}
			final float modEnvVal = modEnvBuf[sampleNo];
			final float lfoVal = lfo.bufferedValueAt(sampleNo);
			final float ampamount = FastCalc.ensureRange(osc2AmBase
					+ lfoVal*modAmount*lfoRingAmt
					+ modEnvVal*ampModAmount
					+ env2v*env2RingAmt
					+ pressure*pressRingAmt
					+ kn*keyRingAmt
					+ nv*velRingAmt
					+ wh*wheelRingAmt, 0, 1);
			ampmodLocal = ampamount > 0 || ampModAmount != 0 || lfoRingAmt != 0
					|| env2RingAmt != 0 || pressRingAmt != 0 || keyRingAmt != 0
					|| velRingAmt != 0 || wheelRingAmt != 0;
			if (ampmodLocal) {
				effFreq = targetFreq * (ampamount * 4);
			}
			else {
				if (effFreq != targetFreq) {
					if (effFreq < targetFreq) effFreq += glide;
					else if (effFreq > targetFreq) effFreq -= glide;
					if (Math.abs(effFreq - targetFreq) < glide) effFreq = targetFreq;
				}
			}
			final float pitchLfo = (1 - lfoVal*modAmount*pitchDepth)
					+ modEnvVal*pitchModEnvDepth + env2v*pitchModEnv2Depth
					+ pressure*pitchModPressDepth + kn*pitchModKeyDepth + wh*pitchWheelDepth;
			final float pitchAsymm = ((lfoVal+1)*modAmount*0.5f*pitch2Depth) + 1
					+ modEnvVal*pitch2ModEnvDepth + env2v*pitch2ModEnv2Depth
					+ pressure*pitch2ModPressDepth + kn*pitch2ModKeyDepth + wh*pitch2WheelDepth;
			final float freq = effFreq * pitchLfo * pitchBend * pitchAsymm;
			final float waveform = wfBase + (lfoVal*modAmount*wfDepth) + modEnvVal*wfModEnvDepth
					+ env2v*wfEnv2Depth + pressure*wfPressDepth + kn*wfKeyDepth + nv*wfVelDepth + wh*wfWheelDepth;
			final float waveformpos;
			if (waveform > 1)      waveformpos = waveIndexMax;
			else if (waveform < 0) waveformpos = 0;
			else                   waveformpos = waveform * waveIndexMax;
			final int waveformBase = (int)waveformpos;
			final float waveformFract = waveformpos - waveformBase;
			final int octindex = octIndexFor[(int)freq];
			final int indexBase = (int)idx;
			final float indexFrac = idx - indexBase;
			final float[] wave1 = wavesetTable[waveformBase][octindex];
			float val = wave1[indexBase];
			val += ((wave1[indexBase+1] - val) * indexFrac);
			if (waveformBase < waveIndexMax) {
				final float[] wave2 = wavesetTable[waveformBase+1][octindex];
				float val2 = wave2[indexBase];
				val2 += ((wave2[indexBase+1] - val2) * indexFrac);
				val = val*(1-waveformFract) + val2*waveformFract;
			}
			// Sync: val is the "before" value. Compute afterVal at index 0, set up
			// correction so output is continuous, then reset the phase.
			if (!ampmodLocal && osc2SyncIs && syncOnFrameBuffer[sampleNo]) {
				float afterVal = wave1[0];
				if (waveformBase < waveIndexMax) {
					afterVal = afterVal*(1-waveformFract) + wavesetTable[waveformBase+1][octindex][0]*waveformFract;
				}
				syncCorrection += val - afterVal;
				syncFadeRemaining = SYNC_FADE_SAMPLES;
				syncCorrectionStep = syncCorrection / SYNC_FADE_SAMPLES;
				idx = 0;
				val = afterVal;
			}
			idx += tableInc * freq;
			if (idx >= tableLen) {
				idx -= tableLen;
			}
			outBuf[sampleNo] = ampmodLocal ? am_buffer[sampleNo]*val*vol : (val + applySyncCorrection())*vol;
		}

		effectiveFrequency = effFreq;
		tableIndex = idx;
		ampmod = ampmodLocal;
	}

	@Override
	public float processSample2nd(final int sampleNo, final float vol, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
		// second osc
		if (ampmod && !P.IS[P.OSC2_AM]) {
			effectiveFrequency = targetFrequency;
		}
		final float ampModAmount = P.VALC[P.MOD_ENV1_AM_AMOUNT];
		final float ampamount = FastCalc.ensureRange(P.VAL[P.OSC2_AM]+modEnvelope.outValue*ampModAmount, 0, 1);
		ampmod = ampamount>0 || ampModAmount!=0;
		if (ampmod) {
			effectiveFrequency = targetFrequency*((ampamount*4));			
		}
		else {
			if (effectiveFrequency!=targetFrequency) {
				if (effectiveFrequency<targetFrequency) {
					effectiveFrequency += glideStepSize;
				}
				else if (effectiveFrequency>targetFrequency) {
					effectiveFrequency -= glideStepSize;
				}
				if (Math.abs(effectiveFrequency-targetFrequency)<glideStepSize) {
					effectiveFrequency = targetFrequency;
				}
			}
		}
		final float freq =
				effectiveFrequency*LFO.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR
				* LFO.lfoAmountAsymm(sampleNo, P.VALXC[P.MOD_PITCH2_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT]);

		final float waveform = P.VAL[P.OSC2_WAVE] + LFO.lfoAmountAdd(sampleNo, P.VALXC[P.MOD_WAVE2_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_WAVE_AMOUNT]);
		final float waveformpos;
		if (waveform>1) {
			waveformpos = WaveTables.WAVE_INDEX_MAX;
		}
		else if (waveform<0) {
			waveformpos = 0;
		}
		else {
			waveformpos = waveform * WaveTables.WAVE_INDEX_MAX;
		}
		final int waveformBase = (int)waveformpos;
		final float waveformFract = waveformpos-waveformBase;
		final int wavesetindex = (int)(P.VAL[wavesetparamindex]*WaveTables.WAVE_SET_COUNT);// TODO calculate in P.set
		final int octindex = WaveTables.OCT_INDEXES_FOR_FREQUENCY[(int)freq];
		final int indexBase = (int)tableIndex;
		final float indexFrac = tableIndex-indexBase;
		final float[] wave1 =   WaveTables.WAVES[wavesetindex][waveformBase][octindex];
		float val = wave1[indexBase];
		val += ((wave1[indexBase+1]-val)*indexFrac);
		if (waveformBase<WaveTables.WAVE_INDEX_MAX) {
			final float[] wave2 =   WaveTables.WAVES[wavesetindex][waveformBase+1][octindex];
			float val2 = wave2[indexBase];
			val2 += ((wave2[indexBase+1]-val2)*indexFrac);
			val = (val*(1-waveformFract) + val2*waveformFract);
		}
		if (!ampmod && P.IS[P.OSC2_SYNC] && syncOnFrameBuffer[sampleNo]) {
			float afterVal = wave1[0];
			if (waveformBase < WaveTables.WAVE_INDEX_MAX) {
				afterVal = afterVal*(1-waveformFract) + WaveTables.WAVES[wavesetindex][waveformBase+1][octindex][0]*waveformFract;
			}
			syncCorrection += val - afterVal;
			syncFadeRemaining = SYNC_FADE_SAMPLES;
			syncCorrectionStep = syncCorrection / SYNC_FADE_SAMPLES;
			tableIndex = 0;
			val = afterVal;
		}
		tableIndex += WaveTables.TABLE_INCREMENT*freq;
		if (tableIndex >= WaveTables.TABLE_LENGTH) {
			tableIndex -= WaveTables.TABLE_LENGTH;
		}
		return ampmod ? am_buffer[sampleNo]*val*vol : (val + applySyncCorrection())*vol;
	}
	
	// sadly the above copy & paste coding approach is much more performant!
	
//	@Override
//	public float processSample1st(final int sampleNo, final float vol, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
//		if (effectiveFrequency!=targetFrequency) {
//			if (effectiveFrequency<targetFrequency) {
//				effectiveFrequency += glideStepSize;
//			}
//			else if (effectiveFrequency>targetFrequency) {
//				effectiveFrequency -= glideStepSize;
//			}
//			if (Math.abs(effectiveFrequency-targetFrequency)<glideStepSize) {
//				effectiveFrequency = targetFrequency;
//			}
//		}
//		final float freq = effectiveFrequency*P.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR;
//		final float waveform = P.VAL[P.OSC1_WAVE] + P.lfoAmountAdd(sampleNo, P.VALXC[P.MOD_WAVE_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_WAVE_AMOUNT]);
//		return process(freq, waveform, sampleNo, am_buffer, syncOnFrameBuffer)*vol;
//	}
//	
//	@Override
//	public float processSample2nd(final int sampleNo, final float vol, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
//		// second osc
//		final float ampmodVal = P.VAL[P.OSC2_AM];
//		if (ampmod && ampmodVal==0) {
//			effectiveFrequency = targetFrequency;
//		}
//		ampmod = ampmodVal>0;
//		if (ampmod) {
//			effectiveFrequency = targetFrequency*(ampmodVal*4);			
//		}
//		else {
//			if (effectiveFrequency!=targetFrequency) {
//				if (effectiveFrequency<targetFrequency) {
//					effectiveFrequency += glideStepSize;
//				}
//				else if (effectiveFrequency>targetFrequency) {
//					effectiveFrequency -= glideStepSize;
//				}
//				if (Math.abs(effectiveFrequency-targetFrequency)<glideStepSize) {
//					effectiveFrequency = targetFrequency;
//				}
//			}
//			if (P.IS[P.OSC2_SYNC] && syncOnFrameBuffer[sampleNo]) {
//				tableIndex = 0;
//			}
//		}
//		final float freq = 
//				effectiveFrequency*P.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR
//				* P.lfoAmountAsymm(sampleNo, P.VALXC[P.MOD_PITCH2_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT]);
//		final float waveform = P.VAL[P.OSC2_WAVE] + P.lfoAmountAdd(sampleNo, P.VALXC[P.MOD_WAVE_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_WAVE_AMOUNT]);
//		final float val = process(freq, waveform, sampleNo, ignoreBuffer, syncOnFrameBuffer);
//		return ampmod ? am_buffer[sampleNo]*val*vol : val*vol;
//	}
//	
//	
//	private float process(final float freq, final float waveform, final int sampleNo, final float[] am_buffer, final boolean[] syncOnFrameBuffer) {
//		final float waveformpos;
//		if (waveform>1) {
//			waveformpos = WaveTables.WAVE_INDEX_MAX;
//		}
//		else if (waveform<0) {
//			waveformpos = 0;
//		}
//		else {
//			waveformpos = waveform * WaveTables.WAVE_INDEX_MAX;
//		}
//		final int waveformBase = (int)waveformpos;
//		final float waveformFract = waveformpos-waveformBase;
//		final int wavesetindex = (int)(P.VAL[wavesetparamindex]*WaveTables.WAVE_SET_COUNT);// TODO calculate in P.set
//		final int octindex = WaveTables.OCT_INDEXES_FOR_FREQUENCY[(int)freq];
//		final int indexBase = (int)tableIndex;
//		final float indexFrac = tableIndex-indexBase;
//		final float[] wave1 =   WaveTables.WAVES[wavesetindex][waveformBase][octindex];
//		float val = wave1[indexBase];
//		val += ((wave1[indexBase+1]-val)*indexFrac);
//		if (waveformBase<WaveTables.WAVE_INDEX_MAX) {
//			final float[] wave2 =   WaveTables.WAVES[wavesetindex][waveformBase+1][octindex];
//			float val2 = wave2[indexBase];
//			val2 += ((wave2[indexBase+1]-val2)*indexFrac);
//			val = (val*(1-waveformFract) + val2*waveformFract);
//		}
//		am_buffer[sampleNo] = val;
//		tableIndex += WaveTables.TABLE_INCREMENT*freq;
//		if (tableIndex >= WaveTables.TABLE_LENGTH) {
//			tableIndex -= WaveTables.TABLE_LENGTH;
//			syncOnFrameBuffer[sampleNo] = true;
//		}
//		else {
//			syncOnFrameBuffer[sampleNo] = false;
//		}
//		return val;
//	}
	
	
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(WaveTableOscillator.class);

	@Override
	public void onPitchBend() {
		setTargetFrequency(frequency);
	}

	
}
