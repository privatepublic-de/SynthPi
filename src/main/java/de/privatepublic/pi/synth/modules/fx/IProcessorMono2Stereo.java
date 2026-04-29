package de.privatepublic.pi.synth.modules.fx;

/**
 * Mono-in / stereo-out FX processor running on a chunk of an audio buffer
 * starting at {@code startPos} with length {@code chunkLen}.
 */
public interface IProcessorMono2Stereo {
	void process(int chunkLen, float[] inBuffer, float[][] outBuffers, int startPos);
}
