package de.privatepublic.pi.synth.modules.fx;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.P.FilterType;
import de.privatepublic.pi.synth.modules.IControlProcessor;
import de.privatepublic.pi.synth.modules.mod.EnvAHD;
import de.privatepublic.pi.synth.modules.mod.LFO;

public class TapeDelay implements IProcessorStereo, IControlProcessor {

	private DelayLine lineL;
	private DelayLine lineR;
	private float wet;
	private float dry;
	
	public TapeDelay() {
		lineL = new DelayLine(P.DELAY_RATE);
		lineR = new DelayLine(P.DELAY_RATE_RIGHT);
	}
	
	@Override
	public void process(float[][] buffers, int startPos) {
		float[] bufferL = buffers[0];
		float[] bufferR = buffers[1];
		for (int i=0;i<P.CONTROL_BUFFER_SIZE;i++) {
			int pos = i+startPos;
			bufferL[pos] = bufferL[pos]*dry + lineL.sample(bufferL[pos])*wet;
			bufferR[pos] = bufferR[pos]*dry + lineR.sample(bufferR[pos])*wet;
		}
	}
	
	@Override
	public void controlTick() {
		wet = P.VAL[P.DELAY_WET];
		dry = 1-P.VAL[P.DELAY_WET];
		lineL.controlTick();
		lineR.controlTick();
	}

	
	private static class DelayLine implements IControlProcessor {
		
		float[] buffer = new float[LINE_LENGTH];
		float accumulator = 0;
		float targetValue = 0;
		float valOut = 0;
		float feedbackLevel = 0;
		int sampleCount = 1;
		int index = 0;

		float frequencyFactor;
		float phase = 0;
		float phaseIncrement = 0;
		boolean trigger = false;
		boolean lastTrigger = false;
		int pRate;
		
		StateVariableFilter filter = new StateVariableFilter(FilterType.LOWPASS, 440, 0);
		
		public DelayLine(int pRate) {
			frequencyFactor = (float)(2 * Math.PI / P.SAMPLE_RATE_HZ);
			this.pRate = pRate;
		}
		
		public float sample(float in) {
			trigger = (phase <= Math.PI);			
			if (trigger && !lastTrigger) {
				// take sample
				index = (index+1) & LINE_LENGTH_MASK;
				targetValue = buffer[(index+1) & LINE_LENGTH_MASK];      
		        buffer[index] = accumulator/sampleCount + buffer[index]*feedbackLevel;
		        accumulator = 0;
		        sampleCount = 0;
			}
			else {
				// accumulate
				sampleCount++;
				accumulator += in;
			}
	        phase += phaseIncrement;
	        while (phase >= PI2) {
	            phase -= PI2;
	        }
	        lastTrigger = trigger;
	        
	        valOut = (valOut+valOut+valOut+targetValue)*.25f;
	        return filter.processSample(valOut);
	        
		}

		@Override
		public void controlTick() {
			float freq = (FREQ_LOW+P.VAL[pRate]*FREQ_RANGE)*LFO.lfoAmount(P.VALXC[P.MOD_DELAY_TIME_AMOUNT])*(1+EnvAHD.GLOBAL.outValue*P.VALXC[P.MOD_AHD_DELAY_TIME_AMOUNT]);
			filter.setCutoff(freq/4);
			phaseIncrement = freq*frequencyFactor;
			feedbackLevel = P.VAL[P.DELAY_FEEDBACK];
		}
		
	}
	
	private static final float FREQ_LOW = 600;
	private static final float FREQ_HIGH = P.SAMPLE_RATE_HZ/2;
	private static final float FREQ_RANGE = FREQ_HIGH-FREQ_LOW;
	
	private static final int LINE_LENGTH = 4096;
	private static final int LINE_LENGTH_MASK = LINE_LENGTH-1;
	private static final float PI2 = (float)(Math.PI*2);
	
}