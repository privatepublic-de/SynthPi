package de.privatepublic.pi.synth.modules.fx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.PresetHandler;
import de.privatepublic.pi.synth.modules.IControlProcessor;
import de.privatepublic.pi.synth.modules.mod.LFO;

public class DigitalDelay extends DelayBase {

	private DelayLine lineL;
	private DelayLine lineR;
	private float wet;
	private LFO lfo;
	
	public DigitalDelay(LFO lfo) {
		lineL = new DelayLine(P.DELAY_RATE, 1);
		lineR = new DelayLine(P.DELAY_RATE_RIGHT, -1);
		this.lfo = lfo;
		PresetHandler.registerReceiver(this);
	}
	
	@Override
	public void process(float[] inBuffer, float[][] outBuffers, int startPos) {
		float[] bufferL = outBuffers[0];
		float[] bufferR = outBuffers[1];
		for (int i=0;i<P.CONTROL_BUFFER_SIZE;i++) {
			int pos = i+startPos;
			float inval = inBuffer[pos];
			bufferL[pos] = inval + lineL.sample(inval*wet);
			bufferR[pos] = inval + lineR.sample(inval*wet);
		}
	}
	
	@Override
	public void controlTick() {
		wet = P.VALX[P.DELAY_WET];
		lineL.controlTick();
		lineR.controlTick();
	}

	
	private class DelayLine implements IControlProcessor {
		
		float[] buffer = new float[LINE_LENGTH];
		float feedbackLevel = 0;
		float modSign = 1;
		int pRate;
		
		int writePos = 0;
		float readOffset = LINE_LENGTH/4;
		float targetReadOffset = readOffset;
		float xfadeTarget = 1;
		float xfadeStep = 1;
		float lastOut = 0;
		int readPos = (int)(writePos + readOffset);
		
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
			writePos = (writePos+1) & LINE_LENGTH_MASK;
			return lastOut;
		}

		@Override
		public void controlTick() {
			feedbackLevel = P.VAL[P.DELAY_FEEDBACK];
			if (xfadeTarget>=1) {
				float newReadOffset = Math.min((1 + (P.VAL[pRate]*LINE_LENGTH_MASK)*lfo.lfoAmount(modSign*P.VALXC[P.MOD_DELAY_TIME_AMOUNT])), LINE_LENGTH_MASK);
				float distance = Math.abs(newReadOffset-readOffset);
				if (distance>0) {
					xfadeStep = 1f/((P.CONTROL_BUFFER_SIZE-1)*3);
					xfadeTarget = 0;
					targetReadOffset = newReadOffset;
				}
			}
		}
		
	}
	
	private static final int LINE_LENGTH = 65536*2;
	private static final int LINE_LENGTH_MASK = LINE_LENGTH-1;
	
	private static final Logger log = LoggerFactory.getLogger(DigitalDelay.class);

	@Override
	public void initPatch() {
		lineL.buffer = new float[LINE_LENGTH];
		lineR.buffer = new float[LINE_LENGTH];
	}
}
