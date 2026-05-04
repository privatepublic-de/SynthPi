package de.privatepublic.pi.synth.modules.osc;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.modules.AnalogSynth;


public abstract class OscillatorBase implements IOscillator {
	
	public static final float PI2 = (float) (2.0*Math.PI);
	
	protected float frequency = 440;
	protected float effectiveFrequency = 440;
	protected float targetFrequency = 440;
	protected float glideStepSize = 0;

	/** Control-rate env2 snapshot set by AnalogSynthVoice before each processBuffer call. */
	public float env2Val = 0f;
	/** Normalized keyboard position: 0 at A4 (440 Hz), ±1 over ±4 octaves. Set at trigger. */
	public float keyNorm = 0f;
	/** Note velocity 0..1 captured at trigger time. */
	public float noteVelocity = 0f;

	// Sync anti-click: at the reset sample, accumulate (before - after) here and
	// decay linearly to zero so the output is continuous across the hard reset.
	protected static final int SYNC_FADE_SAMPLES = 8;
	protected float syncCorrection = 0;
	protected float syncCorrectionStep = 0;
	protected int syncFadeRemaining = 0;

	protected final float applySyncCorrection() {
		if (syncFadeRemaining > 0) {
			final float c = syncCorrection;
			syncCorrection -= syncCorrectionStep;
			if (--syncFadeRemaining == 0) {
				syncCorrection = 0;
				syncCorrectionStep = 0;
			}
			return c;
		}
		return 0;
	}

	protected final boolean isBase;
	protected final boolean isSecond;
	
	public OscillatorBase (boolean primaryOrSecondary) {
		this.isBase = primaryOrSecondary;
		this.isSecond = !primaryOrSecondary;
	}
	
	public void trigger(final float frequency, final float velocity) {
		this.frequency = frequency;
		this.noteVelocity = velocity;
		// keyNorm: 0 at A4 (440 Hz), ±1 over ±4 octaves, clamped.
		final float octaves = (float)(Math.log(frequency / 440.0) / Math.log(2.0)) / 4f;
		this.keyNorm = octaves < -1f ? -1f : (octaves > 1f ? 1f : octaves);
		if (P.IS[P.OSC_GLIDE_RATE]) {
			effectiveFrequency = AnalogSynth.lastTriggeredFrequency;
			glideStepSize = Math.abs((AnalogSynth.lastTriggeredFrequency-frequency)/(P.SAMPLE_RATE_HZ*P.VALX[P.OSC_GLIDE_RATE]));
		}
		else {
			glideStepSize = 0;
		}
		setTargetFrequency(frequency);

	}
	
	protected void setTargetFrequency(final float frequency) {
		final float detunecents = P.detuneCents();
		if (isSecond && detunecents!=0) {
			targetFrequency =  (float) (Math.pow(2d, detunecents)*frequency);
		}
		else {
			targetFrequency = frequency;
		}
		if (glideStepSize==0) {
			effectiveFrequency = targetFrequency;
		}
	}
	
}
