package de.privatepublic.pi.synth.modules.fx;

import de.privatepublic.pi.synth.P;

public class Delay implements IProcessor {

	private final int delayLineSize = (int)(P.SAMPLE_RATE_HZ*2);
	private final int delayLineSizeUnder = delayLineSize-1;
	private final float[] delayLineL = new float[delayLineSize+1];
	private final float[] delayLineR = new float[delayLineSize+1];
	private int writeIndex = 0;
	
	public Delay() {
	}
	
	private float lastrate = 0;
	
	public void process(final int bufferLen, final float[][] buffers) {
		float feedback = P.VAL[P.DELAY_FEEDBACK];
		float delayRate = delayLineSizeUnder*(.001f+.999f*P.VALX[P.DELAY_RATE]);
		float delta = (delayRate - lastrate) / bufferLen;
		final float wet = P.VAL[P.DELAY_WET];
		final float wetInv = 1-wet;
		final float[] bufL = buffers[0];
		final float[] bufR = buffers[1];
		float in1, in2, readindex, indexFract, feedbackL, feedbackR, out1, out2, valR0, valR1, valL0, valL1;
		int indexBase;
		for (int i=0;i<bufferLen;i++) {
			in1 = bufL[i];
			in2 = bufR[i];
			if (++writeIndex==delayLineSize) {
				writeIndex=0;
				delayLineL[delayLineSize] = delayLineL[0];
				delayLineR[delayLineSize] = delayLineR[0];
			}
			readindex = (writeIndex - lastrate);
			if (readindex<0) {	readindex += delayLineSize; }
			indexBase = (int)readindex;
			indexFract = indexBase-indexBase;
			valL0 = delayLineL[indexBase];
			valL1 = delayLineL[indexBase+1];
			valR0 = delayLineR[indexBase];
			valR1 = delayLineR[indexBase+1];
			feedbackL = valR0*feedback;
			feedbackL += (valR1*feedback-feedbackL)*indexFract;
			feedbackR = valL0*feedback;
			feedbackR += (valL1*feedback-feedbackR)*indexFract;
			delayLineL[writeIndex] = feedbackL+in1+in2;
			delayLineR[writeIndex] = feedbackR;
			out1 = valL0*wet;
			out1 += (valL1*wet-out1)*indexFract;
			out2 = valR0*wet;
			out2 += (valR1*wet-out2)*indexFract;
			bufL[i] = in1*wetInv+out1;
			bufR[i] = in2*wetInv+out2;
			lastrate += delta;
		}
		lastrate = delayRate;
	}
	
}
