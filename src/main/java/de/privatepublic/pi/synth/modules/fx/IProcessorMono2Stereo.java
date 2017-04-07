package de.privatepublic.pi.synth.modules.fx;

public interface IProcessorMono2Stereo {

	public void process(final float[] inBuffer, final float[][] outBuffers, final int startPos);
	
}
