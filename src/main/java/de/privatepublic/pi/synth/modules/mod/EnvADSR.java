package de.privatepublic.pi.synth.modules.mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;

public class EnvADSR extends Envelope {

	public static enum State { REST, ATTACK, DECAY, DECAY_LOOP, HOLD, RELEASE }
	public State state = State.REST;

	public EnvADSR(EnvelopeParamConfig conf) {
		this.conf = conf;
	}

	private final EnvelopeParamConfig conf;
	private float value = 0;
	private float sustainValue;
	private float decayCoeff;
	private float releaseCoeff;
	private float timeAttack;
	private float timeDecay;
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
				if (conf.loopMode()) {
					state = State.DECAY_LOOP;
				}
				else {
					state = State.DECAY;
				}
			}
			break;
		case DECAY:
			value += decayCoeff * value;
			if (value<sustainValue) {
				value = sustainValue;
				state = State.HOLD;
			}
			break;
		case DECAY_LOOP:
			value += decayCoeff * value;
			if (value<ZERO_THRESHOLD) {
				noteOn();
			}
			break;
		case RELEASE:
			value += releaseCoeff * value;
			if (value<ZERO_THRESHOLD) {
				value = ZERO_THRESHOLD;
				state = State.REST;
			}
			break;
		}
		outValue = (value<ZERO_THRESHOLD)?0:value;
		return outValue;
	}
	
	
	public void noteOn() {
		float attackOvershoot = 1.05f;
		timeAttack = threshold(MAX_TIME_MILLIS*conf.attack());
		timeDecay = threshold(MAX_TIME_MILLIS*conf.decay());
		float dur = (timeAttack*2)/P.MILLIS_PER_CONTROL_FRAME;//    P.MILLIS_PER_SAMPLE_FRAME;
		float rdur = 1.0f / dur;
		float rdur2 = rdur * rdur;

		slope = 4.0f * attackOvershoot * (rdur - rdur2);
		curve = -8.0f * attackOvershoot * rdur2;
		// value = ZERO_THRESHOLD;
		sustainValue = conf.loopMode()?0:conf.sustain();
		decayCoeff = initStep(1, sustainValue, timeDecay);
		state = State.ATTACK;
	}
	
	
	public void noteOff() {
		if (state==State.ATTACK || state==State.DECAY || state==State.DECAY_LOOP || state==State.HOLD) {
			releaseCoeff = initStep(value, ZERO_THRESHOLD, threshold(MAX_TIME_MILLIS*conf.release()));
			state = State.RELEASE;
		}
	}
	
	private static float initStep(float levelCurrent, float levelEnd, float releaseTime) {
	    return (float) ((Math.log(Math.max(levelEnd, ZERO_THRESHOLD)) - Math.log(Math.max(levelCurrent, ZERO_THRESHOLD))) / (releaseTime/1000 * P.CONTROL_RATE_HZ));
	}
	
	private static float threshold(float v) {
		return Math.max(v, MIN_TIME_MILLIS);
	}
	
//	public static void main(String[] args) {
//		P.setToDefaults();
//		P.setFromMIDI(P.FILTER1_ENV_A, 64);
//		P.setFromMIDI(P.FILTER1_ENV_D, 64);
//		P.setFromMIDI(P.FILTER1_ENV_S, 64);
//		P.setFromMIDI(P.FILTER1_ENV_R, 64);
//		P.setFromMIDI(P.FILTER1_ENV_VELOCITY_SENS, 1);
//		EnvADSR env = new EnvADSR(P.ENV_CONF_FILTER1);
//		float[] out = new float[(int) (P.SAMPLE_RATE_HZ*12)];
//		int noteoffi = (int) (out.length*.75);
//		for (int i=0;i<out.length;++i) {
//			if (i==0) {
//				env.noteOn(1);
//			}
//			if (i==noteoffi) {
//				env.noteOff();
//			}
//			out[i] = env.nextValue();
//		}
//		IOUtils.writeToFileBinary("./env.raw", out);
//	}
	
	
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(EnvADSR.class);
	
	public static  final class EnvelopeParamConfig {
		public final int indexA;
		public final int indexD;
		public final int indexS;
		public final int indexR;
		public final int indexVelSensitive;
		public final int indexLoop;
		
		public EnvelopeParamConfig(int paramIndexA, int paramIndexD, int paramIndexS, int paramIndexR, int paramIndexVelSensitive, int paramIndexLoop) {
			indexA = paramIndexA;
			indexD = paramIndexD;
			indexS = paramIndexS;
			indexR = paramIndexR;
			indexVelSensitive = paramIndexVelSensitive;
			indexLoop = paramIndexLoop;
		}
		
		public float attack() {
			return P.VALX[indexA];
		}
		public float decay() {
			return P.VALX[indexD];
		}
		public float sustain() {
			return P.VAL[indexS];
		}
		public float release() {
			return P.VALX[indexR];
		}
		public boolean velSens() {
			return P.IS[indexVelSensitive];
		}
		public boolean loopMode() {
			return P.IS[indexLoop];
		}
	}

	@Override
	public void controlTick() {
		nextValue();
	}
	
}
