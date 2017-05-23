package de.privatepublic.pi.synth.modules.fx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.P.FilterType;
import de.privatepublic.pi.synth.modules.IControlProcessor;
import de.privatepublic.pi.synth.modules.mod.LFO;

public class DigitalDelay implements IProcessorMono2Stereo, IControlProcessor {

	private DelayLine lineL;
	private DelayLine lineR;
	private float wet;
	private float dry;
	private LFO lfo;
	
	public DigitalDelay(LFO lfo) {
		lineL = new DelayLine(P.DELAY_RATE, 1);
		lineR = new DelayLine(P.DELAY_RATE_RIGHT, -1);
		this.lfo = lfo;
	}
	
	@Override
	public void process(float[] inBuffer, float[][] outBuffers, int startPos) {
		float[] bufferL = outBuffers[0];
		float[] bufferR = outBuffers[1];
		for (int i=0;i<P.CONTROL_BUFFER_SIZE;i++) {
			int pos = i+startPos;
			float inval = inBuffer[pos];
			bufferL[pos] = inval*dry + lineL.sample(inval*wet);// (lineL.sample(inval*wet)+lineL.sample(inval*wet)*.5f);
			bufferR[pos] = inval*dry + lineR.sample(inval*wet);// (lineR.sample(inval*wet)+lineR.sample(inval*wet)*.5f);
		}
		
	}
	
	@Override
	public void controlTick() {
		wet = P.VALX[P.DELAY_WET];
		dry = 1;//1-wet;
		lineL.controlTick();
		lineR.controlTick();
	}

	
	private class DelayLine implements IControlProcessor {
		
		float[] buffer = new float[LINE_LENGTH];
		float accumulator = 0;
		float targetValue = 0;
		float valOut = 0;
		float valIncrementFactor = 0;
		float valStep = 0;
		float feedbackLevel = 0;
		int index = 0;

		
		float phase = 0;
		float phaseIncrement = 0;
		float modSign = 1;
		boolean trigger = false;
		boolean lastTrigger = false;
		int pRate;
		
		int writePos = 0;
		float readOffset = LINE_LENGTH/4;
		float targetReadOffset = readOffset;
		float stepSize = 0;
		float xfadeTarget = 1;
		float xfadeStep = 1;
		float lastOut = 0;
		int readPos = (int)(writePos + readOffset);
		
		StateVariableFilter filter = new StateVariableFilter(FilterType.LOWPASS, 2000, 0);
		StateVariableFilter filter2 = new StateVariableFilter(FilterType.LOWPASS, 440, 0);
		
		public DelayLine(int pRate, int modSign) {
			this.pRate = pRate;
			this.modSign = modSign;
		}
		
		public float sample(float in) {
			buffer[writePos] = in + lastOut*feedbackLevel;
			if (xfadeTarget<1) {
				xfadeTarget += xfadeStep;
			}
			else {
				readOffset = targetReadOffset;
			}
			readPos = (int)(writePos+readOffset) & LINE_LENGTH_MASK;
			lastOut = buffer[readPos]*(1-xfadeTarget*xfadeTarget*xfadeTarget*xfadeTarget) + buffer[(int)(writePos+targetReadOffset) & LINE_LENGTH_MASK]*xfadeTarget*xfadeTarget*xfadeTarget*xfadeTarget;
//			lastOut = filter.processSample(lastOut);
			writePos = (writePos+1) & LINE_LENGTH_MASK;
			return lastOut;
			
//			
//			accumulator = filter.processSample(in);
//			trigger = (phase <= Math.PI);			
//			if (trigger && !lastTrigger) {
//				// take sample
//				index = (index+1) & LINE_LENGTH_MASK;
//				targetValue = buffer[(index+1) & LINE_LENGTH_MASK];
//				valStep = (targetValue-valOut)*valIncrementFactor;
//		        buffer[index] = accumulator + buffer[index]*feedbackLevel;
//			}
//	        phase += phaseIncrement;
//	        while (phase >= PI2) {
//	            phase -= PI2;
//	        }
//	        lastTrigger = trigger;
//	        valOut += valStep;
//	        return filter2.processSample(valOut);
		}

		@Override
		public void controlTick() {
			feedbackLevel = P.VAL[P.DELAY_FEEDBACK];
//			float freq = (FREQ_LOW+P.VALX[pRate]*FREQ_RANGE)*lfo.lfoAmount(modSign*P.VALXC[P.MOD_DELAY_TIME_AMOUNT]);
			if (xfadeTarget>=1) {
				float newReadOffset = Math.min((1 + (P.VAL[pRate]*LINE_LENGTH_MASK)*lfo.lfoAmount(modSign*P.VALXC[P.MOD_DELAY_TIME_AMOUNT])), LINE_LENGTH_MASK);
				float distance = Math.abs(newReadOffset-readOffset);
				if (distance>0) {
					xfadeStep = 1f/((P.CONTROL_BUFFER_SIZE-1)*3);
//					log.debug("step {}, {}", xfadeStep, stepSize);
					xfadeTarget = 0;
					targetReadOffset = newReadOffset;
				}
			}
//			log.debug("targetPos {}", targetReadOffset);
//			filter.setCutoff(freq);
//			filter2.setCutoff(freq);
//			phaseIncrement = freq*INC_FACTOR;
//			valIncrementFactor = phaseIncrement/INC_ORIGINAL*.25f;
		}
		
	}
	
	private static final float FREQ_LOW = P.SAMPLE_RATE_HZ/32;
	private static final float FREQ_HIGH = P.SAMPLE_RATE_HZ/2;
	private static final float FREQ_RANGE = FREQ_HIGH-FREQ_LOW;
	private static final float INC_FACTOR = (float)(2 * Math.PI / P.SAMPLE_RATE_HZ);
	private static final float INC_ORIGINAL = P.SAMPLE_RATE_HZ*INC_FACTOR;
	
	private static final int LINE_LENGTH = 65536*2;
	private static final int LINE_LENGTH_MASK = LINE_LENGTH-1;
	private static final float PI2 = (float)(Math.PI*2);

	
	private static final Logger log = LoggerFactory.getLogger(DigitalDelay.class);
}
