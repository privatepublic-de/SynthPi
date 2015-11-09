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
	
	public ExciterOscillator(boolean primaryOrSecondary) {
		super(primaryOrSecondary);
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
			if (P.IS[P.OSC2_SYNC] && syncOnFrameBuffer[sampleNo]) {
				index = 0;
			}
		}
		final float freq = effectiveFrequency*LFO.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR
				* LFO.lfoAmountAsymm(sampleNo, P.VALXC[P.MOD_PITCH2_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT]);
		final int playBufferLen = Math.min((int)(P.SAMPLE_RATE_HZ / Math.max(freq,1)), bufferLen);
		if (playBufferLen>1) {
//			final int prefIndex = (index-1+playBufferLen)%playBufferLen;
			int prefIndex = index-1;
			if (index-1<0) {
				prefIndex = playBufferLen-1;
			}
			final float val = (delayBuffer[index]*(1-P.VAL[P.OSC2_WAVE])+delayBuffer[prefIndex]*P.VAL[P.OSC2_WAVE]);
			delayBuffer[index] = val;
			index = ++index<playBufferLen?index:0; // (++index)%playBufferLen;
			if (ampmod) {
				return (am_buffer[sampleNo]*val*volume);
			}
			else {
				return val*volume;
			}
		}
		else {
			return 0;
		}
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
