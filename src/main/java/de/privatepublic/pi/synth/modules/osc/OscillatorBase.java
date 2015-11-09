package de.privatepublic.pi.synth.modules.osc;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.modules.AnalogSynth;


public abstract class OscillatorBase implements IOscillator {
	
	public static final float PI2 = (float) (2.0*Math.PI);
	
	protected float frequency = 440;
	protected float effectiveFrequency = 440;
	protected float targetFrequency = 440;
	protected float glideStepSize = 0;
	
	protected final boolean isBase;
	protected final boolean isSecond;
	
	public OscillatorBase (boolean primaryOrSecondary) {
		this.isBase = primaryOrSecondary;
		this.isSecond = !primaryOrSecondary;
	}
	
	public void trigger(final float frequency, final float velocity) {
		this.frequency = frequency;
		if (P.VAL[P.OSC_GLIDE_RATE]>0) {
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
