package de.privatepublic.pi.synth;

import java.text.DecimalFormat;

import de.privatepublic.pi.synth.modules.fx.MultiModeFilter;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.LFO;

public class FancyParam {

	private static final String[] NAME = new String[P.PARAM_STORE_SIZE];
	static {
		// amplifier
		NAME[P.VOLUME] = "Volume";
		NAME[P.OVERDRIVE] = "Overdrive";
		NAME[P.CHORUS_DEPTH] = "Chorus";
		NAME[P.REVERB_ONE_KNOB] = "Reverb";
		NAME[P.DELAY_WET] = "Delay Mix";
		NAME[P.DELAY_FEEDBACK] = "Delay Feedback";
		NAME[P.DELAY_RATE] = "Delay Rate";
		NAME[P.MOD_ENV1_A] = "Amp Env. Attack";
		NAME[P.MOD_ENV1_D] = "Amp Env. Decay";
		NAME[P.MOD_ENV1_S] = "Amp Env. Sustain";
		NAME[P.MOD_ENV1_R] = "Amp Env. Release";
		NAME[P.MOD_ENV1_LOOP] = "Amp Env. Loop Mode";
		
		// oscillators
		NAME[P.OSC1_VOLUME] = "Osc 1 Volume";
		NAME[P.OSC2_VOLUME] = "Osc 1 Volume";
		NAME[P.OSC_SUB_VOLUME] = "Osc Sub Volume";
		NAME[P.OSC_NOISE_LEVEL] = "Noise Level";
		NAME[P.OSC1_WAVE] = "Osc 1 Waveform";
		NAME[P.OSC2_WAVE] = "Osc 2 Waveform";
		NAME[P.OSC2_TUNING] = "Osc 2 Tune";
		NAME[P.OSC2_TUNING_FINE] = "Osc 2 Fine Tune";
		NAME[P.OSC2_SYNC] = "Sync Osc 2";
		NAME[P.OSC2_AM] = "Osc 2 Ringmod";
		NAME[P.OSC_GLIDE_RATE] = "Glide Rate";
		NAME[P.OSC_MONO] = "MONO";
		
		// modulation
		NAME[P.MOD_RATE] = "LFO Frq";
		NAME[P.MOD_AMOUNT_BASE] = "LFO Amount";
		NAME[P.MOD_LFO_TYPE] = "LFO Type";
		NAME[P.MOD_LFO_RESET] = "Reset LFO on Key";
		NAME[P.MOD_FILTER1_AMOUNT] = "LFO > Filter1";
		NAME[P.MOD_PITCH_AMOUNT] = "LFO > Pitch";
		NAME[P.MOD_PITCH2_AMOUNT] = "LFO > Pitch2";
		NAME[P.MOD_PW1_AMOUNT] = "LFO > Waveform 1";
		NAME[P.MOD_PW2_AMOUNT] = "LFO > Waveform 2";
		NAME[P.MOD_ENV2_A] = "Mod Env. Attack";
		NAME[P.MOD_ENV2_D] = "Mod Env. Decay";
		NAME[P.MOD_ENV2_S] = "Mod Env. Sustain";
		NAME[P.MOD_ENV2_R] = "Mod Env. Release";
		NAME[P.MOD_ENV2_LOOP] = "Mod Env. Loop Mode";
		NAME[P.MOD_ENV2_PITCH_AMOUNT] = "Mod Env. > Pitch";
		NAME[P.MOD_ENV2_PITCH2_AMOUNT] = "Mod Env. > Pitch2";
		NAME[P.MOD_ENV2_PW1_AMOUNT] = "Mod Env. > Waveform";
		NAME[P.MOD_ENV2_NOISE_AMOUNT] = "Mod Env. > Noise";
		NAME[P.MOD_VOL_AMOUNT] = "LFO > Volume";
		
		// filters
		NAME[P.FILTER1_TYPE] = "Filter1 Type"; 
		NAME[P.FILTER1_FREQ] = "Filter1 Cutoff Frq";
		NAME[P.FILTER1_RESONANCE] = "Filter1 Resonance";
		NAME[P.FILTER1_ON] = "Filter1";
		NAME[P.FILTER1_TRACK_KEYBOARD] = "Filter1 Keyboard Tracking";
		NAME[P.MOD_ENV2_FILTER_AMOUNT] = "Filter1 Env. Depth";
		
		NAME[P.OSC1_PULSE_WIDTH] = "Osc1 PW";
		NAME[P.OSC2_PULSE_WIDTH] = "Osc2 PW";
		NAME[P.FILTER2_ENV_D] = "Filter2 Env. Decay";
		NAME[P.FILTER2_ENV_S] = "Filter2 Env. Sustain";
		NAME[P.FILTER2_ENV_R] = "Filter2 Env. Release";
		NAME[P.FILTER2_ENV_VELOCITY_SENS] = "Filter2 Env. Velocity";
		
		NAME[P.FILTER_PARALLEL] = "Filter1/2 Routing";
		NAME[P.FILTER1_OVERLOAD] = "Filter 1 Drive";
		NAME[P.FILTER2_OVERLOAD] = "Filter 2 Drive";
	}
	
	public static String nameOf(int paramindex) {
		if (paramindex<0 || paramindex>NAME.length-1) {
			return "unknown";
		}
		String result = NAME[paramindex];
		if (result==null) {
			return P.OSC_PATH[paramindex];
		}
		return result;
	}
	
	
	public static final DecimalFormat FORMAT_FLOAT = new DecimalFormat("0.0");
	public static final DecimalFormat FORMAT_FLOAT2 = new DecimalFormat("0.00");
	public static final DecimalFormat FORMAT_INT = new DecimalFormat("0");
	
	public static String valueOf(int paramindex) {
		final float value = P.TARGET_VAL[paramindex];
		float calculatedVal;
		String result;
		switch (paramindex) {
		case P.OSC1_WAVE:
		case P.OSC2_WAVE:
			result = P.Waveform.selectedWaveform(value).name();
			break;
		case P.OSC2_TUNING:
			result = Math.round(P.VALC[P.OSC2_TUNING]*24)+" st";
			break;
		case P.MOD_RATE:
			calculatedVal = LFO.LOW_FREQ+(P.VALX[P.MOD_RATE]*(LFO.FREQ_RANGE));
			if (calculatedVal<10) {
				result = FORMAT_FLOAT2.format(LFO.LOW_FREQ+(P.VALX[P.MOD_RATE]*(LFO.FREQ_RANGE)))+" Hz";
			}
			else {
				result = FORMAT_FLOAT.format(LFO.LOW_FREQ+(P.VALX[P.MOD_RATE]*(LFO.FREQ_RANGE)))+" Hz";
			}
			break;
		case P.MOD_LFO_TYPE:
			result = LFO.WAVE_NAMES[(int)(P.VAL[P.MOD_LFO_TYPE]*LFO.WAVE_COUNT_CALC)];
			break;
		case P.FILTER1_FREQ:
			calculatedVal = MultiModeFilter.MIN_STABLE_FREQUENCY+MultiModeFilter.MAX_STABLE_FREQUENCY*(value*value*value*value);
			if (calculatedVal>=100) {
				result = Math.round(calculatedVal)+" Hz";
			}
			else {
				result = FORMAT_FLOAT2.format(calculatedVal)+" Hz";
			}
			break;
		case P.MOD_ENV1_A:
		case P.MOD_ENV1_D:
		case P.MOD_ENV1_R:
		case P.MOD_ENV2_A:
		case P.MOD_ENV2_D:
		case P.MOD_ENV2_R:
		case P.MOD_LFO_DELAY:
			long millis = Math.round(EnvADSR.MIN_TIME_MILLIS+(EnvADSR.MAX_TIME_MILLIS-EnvADSR.MIN_TIME_MILLIS)*(value*value*value*value));
			float seconds;
			if (millis<100) {
				result = millis +"ms";
			}
			else if (millis<10000) {
				seconds = (float) Math.round(millis/1000d * 100) / 100;
				result = seconds +"s";
			}
			else {
				seconds = (float) Math.round(millis/1000d * 10) / 10;
				result = seconds +"s";
			}
			break;
		case P.OSC2_SYNC:
		case P.FILTER2_ENV_VELOCITY_SENS:
		case P.FILTER1_ON:
		case P.MOD_ENV2_LOOP:
		case P.MOD_ENV1_LOOP:
		case P.OSC_MONO:
		case P.MOD_LFO_RESET:
			result = value>0?"ON":"OFF";
			break;
		case P.FILTER1_TYPE:
			result = P.FilterType.selectedFilterType(value).name();
			break;
		case P.OSC2_TUNING_FINE:
		case P.MOD_ENV2_FILTER_AMOUNT:
		case P.MOD_ENV2_NOISE_AMOUNT:
		case P.MOD_ENV2_PITCH2_AMOUNT:
		case P.MOD_ENV2_PITCH_AMOUNT:
		case P.MOD_ENV2_PW1_AMOUNT:
		case P.MOD_FILTER1_AMOUNT:
		case P.MOD_PW1_AMOUNT:
		case P.MOD_PITCH_AMOUNT:
		case P.MOD_PITCH2_AMOUNT:
			result = FORMAT_FLOAT.format(((value-.5)*2)*100);
			break;
//		case P.DELAY_RATE:
//			float freq = 2000.0f*(.001f+.999f*P.VALX[P.DELAY_RATE]);
//			millis = Math.round(freq);
//			if (millis<10) {
//				result = FORMAT_FLOAT2.format(freq) +"ms";
//			}
//			else if (millis<100) {
//				result = millis +"ms";
//			}
//			else if (millis<10000) {
//				seconds = (float) Math.round(millis/1000d * 100) / 100;
//				result = seconds +"s";
//			}
//			else {
//				seconds = (float) Math.round(millis/1000d * 10) / 10;
//				result = seconds +"s";
//			}
//			break;
		default:
			result = FORMAT_FLOAT.format(value*100);
			break;
		}
		return result;
	}
	
	
}
