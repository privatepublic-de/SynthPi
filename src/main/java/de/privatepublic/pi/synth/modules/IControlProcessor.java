package de.privatepublic.pi.synth.modules;

/**
 * Implemented by audio-thread components whose state is updated once per
 * control-rate chunk (every {@link de.privatepublic.pi.synth.P#CONTROL_BUFFER_SIZE}
 * samples) rather than per sample. Implementations hoist all parameter-store
 * reads into {@link #controlTick()}, then read only their own cached fields
 * during the per-sample inner loop.
 */
public interface IControlProcessor {
	void controlTick();
}
