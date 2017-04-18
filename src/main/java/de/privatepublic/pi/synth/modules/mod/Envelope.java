package de.privatepublic.pi.synth.modules.mod;

import de.privatepublic.pi.synth.modules.IControlProcessor;

public abstract class Envelope implements IControlProcessor {

	public static final float MIN_TIME_MILLIS = 2;
	public static final float MAX_TIME_MILLIS = 30000;
	
	protected static final float ZERO_THRESHOLD = (float) 1.0E-4;
	
	public float outValue = 0;
	
	public abstract void noteOn();
	public abstract void noteOff();
	
}
