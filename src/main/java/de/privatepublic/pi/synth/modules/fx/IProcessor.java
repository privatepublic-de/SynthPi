package de.privatepublic.pi.synth.modules.fx;

public interface IProcessor {
	public void process(final int bufferLen, final float[][] buffers);
}
