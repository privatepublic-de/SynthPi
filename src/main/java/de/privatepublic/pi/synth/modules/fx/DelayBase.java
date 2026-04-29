package de.privatepublic.pi.synth.modules.fx;

import de.privatepublic.pi.synth.modules.IControlProcessor;

/**
 * Common base for the dual-delay implementations selected at runtime via
 * {@link de.privatepublic.pi.synth.P#DELAY_TYPE}. Implements the legacy
 * {@link IProcessor} interface so it slots into {@code AnalogSynth}'s current
 * FX chain unchanged. {@link IControlProcessor#controlTick()} is a no-op until
 * subclasses opt in (used by the chunked-dispatch path introduced in a later
 * phase).
 */
public abstract class DelayBase implements IProcessor, IControlProcessor {

	/**
	 * Reset internal delay-line state. Called by {@code AnalogSynth} when this
	 * delay is selected after another delay was active, so stale buffer content
	 * from the previous selection doesn't leak through.
	 */
	public abstract void initPatch();

	@Override
	public void controlTick() {
		// default no-op
	}
}
