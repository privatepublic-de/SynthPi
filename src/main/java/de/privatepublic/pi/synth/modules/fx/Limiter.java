package de.privatepublic.pi.synth.modules.fx;

import de.privatepublic.pi.synth.P;

public class Limiter implements IProcessor {
	
	private final float attack;
	private final float release;
	private float envelope;
	
	public Limiter(float attackMS, float releaseMS) {
		attack = (float) Math.pow( 0.01, 1.0 / ( attackMS * P.SAMPLE_RATE_HZ * 0.001 ) );
	    release = (float) Math.pow( 0.01, 1.0 / ( releaseMS * P.SAMPLE_RATE_HZ * 0.001 ) );
	}
	
	float val;
	float[] outL;
	float[] outR;
	
	public void process(final int bufferLen, final float[][] input) {
		outL = input[0];
		outR = input[1];
		for (int i=0;i<bufferLen;i++) {
			val = outL[i] + outR[i];
			if( val>envelope )
	            envelope = attack * ( envelope - val ) + val;
	        else
	            envelope = release * ( envelope - val ) + val;
			if( envelope>1 ) {
				outL[i] /= envelope;
				outR[i] /= envelope;
			}
		}
		P.limiterReductionValue = envelope;
	}

}
