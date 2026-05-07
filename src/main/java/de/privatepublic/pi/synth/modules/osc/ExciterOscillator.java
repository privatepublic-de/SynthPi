package de.privatepublic.pi.synth.modules.osc;

import java.util.Random;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.IPitchBendReceiver;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.util.FastCalc;

public class ExciterOscillator extends OscillatorBase implements IPitchBendReceiver {

	private static final float[] EMPTY_BUFFER = new float[(int)(P.SAMPLE_RATE_HZ)];
	private float[] delayBuffer = new float[(int)(P.SAMPLE_RATE_HZ)];
//	private static float[] ignoreBuffer = new float[P.SAMPLE_BUFFER_SIZE];
	private int bufferLen = delayBuffer.length;
	private int index = 0;
	private int currentSampleNo = 0;
	private Random random = new Random();
	private final LFO lfo;

	public ExciterOscillator(boolean primaryOrSecondary, LFO lfo) {
		super(primaryOrSecondary);
		this.lfo = lfo;
		MidiHandler.registerReceiver(this);
	}
	
//	private static final Logger log = Logger.create();

	public void trigger(final float frequency, final float velocity) {
		super.trigger(frequency, velocity);
		final int len = (int)Math.round(P.SAMPLE_RATE_HZ / frequency);
		index = 0;
		System.arraycopy(EMPTY_BUFFER, 0, delayBuffer, 0, EMPTY_BUFFER.length);
		if (len>1) {
			float waveval = P.VALC[P.OSC1_WAVE]+LFO.lfoAmountAdd(currentSampleNo, P.VALXC[P.MOD_WAVE1_AMOUNT]);
			waveval = FastCalc.ensureRange(waveval, -1, 1);
			float sinevol = Math.max(-waveval, 0);
			float sawvol = 1 - Math.abs(waveval);
			float noisevol = Math.max(waveval, 0);
			float rampval = -1;
			float rampinc = 1/(float)len;
			float maxa = 0;
			float prevnoise = 0;
			for (int i=0;i<len;++i) {
				float val = rampval*sawvol*.67f;
				// noise with simple lowpass
				final float noise = (float) ((2*random.nextGaussian()-1)*noisevol);
				val += (noise+prevnoise)*.5;
				prevnoise = noise;
				final float quotient = i/(float)len;
				// add sine harmonics second & fundamental
				val += Math.sin(PI2*((i*2)/(float)len))*sinevol*0.34+Math.sin(PI2*quotient)*sinevol*.67;
				// simple decay
				val *= (1-quotient);
				maxa = Math.max(maxa, Math.abs(val));
				delayBuffer[i] = val;
				rampval += rampinc;
			}
			if (maxa>1) {
				// normalize
				final float factor = 1.0f/maxa;
				for (int t=0;t<len;t++) {
					delayBuffer[t] *= factor;
				}
			}
		}
	}
	
	private boolean ampmod;
	

	@Override
	public float processSample1st(int sampleNo, float volume, boolean[] syncOnFrameBuffer, float[] am_buffer, EnvADSR modEnvelope) {
		currentSampleNo = sampleNo;
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

		final int playBufferLen = Math.min((int)(P.SAMPLE_RATE_HZ / Math.max(freq,1)), bufferLen);
		if (playBufferLen>1) {
//			final int prefIndex = (index-1+playBufferLen)%playBufferLen;
			int prefIndex = index-1;
			if (index-1<0) {
				prefIndex = playBufferLen-1;
			}
			final float val = (delayBuffer[index]*(1-P.VAL[P.OSC2_WAVE])+delayBuffer[prefIndex]*P.VAL[P.OSC2_WAVE]);
			delayBuffer[index] = val;

			syncOnFrameBuffer[sampleNo] = index==0;
			index = ++index<playBufferLen?index:0; // (++index)%playBufferLen;
			am_buffer[sampleNo] = val;
			return val*volume;
		}
		else {
			return 0;
		}
	}
	
	public float processSample2nd(int sampleNo, float volume, boolean[] syncOnFrameBuffer, float[] am_buffer, EnvADSR modEnvelope) {
		// second osc
		if (ampmod && !P.IS[P.OSC2_AM]) {
			effectiveFrequency = targetFrequency;
		}
		ampmod = P.IS[P.OSC2_AM];
		if (ampmod) {
			effectiveFrequency = targetFrequency*((P.VAL[P.OSC2_AM]*4));			
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
		final float freq = effectiveFrequency*LFO.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR
				* LFO.lfoAmountAsymm(sampleNo, P.VALXC[P.MOD_PITCH2_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT]);
		final int playBufferLen = Math.min((int)(P.SAMPLE_RATE_HZ / Math.max(freq,1)), bufferLen);
		if (playBufferLen>1) {
			if (!ampmod && P.IS[P.OSC2_SYNC] && syncOnFrameBuffer[sampleNo]) {
				int prefBefore = index - 1;
				if (prefBefore < 0) prefBefore = playBufferLen - 1;
				final float wv = P.VAL[P.OSC2_WAVE];
				final float beforeVal = delayBuffer[index]*(1-wv) + delayBuffer[prefBefore]*wv;
				final float afterVal  = delayBuffer[0]*(1-wv)     + delayBuffer[playBufferLen-1]*wv;
				syncCorrection += beforeVal - afterVal;
				syncFadeRemaining = SYNC_FADE_SAMPLES;
				syncCorrectionStep = syncCorrection / SYNC_FADE_SAMPLES;
				index = 0;
			}
			int prefIndex = index-1;
			if (index-1<0) {
				prefIndex = playBufferLen-1;
			}
			final float val = (delayBuffer[index]*(1-P.VAL[P.OSC2_WAVE])+delayBuffer[prefIndex]*P.VAL[P.OSC2_WAVE]);
			delayBuffer[index] = val;
			index = ++index<playBufferLen?index:0;
			if (ampmod) {
				return (am_buffer[sampleNo]*val*volume);
			}
			else {
				return (val + applySyncCorrection())*volume;
			}
		}
		else {
			return 0;
		}
	}
	
	/**
	 * Buffer-rate variant of {@link #processSample1st}. Caller pre-renders {@code modEnvBuf}.
	 */
	public void processBuffer1st(final int nframes, final float volume, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final float[] modEnvBuf, final float[] outBuf) {
		final float pitchBend = P.PITCH_BEND_FACTOR;
		final float pitchDepth = P.VALXC[P.MOD_PITCH_AMOUNT];
		final float pitchModEnvDepth = P.VALXC[P.MOD_ENV1_PITCH_AMOUNT];
		final float pitchModEnv2Depth = P.VALXC[P.MOD_ENV2_PITCH_AMOUNT];
		final float pitchModPressDepth = P.VALXC[P.MOD_PRESS_PITCH_AMOUNT];
		final float pitchModKeyDepth = P.VALXC[P.MOD_KEY_PITCH1_AMOUNT];
		final float modAmount = P.MOD_AMOUNT_COMBINED;
		final float env2v = env2Val;
		final float pressure = P.CHANNEL_PRESSURE;
		final float kn = keyNorm;
		final float wh = P.VAL[P.MOD_WHEEL];
		final float pitchWheelDepth = P.VALXC[P.MOD_WHEEL_PITCH1_AMOUNT];
		final float osc2WaveVal = P.VAL[P.OSC2_WAVE];
		final int blen = bufferLen;
		final float sampleRate = P.SAMPLE_RATE_HZ;
		final float[] dly = delayBuffer;

		float effFreq = effectiveFrequency;
		int idx = index;
		final float targetFreq = targetFrequency;
		final float glide = glideStepSize;

		for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
			currentSampleNo = sampleNo;
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
			final int playBufferLen = Math.min((int)(sampleRate / Math.max(freq, 1)), blen);
			if (playBufferLen > 1) {
				int prefIndex = idx - 1;
				if (prefIndex < 0) prefIndex = playBufferLen - 1;
				final float val = dly[idx]*(1-osc2WaveVal) + dly[prefIndex]*osc2WaveVal;
				dly[idx] = val;
				syncOnFrameBuffer[sampleNo] = idx == 0;
				idx = ++idx < playBufferLen ? idx : 0;
				am_buffer[sampleNo] = val;
				outBuf[sampleNo] = val * volume;
			}
			else {
				am_buffer[sampleNo] = 0;
				syncOnFrameBuffer[sampleNo] = false;
				outBuf[sampleNo] = 0;
			}
		}

		effectiveFrequency = effFreq;
		index = idx;
	}

	/**
	 * Buffer-rate variant of {@link #processSample2nd}. See {@link #processBuffer1st}.
	 */
	public void processBuffer2nd(final int nframes, final float volume, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final float[] modEnvBuf, final float[] outBuf) {
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
		final float modAmount = P.MOD_AMOUNT_COMBINED;
		final float env2v = env2Val;
		final float pressure = P.CHANNEL_PRESSURE;
		final float kn = keyNorm;
		final float wh = P.VAL[P.MOD_WHEEL];
		final float pitchWheelDepth  = P.VALXC[P.MOD_WHEEL_PITCH1_AMOUNT];
		final float pitch2WheelDepth = P.VALXC[P.MOD_WHEEL_PITCH2_AMOUNT];
		final float osc2WaveVal = P.VAL[P.OSC2_WAVE];
		final boolean osc2AmIs = P.IS[P.OSC2_AM];
		final boolean osc2SyncIs = P.IS[P.OSC2_SYNC];
		final float osc2AmBase = P.VAL[P.OSC2_AM];
		final int blen = bufferLen;
		final float sampleRate = P.SAMPLE_RATE_HZ;
		final float[] dly = delayBuffer;

		float effFreq = effectiveFrequency;
		int idx = index;
		boolean ampmodLocal = ampmod;
		final float targetFreq = targetFrequency;
		final float glide = glideStepSize;

		for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
			if (ampmodLocal && !osc2AmIs) {
				effFreq = targetFreq;
			}
			ampmodLocal = osc2AmIs;
			if (ampmodLocal) {
				effFreq = targetFreq * (osc2AmBase * 4);
			}
			else {
				if (effFreq != targetFreq) {
					if (effFreq < targetFreq) effFreq += glide;
					else if (effFreq > targetFreq) effFreq -= glide;
					if (Math.abs(effFreq - targetFreq) < glide) effFreq = targetFreq;
				}
			}
			final float lfoVal = lfo.bufferedValueAt(sampleNo);
			final float modEnvVal = modEnvBuf[sampleNo];
			final float pitchLfo = (1 - lfoVal*modAmount*pitchDepth)
					+ modEnvVal*pitchModEnvDepth + env2v*pitchModEnv2Depth
					+ pressure*pitchModPressDepth + kn*pitchModKeyDepth + wh*pitchWheelDepth;
			final float pitchAsymm = ((lfoVal+1)*modAmount*0.5f*pitch2Depth) + 1
					+ modEnvVal*pitch2ModEnvDepth + env2v*pitch2ModEnv2Depth
					+ pressure*pitch2ModPressDepth + kn*pitch2ModKeyDepth + wh*pitch2WheelDepth;
			final float freq = effFreq * pitchLfo * pitchBend * pitchAsymm;
			final int playBufferLen = Math.min((int)(sampleRate / Math.max(freq, 1)), blen);
			if (playBufferLen > 1) {
				if (!ampmodLocal && osc2SyncIs && syncOnFrameBuffer[sampleNo]) {
					int prefBefore = idx - 1;
					if (prefBefore < 0) prefBefore = playBufferLen - 1;
					final float beforeVal = dly[idx]*(1-osc2WaveVal) + dly[prefBefore]*osc2WaveVal;
					final float afterVal  = dly[0]*(1-osc2WaveVal)   + dly[playBufferLen-1]*osc2WaveVal;
					syncCorrection += beforeVal - afterVal;
					syncFadeRemaining = SYNC_FADE_SAMPLES;
					syncCorrectionStep = syncCorrection / SYNC_FADE_SAMPLES;
					idx = 0;
				}
				int prefIndex = idx - 1;
				if (prefIndex < 0) prefIndex = playBufferLen - 1;
				final float val = dly[idx]*(1-osc2WaveVal) + dly[prefIndex]*osc2WaveVal;
				dly[idx] = val;
				idx = ++idx < playBufferLen ? idx : 0;
				outBuf[sampleNo] = ampmodLocal ? am_buffer[sampleNo]*val*volume : (val + applySyncCorrection())*volume;
			}
			else {
				outBuf[sampleNo] = 0;
			}
		}

		effectiveFrequency = effFreq;
		index = idx;
		ampmod = ampmodLocal;
	}

	// sadly the above copy & paste coding approach is much more performant!
	
//	@Override
//	public float processSample1st(final int sampleNo,final float volume, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
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
//		return process(freq, sampleNo, am_buffer, syncOnFrameBuffer)*volume;
//	}
//	
//	public float processSample2nd(final int sampleNo, final float volume, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
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
//				index = 0;
//			}
//		}
//		final float freq = effectiveFrequency*P.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR
//				* P.lfoAmountAsymm(sampleNo, P.VALXC[P.MOD_PITCH2_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT]);
//
//		final float val = process(freq, sampleNo, ignoreBuffer, syncOnFrameBuffer);
//		if (ampmod) {
//			return (am_buffer[sampleNo]*val*volume);
//		}
//		else {
//			return val*volume;
//		}
//	}
//	
//	private float process(float freq, int sampleNo, float[] am_buffer, boolean[] syncOnFrameBuffer) {
//		final int playBufferLen = Math.min((int)(P.SAMPLE_RATE_HZ / Math.max(freq,1)), bufferLen);
//		if (playBufferLen>1) {
//			final int prefIndex = (index-1+playBufferLen)%playBufferLen;
//			final float val = (delayBuffer[index]*(1-P.VAL[P.OSC2_WAVE])+delayBuffer[prefIndex]*P.VAL[P.OSC2_WAVE]);
//			delayBuffer[index] = val;
//
//			syncOnFrameBuffer[sampleNo] = index==0;
//			index = (++index)%playBufferLen;
//			am_buffer[sampleNo] = val;
//			return val;
//		}
//		else {
//			return 0;
//		}
//	}

	@Override
	public void onPitchBend() {
		setTargetFrequency(frequency);
	}

}
