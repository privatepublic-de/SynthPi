package de.privatepublic.pi.synth.modules.fx;

/**
 * Mono-in / mono-out FX processor running on a chunk of an audio buffer
 * starting at {@code startPos} with length {@code chunkLen}.
 */
public interface IProcessorMono {
	void process(int chunkLen, float[] buffer, int startPos);
}
