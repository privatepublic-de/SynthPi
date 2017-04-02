package de.privatepublic.pi.synth.modules.osc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.IPitchBendReceiver;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.util.FastCalc;

public class BlepOscillator extends OscillatorBase implements IPitchBendReceiver{
	
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(BlepOscillator.class);
	
	
	private static enum Mode {
	    SINE,
	    SAW,
	    SQUARE,
	    TRIANGLE
	};
	
	Mode mode = Mode.SAW;
    float mPhase = 0;
    float mPhaseIncrement;
    float mPhaseIncrementDiv;
    float lastOutput;
	

	public BlepOscillator(boolean primaryOrSecondary) {
		super(primaryOrSecondary);
		MidiHandler.registerReceiver(this);
	}
	
	@Override
	protected void setTargetFrequency(float frequency) {
		super.setTargetFrequency(frequency);
		mPhaseIncrement = (float) (effectiveFrequency * 2 * Math.PI / P.SAMPLE_RATE_HZ);
		mPhaseIncrementDiv = mPhaseIncrement/PI2;
	}
	

	@Override
	public float processSample1st(int sampleNo, float volume, boolean[] syncOnFrameBuffer, float[] am_buffer,
			EnvADSR modEnvelope) {
		
		if (effectiveFrequency!=targetFrequency) {
			if (effectiveFrequency<targetFrequency) {
				effectiveFrequency += glideStepSize;
				mPhaseIncrement = (float) (frequency * 2 * Math.PI / P.SAMPLE_RATE_HZ);
				mPhaseIncrementDiv = mPhaseIncrement/PI2;
			}
			else if (effectiveFrequency>targetFrequency) {
				effectiveFrequency -= glideStepSize;
				mPhaseIncrement = (float) (frequency * 2 * Math.PI / P.SAMPLE_RATE_HZ);
				mPhaseIncrementDiv = mPhaseIncrement/PI2;
			}
			if (Math.abs(effectiveFrequency-targetFrequency)<glideStepSize) {
				effectiveFrequency = targetFrequency;
				mPhaseIncrement = (float) (frequency * 2 * Math.PI / P.SAMPLE_RATE_HZ);
				mPhaseIncrementDiv = mPhaseIncrement/PI2;
			}
		}
		
		// TODO optimize waveform selection
		float modev = P.VAL[isSecond?P.OSC2_WAVE:P.OSC1_WAVE];
		if (modev<.25) {
			mode = Mode.SINE;
		}
		else if (modev<.5) {
			mode = Mode.TRIANGLE;
		}
		else if (modev<.75) {
			mode = Mode.SAW;
		}
		else {
			mode = Mode.SQUARE;
		}
		
		float t = mPhase / PI2;
		float outVal = 0;
		switch(mode) {
		case SINE:
	        outVal = FastCalc.sin(mPhase);
			break;
		case SAW:
			outVal = (float) ((2.0 * mPhase / PI2) - 1.0);
			outVal -= pblep(t);
			break;
		case SQUARE:
			float saw1 = (2.0f * mPhase / PI2) - 1.0f;
			saw1 -= pblep(t);
			float phaseShift = (float)(mPhase+PI2*P.VAL[P.FILTER2_OVERLOAD]);
			while (phaseShift >= PI2) {
				phaseShift -= PI2;
	        }
			float saw2 = (2.0f * phaseShift/PI2) - 1.0f;
			saw2 -= pblep(phaseShift/PI2);
			outVal = saw1-saw2;
			break;
		case TRIANGLE:
			if (mPhase <= Math.PI) {
	            outVal = 1.0f;
	        } else {
	            outVal = -1.0f;
	        }
			outVal += pblep(t);
			outVal -= pblep((t + 0.5f)%1.0f);
			if (mode==Mode.TRIANGLE) {
		        outVal = mPhaseIncrement * outVal + (1 - mPhaseIncrement) * lastOutput;
			}
			break;
		}
		mPhase += mPhaseIncrement;
        while (mPhase >= PI2) {
            mPhase -= PI2;
        }
        lastOutput = outVal;
		return outVal*volume;
	}

	@Override
	public float processSample2nd(int sampleNo, float volume, boolean[] syncOnFrameBuffer, float[] am_buffer,
			EnvADSR modEnvelope) {
		return processSample1st(sampleNo, volume, syncOnFrameBuffer, am_buffer, modEnvelope);
	}
	
	private float pblep(float t) {
	    // 0 <= t < 1
	    if (t < mPhaseIncrementDiv) {
	        t /= mPhaseIncrementDiv;
	        return t+t - t*t - 1.0f;
	    }
	    // -1 < t < 0
	    else if (t > 1.0 - mPhaseIncrementDiv) {
	        t = (t - 1.0f) / mPhaseIncrementDiv;
	        return t*t + t+t + 1.0f;
	    }
	    // 0 otherwise
	    else return 0;
	}

	@Override
	public void onPitchBend() {
		setTargetFrequency(frequency);		
	}

}
