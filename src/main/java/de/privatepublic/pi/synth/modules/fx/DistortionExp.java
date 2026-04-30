package de.privatepublic.pi.synth.modules.fx;

import de.privatepublic.pi.synth.P;

public class DistortionExp implements IProcessor {

	private static final float MAX_GAIN = 5;
	
	float gain, gainDry, gainWet, val, x, y;
	float[] buffer;

	public final void process(final int bufferLen, final float[][] buffers) {
		gain = 1f + MAX_GAIN*P.VAL[P.OVERDRIVE];
		gainDry = P.VALMIXHIGH[P.OVERDRIVE];
		gainWet = P.VALMIXLOW[P.OVERDRIVE];
		for (int b=0;b<2;++b) {
			buffer = buffers[b];
			for (int i=0;i<bufferLen;i++) {
				val = buffer[i];
				x = val*gain;
				y = x / (1f + Math.abs(x));
				buffer[i] = val*gainDry + y*gainWet;
			}
		}
	}
	
	
}
