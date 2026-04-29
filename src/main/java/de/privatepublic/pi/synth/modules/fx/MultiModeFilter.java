package de.privatepublic.pi.synth.modules.fx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.P.FilterType;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.EnvADSR.EnvelopeParamConfig;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.util.FastCalc;

public class MultiModeFilter {

	public static final float MIN_STABLE_FREQUENCY = 40f;
	public static final float MAX_STABLE_FREQUENCY = 12000f - MIN_STABLE_FREQUENCY;
	private static final float DOUBLE_SAMPLE_RATE = P.SAMPLE_RATE_HZ*2;
	
	private int p_track_keyboard = P.FILTER1_TRACK_KEYBOARD;
	private int p_freq = P.FILTER1_FREQ;
	private int p_resonance = P.FILTER1_RESONANCE;
	private int p_mod_amount = P.MOD_FILTER1_AMOUNT;
	private int p_env_depth = P.FILTER1_ENV_DEPTH;
	private int p_type = 0;
	private int p_overload = 0;
	
	
	private final EnvADSR filterEnv; // new EnvADSR(new short[] {P.FILTER1_ENV_A, P.FILTER1_ENV_D, P.FILTER1_ENV_S, P.FILTER1_ENV_R}, P.FILTER1_ENV_VELOCITY_SENS);

	
	public MultiModeFilter(int freq, int res, int mod, int env, int type, int trkkbd, int vel, int overload, EnvelopeParamConfig envelopeConfig) {
		p_freq = freq;
		p_resonance = res;
		p_mod_amount = mod;
		p_env_depth = env;
		p_type = type;
		p_track_keyboard = trkkbd;
		p_overload = overload;
		filterEnv = new EnvADSR(envelopeConfig);
	}
	
	
	public void trigger(final float freq, final float velocity) {
		filterEnv.noteOn(velocity);
//		freq_keyboard_offset = freq*AU.FILTER_KEYBOARD_TRACKING;
		if (P.VAL[p_track_keyboard]>0) {
			frqOffset = freq*P.VAL[p_track_keyboard]*2f;//freq_keyboard_offset;
		}
		else {
			frqOffset = 0;
		}
	}
	
	public void noteOff() {
		filterEnv.noteOff();
	}

	private float frq, Q, G, Gm1, G4, moogK;
	private float frqOffset, state0, state1, state2, state3;
	private float f1, damp;
//	private float y_l0, y_l1, y_b0, y_b1, y_h1; 
//	private float f1 = 0;
//	private float gain = 0;
//	private float damp, drive=0, notch, low, high, band, out, in;
	private float notch, low, high, band, out;
//	private float dnormoffset =  1.0E-25;
	float drive, dsquare, inValue;
	FilterType type;
	
	public void updateFreqResponse() {
		type = P.VAL_FILTER_TYPE_FOR[p_type];
		frq = FastCalc.ensureRange(
				(
					MIN_STABLE_FREQUENCY
					+ MAX_STABLE_FREQUENCY*P.VALX[p_freq]
					+ (MAX_STABLE_FREQUENCY * (filterEnv.outValue * P.VALXC[p_env_depth]))
					+ frqOffset
				) 
				* LFO.lfoAmount(0, P.VALXC[p_mod_amount]),
				MIN_STABLE_FREQUENCY, MAX_STABLE_FREQUENCY);

		if (type==FilterType.LOWPASS24) {
			float correctedFrq = Math.min(frq * 2.3f, P.SAMPLE_RATE_HZ * 0.45f);
			float g = (float) Math.tan(Math.PI * correctedFrq / P.SAMPLE_RATE_HZ);
			G = g / (1f + g);
			Gm1 = 1f - G;
			G4 = G * G * G * G;
			moogK = P.VAL[p_resonance] * 3.99f;
		}
		else {
			Q = P.VAL[p_resonance];
			f1 = (float) (2.0*Math.sin(Math.PI*(frq/DOUBLE_SAMPLE_RATE)));  // the fs*2 is because it's float sampled
			damp = (float) Math.min(2.0*(1.0 - FastCalc.pow(Q, 0.25f)), Math.min(2.0f, 2.0f/f1 - f1*0.5f));
			}
	}
	
	@SuppressWarnings("incomplete-switch")
	public float processSample(final float sampleValue, final int i) {
		filterEnv.nextValue();
		// apply drive
		drive = sampleValue*(1f + 10f*P.VALX[p_overload]);
		dsquare = drive*drive;
		drive = drive * ( 27 + dsquare ) / ( 27 + 9 * dsquare );
		inValue = sampleValue*P.VALMIXHIGH[p_overload] + drive*P.VALMIXLOW[p_overload]*.334f;
		
//		frq = FastCalc.ensureRange(
//				(
//					MIN_STABLE_FREQUENCY
//					+ MAX_STABLE_FREQUENCY*P.VALX[p_freq]
//					+ (MAX_STABLE_FREQUENCY * (filterEnv.nextValue() * P.VALXC[p_env_depth]))
//					+ frqOffset
//				) 
//				* LFO.lfoAmount(i, P.VALXC[p_mod_amount]),
//				MIN_STABLE_FREQUENCY, MAX_STABLE_FREQUENCY);

		if (type==FilterType.LOWPASS24) {
			// ZDF: solve feedback loop algebraically to avoid one-sample delay instability at high fc
			// y3 = G^4*u + (1-G)*(G^3*s0 + G^2*s1 + G*s2 + s3), substitute into u = input - k*y3
			float G2 = G * G;
			float S = G2*G*state0 + G2*state1 + G*state2 + state3;
			float u = (inValue - moogK * Gm1 * S) / (1f + moogK * G4);
			float sq = u * u;
			u = u * (27f + sq) / (27f + 9f * sq);  // tanh approximation
			float y0 = G * (u  - state0) + state0;  state0 = 2f*y0 - state0;
			float y1 = G * (y0 - state1) + state1;  state1 = 2f*y1 - state1;
			float y2 = G * (y1 - state2) + state2;  state2 = 2f*y2 - state2;
			float y3 = G * (y2 - state3) + state3;  state3 = 2f*y3 - state3;
			return y3;
		}
		else {
//			Q = P.VAL[p_resonance];
//			f1 = (float) (2.0*Math.sin(Math.PI*(frq/DOUBLE_SAMPLE_RATE)));  // the fs*2 is because it's float sampled
//			damp = (float) Math.min(2.0*(1.0 - FastCalc.pow(Q, 0.25f)), Math.min(2.0f, 2.0f/f1 - f1*0.5f));
			
			notch = inValue - damp*band;
			low   = low + f1*band;
			high  = notch - low;
			band  = f1*high + band;// - drive*band*band*band;
			switch (type) {
			case LOWPASS:
				out = low;
				break;
			case BANDPASS:
				out = band;
				break;
			case HIGHPASS:
				out = high;
				break;
			case NOTCH:
				out = notch;
				break;
			case ALLPASS:
			default:
				out = low+band+high;
			}
			notch = inValue - damp*band;
			low   = low + f1*band;
			high  = notch - low;
			band  = f1*high + band;// - drive*band*band*band;
			switch (type) {
			case LOWPASS:
				return out + low;
			case BANDPASS:
				return out + band;
			case HIGHPASS:
				return out + high;
			case NOTCH:
				return out + notch;
			}
			return out + low+band+high;
			
		}
	}
	
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(MultiModeFilter.class);
}
