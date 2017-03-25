package de.privatepublic.pi.synth;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.PresetHandler.PatchCategory;
import de.privatepublic.pi.synth.comm.ControlMessageDispatcher;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.fx.Freeverb;
import de.privatepublic.pi.synth.modules.mod.EnvADSR.EnvelopeParamConfig;


public class P {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(P.class);
	
	public static enum OscillatorMode {
		VIRTUAL_ANALOG,
		ADDITIVE,
		EXITER
	}
	
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
	
	public static FilterType[] VAL_FILTER_TYPE_FOR = new FilterType[] {FilterType.LOWPASS, FilterType.ALLPASS};
	public static FilterRouting VAL_FILTER_ROUTING = FilterRouting.SERIAL;
	public static OscillatorMode VAL_OSCILLATOR_MODE = OscillatorMode.VIRTUAL_ANALOG;
	
	public static int MIDI_CHANNEL = 1 -1; // CAUTION 0-based
	public static int PORT_HTTP = 31415;
	public static String CUSTOM_BROWSER_COMMAND = null;
	
	/** Unused property (always 0) */
	public static final int UNUSED = 0;
	/** Mod wheel amount */
	public static final int MOD_WHEEL = 1;
	/** Oscillator 1 wave form */
	public static final int OSC1_WAVE = 2;
	/** Oscillator 2 wave form */
	public static final int OSC2_WAVE = 3;
	/** Oscillator 2 coarse tuning */
	public static final int OSC2_TUNING = 4;
	/** Noise level mixed to oscillator sounds */
	public static final int OSC_NOISE_LEVEL = 5;
	/** Filter1 cut off frequency */
	public static final int FILTER1_FREQ = 6;
	/** Filter1 resonance (Q) level */
	public static final int FILTER1_RESONANCE = 7;
	/** Filter1 envelope modulation depth (use centered) */
	public static final int FILTER1_ENV_DEPTH = 8;
	/** Filter1 type 
	 * @see FilterType */
	public static final int FILTER1_TYPE = 9;
	/** Filter1 mod envelope attack time */
	public static final int FILTER1_ENV_A = 10;
	/** Filter1 mod envelope decay time */
	public static final int FILTER1_ENV_D = 11;
	/** Filter1 mod envelope sustain level */
	public static final int FILTER1_ENV_S = 12;
	/** Filter1 mod envelope release time */
	public static final int FILTER1_ENV_R = 13;
	/** Amp envelope attack time */
	public static final int AMP_ENV_A = 14;
	/** Amp envelope decay time */
	public static final int AMP_ENV_D = 15;
	/** Amp envelope sustain level */
	public static final int AMP_ENV_S = 16;
	/** Amp envelope release time */
	public static final int AMP_ENV_R = 17;
	/** Mix balance of oscillators 1 and 2. 
	 * @see VALMIXLOW, VALMIXHIGH */
	public static final int OSC_1_2_MIX = 18;
	/** Pitch bend amount */
	public static final int PITCH_BEND = 19;
	/** Filter1 LFO modulation amount (use centered) */
	public static final int MOD_FILTER1_AMOUNT = 20;
//	public static final int MOD_PULSEWIDTH_AMOUNT = 21; // THIS IS A GAP!
	/** LFO modulation rate */
	public static final int MOD_RATE = 22;
	/** Amplifier master volume */
	public static final int VOLUME = 23;
	/** Amplifier envelope velocity sensitivity >0 = true*/
	public static final int AMP_ENV_VELOCITY_SENS = 24;
	/** Filter1 mod envelope velocity sensitivity >0 = true*/
	public static final int FILTER1_ENV_VELOCITY_SENS = 25;
	/** Hard sync oscillator 2 to oscillator 1 */
	public static final int OSC2_SYNC = 26;
	/** Oscillator 2 amplitude (ring) modulation with oscillator 1 */
	public static final int OSC2_AM = 27;
	/** Filter1 cut off frequency keyboard tracking amount */
	public static final int FILTER1_TRACK_KEYBOARD = 28;
	/** Amplifier distortion (overdrive) */
	public static final int OVERDRIVE = 29;
	/** LFO pitch modulation amount */
	public static final int MOD_PITCH_AMOUNT = 30;
	/** Oscillators frequency glide (portamento) rate */
	public static final int OSC_GLIDE_RATE = 31;
	/** Play monophonic (use only one synth voice) */
	public static final int OSC_MONO = 32;
	/** Filter2 cut-off frequency */
	public static final int FILTER2_FREQ = 33;
	/** Filter2 resonance (Q) level */
	public static final int FILTER2_RESONANCE = 34;
	/** Filter2 envelope modulation depth (use centered) */
	public static final int FILTER2_ENV_DEPTH = 35;
	/** Filter2 type 
	 * @see FilterType */
	public static final int FILTER2_TYPE = 36;
	/** Filter2 LFO modulation amount (use centered) */
	public static final int MOD_FILTER2_AMOUNT = 37;
	/** Filter2 is enabled */
	public static final int FILTER2_ON = 38;
	/** Filter2 cut off frequency keyboard tracking amount */
	public static final int FILTER2_TRACK_KEYBOARD = 39;
	/** Filter2 mod envelope attack time */
	public static final int FILTER2_ENV_A = 40;
	/** Filter2 mod envelope decay time */
	public static final int FILTER2_ENV_D = 41;
	/** Filter2 mod envelope sustain level */
	public static final int FILTER2_ENV_S = 42;
	/** Filter2 mod envelope release time */
	public static final int FILTER2_ENV_R = 43;
	/** Filter2 mod envelope velocity sensitivity >0 = true*/
	public static final int FILTER2_ENV_VELOCITY_SENS = 44;
	/** LFO pitch of oscillator2 modulation amount (use centered) */
	public static final int MOD_PITCH2_AMOUNT = 45;
	/** Oscillator2 fine tuning -/+100 cents (use centered) */
	public static final int OSC2_TUNING_FINE = 46;
	/** Oscillator1+2 wave form modulation amount */
	public static final int MOD_WAVE1_AMOUNT = 47;
	/** Selected wave set for oscillator 1 */
	public static final int OSC1_WAVE_SET = 48;
	/** Selected wave set for oscillator 2 */
	public static final int OSC2_WAVE_SET = 49;
	/** Filter1 enabled */
	public static final int FILTER1_ON = 50;
	/** Filter chain is parallel (>0) */
	public static final int FILTER_PARALLEL = 51;
	/** Filter parallel mix level 
	 * @see VALMIXLOW, VALMIXHIGH */
	public static final int FILTER_PARALLEL_MIX = 52;
	/** Mod envelope attack time */
	public static final int MOD_ENV1_A = 53;
	/** Mod envelope decay time */
	public static final int MOD_ENV1_D = 54;
	/** Mod envelope sustain level */
	public static final int MOD_ENV1_S = 55;
	/** Mod envelope release time */
	public static final int MOD_ENV1_R = 56;
	/** Mod envelope pitch modulation amount (use centered) */
	public static final int MOD_ENV1_PITCH_AMOUNT = 57;
	/** Mod envelope pitch of oscillator 2 modulation amount (use centered) */
	public static final int MOD_ENV1_PITCH2_AMOUNT = 58;
	/** Mod envelope wave form modulation amount (use centered) */
	public static final int MOD_ENV1_WAVE_AMOUNT = 59;
	/** Mod envelope noise level modulation amount (use centered) */
	public static final int MOD_ENV1_NOISE_AMOUNT = 60;
	/** LFO wave form */
	public static final int MOD_LFO_TYPE = 61;
	/** LFO resets on every note on (key stroke) */
	public static final int MOD_LFO_RESET = 62;
	/** Chorus modulation rate */
	public static final int CHORUS_LFO_RATE = 63;
	/** Chorus modulation wave form rate */
	public static final int CHORUS_LFO_TYPE = 64;
	/** Chorus depth */
	public static final int CHORUS_DEPTH = 65;
	/** LFO base amount */
	public static final int MOD_AMOUNT_BASE = 66;
	/** Mod envelope loop mode */
	public static final int MOD_ENV1_LOOP = 67;
	/** Filter 1 envelope loop mode */
	public static final int FILTER1_ENV_LOOP = 68;
	/** Filter 2 envelope loop mode */
	public static final int FILTER2_ENV_LOOP = 69;
	/** Amp envelope loop mode */
	public static final int AMP_ENV_LOOP = 70;
	
	public static final int DELAY_WET = 71;
	public static final int OSC_MODE = 72;
	public static final int OSC_GAIN = 73;
	public static final int MIDI_VELOCITY_CURVE = 74;
	public static final int REVERB_ONE_KNOB = 75;
	public static final int SPREAD = 76;
	public static final int MOD_WAVE2_AMOUNT = 77;
	public static final int MOD_VOL_AMOUNT = 78;
	public static final int MOD_ENV1_AM_AMOUNT = 79;
	public static final int DELAY_RATE = 80;
	public static final int DELAY_FEEDBACK = 81;
	
	public static final int FILTER1_OVERLOAD = 82;
	public static final int FILTER2_OVERLOAD = 83;
	
	public static final int[] SET_INTERPOLATED = new int[] {
		FILTER1_FREQ,
		FILTER2_FREQ,
		FILTER1_RESONANCE,
		FILTER2_RESONANCE,
		OSC_1_2_MIX,
		OSC2_AM,
		VOLUME,
		OVERDRIVE,
		CHORUS_DEPTH,
		REVERB_ONE_KNOB,
		FILTER_PARALLEL_MIX,
		OSC_NOISE_LEVEL,
		OSC_GAIN,
		DELAY_WET,
		DELAY_FEEDBACK
	};
	public static final int SET_INTERPOLATED_SIZE = SET_INTERPOLATED.length;
	
	public static final EnvelopeParamConfig ENV_CONF_AMP = new EnvelopeParamConfig(AMP_ENV_A, AMP_ENV_D, AMP_ENV_S, AMP_ENV_R, AMP_ENV_VELOCITY_SENS, AMP_ENV_LOOP);
	public static final EnvelopeParamConfig ENV_CONF_FILTER1 = new EnvelopeParamConfig(FILTER1_ENV_A, FILTER1_ENV_D, FILTER1_ENV_S, FILTER1_ENV_R, FILTER1_ENV_VELOCITY_SENS, FILTER1_ENV_LOOP);
	public static final EnvelopeParamConfig ENV_CONF_FILTER2 = new EnvelopeParamConfig(FILTER2_ENV_A, FILTER2_ENV_D, FILTER2_ENV_S, FILTER2_ENV_R, FILTER2_ENV_VELOCITY_SENS, FILTER2_ENV_LOOP);
	public static final EnvelopeParamConfig ENV_CONF_MOD_ENV = new EnvelopeParamConfig(MOD_ENV1_A, MOD_ENV1_D, MOD_ENV1_S, MOD_ENV1_R, UNUSED, MOD_ENV1_LOOP);
	public static final String VERSION_STRING = "0.9";
	
	public static Freeverb reverbObject = null; // TODO more generic with interface and receiver handler!?
	
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
		OSC_PATH[REVERB_ONE_KNOB] = "/fx/reverb/oneknob";
		OSC_PATH[SPREAD] = "/fx/spread";
		OSC_PATH[DELAY_WET] = "/fx/delay/wet";
		OSC_PATH[DELAY_RATE] = "/fx/delay/rate";
		OSC_PATH[DELAY_FEEDBACK] = "/fx/delay/feedback";
		OSC_PATH[AMP_ENV_A] = "/amp/env/1";
		OSC_PATH[AMP_ENV_D] = "/amp/env/2";
		OSC_PATH[AMP_ENV_S] = "/amp/env/3";
		OSC_PATH[AMP_ENV_R] = "/amp/env/4";
		OSC_PATH[AMP_ENV_VELOCITY_SENS] = "/amp/velocity";
		OSC_PATH[AMP_ENV_LOOP] = "/amp/env/loop";
		
		// oscillators
		OSC_PATH[OSC_1_2_MIX] = "/osc/mix";
		OSC_PATH[OSC_NOISE_LEVEL] = "/osc/noiselevel";
		OSC_PATH[OSC1_WAVE] = "/osc/1/wave";
		OSC_PATH[OSC1_WAVE_SET] = "/osc/1/waveset";
		OSC_PATH[OSC2_WAVE] = "/osc/2/wave";
		OSC_PATH[OSC2_WAVE_SET] = "/osc/2/waveset";
		OSC_PATH[OSC2_TUNING] = "/osc/2/tuning";
		OSC_PATH[OSC2_TUNING_FINE] = "/osc/2/tuning/fine";
		OSC_PATH[OSC2_SYNC] = "/osc/2/sync";
		OSC_PATH[OSC2_AM] = "/osc/2/am";
		OSC_PATH[OSC_GLIDE_RATE] = "/osc/gliderate";
		OSC_PATH[OSC_MONO] = "/osc/mono";
//		OSC_PATH[UNUSED_RELIC] = "/osc/mode/va";
		OSC_PATH[OSC_MODE] = "/osc/mode";
		OSC_PATH[OSC_GAIN] = "/osc/gain";
		
		// modulation
		OSC_PATH[MOD_RATE] = "/mod/1/rate";
		OSC_PATH[MOD_AMOUNT_BASE] = "/mod/1/amount";
		OSC_PATH[MOD_LFO_TYPE] = "/mod/1/type";
		OSC_PATH[MOD_LFO_RESET] = "/mod/1/reset";
		OSC_PATH[MOD_FILTER1_AMOUNT] = "/mod/1/depth/filter/1";
		OSC_PATH[MOD_FILTER2_AMOUNT] = "/mod/1/depth/filter/2";
		OSC_PATH[MOD_PITCH_AMOUNT] = "/mod/1/depth/pitch";
		OSC_PATH[MOD_PITCH2_AMOUNT] = "/mod/1/depth/pitch/2";
		OSC_PATH[MOD_WAVE1_AMOUNT] = "/mod/1/depth/wave/1";
		OSC_PATH[MOD_WAVE2_AMOUNT] = "/mod/1/depth/wave/2";
		OSC_PATH[MOD_VOL_AMOUNT] = "/mod/1/depth/vol";
		OSC_PATH[MOD_ENV1_A] = "/mod/env/1";
		OSC_PATH[MOD_ENV1_D] = "/mod/env/2";
		OSC_PATH[MOD_ENV1_S] = "/mod/env/3";
		OSC_PATH[MOD_ENV1_R] = "/mod/env/4";
		OSC_PATH[MOD_ENV1_LOOP] = "/mod/env/loop";
		OSC_PATH[MOD_ENV1_PITCH_AMOUNT] = "/mod/env/depth/pitch";
		OSC_PATH[MOD_ENV1_PITCH2_AMOUNT] = "/mod/env/depth/pitch/2";
		OSC_PATH[MOD_ENV1_WAVE_AMOUNT] = "/mod/env/depth/wave";
		OSC_PATH[MOD_ENV1_NOISE_AMOUNT] = "/mod/env/depth/noise";
		OSC_PATH[MOD_ENV1_AM_AMOUNT] = "/mod/env/depth/am";
		OSC_PATH[MOD_WHEEL] = "/play/mod/wheel";
		
		// filters
		OSC_PATH[FILTER1_TYPE] = "/filter/1/type";
		OSC_PATH[FILTER1_FREQ] = "/filter/1/freq";
		OSC_PATH[FILTER1_RESONANCE] = "/filter/1/res";
		OSC_PATH[FILTER1_ON] = "/filter/1/enable";
		OSC_PATH[FILTER1_TRACK_KEYBOARD] = "/filter/1/kbdtracking";
		OSC_PATH[FILTER1_ENV_A] = "/filter/1/env/1";
		OSC_PATH[FILTER1_ENV_D] = "/filter/1/env/2";
		OSC_PATH[FILTER1_ENV_S] = "/filter/1/env/3";
		OSC_PATH[FILTER1_ENV_R] = "/filter/1/env/4";
		OSC_PATH[FILTER1_ENV_DEPTH] = "/filter/1/env/amount";
		OSC_PATH[FILTER1_ENV_LOOP] = "/filter/1/env/loop";
		OSC_PATH[FILTER1_ENV_VELOCITY_SENS] = "/filter/1/velocity";
		OSC_PATH[FILTER1_OVERLOAD] = "/filter/1/overload";
		
		OSC_PATH[FILTER2_TYPE] = "/filter/2/type"; 
		OSC_PATH[FILTER2_FREQ] = "/filter/2/freq";
		OSC_PATH[FILTER2_RESONANCE] = "/filter/2/res";
		OSC_PATH[FILTER2_ON] = "/filter/2/enable";
		OSC_PATH[FILTER2_TRACK_KEYBOARD] = "/filter/2/kbdtracking";
		OSC_PATH[FILTER2_ENV_A] = "/filter/2/env/1";
		OSC_PATH[FILTER2_ENV_D] = "/filter/2/env/2";
		OSC_PATH[FILTER2_ENV_S] = "/filter/2/env/3";
		OSC_PATH[FILTER2_ENV_R] = "/filter/2/env/4";
		OSC_PATH[FILTER2_ENV_DEPTH] = "/filter/2/env/amount";
		OSC_PATH[FILTER2_ENV_LOOP] = "/filter/2/env/loop";
		OSC_PATH[FILTER2_ENV_VELOCITY_SENS] = "/filter/2/velocity";
		OSC_PATH[FILTER2_OVERLOAD] = "/filter/2/overload";
		
		OSC_PATH[FILTER_PARALLEL] = "/filter/parallel";
		OSC_PATH[FILTER_PARALLEL_MIX] = "/filter/parallelmix";
		
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
		setDirectly(FILTER2_FREQ, 0.5f);
		setDirectly(FILTER1_TYPE, 0);
		setDirectly(FILTER2_TYPE, 1f/6);
		setDirectly(FILTER1_ENV_DEPTH, 0.5f);
		setDirectly(FILTER2_ENV_DEPTH, 0.5f);
		setDirectly(OSC_MODE, 0);
		setDirectly(OSC_NOISE_LEVEL, 0);
		setDirectly(OSC_GAIN, .8f);
		setDirectly(OSC1_WAVE, 0.3333334f);
		setDirectly(OSC2_WAVE, 0.5f);
		setDirectly(OSC2_TUNING_FINE, 0.66667f);
		setDirectly(AMP_ENV_S, 1);
		setDirectly(OSC_1_2_MIX, .5f);
		setDirectly(MOD_RATE, .666667f);
		setDirectly(MOD_FILTER1_AMOUNT, .875f);
		setDirectly(MOD_FILTER2_AMOUNT, .5f);
		setDirectly(MOD_WAVE1_AMOUNT, .5f);
		setDirectly(MOD_WAVE2_AMOUNT, .5f);
		setDirectly(MOD_PITCH_AMOUNT, .8335f);
		setDirectly(MOD_PITCH2_AMOUNT, .5f);
		setDirectly(FILTER1_ENV_VELOCITY_SENS, 0);
		setDirectly(OSC2_TUNING, .5f);
		setDirectly(FILTER1_TRACK_KEYBOARD, .5f);
		setDirectly(FILTER2_TRACK_KEYBOARD, .5f);
		setDirectly(FILTER1_ON, 0);
		setDirectly(FILTER_PARALLEL_MIX, .5f);
		setDirectly(FILTER_PARALLEL, 0);
		setDirectly(MOD_ENV1_PITCH_AMOUNT, .5f);
		setDirectly(MOD_ENV1_PITCH2_AMOUNT, .5f);
		setDirectly(MOD_ENV1_WAVE_AMOUNT, .5f);
		setDirectly(MOD_ENV1_NOISE_AMOUNT, .5f);
		setDirectly(MOD_ENV1_AM_AMOUNT, .5f);
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
		case FILTER1_TYPE:
			VAL_FILTER_TYPE_FOR[0] = selectedFilterType(val);
			break;
		case FILTER2_TYPE:
			VAL_FILTER_TYPE_FOR[1] = selectedFilterType(val);
			break;
		case FILTER_PARALLEL:
			VAL_FILTER_ROUTING = FilterRouting.values()[(int)Math.round(val * (FilterRouting.values().length-1))];
			break;
		case OSC_MODE:
			VAL_OSCILLATOR_MODE = selectedOscillatorMode(val);
			break;
		case REVERB_ONE_KNOB:
			// notify reverb object
			if (reverbObject!=null) {
				reverbObject.updateOneKnobSetting();
			}
			break;
		case MOD_WHEEL:
		case MOD_AMOUNT_BASE:
			MOD_AMOUNT_COMBINED = Math.max(VAL[MOD_WHEEL], VAL[MOD_AMOUNT_BASE]);
		}
	}
	
	public static void setFromMIDI(int index, int val) {
		float v;
		if (index==P.OSC2_TUNING) {
			// consider range for midi device
			v = val/(float)MidiHandler.CC_OSC2_DETUNE_VALUE_RANGE;
		} else 
		if (index==P.OSC1_WAVE_SET || index==P.OSC2_WAVE_SET) {
			v = (val<127?val/127f:126f/127f); // mustn't reach 1
		}
		else {
			v = val==64?.5f:val/127f;
		}
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
	
	
	
	public static FilterType selectedFilterType(float val) {
		return FilterType.values()[(int)Math.round(val * (FilterType.values().length-1))];
	}
	
	public static OscillatorMode selectedOscillatorMode(float val) {
		return OscillatorMode.values()[(int)Math.round(val * (OscillatorMode.values().length-1))];
	}
	
	public static float detuneCents() {
		return (Math.round(VALC[P.OSC2_TUNING]*24)*100+VALXC[P.OSC2_TUNING_FINE]*100)/OCTAVE_CENTS;
	}
	
}
