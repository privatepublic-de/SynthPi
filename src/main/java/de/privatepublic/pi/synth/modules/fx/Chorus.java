package de.privatepublic.pi.synth.modules.fx;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.P.FilterType;
import de.privatepublic.pi.synth.modules.mod.LFO;

public class Chorus implements IProcessorStereo {

	private final float[] delayLineL;
	private final float[] delayLineR;
	private final int delayLineSize;
	private final int delayLineSizeUnder;
	private final float feedback = 0;//.4f;
	private final LFO lfo;
	private StateVariableFilter highpass1 = new StateVariableFilter(FilterType.HIGHPASS, 100, 0);
	private StateVariableFilter highpass2 = new StateVariableFilter(FilterType.HIGHPASS, 100, 0);
	
	public Chorus(float maxDelayMS) {
		delayLineSize = (int)(maxDelayMS/P.MILLIS_PER_SAMPLE_FRAME);
		delayLineSizeUnder = delayLineSize-2;
		delayLineL = new float[delayLineSize+2];
		delayLineR = new float[delayLineSize+2];
		lfo = new LFO(P.CHORUS_LFO_RATE, P.CHORUS_LFO_TYPE);
	}
	
	float wet, dry, in1, in2, hin1, hin2, modval, lfoval, readindexL, readindexR, outL, outR;
	int indexBaseL, indexBaseR, writeIndex;
	float[] bufL, bufR;
	
	@Override
	public void process(float[][] buffers, int startPos) {
		wet = P.VAL[P.CHORUS_DEPTH]*.7f;
		dry = 1-wet;
		bufL = buffers[0];
		bufR = buffers[1];
		lfo.controlTick();
		lfoval = lfo.value();
//		System.out.println(lfoval);
		for (int i=0;i<P.CONTROL_BUFFER_SIZE;i++) {
			final int pos = i+startPos;
			in1 = bufL[pos];
			in2 = bufR[pos];
			hin1 = highpass1.processSample(in1);
			hin2 = highpass2.processSample(in2);
//			sampleval = highpass.processSample((in1+in2)*.5f);
			if (++writeIndex==delayLineSize) {
				writeIndex=0;
			}
			modval = (1+lfoval)*.5f;
			readindexL = (writeIndex - delayLineSizeUnder*modval);
			if (readindexL<0) {	readindexL += delayLineSize; }
			indexBaseL = (int)readindexL;
			
			modval = (1+lfoval*-1)*.5f;
			readindexR = (writeIndex - delayLineSizeUnder*modval);
			if (readindexR<0) {	readindexR += delayLineSize; }
			indexBaseR = (int)readindexR;
			
			delayLineL[writeIndex] = delayLineL[indexBaseL]*feedback + hin1;
			delayLineR[writeIndex] = delayLineR[indexBaseR]*feedback + hin2;
			if (writeIndex==0) {
				delayLineL[delayLineSize] = delayLineL[0];
				delayLineR[delayLineSize] = delayLineR[0];
			}
			outL = delayLineL[indexBaseL]*wet;
			outL += (delayLineL[indexBaseL+1]*wet-outL)*(readindexL-indexBaseL);
			outR = delayLineR[indexBaseR]*wet;
			outR += (delayLineR[indexBaseR+1]*wet-outR)*(readindexR-indexBaseR);
			bufL[pos] = in1*dry+outL;
			bufR[pos] = in2*dry-outR;
		}
		
	}
		
}
