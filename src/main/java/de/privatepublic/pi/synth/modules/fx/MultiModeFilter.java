package de.privatepublic.pi.synth.modules.fx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.P.FilterType;
import de.privatepublic.pi.synth.modules.IControlProcessor;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.util.FastCalc;

public class MultiModeFilter implements IControlProcessor {

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
	private EnvADSR filterEnv;
	
	
	public MultiModeFilter(int freq, int res, int mod, int env, int type, int trkkbd, int vel, int overload, EnvADSR modEnv) {
		p_freq = freq;
		p_resonance = res;
		p_mod_amount = mod;
		p_env_depth = env;
		p_type = type;
		p_track_keyboard = trkkbd;
		p_overload = overload;
		this.filterEnv = modEnv;
		
	}
	
	
	public void trigger(final float freq, final float velocity) {
		if (P.VAL[p_track_keyboard]>0) {
			frqOffset = freq*P.VAL[p_track_keyboard]*2f;//freq_keyboard_offset;
		}
		else {
			frqOffset = 0;
		}
	}
	
	public void noteOff() {
//		filterEnv.noteOff();
	}

	private float frq, K, Q, QtimesK, a, b, A0, A1, A2, B1, A3, A5, stage1, input;
	private float frqOffset, state0, state1, state2, state3, gain;
	private float f1, damp;
//	private float y_l0, y_l1, y_b0, y_b1, y_h1; 
//	private float f1 = 0;
//	private float gain = 0;
//	private float damp, drive=0, notch, low, high, band, out, in;
	private float notch, low, high, band, out;
//	private float dnormoffset =  1.0E-25;
	float drive, dsquare, inValue;
	FilterType type;
	
	
	@SuppressWarnings("incomplete-switch")
	public float processSample(final float sampleValue) {
		
		
		//filterEnv.nextValue();
		// apply drive
		drive = sampleValue*(1f + 10f*P.VALX[p_overload]);
		dsquare = drive*drive;
		drive = drive * ( 27 + dsquare ) / ( 27 + 9 * dsquare );
		inValue = sampleValue*P.VALMIXHIGH[p_overload] + drive*P.VALMIXLOW[p_overload]*.334f;
		
		

		if (type==FilterType.LOWPASS24) {
//			Q = 1-P.VAL[p_resonance];
//			gain = (float) Math.sqrt(Q);
//
//			K = (float) Math.tan(Math.PI*frq/P.SAMPLE_RATE_HZ);
//			QtimesK = Q * K;
//			a = 0.76536686473f * QtimesK;
//			b = 1.84775906502f * QtimesK;
//			K = K*K;

			A0 = (K+a+1);
			A1 = 2*(1-K);
			A2 =(a-K-1);
//			final float B0 = K;
			B1 = 2*K;
//			final float B2 = B0;

			A3 = (K+b+1);
//			final float A4 = 2*(1-K);
			A5 = (b-K-1);
//			final float B3 = K;
//			final float B4 = 2*B3;
//			final float B5 = B3; 

			input = inValue*gain;

			stage1 = K*input + state0;
			state0 = B1*input + A1/A0*stage1 + state1;
			state1 = K*input + A2/A0*stage1;

			input = K*stage1 + state2; // gain??
			state2 = B1*stage1 + A1/A3*input + state3;
			state3 = K*stage1 + A5/A3*input;
			return input;

		}
		else {
//			Q = P.VAL[p_resonance];
//			f1 = (2.0f*FastCalc.sin((float)Math.PI*(frq/DOUBLE_SAMPLE_RATE)));  // the fs*2 is because it's float sampled
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


	@Override
	public void controlTick() {
		type = P.VAL_FILTER_TYPE_FOR[p_type];
		
		frq = FastCalc.ensureRange(
				(
					MIN_STABLE_FREQUENCY
					+ MAX_STABLE_FREQUENCY*P.VALX[p_freq]
					+ (MAX_STABLE_FREQUENCY * (filterEnv.outValue * P.VALXC[p_env_depth]))
					+ frqOffset
				) 
				* LFO.lfoAmount(P.VALXC[p_mod_amount]),
				MIN_STABLE_FREQUENCY, MAX_STABLE_FREQUENCY);
		
		if (type==FilterType.LOWPASS24) {
			Q = 1-P.VAL[p_resonance];
			gain = (float) Math.sqrt(Q);

			K = (float) Math.tan(Math.PI*frq/P.SAMPLE_RATE_HZ);
			QtimesK = Q * K;
			a = 0.76536686473f * QtimesK;
			b = 1.84775906502f * QtimesK;
			K = K*K;
		}
		else {
			Q = P.VAL[p_resonance];
			f1 = (2.0f*FastCalc.sin((float)Math.PI*(frq/DOUBLE_SAMPLE_RATE)));  // the fs*2 is because it's float sampled
			damp = (float) Math.min(2.0*(1.0 - FastCalc.pow(Q, 0.25f)), Math.min(2.0f, 2.0f/f1 - f1*0.5f));
		}
		
	}
}
