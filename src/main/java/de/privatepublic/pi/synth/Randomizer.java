package de.privatepublic.pi.synth;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.PresetHandler.PatchCategory;

public class Randomizer {

	private static final Logger log = LoggerFactory.getLogger(Randomizer.class);
	private static final int[] RANDOM_TUNE_SEMIS = new int[]{-5, -7, -12, -12-5, -12-7, -24, 5, 7, 12, 12+5, 12+7, 24};
	private static Random RANDOM = new Random();
	
	public static void randomize() {
		P.setToDefaults();
		
		
		// oscillators
		P.setDirectly(P.OSC1_WAVE, fullRange());
		P.setDirectly(P.OSC2_WAVE, fullRange());
		
		if (P.Waveform.selectedWaveform(P.VAL[P.OSC1_WAVE])==P.Waveform.PULSE) {
			float pw = range(.1f, .9f);
			P.setDirectly(P.OSC1_PULSE_WIDTH, pw);
			if (enable()) {
				float modamount = range(.2f, .4f);
				P.setDirectly(P.MOD_ENV2_PW1_AMOUNT, pw<.5?.5f+modamount:.5f-modamount);
			}
			if (enable()) {
				float modamount = range(.2f, .4f);
				P.setDirectly(P.MOD_PW1_AMOUNT, pw<.5?.5f+modamount:.5f-modamount);
			}
		}
		if (P.Waveform.selectedWaveform(P.VAL[P.OSC2_WAVE])==P.Waveform.PULSE) {
			float pw = range(.1f, .9f);
			P.setDirectly(P.OSC2_PULSE_WIDTH, pw);
			if (enable()) {
				float modamount = range(.2f, .4f);
				P.setDirectly(P.MOD_ENV2_PW2_AMOUNT, pw<.5?.5f+modamount:.5f-modamount);
			}
			if (enable()) {
				float modamount = range(.2f, .4f);
				P.setDirectly(P.MOD_PW2_AMOUNT, pw<.5?.5f+modamount:.5f-modamount);
			}
		}
		
		boolean oscsync = enable(.25f);
		boolean ringmod = false;
		if (oscsync) {
			P.setDirectly(P.OSC2_SYNC, 1);
			P.setDirectly(P.OSC2_TUNING, range(.6f, 1));
		}
		else {
			// tune only 4ths 5ths and octaves
			if (enable(.25f)) {
				int factor = RANDOM_TUNE_SEMIS[(int)(RANDOM_TUNE_SEMIS.length*fullRange())];
				P.setDirectly(P.OSC2_TUNING, 0.5f+factor/48f);
			}
			// am
			ringmod = enable(.25f);
			if (ringmod) {
				P.setDirectly(P.OSC2_AM, 1);
			}
		}
		if (ringmod) {
			if (enable(.25f)) {
				P.setDirectly(P.MOD_ENV2_PITCH2_AMOUNT, .5f+(enable()?-1:1)*range(.2f,.5f));
			}
		}
		if (oscsync) {
			if (enable(.25f)) {
				P.setDirectly(P.MOD_ENV2_PITCH2_AMOUNT, .5f+(enable()?-1:1)*range(.2f,.5f));
			}
		}
		if (enable(.334f)) {
			P.setDirectly(P.OSC_NOISE_LEVEL, range(.1f, .4f));
		}
		if (enable(.2f)) {
			P.setDirectly(P.MOD_ENV2_NOISE_AMOUNT, range(.667f, .8f));
		}
		if (enable(.5f)) {
			P.setDirectly(P.OSC_SUB_VOLUME, range(.334f, .8f));
			P.setDirectly(P.OSC_SUB_SQUARE, enable(.5f)?0:1);
			P.setDirectly(P.OSC_SUB_LOW, enable(.3f)?0:1);
		}
		
		// delay
		if (enable(.334f)) {
			P.setDirectly(P.DELAY_WET, range(.5f, .9f));
			P.setDirectly(P.DELAY_FEEDBACK, range(.2f, .9f));
			P.setDirectly(P.DELAY_RATE, range(.4f, .9f));
			P.setDirectly(P.DELAY_RATE_RIGHT, range(.4f, .9f));
			P.setDirectly(P.DELAY_TYPE, enable(.5f)?0:1);
		}

		// filters
		boolean filter1on = enable();
		if (filter1on) {
			P.setDirectly(P.FILTER1_ON, 1);
			P.setDirectly(P.FILTER1_FREQ, range(.2f,.8f));
			P.setDirectly(P.FILTER1_RESONANCE, range(0, .9f));
			P.setDirectly(P.FILTER1_TYPE, fullRange());
			P.setDirectly(P.FILTER1_OVERLOAD, fullRange());
			
			if (enable()) {
				// filter mod
				if (enable()) {
					P.setDirectly(P.MOD_ENV1_FILTER_AMOUNT, .5f+(enable()?1:-1)*range(.25f, .4f));
				}
				if (enable()) {
					P.setDirectly(P.MOD_ENV2_FILTER_AMOUNT, .5f-(enable()?1:-1)*range(.25f, .4f));
				}
			}
			
		}
		else {
			P.setDirectly(P.FILTER1_ON, 0);
		}
		
		P.setDirectly(P.MOD_PITCH_AMOUNT, .5f);
		if (enable()) {
			P.setDirectly(P.MOD_AMOUNT_BASE, range(.334f, .7f));
			P.setDirectly(P.MOD_RATE, range(.334f, .7f));
			if (enable()) {
				P.setDirectly(P.MOD_LFO_DELAY, range(0, .667f));
			}
			if (enable()) {
				P.setDirectly(P.MOD_LFO_TYPE, fullRange());
			}
		}
		
		// amp
		P.setDirectly(P.MOD_ENV1_A, range(0,.5f));
		P.setDirectly(P.MOD_ENV1_D, range(.2f, .8f));
		P.setDirectly(P.MOD_ENV1_S, fullRange());
		P.setDirectly(P.MOD_ENV1_R, fullRange());
		P.setDirectly(P.MOD_ENV1_LOOP, enable(.2f)?1:0);
		
		P.setDirectly(P.MOD_ENV2_A, range(0,.8f));
		P.setDirectly(P.MOD_ENV2_D, range(.2f, .8f));
		P.setDirectly(P.MOD_ENV2_S, fullRange());
		P.setDirectly(P.MOD_ENV2_R, fullRange());
		P.setDirectly(P.MOD_ENV2_LOOP, enable(.2f)?1:0);
		
		P.setDirectly(P.BASS_BOOSTER_LEVEL, fullRange());
		
		if (enable(.334f)) {
			P.setDirectly(P.CHORUS_DEPTH, fullRange());
		}
		if (enable(.2f)) {
			P.setDirectly(P.OVERDRIVE, range(.2f, .7f));
		}
		P.LAST_LOADED_PATCH_NAME = createFancyPatchName();
		P.LAST_LOADED_PATCH_CATEGORY = PatchCategory.MISC;
		log.info("Randomized patch: {}", P.LAST_LOADED_PATCH_NAME);
		PresetHandler.sendPatchInitNotification();
		SynthPi.uiMessage("Randomized patch ("+P.LAST_LOADED_PATCH_NAME+")");
		SynthPi.uiLCDMessage(P.LAST_LOADED_PATCH_NAME, "RANDOMIZED");
	}
	
	private static boolean enable(float probability) {
		return onOff(probability)>0;
	}
	
	private static boolean enable() {
		return onOff()>0;
	}
	
	private static float onOff() {
		return RANDOM.nextBoolean()?1:0;
	}
	
	private static float onOff(float probability) {
		return RANDOM.nextFloat()<probability?1:0;
	}
	
	private static float range(float min, float max) {
		return (max-min)*RANDOM.nextFloat()+min;
	}
	
	private static float fullRange() {
		return RANDOM.nextFloat();
	}
	
	private static String createFancyPatchName() {
		int adj1 = RANDOM.nextInt(adjectives.length);
		int adj2 = RANDOM.nextInt(adjectives.length);
		while (adj2==adj1) {
			adj2 = RANDOM.nextInt(adjectives.length);
		}
		return adjectives[adj1]+" "+adjectives[adj2]+" "+nouns[RANDOM.nextInt(nouns.length)];
	}
	
	private static final String[] adjectives = new String[] {"new", "random", "ugly", "nasty", "crazy", "weird", "scary", "funny", "bizarre", "strange", "odd", "peculiar", "freaky"};
	private static final String[] nouns = new String[] {"sound", "noise", "tone"};
	
}
