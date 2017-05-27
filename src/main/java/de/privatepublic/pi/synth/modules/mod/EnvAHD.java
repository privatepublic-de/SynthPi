package de.privatepublic.pi.synth.modules.mod;

import de.privatepublic.pi.synth.P;

public class EnvAHD extends Envelope {

//	public static EnvAHD GLOBAL = new EnvAHD(P.MOD_AHD_ATTACK, P.MOD_AHD_DECAY);
	
	public static enum State { REST, ATTACK, DECAY, HOLD }
	public State state = State.REST;
	
	public EnvAHD(int pAttack, int pDecay) {
		this.pAttack = pAttack;
		this.pDecay = pDecay;
	}

	private int pAttack;
	private int pDecay;
	
	
	@Override
	public void controlTick() {
		nextValue();
	}
	
	private float value = 0;
	private float decayCoeff;
	private float timeAttack;
	private float slope;
	private float curve;
	
	@SuppressWarnings("incomplete-switch")
	private float nextValue() {
		switch (state) {
		case ATTACK: 
			value += slope;
		    slope += curve;
			if (value>1) {
				value = 1;
				state = State.HOLD;
			}
			break;
		case DECAY:
			value += decayCoeff * value;
			if (value<ZERO_THRESHOLD) {
				value = ZERO_THRESHOLD;
				state = State.REST;
			}
			break;
		}
		outValue = (value<ZERO_THRESHOLD)?0:value;
		return outValue;
	}
	
	
	public void noteOn(float velocity) {
		float attackOvershoot = 1.05f;
		timeAttack = threshold(MAX_TIME_MILLIS*P.VALX[pAttack]);
		float dur = (timeAttack*2)/P.MILLIS_PER_CONTROL_FRAME;//    P.MILLIS_PER_SAMPLE_FRAME;
		float rdur = 1.0f / dur;
		float rdur2 = rdur * rdur;

		slope = 4.0f * attackOvershoot * (rdur - rdur2);
		curve = -8.0f * attackOvershoot * rdur2;
		// value = ZERO_THRESHOLD;
		state = State.ATTACK;
	}
	
	
	public void noteOff(float velocity) {
		if (state==State.ATTACK || state==State.HOLD) {
			decayCoeff = initStep(value, ZERO_THRESHOLD, threshold(MAX_TIME_MILLIS*P.VALX[pDecay]));
			state = State.DECAY;
		}
	}
	
	private static float initStep(float levelCurrent, float levelEnd, float releaseTime) {
	    return (float) ((Math.log(Math.max(levelEnd, ZERO_THRESHOLD)) - Math.log(Math.max(levelCurrent, ZERO_THRESHOLD))) / (releaseTime/1000 * P.CONTROL_RATE_HZ));
	}
	
	private static float threshold(float v) {
		return Math.max(v, MIN_TIME_MILLIS);
	}

}
