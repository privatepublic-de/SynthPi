package de.privatepublic.pi.synth.modules.osc;

import de.privatepublic.pi.synth.modules.mod.EnvADSR;

public interface IOscillator {

	public static final boolean PRIMARY_OSC = true;
	public static final boolean SECONDARY_OSC = false;
	
	public void trigger(float frequency, float velocity);
	public float processSample1st(final int sampleNo, final float volume, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope);
	public float processSample2nd(final int sampleNo, final float volume, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope);
	
}
