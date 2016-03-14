package de.privatepublic.pi.synth.modules.fx;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.P.FilterType;
import de.privatepublic.pi.synth.modules.mod.LFO;

public class Chorus implements IProcessor {

	private final float[] delayLineL;
	private final float[] delayLineR;
	private final int delayLineSize;
	private final int delayLineSizeUnder;
	private final float feedback = 0;//.4f;
	private final LFO lfo;
	private StateVariableFilter highpass = new StateVariableFilter(FilterType.HIGHPASS, 100, 0);
	
	public Chorus(float maxDelayMS) {
		delayLineSize = (int)(maxDelayMS/P.MILLIS_PER_SAMPLE_FRAME);
		delayLineSizeUnder = delayLineSize-2;
		delayLineL = new float[delayLineSize+2];
		delayLineR = new float[delayLineSize+2];
//		P.VAL[P.CHORUS_LFO_RATE] = 1/4f;
//		P.VALX[P.CHORUS_LFO_RATE] =  (float) Math.pow(P.VAL[P.CHORUS_LFO_RATE], 4);
//		P.VAL[P.CHORUS_LFO_TYPE] = 0;
//		P.set(P.CHORUS_LFO_RATE, 1/4f);
//		P.set(P.CHORUS_LFO_TYPE, 0);
		lfo = new LFO(P.CHORUS_LFO_RATE, P.CHORUS_LFO_TYPE);
	}
	
	
	
	float wet, dry, in1, in2, sampleval, modval, lfoval, readindexL, readindexR, outL, outR;
	int indexBaseL, indexBaseR, writeIndex;
	float[] bufL, bufR;
	
	public void process(final int bufferLen, final float[][] buffers) {
		wet = P.VAL[P.CHORUS_DEPTH];
		dry = 1-wet*.5f;
		bufL = buffers[0];
		bufR = buffers[1];
		for (int i=0;i<bufferLen;i++) {
			in1 = bufL[i];
			in2 = bufR[i];
			sampleval = highpass.processSample((in1+in2)*.5f);
			if (++writeIndex==delayLineSize) {
				writeIndex=0;
			}
			lfoval = lfo.valueAt(i);
			modval = (1+lfoval)*.5f;
			
			readindexL = (writeIndex - delayLineSizeUnder*modval);
			if (readindexL<0) {	readindexL += delayLineSize; }
			indexBaseL = (int)readindexL;
			
			modval = (1+lfoval*-1)*.5f;
			readindexR = (writeIndex - delayLineSizeUnder*modval);
			if (readindexR<0) {	readindexR += delayLineSize; }
			indexBaseR = (int)readindexR;
			
			delayLineL[writeIndex] = delayLineL[indexBaseL]*feedback + sampleval;
			delayLineR[writeIndex] = delayLineR[indexBaseR]*feedback + sampleval;
			if (writeIndex==0) {
				delayLineL[delayLineSize] = delayLineL[0];
				delayLineR[delayLineSize] = delayLineR[0];
			}
			outL = delayLineL[indexBaseL]*wet;
			outL += (delayLineL[indexBaseL+1]*wet-outL)*(readindexL-indexBaseL);
			outR = delayLineR[indexBaseR]*wet;
			outR += (delayLineR[indexBaseR+1]*wet-outR)*(readindexR-indexBaseR);
			bufL[i] = in1*dry+outL;
			bufR[i] = in2*dry-outR;
		}
		lfo.nextBufferSlice(bufferLen);
	}
	
}
