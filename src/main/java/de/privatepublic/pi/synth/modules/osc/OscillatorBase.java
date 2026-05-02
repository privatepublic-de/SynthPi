package de.privatepublic.pi.synth.modules.osc;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.modules.AnalogSynth;


public abstract class OscillatorBase implements IOscillator {
	
	public static final float PI2 = (float) (2.0*Math.PI);
	
	protected float frequency = 440;
	protected float effectiveFrequency = 440;
	protected float targetFrequency = 440;
	protected float glideStepSize = 0;

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
