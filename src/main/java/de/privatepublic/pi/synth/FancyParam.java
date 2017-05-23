package de.privatepublic.pi.synth;

import java.awt.Color;
import java.text.DecimalFormat;

import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.LFO;

public class FancyParam {

	private static final String[] NAME = new String[P.PARAM_STORE_SIZE];
	static {
		// amplifier
		NAME[P.VOLUME]                   = "Volume";
		NAME[P.OVERDRIVE]                = "Overdrive";
		NAME[P.BASS_BOOSTER_LEVEL]       = "Bass-Boost";
		NAME[P.CHORUS_DEPTH]             = "Chorus";
		NAME[P.DELAY_WET]                = "Delay Level";
		NAME[P.DELAY_FEEDBACK]           = "Delay Feedback";
        //                                  ---------------
		NAME[P.DELAY_RATE]               = "Delay Rate Left";
		NAME[P.DELAY_RATE_RIGHT]         = "Delay Rate Rght";
		NAME[P.DELAY_TYPE]               = "Delay Type";
		NAME[P.MOD_ENV1_A]               = "ENV1 Attack";
		NAME[P.MOD_ENV1_D]               = "ENV1 Decay";
		NAME[P.MOD_ENV1_S]               = "ENV1 Sustain";
		NAME[P.MOD_ENV1_R]               = "ENV1 Release";
		NAME[P.MOD_ENV1_LOOP]            = "ENV1 Loop";
		NAME[P.MOD_ENV1_PITCH2_AMOUNT]   = "ENV1 > Pitch2";
		NAME[P.MOD_ENV1_PW1_AMOUNT]      = "ENV1 > PW1";
		NAME[P.MOD_ENV1_PW2_AMOUNT]      = "ENV1 > PW2";
		NAME[P.MOD_ENV1_FILTER_AMOUNT]   = "ENV1 > Filter";
		
		// oscillators
		NAME[P.OSC1_VOLUME]              = "OSC1 Level";
		NAME[P.OSC2_VOLUME]              = "OSC2 Level";
		NAME[P.OSC_SUB_VOLUME]           = "OSCSub Level";
        //                                  ---------------
		NAME[P.OSC_NOISE_LEVEL]          = "Noise Level";
		NAME[P.OSC1_WAVE]                = "OSC1 Waveform";
		NAME[P.OSC2_WAVE]                = "OSC2 Waveform";
		NAME[P.OSC1_PULSE_WIDTH]         = "OSC1 Pulse-wdth";
		NAME[P.OSC2_PULSE_WIDTH]         = "OSC2 Pulse-wdth";
		NAME[P.OSC2_TUNING]              = "OSC2 Tune";
		NAME[P.OSC2_TUNING_FINE]         = "OSC2 Fine";
		NAME[P.OSC2_SYNC]                = "OSC2 Sync";
		NAME[P.OSC2_AM]                  = "OSC2 Ringmod";
		NAME[P.OSC_GLIDE_RATE]           = "Glide Rate";
		NAME[P.OSC_MONO]                 = "Monophonic";
		NAME[P.OSC_SUB_SQUARE]           = "OSCSub Wave";
		NAME[P.OSC_SUB_LOW]              = "OSCSub 2 Oct";
        //                                  ---------------
		// modulation
		NAME[P.MOD_WHEEL]                = "Mod Wheel";
		NAME[P.MOD_RATE]                 = "LFO Rate";
		NAME[P.MOD_AMOUNT_BASE]          = "LFO Amount";
		NAME[P.MOD_LFO_TYPE]             = "LFO Type";
		NAME[P.MOD_LFO_DELAY]            = "LFO Delay Rate";
		NAME[P.MOD_LFO_RESET]            = "LFO Key-Reset";
		NAME[P.MOD_FILTER1_AMOUNT]       = "LFO > Filter";
		NAME[P.MOD_PITCH_AMOUNT]         = "LFO > Pitch";
		NAME[P.MOD_PITCH2_AMOUNT]        = "LFO > Pitch2";
		NAME[P.MOD_DELAY_TIME_AMOUNT]    = "LFO > Delay";
		NAME[P.MOD_PW1_AMOUNT]           = "LFO > PW1";
		NAME[P.MOD_PW2_AMOUNT]           = "LFO > PW2";
        //                                  ---------------
		NAME[P.MOD_ENV2_A]               = "ENV2 Attack";
		NAME[P.MOD_ENV2_D]               = "ENV2 Decay";
		NAME[P.MOD_ENV2_S]               = "ENV2 Sustain";
		NAME[P.MOD_ENV2_R]               = "ENV2 Release";
		NAME[P.MOD_ENV2_LOOP]            = "ENV2 Loop";
		NAME[P.MOD_ENV2_PITCH_AMOUNT]    = "ENV2 > Pitch";
		NAME[P.MOD_ENV2_PITCH2_AMOUNT]   = "ENV2 > Pitch2";
		NAME[P.MOD_ENV2_PW1_AMOUNT]      = "ENV2 > PW1";
		NAME[P.MOD_ENV2_PW2_AMOUNT]      = "ENV2 > PW2";
		NAME[P.MOD_ENV2_OSC2_VOL_AMOUNT] = "ENV2 > OSC2 Vol";
		NAME[P.MOD_ENV2_NOISE_AMOUNT]    = "ENV2 > Noise";
		NAME[P.MOD_ENV2_FILTER_AMOUNT]   = "ENV2 > Filter";
		NAME[P.MOD_ENV2_LFORATE_AMOUNT]  = "ENV2 > LFO Rate";
		NAME[P.MOD_VOL_AMOUNT]           = "LFO > Volume";
        //                                  ---------------
		NAME[P.MOD_PRESS_PITCH_AMOUNT]   = "PRESS > Pitch";
		NAME[P.MOD_PRESS_PITCH2_AMOUNT]  = "PRESS > Pitch2";
		NAME[P.MOD_PRESS_FILTER_AMOUNT]  = "PRESS > Filter";
		NAME[P.MOD_VEL_VOL_AMOUNT]       = "VELO > Volume";
		NAME[P.MOD_VEL_FILTER_AMOUNT]    = "VELO > Filter";
		// filters
		NAME[P.FILTER1_TYPE]             = "Filter Type"; 
		NAME[P.FILTER1_FREQ]             = "Filter Cut-off";
		NAME[P.FILTER1_RESONANCE]        = "Filter Reso";
		NAME[P.FILTER1_ON]               = "Filter Enable";
		NAME[P.FILTER1_TRACK_KEYBOARD]   = "Filter Keytrack";
		NAME[P.FILTER1_OVERLOAD]         = "Filter Drive";
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
		case P.MOD_ENV1_A:
		case P.MOD_ENV1_D:
		case P.MOD_ENV1_R:
		case P.MOD_ENV2_A:
		case P.MOD_ENV2_D:
		case P.MOD_ENV2_R:
		case P.MOD_LFO_DELAY:
			float millis = EnvADSR.MIN_TIME_MILLIS+(EnvADSR.MAX_TIME_MILLIS-EnvADSR.MIN_TIME_MILLIS)*(value*value*value*value)/2;
			float seconds;
			if (millis<10) {
				result = Math.round(millis*100)/100f +"ms";
			}
			else if (millis<1000) {
				result = Math.round(millis) +"ms";
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
		case P.FILTER1_ON:
		case P.MOD_ENV2_LOOP:
		case P.MOD_ENV1_LOOP:
		case P.OSC_MONO:
		case P.MOD_LFO_RESET:
		case P.OSC_SUB_LOW:
		case P.OSC2_AM:
			result = value>0?"ON":"OFF";
			break;
		case P.OSC_SUB_SQUARE:
			result = value>0?"SQUARE":"TRIANGLE";
			break;
		case P.FILTER1_TYPE:
			result = P.FilterType.selectedFilterType(value).name();
			break;
		case P.DELAY_TYPE:
			result = value>0?"DIGITAL":"LOW-FI";
			break;
		default:
			if (P.IS_BIPOLAR[paramindex]) {
				result = FORMAT_FLOAT.format(((value-.5)*2)*100);	
			}
			else {
				result = FORMAT_FLOAT.format(value*100);				
			}
			break;
		}
		return result;
	}
	
	private static final Color COLOR_ORANGE =Color.decode("#ff5500"); 
	
	public static Color colorOf(int paramindex) {
		int order = P.PARAMETER_ORDER.length;
		for (int n=0;n<P.PARAMETER_ORDER.length;n++) {
			if (P.PARAMETER_ORDER[n]==paramindex) {
				order = n;
				break;
			}
		}
		if (order<16) {
			return Color.YELLOW;
		}
		if (order<22) {
			return COLOR_ORANGE;
		}
		if (order<30) {
			return Color.CYAN;
		}
		if (order<45) {
			return Color.MAGENTA;
		}
		return Color.DARK_GRAY;
	}
}
