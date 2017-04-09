package de.privatepublic.pi.synth.modules.osc;

import de.privatepublic.pi.synth.modules.IControlProcessor;

public interface IOscillator extends IControlProcessor {

	public static enum Mode { PRIMARY, SECONDARY, SUB };
	
	public void trigger(float frequency, float velocity);
	public float process(final int sampleNo, final float volume, final boolean[] syncOnFrameBuffer, final float[] am_buffer);
	
}
