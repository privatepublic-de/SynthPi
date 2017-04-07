package de.privatepublic.pi.synth.modules.osc;

import de.privatepublic.pi.synth.modules.IControlProcessor;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;

public interface IOscillator extends IControlProcessor {

	public static enum Mode { PRIMARY, SECONDARY, SUB };
	
	public void trigger(float frequency, float velocity);
	public float process(final int sampleNo, final float volume, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope);
	
}
