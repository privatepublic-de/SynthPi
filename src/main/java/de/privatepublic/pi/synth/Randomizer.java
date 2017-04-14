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
		
		boolean oscsync = enable(.25f);
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
			if (enable(.25f)) {
				P.setDirectly(P.OSC2_AM, fullRange());
			}
		}
		// noise
		if (enable(.334f)) {
			P.setDirectly(P.OSC_NOISE_LEVEL, range(.2f, .667f));
		}

		// filters
		boolean filter1on = enable();
		if (filter1on) {
			P.setDirectly(P.FILTER1_ON, 1);
			P.setDirectly(P.FILTER1_FREQ, range(.2f,.8f));
			P.setDirectly(P.FILTER1_RESONANCE, range(0, .7f));
			P.setDirectly(P.FILTER1_TYPE, fullRange());
		}
		else {
			P.setDirectly(P.FILTER1_ON, 0);
		}
		
		// amp
		P.setDirectly(P.MOD_ENV1_A, range(0,.5f));
		P.setDirectly(P.MOD_ENV1_D, range(.2f, .8f));
		P.setDirectly(P.MOD_ENV1_S, fullRange());
		P.setDirectly(P.MOD_ENV1_R, fullRange());
		if (enable(.334f)) {
			P.setDirectly(P.CHORUS_DEPTH, fullRange());
		}
		if (enable(.334f)) {
			P.setDirectly(P.REVERB_ONE_KNOB, range(.1f,.85f));
		}
		if (enable(.2f)) {
			P.setDirectly(P.OVERDRIVE, range(.2f, .7f));
		}
		P.LAST_LOADED_PATCH_NAME = createFancyPatchName();
		P.LAST_LOADED_PATCH_CATEGORY = PatchCategory.WHATEVER;
		log.info("Randomized patch: {}", P.LAST_LOADED_PATCH_NAME);
		SynthPi.uiMessage("Randomized patch ("+P.LAST_LOADED_PATCH_NAME+")");
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
