package de.privatepublic.pi.synth.modules.fx;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.P.FilterType;
import de.privatepublic.pi.synth.modules.mod.LFO;

public class Chorus implements IProcessor {

	private final float[] delayLineL;
//	private final float[] delayLineR;
	private final int delayLineSize;
	private final int delayLineSizeUnder;
	private int writeIndex = 0;
	private final float feedback = 0;//.4f;
	private final LFO lfo;
	private StateVariableFilter highpass = new StateVariableFilter(FilterType.HIGHPASS, 100, 0);
	
	public Chorus(float maxDelayMS) {
		delayLineSize = (int)(maxDelayMS/P.MILLIS_PER_SAMPLE_FRAME);
		delayLineSizeUnder = delayLineSize-1;
		delayLineL = new float[delayLineSize+2];
//		delayLineR = new float[delayLineSize+1];
		P.set(P.CHORUS_LFO_RATE, 1/4f);
		P.set(P.CHORUS_LFO_TYPE, 0);
		lfo = new LFO(P.CHORUS_LFO_RATE, P.CHORUS_LFO_TYPE);
	}
	
	
	
	float wet, dry, in1, in2, sampleval, lfoval, readindexL, out1;
	int indexBaseL;
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
			lfoval = (1+lfo.valueAt(i))*.5f;
			readindexL = (writeIndex - delayLineSizeUnder*lfoval);
			if (readindexL<0) {	readindexL += delayLineSize; }
			indexBaseL = (int)readindexL;
			delayLineL[writeIndex] = delayLineL[indexBaseL]*feedback + sampleval; 
			if (writeIndex==0) {
				delayLineL[delayLineSize] = delayLineL[0];
			}
			out1 = delayLineL[indexBaseL]*wet;
			out1 += (delayLineL[indexBaseL+1]*wet-out1)*(readindexL-indexBaseL);
			bufL[i] = in1*dry+out1;
			bufR[i] = in2*dry-out1;
		}
		lfo.nextBufferSlice(bufferLen);
	}
	
}
