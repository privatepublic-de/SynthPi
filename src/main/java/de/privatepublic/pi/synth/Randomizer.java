package de.privatepublic.pi.synth;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.PresetHandler.PatchCategory;

public class Randomizer {

	private static final Logger log = LoggerFactory.getLogger(Randomizer.class);

	// ±24 removed — two-octave jumps produce thin blends when one osc is barely audible
	private static final int[] RANDOM_TUNE_SEMIS = new int[]{-12, -7, -5, 5, 7, 12};
	private static final Random RANDOM = new Random();

	private enum Archetype { PAD, PLUCK, LEAD, BASS }

	public static void randomize() {
		P.setToDefaults();

		// ── Archetype — drives envelope and LFO character ──────────────────────
		Archetype archetype = Archetype.values()[RANDOM.nextInt(Archetype.values().length)];

		// ── OSC mode: weighted toward BLEP/Wavetable ───────────────────────────
		float modeRoll = fullRange();
		float oscMode;
		if      (modeRoll < 0.40f) oscMode = 0f;       // BLEP
		else if (modeRoll < 0.70f) oscMode = 1f / 3f;  // WAVETABLE
		else if (modeRoll < 0.90f) oscMode = 2f / 3f;  // ADDITIVE
		else                       oscMode = 1f;        // EXITER
		P.setDirectly(P.OSC_MODE, oscMode);

		// ── Oscillators ────────────────────────────────────────────────────────
		P.setDirectly(P.OSC1_WAVE, fullRange());
		P.setDirectly(P.OSC1_WAVE_SET, fullRange());
		if (P.VAL_OSCILLATOR_MODE == P.OscillatorMode.EXITER) {
			P.setDirectly(P.OSC2_WAVE, range(0, .667f));
		} else {
			P.setDirectly(P.OSC2_WAVE, fullRange());
			P.setDirectly(P.OSC2_WAVE_SET, fullRange());
		}

		// OSC mix — vary rather than always 50/50
		P.setDirectly(P.OSC_1_2_MIX, range(0.2f, 0.8f));

		// OSC2 fine detune — slight random offset from zero for fatness
		P.setDirectly(P.OSC2_TUNING_FINE, range(0.44f, 0.56f));

		// OSC2 coarse: sync, harmonic interval, or near-unison
		boolean oscsync = enable(.25f);
		if (oscsync) {
			P.setDirectly(P.OSC2_SYNC, 1);
			P.setDirectly(P.OSC2_TUNING, range(.6f, 1));
		} else {
			if (enable(.25f)) {
				int factor = RANDOM_TUNE_SEMIS[RANDOM.nextInt(RANDOM_TUNE_SEMIS.length)];
				P.setDirectly(P.OSC2_TUNING, 0.5f + factor / 48f);
			}
			if (enable(.25f)) {
				P.setDirectly(P.OSC2_AM, fullRange());
			}
		}

		// Pulse width — classic analog range around square
		P.setDirectly(P.OSC1_PULSE_WIDTH, range(0.35f, 0.65f));
		P.setDirectly(P.OSC2_PULSE_WIDTH, range(0.35f, 0.65f));

		// Sub oscillator — occasional bass weight
		if (enable(.25f)) {
			P.setDirectly(P.OSC_SUB_VOLUME, range(0.1f, 0.4f));
		}

		// Noise
		if (enable(.334f)) {
			P.setDirectly(P.OSC_NOISE_LEVEL, range(.2f, .667f));
		}

		// ── Amp envelope — archetype constrains character ──────────────────────
		switch (archetype) {
			case PAD:
				P.setDirectly(P.AMP_ENV_A, range(0.4f, 0.75f));
				P.setDirectly(P.AMP_ENV_D, range(0.3f, 0.6f));
				P.setDirectly(P.AMP_ENV_S, range(0.7f, 1.0f));
				P.setDirectly(P.AMP_ENV_R, range(0.4f, 0.8f));
				break;
			case PLUCK:
				P.setDirectly(P.AMP_ENV_A, range(0f, 0.05f));
				P.setDirectly(P.AMP_ENV_D, range(0.1f, 0.4f));
				P.setDirectly(P.AMP_ENV_S, 0f);
				P.setDirectly(P.AMP_ENV_R, range(0.1f, 0.3f));
				break;
			case LEAD:
				P.setDirectly(P.AMP_ENV_A, range(0f, 0.2f));
				P.setDirectly(P.AMP_ENV_D, range(0.2f, 0.5f));
				P.setDirectly(P.AMP_ENV_S, range(0.5f, 0.9f));
				P.setDirectly(P.AMP_ENV_R, range(0.15f, 0.4f));
				break;
			case BASS:
				P.setDirectly(P.AMP_ENV_A, range(0f, 0.1f));
				P.setDirectly(P.AMP_ENV_D, range(0.15f, 0.45f));
				P.setDirectly(P.AMP_ENV_S, range(0.4f, 0.8f));
				P.setDirectly(P.AMP_ENV_R, range(0.1f, 0.35f));
				break;
		}

		// ── Filters ────────────────────────────────────────────────────────────
		boolean filter1on = enable();
		boolean filter2on = enable(.25f);
		if (filter1on) {
			P.setDirectly(P.FILTER1_ON, 1);
			P.setDirectly(P.FILTER1_FREQ, range(.2f, .8f));
			P.setDirectly(P.FILTER1_RESONANCE, range(0, .7f));
			P.setDirectly(P.FILTER1_TYPE, fullRange());
			// Filter envelope — the sweep is what makes it musical
			float envDepth = archetype == Archetype.PAD ? range(0.5f, 0.72f) : range(0.58f, 0.9f);
			P.setDirectly(P.FILTER1_ENV_DEPTH, envDepth);
			switch (archetype) {
				case PAD:
					P.setDirectly(P.FILTER1_ENV_A, range(0.2f, 0.6f));
					P.setDirectly(P.FILTER1_ENV_D, range(0.3f, 0.7f));
					P.setDirectly(P.FILTER1_ENV_S, range(0.3f, 0.7f));
					P.setDirectly(P.FILTER1_ENV_R, range(0.3f, 0.7f));
					break;
				case PLUCK:
					P.setDirectly(P.FILTER1_ENV_A, 0f);
					P.setDirectly(P.FILTER1_ENV_D, range(0.1f, 0.45f));
					P.setDirectly(P.FILTER1_ENV_S, 0f);
					P.setDirectly(P.FILTER1_ENV_R, range(0.1f, 0.3f));
					break;
				case LEAD:
					P.setDirectly(P.FILTER1_ENV_A, range(0f, 0.15f));
					P.setDirectly(P.FILTER1_ENV_D, range(0.15f, 0.5f));
					P.setDirectly(P.FILTER1_ENV_S, range(0.2f, 0.7f));
					P.setDirectly(P.FILTER1_ENV_R, range(0.1f, 0.4f));
					break;
				case BASS:
					P.setDirectly(P.FILTER1_ENV_A, range(0f, 0.05f));
					P.setDirectly(P.FILTER1_ENV_D, range(0.1f, 0.4f));
					P.setDirectly(P.FILTER1_ENV_S, range(0.1f, 0.5f));
					P.setDirectly(P.FILTER1_ENV_R, range(0.1f, 0.35f));
					break;
			}
		} else {
			P.setDirectly(P.FILTER1_ON, 0);
		}
		if (filter2on) {
			P.setDirectly(P.FILTER2_ON, 1);
			P.setDirectly(P.FILTER2_FREQ, range(.2f, .8f));
			P.setDirectly(P.FILTER2_RESONANCE, range(0, .7f));
			P.setDirectly(P.FILTER2_TYPE, fullRange());
		} else {
			P.setDirectly(P.FILTER2_ON, 0);
		}
		if (filter1on && filter2on) {
			P.setDirectly(P.FILTER_PARALLEL, range(.334f, 1));
		} else {
			if (filter1on || filter2on) {
				P.setDirectly(P.FILTER_PARALLEL, 0);
			}
		}

		// ── LFO ────────────────────────────────────────────────────────────────
		// Reset all LFO target amounts to neutral so enabling MOD_AMOUNT_BASE
		// doesn't inadvertently activate the non-center defaults from setToDefaults
		P.setDirectly(P.MOD_FILTER1_AMOUNT, .5f);
		P.setDirectly(P.MOD_FILTER2_AMOUNT, .5f);
		P.setDirectly(P.MOD_PITCH_AMOUNT, .5f);
		P.setDirectly(P.MOD_PITCH2_AMOUNT, .5f);
		P.setDirectly(P.MOD_WAVE1_AMOUNT, .5f);
		P.setDirectly(P.MOD_WAVE2_AMOUNT, .5f);

		if (enable(.5f)) {
			P.setDirectly(P.MOD_AMOUNT_BASE, range(0.15f, 0.45f));
			// Slower LFO for pads; faster for leads and bass
			float lfoRate = archetype == Archetype.PAD
					? range(0.3f, 0.55f)
					: range(0.5f, 0.75f);
			P.setDirectly(P.MOD_RATE, lfoRate);
			// Pick one primary target; pitch vibrato stays subtle
			int targetRoll = RANDOM.nextInt(3);
			if (targetRoll == 0 && filter1on) {
				P.setDirectly(P.MOD_FILTER1_AMOUNT, range(0.6f, 0.85f));
			} else if (targetRoll == 1) {
				P.setDirectly(P.MOD_WAVE1_AMOUNT, range(0.6f, 0.8f));
			} else {
				P.setDirectly(P.MOD_PITCH_AMOUNT, range(0.55f, 0.68f));
			}
		}

		// ── FX ─────────────────────────────────────────────────────────────────
		if (enable(.334f)) {
			P.setDirectly(P.CHORUS_DEPTH, fullRange());
		}
		if (enable(.334f)) {
			P.setDirectly(P.REVERB_ONE_KNOB, range(.1f, .85f));
		}
		if (enable(.2f)) {
			P.setDirectly(P.OVERDRIVE, range(.2f, .7f));
		}
		if (enable(.25f)) {
			P.setDirectly(P.DELAY_WET, range(0.2f, 0.5f));
			P.setDirectly(P.DELAY_FEEDBACK, range(0.2f, 0.45f));
		}

		P.LAST_LOADED_PATCH_NAME = createFancyPatchName(archetype);
		P.LAST_LOADED_PATCH_CATEGORY = PatchCategory.WHATEVER;
		log.info("Randomized patch [{}]: {}", archetype, P.LAST_LOADED_PATCH_NAME);
		SynthPi.uiMessage("Randomized patch (" + P.LAST_LOADED_PATCH_NAME + ")");
	}

	private static boolean enable(float probability) {
		return RANDOM.nextFloat() < probability;
	}

	private static boolean enable() {
		return RANDOM.nextBoolean();
	}

	private static float range(float min, float max) {
		return (max - min) * RANDOM.nextFloat() + min;
	}

	private static float fullRange() {
		return RANDOM.nextFloat();
	}

	private static String createFancyPatchName(Archetype archetype) {
		int adj1 = RANDOM.nextInt(adjectives.length);
		int adj2 = RANDOM.nextInt(adjectives.length);
		while (adj2 == adj1) {
			adj2 = RANDOM.nextInt(adjectives.length);
		}
		String[] nouns = archetypeNouns[archetype.ordinal()];
		return adjectives[adj1] + " " + adjectives[adj2] + " " + nouns[RANDOM.nextInt(nouns.length)];
	}

	private static final String[] adjectives = new String[]{
		"new", "random", "ugly", "nasty", "crazy", "weird", "scary", "funny",
		"bizarre", "strange", "odd", "peculiar", "freaky"
	};

	private static final String[][] archetypeNouns = {
		{"pad", "cloud", "wash", "shimmer"},   // PAD
		{"pluck", "click", "pop", "ping"},     // PLUCK
		{"lead", "buzz", "blade", "tone"},     // LEAD
		{"bass", "thud", "growl", "sub"},      // BASS
	};
}
