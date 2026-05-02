package de.privatepublic.pi.synth.modules.osc;

import java.util.SplittableRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.IPitchBendReceiver;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.util.FastCalc;

/**
 * PolyBLEP analog-style oscillator with sine / triangle / saw / pulse waveforms,
 * pulse-width modulation, sub-oscillator (primary only), and analog-style drift.
 * The fourth {@link de.privatepublic.pi.synth.P.OscillatorMode} value, alongside
 * the existing wavetable / additive / exciter modes.
 *
 * <p>Differs from the other three oscillators in that it reads modulation from
 * the per-voice {@link LFO} and the per-voice {@code env2} (both injected via
 * constructor), in addition to the master modEnvelope passed per call. This
 * enables BLEP-mode patches to use the env2 second envelope and channel
 * pressure for per-voice modulation, neither of which is wired into the legacy
 * three oscillators.
 */
public class BlepOscillator extends OscillatorBase implements IPitchBendReceiver {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(BlepOscillator.class);

	// Per-voice modulation sources injected by AnalogSynthVoice
	private final EnvADSR env2;
	private final LFO lfo;

	// Phase accumulators (0..1)
	private float phase = 0f;
	private float subPhase = 0f;

	// Drift state — slow random walk added to phase increment
	private final SplittableRandom driftRng = new SplittableRandom();
	private float driftValue = 0f;

	// OSC2 ring-mod active flag; matches WaveTableOscillator.ampmod pattern
	private boolean ampmod;

	// Cached parameter indices, primary-vs-secondary specific
	private final int paramOscWave;
	private final int paramOscPW;
	private final int paramModPW;
	private final int paramModEnv1PW;
	private final int paramModEnv2PW;
	private final int paramModEnv1Pitch;
	private final int paramModEnv2Pitch;
	private final int paramModPressPitch;
	private final int paramModPitch;

	public BlepOscillator(final boolean primaryOrSecondary, final EnvADSR env2, final LFO lfo) {
		super(primaryOrSecondary);
		this.env2 = env2;
		this.lfo = lfo;
		if (primaryOrSecondary) {
			paramOscWave = P.OSC1_WAVE;
			paramOscPW = P.OSC1_PULSE_WIDTH;
			paramModPW = P.MOD_PW1_AMOUNT;
			paramModEnv1PW = P.MOD_ENV1_PW1_AMOUNT;
			paramModEnv2PW = P.MOD_ENV2_PW1_AMOUNT;
			paramModEnv1Pitch = P.MOD_ENV1_PITCH_AMOUNT;
			paramModEnv2Pitch = P.MOD_ENV2_PITCH_AMOUNT;
			paramModPressPitch = P.MOD_PRESS_PITCH_AMOUNT;
			paramModPitch = P.MOD_PITCH_AMOUNT;
		} else {
			paramOscWave = P.OSC2_WAVE;
			paramOscPW = P.OSC2_PULSE_WIDTH;
			paramModPW = P.MOD_PW2_AMOUNT;
			paramModEnv1PW = P.MOD_ENV1_PW2_AMOUNT;
			paramModEnv2PW = P.MOD_ENV2_PW2_AMOUNT;
			paramModEnv1Pitch = P.MOD_ENV1_PITCH2_AMOUNT;
			paramModEnv2Pitch = P.MOD_ENV2_PITCH2_AMOUNT;
			paramModPressPitch = P.MOD_PRESS_PITCH2_AMOUNT;
			paramModPitch = P.MOD_PITCH2_AMOUNT;
		}
		MidiHandler.registerReceiver(this);
	}

	@Override
	public void trigger(final float frequency, final float velocity) {
		super.trigger(frequency, velocity);
		phase = 0f;
		subPhase = 0f;
		ampmod = false;
		driftValue = 0f;
	}

	@Override
	protected void setTargetFrequency(final float frequency) {
		// OSC2 keytracking switch: when disabled, OSC2 sits at fixed C4 frequency
		// regardless of the played key — useful for ring-modulation experiments.
		if (isSecond && !P.IS[P.OSC2_KEYTRACKING]) {
			super.setTargetFrequency(P.MIDI_NOTE_FREQUENCY_HZ[60]);
		} else {
			super.setTargetFrequency(frequency);
		}
	}

	@Override
	public void onPitchBend() {
		setTargetFrequency(frequency);
	}

	// --- Waveform helpers ---

	/** PolyBLEP residual at phase {@code t} given per-sample phase increment {@code dt}. */
	private static float polyBlep(float t, final float dt) {
		if (t < dt) {
			t /= dt;
			return t + t - t*t - 1f;
		}
		if (t > 1f - dt) {
			t = (t - 1f) / dt;
			return t*t + t + t + 1f;
		}
		return 0f;
	}

	/** Naive saw with PolyBLEP correction at the wrap discontinuity. Output -1..1. */
	private static float bandlimitedSaw(final float p, final float dt) {
		return (2f*p - 1f) - polyBlep(p, dt);
	}

	/** Pulse with arbitrary width {@code pw}, two PolyBLEP corrections (one per edge). */
	private static float bandlimitedPulse(final float p, final float dt, final float pw) {
		float val = p < pw ? 1f : -1f;
		val += polyBlep(p, dt);
		// Discontinuity at p=pw: shift phase so it wraps at the same place.
		float t = p - pw;
		if (t < 0f) t += 1f;
		val -= polyBlep(t, dt);
		return val;
	}

	/** Triangle generated as integrated square (one-pole leak prevents DC drift). */
	private float triangleAccum = 0f;
	private float bandlimitedTriangle(final float p, final float dt, final float lastTri) {
		// Use 50% pulse as derivative source; integrate with a leaky one-pole.
		final float sq = bandlimitedPulse(p, dt, 0.5f);
		// Integration step proportional to dt to keep amplitude consistent across pitch.
		final float step = dt * 4f;
		float tri = lastTri + step * sq;
		// Light leak (decays toward 0) prevents long-term DC drift accumulation.
		tri *= 0.999f;
		return FastCalc.ensureRange(tri, -1f, 1f);
	}

	/**
	 * Generate one sample for the current waveform selection.
	 * waveSel: 0..1 — 0..0.25 sine, 0.25..0.5 triangle, 0.5..0.75 saw, else pulse.
	 */
	private float generateSample(final float waveSel, final float p, final float dt, final float pw) {
		if (waveSel < 0.25f) {
			return (float) Math.sin(p * 2f * Math.PI);
		} else if (waveSel < 0.5f) {
			triangleAccum = bandlimitedTriangle(p, dt, triangleAccum);
			return triangleAccum;
		} else if (waveSel < 0.75f) {
			return bandlimitedSaw(p, dt);
		} else {
			return bandlimitedPulse(p, dt, pw);
		}
	}

	/** Sub oscillator: square (or triangle) at half or quarter primary frequency. */
	private float generateSubSample(final float p) {
		final boolean useSquare = P.IS[P.OSC_SUB_SQUARE];
		if (useSquare) {
			return p < 0.5f ? 1f : -1f;
		} else {
			// Triangle: rising 0..0.5, falling 0.5..1
			return p < 0.5f ? (4f*p - 1f) : (3f - 4f*p);
		}
	}

	// --- IOscillator: per-sample variants (used by USE_BUFFER_PATH=false fallback) ---

	@Override
	public float processSample1st(final int sampleNo, final float volume,
			final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
		// Glide
		if (effectiveFrequency != targetFrequency) {
			if (effectiveFrequency < targetFrequency) effectiveFrequency += glideStepSize;
			else effectiveFrequency -= glideStepSize;
			if (Math.abs(effectiveFrequency - targetFrequency) < glideStepSize) {
				effectiveFrequency = targetFrequency;
			}
		}
		// Compute frequency with all modulation sources
		final float lfoVal = lfo.bufferedValueAt(sampleNo);
		final float modAmount = P.MOD_AMOUNT_COMBINED;
		final float pitchModLfo = 1 - lfoVal*modAmount*P.VALXC[paramModPitch];
		final float pitchModEnv = modEnvelope.outValue * P.VALXC[paramModEnv1Pitch]
				+ env2.outValue * P.VALXC[paramModEnv2Pitch]
				+ P.CHANNEL_PRESSURE * P.VALXC[paramModPressPitch];
		final float freq = effectiveFrequency * (pitchModLfo + pitchModEnv) * P.PITCH_BEND_FACTOR;
		final float dt = freq / P.SAMPLE_RATE_HZ;
		// Compute pulse width with all modulation
		final float pwBase = P.VAL[paramOscPW];
		final float pwMod = lfoVal*modAmount*P.VALXC[paramModPW]
				+ modEnvelope.outValue * P.VALC[paramModEnv1PW]
				+ env2.outValue * P.VALC[paramModEnv2PW];
		final float pw = FastCalc.ensureRange(pwBase + pwMod, 0.05f, 0.95f);
		// Wave selection from knob
		final float waveSel = P.VAL[paramOscWave];
		final float val = generateSample(waveSel, phase, dt, pw);
		// Sub-osc (primary only)
		float out = val;
		if (isBase && P.IS[P.OSC_SUB_VOLUME]) {
			out += generateSubSample(subPhase) * P.VAL[P.OSC_SUB_VOLUME];
		}
		am_buffer[sampleNo] = out;
		// Advance phase
		phase += dt;
		if (phase >= 1f) {
			phase -= 1f;
			syncOnFrameBuffer[sampleNo] = true;
		} else {
			syncOnFrameBuffer[sampleNo] = false;
		}
		// Sub at half (or quarter) frequency
		final float subDt = dt * (P.IS[P.OSC_SUB_LOW] ? 0.25f : 0.5f);
		subPhase += subDt;
		if (subPhase >= 1f) subPhase -= 1f;
		return out * volume;
	}

	@Override
	public float processSample2nd(final int sampleNo, final float volume,
			final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
		// AM / ring-mod handling — matches WaveTableOscillator
		if (ampmod && !P.IS[P.OSC2_AM]) {
			effectiveFrequency = targetFrequency;
		}
		final float ampModAmount = P.VALC[P.MOD_ENV1_AM_AMOUNT];
		final float ampamount = FastCalc.ensureRange(P.VAL[P.OSC2_AM] + modEnvelope.outValue*ampModAmount, 0, 1);
		ampmod = ampamount > 0 || ampModAmount != 0;
		if (ampmod) {
			effectiveFrequency = targetFrequency * (ampamount * 4);
		} else {
			if (effectiveFrequency != targetFrequency) {
				if (effectiveFrequency < targetFrequency) effectiveFrequency += glideStepSize;
				else effectiveFrequency -= glideStepSize;
				if (Math.abs(effectiveFrequency - targetFrequency) < glideStepSize) {
					effectiveFrequency = targetFrequency;
				}
			}
		}
		final float lfoVal = lfo.bufferedValueAt(sampleNo);
		final float modAmount = P.MOD_AMOUNT_COMBINED;
		final float pitchModLfo = 1 - lfoVal*modAmount*P.VALXC[P.MOD_PITCH_AMOUNT];
		final float pitchModEnv = modEnvelope.outValue * P.VALXC[P.MOD_ENV1_PITCH_AMOUNT];
		final float pitch2Lfo = ((lfoVal+1)*modAmount*0.5f*P.VALXC[paramModPitch]) + 1;
		final float pitch2Env = modEnvelope.outValue * P.VALXC[paramModEnv1Pitch]
				+ env2.outValue * P.VALXC[paramModEnv2Pitch]
				+ P.CHANNEL_PRESSURE * P.VALXC[paramModPressPitch];
		final float freq = effectiveFrequency * (pitchModLfo + pitchModEnv) * P.PITCH_BEND_FACTOR
				* (pitch2Lfo + pitch2Env);
		final float dt = freq / P.SAMPLE_RATE_HZ;
		final float pwBase = P.VAL[paramOscPW];
		final float pwMod = lfoVal*modAmount*P.VALXC[paramModPW]
				+ modEnvelope.outValue * P.VALC[paramModEnv1PW]
				+ env2.outValue * P.VALC[paramModEnv2PW];
		final float pw = FastCalc.ensureRange(pwBase + pwMod, 0.05f, 0.95f);
		final float waveSel = P.VAL[paramOscWave];
		final float savedAccum = triangleAccum;
		float val = generateSample(waveSel, phase, dt, pw);
		if (!ampmod && P.IS[P.OSC2_SYNC] && syncOnFrameBuffer[sampleNo]) {
			triangleAccum = savedAccum;
			final float afterVal = generateSample(waveSel, 0f, dt, pw);
			syncCorrection += val - afterVal;
			syncFadeRemaining = SYNC_FADE_SAMPLES;
			syncCorrectionStep = syncCorrection / SYNC_FADE_SAMPLES;
			phase = 0f;
			val = afterVal;
		}
		phase += dt;
		if (phase >= 1f) phase -= 1f;
		return ampmod ? am_buffer[sampleNo]*val*volume : (val + applySyncCorrection())*volume;
	}

	// --- IOscillator: buffer-rate variants (used by USE_BUFFER_PATH=true, the default) ---

	public void processBuffer1st(final int nframes, final float vol,
			final boolean[] syncOnFrameBuffer, final float[] am_buffer,
			final float[] modEnvBuf, final float[] outBuf) {
		// Hoist all P.* reads at chunk boundary.
		final float pitchBend = P.PITCH_BEND_FACTOR;
		final float pitchDepth = P.VALXC[P.MOD_PITCH_AMOUNT];
		final float pitchModEnv1Depth = P.VALXC[paramModEnv1Pitch];
		final float pitchModEnv2Depth = P.VALXC[paramModEnv2Pitch];
		final float pitchModPressDepth = P.VALXC[paramModPressPitch];
		final float channelPressure = P.CHANNEL_PRESSURE;
		final float modAmount = P.MOD_AMOUNT_COMBINED;
		final float pwBase = P.VAL[paramOscPW];
		final float pwLfoDepth = P.VALXC[paramModPW];
		final float pwModEnv1Depth = P.VALC[paramModEnv1PW];
		final float pwModEnv2Depth = P.VALC[paramModEnv2PW];
		final float waveSel = P.VAL[paramOscWave];
		final float env2Val = env2.outValue;
		final boolean subOn = isBase && P.IS[P.OSC_SUB_VOLUME];
		final float subVol = subOn ? P.VAL[P.OSC_SUB_VOLUME] : 0f;
		final float subFactor = P.IS[P.OSC_SUB_LOW] ? 0.25f : 0.5f;
		// Drift: very small random walk per chunk, applied as a ratio to phase increment.
		driftValue = driftValue * 0.95f + (driftRng.nextFloat() - 0.5f) * 0.0005f;
		final float driftRatio = 1f + driftValue;

		float effFreq = effectiveFrequency;
		float p = phase;
		float sp = subPhase;
		final float targetFreq = targetFrequency;
		final float glide = glideStepSize;
		final float invSampleRate = 1f / P.SAMPLE_RATE_HZ;

		for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
			if (effFreq != targetFreq) {
				if (effFreq < targetFreq) effFreq += glide;
				else effFreq -= glide;
				if (Math.abs(effFreq - targetFreq) < glide) effFreq = targetFreq;
			}
			final float lfoVal = lfo.bufferedValueAt(sampleNo);
			final float modEnvVal = modEnvBuf[sampleNo];
			final float pitchModLfo = 1 - lfoVal*modAmount*pitchDepth;
			final float pitchModEnv = modEnvVal*pitchModEnv1Depth + env2Val*pitchModEnv2Depth
					+ channelPressure*pitchModPressDepth;
			final float freq = effFreq * (pitchModLfo + pitchModEnv) * pitchBend;
			final float dt = freq * invSampleRate * driftRatio;
			final float pwMod = lfoVal*modAmount*pwLfoDepth + modEnvVal*pwModEnv1Depth + env2Val*pwModEnv2Depth;
			final float pw = FastCalc.ensureRange(pwBase + pwMod, 0.05f, 0.95f);
			final float val = generateSample(waveSel, p, dt, pw);
			float out = val;
			if (subOn) {
				out += generateSubSample(sp) * subVol;
			}
			am_buffer[sampleNo] = out;
			outBuf[sampleNo] = out * vol;
			p += dt;
			if (p >= 1f) {
				p -= 1f;
				syncOnFrameBuffer[sampleNo] = true;
			} else {
				syncOnFrameBuffer[sampleNo] = false;
			}
			sp += dt * subFactor;
			if (sp >= 1f) sp -= 1f;
		}
		effectiveFrequency = effFreq;
		phase = p;
		subPhase = sp;
	}

	public void processBuffer2nd(final int nframes, final float vol,
			final boolean[] syncOnFrameBuffer, final float[] am_buffer,
			final float[] modEnvBuf, final float[] outBuf) {
		// Hoist
		final float pitchBend = P.PITCH_BEND_FACTOR;
		final float pitchDepth = P.VALXC[P.MOD_PITCH_AMOUNT];
		final float pitchModEnv1Depth = P.VALXC[P.MOD_ENV1_PITCH_AMOUNT];
		final float pitch2Depth = P.VALXC[paramModPitch];
		final float pitch2ModEnv1Depth = P.VALXC[paramModEnv1Pitch];
		final float pitch2ModEnv2Depth = P.VALXC[paramModEnv2Pitch];
		final float pitch2ModPressDepth = P.VALXC[paramModPressPitch];
		final float channelPressure = P.CHANNEL_PRESSURE;
		final float modAmount = P.MOD_AMOUNT_COMBINED;
		final float ampModAmount = P.VALC[P.MOD_ENV1_AM_AMOUNT];
		final float osc2AmBase = P.VAL[P.OSC2_AM];
		final boolean osc2AmIs = P.IS[P.OSC2_AM];
		final boolean osc2SyncIs = P.IS[P.OSC2_SYNC];
		final float pwBase = P.VAL[paramOscPW];
		final float pwLfoDepth = P.VALXC[paramModPW];
		final float pwModEnv1Depth = P.VALC[paramModEnv1PW];
		final float pwModEnv2Depth = P.VALC[paramModEnv2PW];
		final float waveSel = P.VAL[paramOscWave];
		final float env2Val = env2.outValue;

		float effFreq = effectiveFrequency;
		float p = phase;
		boolean ampmodLocal = ampmod;
		final float targetFreq = targetFrequency;
		final float glide = glideStepSize;
		final float invSampleRate = 1f / P.SAMPLE_RATE_HZ;

		for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
			if (ampmodLocal && !osc2AmIs) {
				effFreq = targetFreq;
			}
			final float modEnvVal = modEnvBuf[sampleNo];
			final float ampamount = FastCalc.ensureRange(osc2AmBase + modEnvVal*ampModAmount, 0, 1);
			ampmodLocal = ampamount > 0 || ampModAmount != 0;
			if (ampmodLocal) {
				effFreq = targetFreq * (ampamount * 4);
			} else {
				if (effFreq != targetFreq) {
					if (effFreq < targetFreq) effFreq += glide;
					else effFreq -= glide;
					if (Math.abs(effFreq - targetFreq) < glide) effFreq = targetFreq;
				}
			}
			final float lfoVal = lfo.bufferedValueAt(sampleNo);
			final float pitchModLfo = 1 - lfoVal*modAmount*pitchDepth;
			final float pitchModEnv = modEnvVal*pitchModEnv1Depth;
			final float pitch2Lfo = ((lfoVal+1)*modAmount*0.5f*pitch2Depth) + 1;
			final float pitch2Env = modEnvVal*pitch2ModEnv1Depth + env2Val*pitch2ModEnv2Depth
					+ channelPressure*pitch2ModPressDepth;
			final float freq = effFreq * (pitchModLfo + pitchModEnv) * pitchBend * (pitch2Lfo + pitch2Env);
			final float dt = freq * invSampleRate;
			final float pwMod = lfoVal*modAmount*pwLfoDepth + modEnvVal*pwModEnv1Depth + env2Val*pwModEnv2Depth;
			final float pw = FastCalc.ensureRange(pwBase + pwMod, 0.05f, 0.95f);
			final float savedAccum = triangleAccum;
			float val = generateSample(waveSel, p, dt, pw);
			if (!ampmodLocal && osc2SyncIs && syncOnFrameBuffer[sampleNo]) {
				triangleAccum = savedAccum;
				final float afterVal = generateSample(waveSel, 0f, dt, pw);
				syncCorrection += val - afterVal;
				syncFadeRemaining = SYNC_FADE_SAMPLES;
				syncCorrectionStep = syncCorrection / SYNC_FADE_SAMPLES;
				p = 0f;
				val = afterVal;
			}
			outBuf[sampleNo] = ampmodLocal ? am_buffer[sampleNo]*val*vol : (val + applySyncCorrection())*vol;
			p += dt;
			if (p >= 1f) p -= 1f;
		}
		effectiveFrequency = effFreq;
		phase = p;
		ampmod = ampmodLocal;
	}
}
