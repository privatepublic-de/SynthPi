package de.privatepublic.pi.synth.modules.fx;

import de.privatepublic.pi.synth.P;

public class DistortionExp implements IProcessorMono {

	private static final float MAX_GAIN = 5;
	
	float gain, gainDry, gainWet, val, x, x2, y;
	float[] buffer;
	
	@Override
	public void process(float[] buffer, int startPos) {
		gain = 1f + MAX_GAIN*P.VALX[P.OVERDRIVE];
		gainDry = P.VALMIXHIGH[P.OVERDRIVE];
		gainWet = P.VALMIXLOW[P.OVERDRIVE];
		for (int i=0;i<P.CONTROL_BUFFER_SIZE;i++) {
			final int pos = i+startPos;
			val = buffer[pos];
			x = val*gain;
			x2 = x*x;
			y = x * ( 27 + x2 ) / ( 27 + 9 * x2 );
			buffer[pos] = val*gainDry + y*gainWet;
		}
	}
	
	
}
