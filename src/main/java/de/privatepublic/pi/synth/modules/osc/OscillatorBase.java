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
	
	
	protected void setTargetFrequency(final float frequency) {
		final float detunecents = P.osc2DetuneCents;
		if (isSecond && detunecents!=0) {
			targetFrequency =  P.osc2DetuneFactor*frequency;
		}
		else {
			targetFrequency = frequency;
		}
		if (glideStepSize==0) {
			effectiveFrequency = targetFrequency;
		}
	}
	
}
