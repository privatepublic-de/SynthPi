package de.privatepublic.pi.synth.modules.osc;

import java.util.SplittableRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.IPitchBendReceiver;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.AnalogSynth;
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
    float phase = 0;
    float phaseIncrement;
    float phaseIncrementDiv;
    float lastOutput;
    float syncPhase = 0;
    float drift = 0;
    float pulsewidth = .5f;
    float freq = 0;
    boolean ampmod = false;
    private SplittableRandom random = new SplittableRandom();
    private EnvADSR env1;
    private EnvADSR env2;
    private LFO lfo;
	

	public BlepOscillator(OscillatorBase.Mode mode, EnvADSR env1, EnvADSR env2, LFO lfo) {
		super(mode);
		MidiHandler.registerReceiver(this);
		if (mode==OscillatorBase.Mode.SUB) {
			wave = Wave.TRIANGLE;
		}
		this.env1 = env1;
		this.env2 = env2;
		this.lfo = lfo;
	}
	

	public void trigger(final float frequency, final float velocity) {
		this.frequency = frequency;
		if (P.IS[P.OSC_GLIDE_RATE]) {
			if (isSecond) {
				effectiveFrequency = P.osc2DetuneFactor*AnalogSynth.lastTriggeredFrequency;				
			}
			else {
				effectiveFrequency = AnalogSynth.lastTriggeredFrequency;
			}
			glideStepSize = Math.abs((AnalogSynth.lastTriggeredFrequency-frequency)/(P.SAMPLE_RATE_HZ/P.CONTROL_BUFFER_SIZE*P.VALX[P.OSC_GLIDE_RATE]));
		}
		else {
			glideStepSize = 0;
		}
		setTargetFrequency(frequency);
//		phase = 0;
	}
	
	@Override
	protected void setTargetFrequency(float frequency) {
		super.setTargetFrequency(frequency);
		phaseIncrement = effectiveFrequency * PI2 / P.SAMPLE_RATE_HZ;
		phaseIncrementDiv = phaseIncrement/PI2;
	}
	
	
	public float process(int sampleNo, float volume, boolean[] syncOnFrameBuffer, float[] am_buffer) {
		if (isBase) {
			syncOnFrameBuffer[sampleNo] = false;
		}
		else {
			if (isSecond && P.IS[P.OSC2_SYNC] && syncOnFrameBuffer[sampleNo]) {
//				syncPhase = phase;
				phase = 0;
			}			
		}
		
		float t = phase / PI2;
		float outVal = 0;
		switch(wave) {
		case SINE:
	        outVal = FastCalc.sin(phase);
	        if (isSecond && P.IS[P.OSC2_SYNC]) {
//	        	outVal -= pblep(syncPhase/PI2);
	        	outVal = phaseIncrement * outVal + (1 - phaseIncrement) * lastOutput;
//	        	syncPhase += phaseIncrement;
//	        	while (syncPhase >= PI2) {
//	        		syncPhase -= PI2;
//	        	}
	        }
			break;
		case SAW:
			outVal = (float) ((2.0 * t) - 1.0);
			outVal -= pblep(t);
			break;
		case SQUARE:
			float saw1 = (2.0f * t) - 1.0f;
			saw1 -= pblep(t);
			
			float phaseShift = (float)(phase+PI2*pulsewidth);
			while (phaseShift >= PI2) {
				phaseShift -= PI2;
	        }
			float t2 = phaseShift/PI2;
			float saw2 = (2.0f * t2) - 1.0f;
			saw2 -= pblep(t2);
			outVal = saw1-saw2;
			break;
		case TRIANGLE:
			if (phase <= Math.PI) {
	            outVal = 1.0f;
	        } else {
	            outVal = -1.0f;
	        }
			outVal += pblep(t);
			outVal -= pblep((t + 0.5f)%1.0f);
	        outVal = phaseIncrement * outVal + (1 - phaseIncrement) * lastOutput;
			break;
		}
		phase += phaseIncrement;
        while (phase >= PI2) {
            phase -= PI2;
            if (isBase) {
            	syncOnFrameBuffer[sampleNo] = true;
            }
        }
        lastOutput = outVal;
        if (isBase) am_buffer[sampleNo] = outVal;
        return ampmod ? am_buffer[sampleNo]*outVal*volume : outVal*volume;
	}

	
	private float pblep(float t) {
	    // 0 <= t < 1
	    if (t < phaseIncrementDiv) {
	        t /= phaseIncrementDiv;
	        return t+t - t*t - 1.0f;
	    }
	    // -1 < t < 0
	    else if (t > 1.0 - phaseIncrementDiv) {
	        t = (t - 1.0f) / phaseIncrementDiv;
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
		if (oscMode!=Mode.SUB) { // sub is only square/triangle
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
		else {
			wave = P.IS[P.OSC_SUB_SQUARE]?Wave.SQUARE:Wave.TRIANGLE;
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
		drift = (drift+((float)random.nextDouble()*.5f-.25f)*2)*.5f;
		
		freq = effectiveFrequency * P.PITCH_BEND_FACTOR
				* (lfo.lfoAmount(P.VALXC[P.MOD_PITCH_AMOUNT])
				+env2.outValue*P.VALXC[P.MOD_ENV2_PITCH_AMOUNT]
				+P.CHANNEL_PRESSURE*P.VALXC[P.MOD_PRESS_PITCH_AMOUNT]
				);
		
		switch(oscMode) {
		case PRIMARY:
			break;
		case SECONDARY:
			freq = freq 
					* (lfo.lfoAmount(P.VALXC[P.MOD_PITCH2_AMOUNT])
							+env1.outValue*P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT]
							+env2.outValue*P.VALXC[P.MOD_ENV2_PITCH2_AMOUNT]
							+P.CHANNEL_PRESSURE*P.VALXC[P.MOD_PRESS_PITCH2_AMOUNT]);
			break;
		case SUB:
			freq = freq	* (P.IS[P.OSC_SUB_LOW]?.25f:.5f);
		}
		
		phaseIncrement = (freq+drift) * PI2 / P.SAMPLE_RATE_HZ;
		phaseIncrementDiv = phaseIncrement/PI2;
		
		if (isBase) {
			pulsewidth = FastCalc.ensureRange(P.VAL[P.OSC1_PULSE_WIDTH]+lfo.lfoAmountAdd(P.VALXC[P.MOD_PW1_AMOUNT])+env1.outValue*P.VALC[P.MOD_ENV1_PW1_AMOUNT]+env2.outValue*P.VALC[P.MOD_ENV2_PW1_AMOUNT], 0, 1);
		}
		else if (isSecond) {
			pulsewidth = FastCalc.ensureRange(P.VAL[P.OSC2_PULSE_WIDTH]+lfo.lfoAmountAdd(P.VALXC[P.MOD_PW2_AMOUNT])+env1.outValue*P.VALC[P.MOD_ENV1_PW2_AMOUNT]+env2.outValue*P.VALC[P.MOD_ENV2_PW2_AMOUNT], 0, 1);
		}
		
	}


}
