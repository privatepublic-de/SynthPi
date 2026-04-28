package de.privatepublic.pi.synth.modules.fx;

/**
 * Stereo-in-place FX processor running on a chunk of an audio buffer
 * starting at {@code startPos} with length {@code chunkLen}.
 */
public interface IProcessorStereo {
	void process(int chunkLen, float[][] buffers, int startPos);
}
