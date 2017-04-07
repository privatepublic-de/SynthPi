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
	protected final boolean isSub;
	
	protected final Mode mode;
	
	
	public OscillatorBase(boolean isPrimary) {
		isBase = isPrimary;
		isSecond = !isPrimary;
		if (isBase) {
			mode = Mode.PRIMARY;
		}
		else {
			mode = Mode.SECONDARY;
		}
		isSub = false;
	}
	
	
	public OscillatorBase (Mode mode) {
		this.isBase = mode==Mode.PRIMARY;
		this.isSecond = mode==Mode.SECONDARY;
		this.isSub = mode==Mode.SUB;
		this.mode = mode;
	}
	
	public void trigger(final float frequency, final float velocity) {
		this.frequency = frequency;
		if (P.IS[P.OSC_GLIDE_RATE]) {
			effectiveFrequency = AnalogSynth.lastTriggeredFrequency;
			glideStepSize = Math.abs((AnalogSynth.lastTriggeredFrequency-frequency)/(P.SAMPLE_RATE_HZ/P.CONTROL_BUFFER_SIZE*P.VALX[P.OSC_GLIDE_RATE]));
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
