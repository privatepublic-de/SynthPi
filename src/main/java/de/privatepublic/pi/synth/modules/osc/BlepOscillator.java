package de.privatepublic.pi.synth.modules.osc;

import java.util.SplittableRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.IPitchBendReceiver;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.IControlProcessor;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.util.FastCalc;

public class BlepOscillator extends OscillatorBase implements IControlProcessor, IPitchBendReceiver{
	
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(BlepOscillator.class);
	
	
	private static enum Wave {
	    SINE,
	    SAW,
	    SQUARE,
	    TRIANGLE
	};
	
	Wave wave = Wave.SAW;
    float mPhase = 0;
    float mPhaseIncrement;
    float mPhaseIncrementDiv;
    float lastOutput;
    float syncPhase = 0;
    float drift = 0;
    boolean ampmod = false;
    private SplittableRandom random = new SplittableRandom();
	

	public BlepOscillator(IOscillator.Mode mode) {
		super(mode);
		MidiHandler.registerReceiver(this);
		if (mode==IOscillator.Mode.SUB) {
			wave = Wave.SQUARE;
		}
	}
	
	@Override
	protected void setTargetFrequency(float frequency) {
		super.setTargetFrequency(frequency);
		mPhaseIncrement = effectiveFrequency * PI2 / P.SAMPLE_RATE_HZ;
		mPhaseIncrementDiv = mPhaseIncrement/PI2;
	}
	

	@Override
	public float process(int sampleNo, float volume, boolean[] syncOnFrameBuffer, float[] am_buffer,
			EnvADSR modEnvelope) {
		final float freq;
		switch(mode) {
		case PRIMARY:
			freq = effectiveFrequency*LFO.lfoAmount(P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR;
			break;
		case SECONDARY:
			freq = 
				effectiveFrequency*LFO.lfoAmount(P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR
				* LFO.lfoAmountAsymm(P.VALXC[P.MOD_PITCH2_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT]);
			break;
		case SUB:
		default:
			freq = effectiveFrequency*LFO.lfoAmount(P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR / 2;
		}
		
		mPhaseIncrement = (freq+drift) * PI2 / P.SAMPLE_RATE_HZ;
		mPhaseIncrementDiv = mPhaseIncrement/PI2;
		
		if (isBase) {
			syncOnFrameBuffer[sampleNo] = false;
		}
		else {
			if (isSecond && P.IS[P.OSC2_SYNC] && syncOnFrameBuffer[sampleNo]) {
				mPhase = 0;
			}			
		}
		
		float t = mPhase / PI2;
		float outVal = 0;
		switch(wave) {
		case SINE:
	        outVal = FastCalc.sin(mPhase);
	        if (isSecond && P.IS[P.OSC2_SYNC]) {
	        	outVal -= pblep((syncPhase/PI2)%PI2);
	        	outVal = mPhaseIncrement * outVal + (1 - mPhaseIncrement) * lastOutput;
	        }
			break;
		case SAW:
			outVal = (float) ((2.0 * t) - 1.0);
			outVal -= pblep(t);
			break;
		case SQUARE:
			float saw1 = (2.0f * t) - 1.0f;
			saw1 -= pblep(t);
			float pulsewidth = .5f;
			
			if (isBase) {
				pulsewidth = P.VAL[P.OSC1_PULSE_WIDTH]+LFO.lfoAmountAdd(P.VALXC[P.MOD_WAVE1_AMOUNT]);
			}
			else {
				pulsewidth = P.VAL[P.OSC2_PULSE_WIDTH]+LFO.lfoAmountAdd(P.VALXC[P.MOD_WAVE2_AMOUNT]);;
			}
			
			float phaseShift = (float)(mPhase+PI2*pulsewidth);
			while (phaseShift >= PI2) {
				phaseShift -= PI2;
	        }
			float t2 = phaseShift/PI2;
			float saw2 = (2.0f * t2) - 1.0f;
			saw2 -= pblep(t2);
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
	        outVal = mPhaseIncrement * outVal + (1 - mPhaseIncrement) * lastOutput;
			break;
		}
		mPhase += mPhaseIncrement;
        while (mPhase >= PI2) {
            mPhase -= PI2;
            if (isBase) {
            	syncOnFrameBuffer[sampleNo] = true;
            	syncPhase = mPhase;
            }
        }
        lastOutput = outVal;
        if (isBase) am_buffer[sampleNo] = outVal;
        return ampmod ? am_buffer[sampleNo]*outVal*volume : outVal*volume;
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

	@Override
	public void controlTick() {
		if (mode!=Mode.SUB) {
			float modev = P.VAL[isSecond?P.OSC2_WAVE:P.OSC1_WAVE];
			if (modev<.25) {
				wave = Wave.SINE;
			}
			else if (modev<.5) {
				wave = Wave.TRIANGLE;
			}
			else if (modev<.75) {
				wave = Wave.SAW;
			}
			else {
				wave = Wave.SQUARE;
			}
		}
		ampmod = isSecond && P.IS[P.OSC2_AM];
		if (effectiveFrequency!=targetFrequency) {
			if (effectiveFrequency<targetFrequency) {
				effectiveFrequency += glideStepSize;
			}
			else if (effectiveFrequency>targetFrequency) {
				effectiveFrequency -= glideStepSize;
			}
			if (Math.abs(effectiveFrequency-targetFrequency)<glideStepSize) {
				effectiveFrequency = targetFrequency;
			}
		}
		drift = ((float)random.nextDouble()*.5f-.25f)*2;
	}


}
