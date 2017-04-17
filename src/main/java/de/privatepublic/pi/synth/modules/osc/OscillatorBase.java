package de.privatepublic.pi.synth.modules.osc;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.modules.AnalogSynth;


public abstract class OscillatorBase {
	
	public static enum Mode { PRIMARY, SECONDARY, SUB }

	public static final float PI2 = (float) (2.0*Math.PI);
	
	protected float frequency = 440;
	protected float effectiveFrequency = 440;
	protected float targetFrequency = 440;
	protected float glideStepSize = 0;
	
	protected final boolean isBase;
	protected final boolean isSecond;
	protected final boolean isSub;
	
	protected final Mode oscMode;
	
	
	public OscillatorBase(boolean isPrimary) {
		isBase = isPrimary;
		isSecond = !isPrimary;
		if (isBase) {
			oscMode = Mode.PRIMARY;
		}
		else {
			oscMode = Mode.SECONDARY;
		}
		isSub = false;
	}
	
	
	public OscillatorBase (Mode mode) {
		this.isBase = mode==Mode.PRIMARY;
		this.isSecond = mode==Mode.SECONDARY;
		this.isSub = mode==Mode.SUB;
		this.oscMode = mode;
	}
	
	public void trigger(final float frequency, final float velocity) {
		this.frequency = frequency;
		if (P.IS[P.OSC_GLIDE_RATE]) {
			if (isSecond) {
				effectiveFrequency = (float) (Math.pow(2d, P.detuneCents())*AnalogSynth.lastTriggeredFrequency);				
			}
			else {
				effectiveFrequency = AnalogSynth.lastTriggeredFrequency;
			}
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
