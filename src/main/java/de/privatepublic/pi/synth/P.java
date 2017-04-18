package de.privatepublic.pi.synth;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.PresetHandler.PatchCategory;
import de.privatepublic.pi.synth.comm.ControlMessageDispatcher;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.mod.EnvADSR.EnvelopeParamConfig;


public class P {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(P.class);
	
	/** Filter types */
	public static enum FilterType { 
		LOWPASS24("LOW24"), 
		LOWPASS("LOW  "), 
		BANDPASS("BAND "), 
		HIGHPASS("HIGH "), 
		NOTCH("NOTCH"), 
		ALLPASS("ALLP ");
		
		private final String shortName;
		FilterType(String s) {
			shortName = s;
		}
		public String shortName() {
			return shortName;
		}
		
		public static FilterType selectedFilterType(float val) {
			return values()[(int)Math.round(val * (values().length-1))];
		}
	}
	
	/** Oscillator Waveforms */
	public static enum Waveform { 
		SINE("SIN"), 
		TRIANGLE("TRI"), 
		SAW("SAW"), 
		PULSE("PUL");
		
		private final String shortName;
		Waveform(String s) {
			shortName = s;
		}
		public String shortName() {
			return shortName;
		}
		
		public static Waveform selectedWaveform(float val) {
			return values()[(int)Math.round(val * (values().length-1))];
		}
	}
	
	/** Available filter routings */
	public static enum FilterRouting { 
		SERIAL("Serial", "serial"), 
		PARALLEL("Parallel", "parall"), 
		PEROSC("Per Oscillator", "osc   ");
		
		private final String descName;
		private final String shortName;
		FilterRouting(String s, String sShort) {
			descName = s;
			shortName = sShort;
		}
		public String descName() {
			return descName;
		}
		public String shortName() {
			return shortName;
		}
	}
	
	
	public static String AUDIO_SYSTEM_NAME = "JavaSound";
	public static float SAMPLE_RATE_HZ = 48000;
	public static float MILLIS_PER_SAMPLE_FRAME = 1000f/SAMPLE_RATE_HZ;
	public static int CONTROL_BUFFER_SIZE = 16;
	public static float CONTROL_RATE_HZ = SAMPLE_RATE_HZ/CONTROL_BUFFER_SIZE;
	public static float MILLIS_PER_CONTROL_FRAME = 1000f/CONTROL_RATE_HZ;
	public static float FINAL_GAIN_FACTOR = .8f;
	public static int SAMPLE_BUFFER_SIZE = 128;
	public static float BEND_RANGE_CENTS = 200f;
	public static final int POLYPHONY_MAX  = 8;
	public static int POLYPHONY = POLYPHONY_MAX;
	public static boolean HTTP_SEND_PERFORMACE_DATA = false;
	public static boolean LIMITER_ENABLED = false;
	public static boolean LOW_BUDGET_ADDITIVE = false;

	public static final float ROOT_FREQUENCY = 440;	
	public static final float OCTAVE_CENTS = 1200f;
	
	public static String MIDI_FILE_NAME;
	
	public static final float[] MIDI_NOTE_FREQUENCY_HZ = new float[128];
	public static final String[] MIDI_NOTE_NAME = new String[128];

	/** Last used (loaded or saved) patch name */
	public static String LAST_LOADED_PATCH_NAME = "INITIAL";
	public static PatchCategory LAST_LOADED_PATCH_CATEGORY = PatchCategory.WHATEVER;
	
	/** Number of available parameters */
	public static final int PARAM_STORE_SIZE = 84;
	
	/** float value of parameter (0 - 1) */
	public static final float[] VAL = new float[PARAM_STORE_SIZE];
	/** Centered float value of parameter (-1 - 0 - 1) */
	public static final float[] VALC = new float[PARAM_STORE_SIZE];
	/** Quadratic float value of parameter (0 - 1) */
	public static final float[] VALX = new float[PARAM_STORE_SIZE];
	/** Centered quadratic float value of parameter (-1 - 0 - 1) */
	public static final float[] VALXC = new float[PARAM_STORE_SIZE];
	/** 
	 * Low mix amount of parameter (0 - 1). Works like this, based on
	 * the centered quadratic value of the parameter.
	 * <pre>
	 * valxc   -1  -0.25  0  0.25  1
	 * _____________________________
	 * lowmix   1   1     1  0.25  0
	 * highmix  0   0.25  1  1     1</pre>
	 * @see VALMIXHIGH, VALXC
	 */
	public static final float[] VALMIXLOW = new float[PARAM_STORE_SIZE];
	/** 
	 * High mix amount of parameter (0 - 1). Works like this, based on
	 * the centered quadratic value of the parameter.
	 * <pre>
	 * valxc   -1  -0.25  0  0.25  1
	 * _____________________________
	 * lowmix   1   1     1  0.25  0
	 * highmix  0   0.25  1  1     1</pre>
	 * @see VALMIXLOW, VALXC
	 */
	public static final float[] VALMIXHIGH = new float[PARAM_STORE_SIZE];
	/** Parameter value is >0 */
	public static final boolean[] IS = new boolean[PARAM_STORE_SIZE];
	/** 7-Bit MIDI value of parameter (0 - 127) */
	public static final int[] VAL_RAW_MIDI = new int[PARAM_STORE_SIZE];
	
	public static final float[] TARGET_VAL = new float[PARAM_STORE_SIZE];
	private static final int[] TARGET_STEP_COUNT = new int[PARAM_STORE_SIZE];
	private static final float TARGET_STEPS = 12f; // 1536 / BUFFERSIZE
	
	public static boolean FIX_STRANGE_MIDI_PITCH_BEND = true;
	public static boolean PEDAL = false;
	/** Pitch bend frequency factor. No pitch bend=1. This factor can be applied directly to a frequency. */
	public static float PITCH_BEND_FACTOR = 1;
	/** Combined lfo modulation amount: max(MOD_WHEEL, MOD_AMOUNT_BASE) */
	public static float MOD_AMOUNT_COMBINED = 0;
	
	public static final String[] OSC_PATH = new String[PARAM_STORE_SIZE];
	
	public static FilterType VAL_FILTER_TYPE = FilterType.LOWPASS;
	public static Waveform VAL_OSC1_WAVEFORM = Waveform.SAW;
	public static Waveform VAL_OSC2_WAVEFORM = Waveform.SAW;
	public static float osc2DetuneCents;
	public static float osc2DetuneFactor;

	
	public static int MIDI_CHANNEL = 1 -1; // CAUTION 0-based
	public static int PORT_HTTP = 31415;
	public static String CUSTOM_BROWSER_COMMAND = null;
	
	public static final int UNUSED = 0; /** Unused property (always 0) */
	public static final int MOD_WHEEL = 1; /** Mod wheel amount */
	public static final int OSC1_WAVE = 2;
	public static final int OSC2_WAVE = 3;
	public static final int OSC2_TUNING = 4;
	public static final int OSC_NOISE_LEVEL = 5;
	public static final int FILTER1_FREQ = 6;
	public static final int FILTER1_RESONANCE = 7;
	public static final int MOD_ENV2_FILTER_AMOUNT = 8;
	public static final int FILTER1_TYPE = 9;
	public static final int MOD_ENV1_FILTER_AMOUNT = 10;
	public static final int MOD_ENV1_PITCH_AMOUNT = 11;
	public static final int MOD_ENV1_PITCH2_AMOUNT = 12;
	public static final int MOD_ENV2_VOL_AMOUNT = 13;
	public static final int MOD_ENV1_A = 14;
	public static final int MOD_ENV1_D = 15;
	public static final int MOD_ENV1_S = 16;
	public static final int MOD_ENV1_R = 17;
	public static final int OSC1_VOLUME = 18;
	public static final int PITCH_BEND = 19; /** Pitch bend amount */
	public static final int MOD_FILTER1_AMOUNT = 20;
	public static final int MOD_ENV1_VOL_AMOUNT = 21;
	public static final int MOD_RATE = 22;
	public static final int VOLUME = 23;
	public static final int MOD_VEL_VOL_AMOUNT = 24;
	public static final int MOD_VEL_FILTER_AMOUNT = 25;
	public static final int OSC2_SYNC = 26;
	public static final int OSC2_AM = 27;
	public static final int FILTER1_TRACK_KEYBOARD = 28;
	public static final int OVERDRIVE = 29;
	public static final int MOD_PITCH_AMOUNT = 30;
	public static final int OSC_GLIDE_RATE = 31;
	public static final int OSC_MONO = 32;
	public static final int OSC1_PULSE_WIDTH = 33;
	public static final int OSC2_PULSE_WIDTH = 34;
	public static final int OSC2_VOLUME = 35;
	public static final int OSC_SUB_VOLUME = 36;
	public static final int MOD_LFO_DELAY = 37;
	public static final int OSC_SUB_LOW = 38;
//	public static final int MOD_AHD_DECAY = 39;
	public static final int DELAY_RATE_RIGHT = 40;
//	public static final int FILTER2_ENV_D = 41;
//	public static final int FILTER2_ENV_S = 42;
//	public static final int FILTER2_ENV_R = 43;
//	public static final int FILTER2_ENV_VELOCITY_SENS = 44;
	public static final int MOD_PITCH2_AMOUNT = 45;
	public static final int OSC2_TUNING_FINE = 46;
	public static final int MOD_PW1_AMOUNT = 47;
//	public static final int OSC1_WAVE_SET = 48;
//	public static final int OSC2_WAVE_SET = 49;
	public static final int FILTER1_ON = 50;
//	public static final int FILTER_PARALLEL = 51;
	public static final int MOD_ENV2_PW2_AMOUNT = 52;
	public static final int MOD_ENV2_A = 53;
	public static final int MOD_ENV2_D = 54;
	public static final int MOD_ENV2_S = 55;
	public static final int MOD_ENV2_R = 56;
	public static final int MOD_ENV2_PITCH_AMOUNT = 57;
	public static final int MOD_ENV2_PITCH2_AMOUNT = 58;
	public static final int MOD_ENV2_PW1_AMOUNT = 59;
	public static final int MOD_ENV2_NOISE_AMOUNT = 60;
	public static final int MOD_LFO_TYPE = 61;
	public static final int MOD_LFO_RESET = 62;
	public static final int CHORUS_LFO_RATE = 63;
	public static final int CHORUS_LFO_TYPE = 64;
	public static final int CHORUS_DEPTH = 65;
	public static final int MOD_AMOUNT_BASE = 66;
	public static final int MOD_ENV2_LOOP = 67;
	public static final int MOD_ENV1_PW1_AMOUNT = 68;
	public static final int MOD_ENV1_PW2_AMOUNT = 69;
	public static final int MOD_ENV1_LOOP = 70;
	public static final int DELAY_WET = 71;
//	public static final int OSC_MODE = 72;
//	public static final int MOD_AHD_DELAY_TIME_AMOUNT = 73;
	public static final int MIDI_VELOCITY_CURVE = 74;
//	public static final int REVERB_ONE_KNOB = 75;
	public static final int MOD_DELAY_TIME_AMOUNT = 76;
	public static final int MOD_PW2_AMOUNT = 77;
	public static final int MOD_VOL_AMOUNT = 78;
	public static final int MOD_ENV1_NOISE_AMOUNT = 79;
	public static final int DELAY_RATE = 80;
	public static final int DELAY_FEEDBACK = 81;
	public static final int FILTER1_OVERLOAD = 82;
//	public static final int FILTER2_OVERLOAD = 83;
	
	public static final int[] SET_INTERPOLATED = new int[] {
		FILTER1_FREQ,
		FILTER1_RESONANCE,
		OSC1_VOLUME,
		OSC2_VOLUME,
		OSC_SUB_VOLUME,
		OSC1_PULSE_WIDTH,
		OSC2_PULSE_WIDTH,
		VOLUME,
		OVERDRIVE,
		CHORUS_DEPTH,
		OSC_NOISE_LEVEL,
		DELAY_WET,
		DELAY_FEEDBACK
	};
	public static final int SET_INTERPOLATED_SIZE = SET_INTERPOLATED.length;
	
	public static final EnvelopeParamConfig ENV_CONF_AMP = new EnvelopeParamConfig(MOD_ENV1_A, MOD_ENV1_D, MOD_ENV1_S, MOD_ENV1_R, UNUSED, MOD_ENV1_LOOP);
	public static final EnvelopeParamConfig ENV_CONF_MOD_ENV = new EnvelopeParamConfig(MOD_ENV2_A, MOD_ENV2_D, MOD_ENV2_S, MOD_ENV2_R, UNUSED, MOD_ENV2_LOOP);
	public static final String VERSION_STRING = "0.9";
	
	public static float limiterReductionValue = 0;
//	public static Limiter limiterObject = null;
	
	static {
		
		final String[] basenames = new String[]{"A-","A#","B-","C-","C#","D-","D#","E-","F-","F#","G-","G#"};
		for (int i=0;i<128;++i) {
			MIDI_NOTE_FREQUENCY_HZ[i] = (float) (ROOT_FREQUENCY*Math.pow(Math.pow(2d, i-69d), 1/12d));
			if (i<21) {
				MIDI_NOTE_NAME[i] = "---";
			}
			else {
				MIDI_NOTE_NAME[i] = basenames[(i-21)%basenames.length]+((i-12)/basenames.length);
			}
		}
		
		// map osc paths to params
		
		// amplifier
		OSC_PATH[VOLUME] = "/amp/volume"; // TODO paths as constants!
		OSC_PATH[OVERDRIVE] = "/fx/overdrive";
		OSC_PATH[CHORUS_DEPTH] = "/fx/chorus/drywet";
		OSC_PATH[DELAY_WET] = "/fx/delay/wet";
		OSC_PATH[DELAY_RATE] = "/fx/delay/rate";
		OSC_PATH[DELAY_RATE_RIGHT] = "/fx/delay/rateright";
		OSC_PATH[DELAY_FEEDBACK] = "/fx/delay/feedback";
		
		// oscillators
		OSC_PATH[OSC1_VOLUME] = "/osc/1/vol";
		OSC_PATH[OSC2_VOLUME] = "/osc/2/vol";
		OSC_PATH[OSC_SUB_VOLUME] = "/osc/sub/vol";
		OSC_PATH[OSC_NOISE_LEVEL] = "/osc/noiselevel";
		OSC_PATH[OSC1_WAVE] = "/osc/1/wave";
		OSC_PATH[OSC2_WAVE] = "/osc/2/wave";
		OSC_PATH[OSC2_TUNING] = "/osc/2/tuning";
		OSC_PATH[OSC2_TUNING_FINE] = "/osc/2/tuning/fine";
		OSC_PATH[OSC2_SYNC] = "/osc/2/sync";
		OSC_PATH[OSC2_AM] = "/osc/2/am";
		OSC_PATH[OSC_GLIDE_RATE] = "/osc/gliderate";
		OSC_PATH[OSC_MONO] = "/osc/mono";
		OSC_PATH[OSC_SUB_LOW] = "/osc/sub/low";
		
		// modulation
		OSC_PATH[MOD_RATE] = "/mod/1/rate";
		OSC_PATH[MOD_AMOUNT_BASE] = "/mod/1/amount";
		OSC_PATH[MOD_LFO_TYPE] = "/mod/1/type";
		OSC_PATH[MOD_LFO_RESET] = "/mod/1/reset";
		OSC_PATH[MOD_FILTER1_AMOUNT] = "/mod/1/depth/filter/1";
		OSC_PATH[MOD_PITCH_AMOUNT] = "/mod/1/depth/pitch";
		OSC_PATH[MOD_PITCH2_AMOUNT] = "/mod/1/depth/pitch/2";
		OSC_PATH[MOD_PW1_AMOUNT] = "/mod/1/depth/wave/1";
		OSC_PATH[MOD_PW2_AMOUNT] = "/mod/1/depth/wave/2";
		OSC_PATH[MOD_VOL_AMOUNT] = "/mod/1/depth/vol";
		OSC_PATH[MOD_DELAY_TIME_AMOUNT] = "/mod/1/depth/delaytime";
		OSC_PATH[MOD_LFO_DELAY] = "/mod/1/delay";
		
		OSC_PATH[MOD_VEL_VOL_AMOUNT] = "/mod/vel/depth/vol";
		OSC_PATH[MOD_VEL_FILTER_AMOUNT] = "/mod/vel/depth/filter";
		
		OSC_PATH[MOD_ENV1_A] = "/mod/env/1/1";
		OSC_PATH[MOD_ENV1_D] = "/mod/env/1/2";
		OSC_PATH[MOD_ENV1_S] = "/mod/env/1/3";
		OSC_PATH[MOD_ENV1_R] = "/mod/env/1/4";
		OSC_PATH[MOD_ENV1_LOOP] = "/mod/env/1/loop";
		OSC_PATH[MOD_ENV1_PITCH_AMOUNT] = "/mod/env/1/depth/pitch";
		OSC_PATH[MOD_ENV1_PITCH2_AMOUNT] = "/mod/env/1/depth/pitch/2";
		OSC_PATH[MOD_ENV1_PW1_AMOUNT] = "/mod/env/1/depth/pw/1";
		OSC_PATH[MOD_ENV1_PW2_AMOUNT] = "/mod/env/1/depth/pw/2";
		OSC_PATH[MOD_ENV1_NOISE_AMOUNT] = "/mod/env/1/depth/noise";
		OSC_PATH[MOD_ENV1_FILTER_AMOUNT] = "/mod/env/1/depth/filter";
		OSC_PATH[MOD_ENV1_VOL_AMOUNT] = "/mod/env/1/depth/vol";
		
		OSC_PATH[MOD_ENV2_A] = "/mod/env/2/1";
		OSC_PATH[MOD_ENV2_D] = "/mod/env/2/2";
		OSC_PATH[MOD_ENV2_S] = "/mod/env/2/3";
		OSC_PATH[MOD_ENV2_R] = "/mod/env/2/4";
		OSC_PATH[MOD_ENV2_LOOP] = "/mod/env/2/loop";
		OSC_PATH[MOD_ENV2_PITCH_AMOUNT] = "/mod/env/2/depth/pitch";
		OSC_PATH[MOD_ENV2_PITCH2_AMOUNT] = "/mod/env/2/depth/pitch/2";
		OSC_PATH[MOD_ENV2_PW1_AMOUNT] = "/mod/env/2/depth/pw/1";
		OSC_PATH[MOD_ENV2_PW2_AMOUNT] = "/mod/env/2/depth/pw/2";
		OSC_PATH[MOD_ENV2_NOISE_AMOUNT] = "/mod/env/2/depth/noise";
		OSC_PATH[MOD_ENV2_FILTER_AMOUNT] = "/mod/env/2/depth/filter";
		OSC_PATH[MOD_ENV2_VOL_AMOUNT] = "/mod/env/2/depth/vol";
		
		
		
		OSC_PATH[MOD_WHEEL] = "/play/mod/wheel";
		
		// filters
		OSC_PATH[FILTER1_TYPE] = "/filter/1/type";
		OSC_PATH[FILTER1_FREQ] = "/filter/1/freq";
		OSC_PATH[FILTER1_RESONANCE] = "/filter/1/res";
		OSC_PATH[FILTER1_ON] = "/filter/1/enable";
		OSC_PATH[FILTER1_TRACK_KEYBOARD] = "/filter/1/kbdtracking";
		OSC_PATH[FILTER1_OVERLOAD] = "/filter/1/overload";
		
		OSC_PATH[OSC1_PULSE_WIDTH] = "/osc/1/pw";
		OSC_PATH[OSC2_PULSE_WIDTH] = "/osc/2/pw";
		
		// defaults!
		setToDefaults();
		
		// send limiter status thread
		Timer timer = new Timer("LimiterState", true);
		timer.schedule(new TimerTask() {
			private boolean lastWasGood = false;
			@Override
			public void run() {
				if (HTTP_SEND_PERFORMACE_DATA) {
					float val = limiterReductionValue;
					if (val>1) {
						val = (val-1)*.2f+1;
						ControlMessageDispatcher.INSTANCE.sendToAll("/label/limiter/reduction="+val);
						lastWasGood = false;
					}
					else {
						if (!lastWasGood) {
							ControlMessageDispatcher.INSTANCE.sendToAll("/label/limiter/reduction=1");
							lastWasGood = true;	
						}
					}
				}
			}
		}, 2000, 100);

	}
	
	public static void setToDefaults() {
		for (int i=0;i<PARAM_STORE_SIZE;++i) {
			setDirectly(i,0);
		}
		setDirectly(VOLUME, 0.79f);
		setDirectly(FILTER1_FREQ, 0.5f);
		setDirectly(OSC1_PULSE_WIDTH, 0.5f);
		setDirectly(OSC2_PULSE_WIDTH, 0.5f);
		setDirectly(OSC1_VOLUME, 0.66667f);
		setDirectly(OSC2_VOLUME, 0.66667f);
		setDirectly(OSC_SUB_VOLUME, 0f);
		setDirectly(FILTER1_TYPE, 0);
		setDirectly(OSC_NOISE_LEVEL, 0);
		setDirectly(OSC1_WAVE, 0.5f);
		setDirectly(OSC2_WAVE, 1f);
		setDirectly(OSC2_TUNING_FINE, 0.66667f);
		setDirectly(OSC2_TUNING, .5f);
		setDirectly(FILTER1_TRACK_KEYBOARD, .5f);
		setDirectly(FILTER1_ON, 0);		
		
		setDirectly(MOD_RATE, .5f);
		setDirectly(MOD_FILTER1_AMOUNT, .875f);
		setDirectly(MOD_PW1_AMOUNT, .5f);
		setDirectly(MOD_PW2_AMOUNT, .5f);
		setDirectly(MOD_DELAY_TIME_AMOUNT, .5f);
		setDirectly(MOD_PITCH_AMOUNT, .8335f);
		setDirectly(MOD_PITCH2_AMOUNT, .5f);

		
		setDirectly(MOD_ENV1_S, 1);
		setDirectly(MOD_ENV1_PITCH_AMOUNT, .5f);
		setDirectly(MOD_ENV1_PITCH2_AMOUNT, .5f);
		setDirectly(MOD_ENV1_PW1_AMOUNT, .5f);
		setDirectly(MOD_ENV1_PW2_AMOUNT, .5f);
		setDirectly(MOD_ENV1_NOISE_AMOUNT, .5f);
		setDirectly(MOD_ENV1_FILTER_AMOUNT, 0.5f);
		setDirectly(MOD_ENV1_VOL_AMOUNT, 1f);
		
		setDirectly(MOD_ENV2_PITCH_AMOUNT, .5f);
		setDirectly(MOD_ENV2_PITCH2_AMOUNT, .5f);
		setDirectly(MOD_ENV2_PW1_AMOUNT, .5f);
		setDirectly(MOD_ENV2_PW2_AMOUNT, .5f);
		setDirectly(MOD_ENV2_NOISE_AMOUNT, .5f);
		setDirectly(MOD_ENV2_FILTER_AMOUNT, 0.5f);
		setDirectly(MOD_ENV2_VOL_AMOUNT, 0.5f);
		
		setDirectly(DELAY_RATE, .66f);
		setDirectly(MIDI_VELOCITY_CURVE, 0.0f);
		P.VAL[P.PITCH_BEND] = 0;
		P.VAL_RAW_MIDI[P.PITCH_BEND] = 8192;
		P.VAL[P.CHORUS_LFO_RATE] = 1/12f;
		P.VALX[P.CHORUS_LFO_RATE] =  (float) Math.pow(P.VAL[P.CHORUS_LFO_RATE], 4);
		P.VAL[P.CHORUS_LFO_TYPE] = 0;
	}
	
	public static void set(int index, float val) {
		if (index==CHORUS_LFO_RATE || index==CHORUS_LFO_TYPE) {
			return; // ugly, but these are immutable and only set by defaults
		}
		TARGET_VAL[index] = val;
		if (!isSetInterpolated(index)) {
			setDirectly(index, val);
		}
		else {
			TARGET_STEP_COUNT[index] = 0;
		}
	}
	
	public static void interpolate() {
		for (int i=0;i<SET_INTERPOLATED_SIZE;i++) {
			final int pi = SET_INTERPOLATED[i];
			if (VAL[pi]!=TARGET_VAL[pi] && TARGET_STEP_COUNT[pi]<TARGET_STEPS) {
				final float y1 = VAL[pi];
				final float y2 = TARGET_VAL[pi];
				final float stepfactor = (++TARGET_STEP_COUNT[pi])/TARGET_STEPS;
				setDirectly(pi, (y1*(1-stepfactor)+y2*stepfactor), false);
			}
		}
	}
	
	
	
	private static boolean isSetInterpolated(int index) {
		for (int i=0;i<SET_INTERPOLATED.length;i++) {
			if (SET_INTERPOLATED[i]==index) {
				return true;
			}
		}
		return false;
	}
	
	protected static void setDirectly(int index, float val) {
		if (index==CHORUS_LFO_RATE || index==CHORUS_LFO_TYPE) {
			return; // ugly, but these are immutable and only set by defaults
		}
		setDirectly(index, val, true);
	}
	
	private static void setDirectly(int index, float val, boolean setTarget) {
		if (setTarget) {
			TARGET_VAL[index] = val;
		}
		VAL[index] = val;
		VALC[index] = (val-.5f)*2f;
		VALX[index] = val*val*val*val; // val^4 for exponential approximation
		VALXC[index] = Math.signum(VALC[index])*(VALC[index]*VALC[index]*VALC[index]*VALC[index]);
		IS[index] = (val>0);
		VAL_RAW_MIDI[index] = (int)Math.round(val*127);
		final float x2 = Math.abs(VALC[index])-1;
		final float y = -(x2*x2*x2*x2) + 1;
		final float cv = Math.signum(VALC[index])*y;
		VALMIXLOW[index] = (float) Math.sqrt(0.5*(1+cv));
		VALMIXHIGH[index]  = (float) Math.sqrt(0.5*(1-cv));
		
		switch (index) {
		case OSC2_TUNING:
			osc2DetuneCents = (Math.round(VALC[P.OSC2_TUNING]*24)*100+VALXC[P.OSC2_TUNING_FINE]*100)/OCTAVE_CENTS;
			osc2DetuneFactor = (float)Math.pow(2f, osc2DetuneCents);
			break;
		case FILTER1_TYPE:
			VAL_FILTER_TYPE = FilterType.selectedFilterType(val);
			break;
		case OSC1_WAVE:
			VAL_OSC1_WAVEFORM = Waveform.selectedWaveform(val);
			break;
		case OSC2_WAVE:
			VAL_OSC2_WAVEFORM = Waveform.selectedWaveform(val);
			break;
		case MOD_WHEEL:
		case MOD_AMOUNT_BASE:
			MOD_AMOUNT_COMBINED = Math.max(VAL[MOD_WHEEL], VAL[MOD_AMOUNT_BASE]);
		}
	}
	
	public static void setFromMIDI(int index, int val) {
		float v = val==64?.5f:val/127f;
		set(index, v);
		VAL_RAW_MIDI[index] = (int)val;
	}
	
	public static void resetPerformanceControllers() {
		set(P.MOD_WHEEL, 0);
		P.VAL[P.PITCH_BEND] = 0;
		P.VAL_RAW_MIDI[P.PITCH_BEND] = 8192; // 0 - 8192 - 16383
		P.PITCH_BEND_FACTOR = 1;
		MidiHandler.sendPitchBendNotification();
	}
	
	
}
