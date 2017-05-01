package de.privatepublic.pi.synth.modules.mod;

import java.util.SplittableRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.modules.IControlProcessor;
import de.privatepublic.pi.synth.util.FastCalc;


public class LFO implements IControlProcessor {

	private static final Logger log = LoggerFactory.getLogger(LFO.class); 

	public final static int WAVE_COUNT = 6;
	public final static int WAVE_COUNT_CALC = WAVE_COUNT-1;
	public final static String[] WAVE_NAMES = new String[] {"SIN","TRG","RUP","RDN", "SQR", "SH"};
	public static int WAVE_LENGHT = (int)P.SAMPLE_RATE_HZ;
	public final static float LOW_FREQ = 0.03f;
	public final static float HI_FREQ = 100;
	public final static float FREQ_RANGE = HI_FREQ-LOW_FREQ;
	private final static float[][] TABLES = new float[WAVE_COUNT][];
	private final SplittableRandom random = new SplittableRandom();
	
	public static void init() {
		if (TABLES[0]==null) {
			WAVE_LENGHT = (int)P.SAMPLE_RATE_HZ;
			float x, val, inc, val2;
			// sine
			TABLES[0] = new float[WAVE_LENGHT];
			float increment = (1 / P.SAMPLE_RATE_HZ);
			for (int i=0;i<WAVE_LENGHT;++i) {
				x = i/(float)WAVE_LENGHT;
				TABLES[0][i] = (float) Math.cos((2 * Math.PI)*x);
				x += increment;
			}

			// triangle
			TABLES[1] = new float[WAVE_LENGHT];
			for (int i=0;i<WAVE_LENGHT;++i) {
				x = i/(float)WAVE_LENGHT;
				val = (float) (2.0 * (x - Math.floor(x + 0.5)));
				if (val>= 0.0) 
					val = (float) (1.0 - (2.0 * val));
				else 
					val = (float) (1.0 + (2.0 * val));
				TABLES[1][i] = val;
			}

			// ramp up/down
			TABLES[2] = new float[WAVE_LENGHT];
			TABLES[3] = new float[WAVE_LENGHT];
			inc = 2*1f/WAVE_LENGHT;
			val = -1;
			val2 = 1;
			for (int i=0;i<WAVE_LENGHT;++i) {
				TABLES[2][i] = val;
				TABLES[3][i] = val2;
				val += inc;
				val2 -= inc;
			}

			// square
			TABLES[4] = new float[WAVE_LENGHT];
			TABLES[5] = new float[WAVE_LENGHT];
			for (int i=0;i<WAVE_LENGHT;++i) {
				val = i<WAVE_LENGHT/2?0:1;
				TABLES[4][i] = val;
				TABLES[5][i] = val;
			}

			log.debug("Created LFO waves with {} samples", (int)P.SAMPLE_RATE_HZ);
		}
	}
	
	private float indexOffset = 0;
	private float[] currentWave = TABLES[0];
	private final int paraIndexLfoType;
	private final int paraIndexLfoRate;
	private int index = 0;
	private EnvADSR env;

	// 0 - 2
	// filter
	public float lfoAmount(final float depth) {
		return (1-this.value()*P.MOD_AMOUNT_COMBINED*depth);
	}
	
	// 0 - 2 + env
	// oscillators pitch modulation
	public float lfoAmount(final float depth, final EnvADSR modEnv, final float modEnvDepth) {
		return (1-this.value()*P.MOD_AMOUNT_COMBINED*depth)+modEnv.outValue*modEnvDepth;
	}
	
	
	// -1 - 1
	// voice volume modulation
	public float lfoAmountAdd(final float depth) {
		// caution! copy & paste
		return (this.value()*P.MOD_AMOUNT_COMBINED*depth);
	}
	
	// 0 - 2 + env
	// oscillators pitch2 modulation
	public float lfoAmountAsymm(final float depth, final EnvADSR modEnv, final float modEnvDepth) {
		return (((this.value()+1)*P.MOD_AMOUNT_COMBINED*.5f*depth)+1)+modEnv.outValue*modEnvDepth;
	}
	
	public LFO(EnvADSR env) {
		init();
		this.env = env;
		paraIndexLfoRate = P.MOD_RATE;
		paraIndexLfoType = P.MOD_LFO_TYPE;
		currentWave = TABLES[(int)(P.VAL[paraIndexLfoType]*WAVE_COUNT_CALC)];
	}
	
	public LFO(int paraIndexLfoRate, int paraIndexLfoType) {
		init();
		this.paraIndexLfoRate = paraIndexLfoRate;
		this.paraIndexLfoType = paraIndexLfoType;
		currentWave = TABLES[(int)(P.VAL[paraIndexLfoType]*WAVE_COUNT_CALC)];
		delayValue = 1;
		delayIncrementValue = 1;
	}
	
	
	private float randvalue;
	private float lastRandTrigger;
	
	public float value() {
		if (currentWave==TABLES[5]) {
			if (currentWave[index]>lastRandTrigger) {
				randvalue = (float)random.nextDouble()*2-1; 
			}
			lastRandTrigger = currentWave[index]; 
			return randvalue*delayValue*delayValue;
		}
		else {
			return currentWave[index]*delayValue*delayValue;
		}
	}
	
	public void resetPhase() {
		indexOffset = 0;
	}
	
	private float slope;
	private float curve;
	private float delayValue;
	private float delayIncrementValue;
	
	public void resetDelay() {
		float attackOvershoot = 1.05f;
		float timeAttack =  Math.max(Envelope.MAX_TIME_MILLIS*P.VALX[P.MOD_LFO_DELAY], Envelope.MIN_TIME_MILLIS);
		float dur = (timeAttack*2)/P.MILLIS_PER_CONTROL_FRAME;//    P.MILLIS_PER_SAMPLE_FRAME;
		float rdur = 1.0f / dur;
		float rdur2 = rdur * rdur;
		slope = 4.0f * attackOvershoot * (rdur - rdur2);
		curve = -8.0f * attackOvershoot * rdur2;
		delayIncrementValue = 0;
	}

	@Override
	public void controlTick() {
		currentWave = TABLES[(int)(P.VAL[paraIndexLfoType]*WAVE_COUNT_CALC)];
		float frequency;
		if (env!=null) {
			frequency = 
					LOW_FREQ+
					(P.VALX[paraIndexLfoRate]+P.VALXC[P.MOD_ENV2_LFORATE_AMOUNT]*env.outValue)*FREQ_RANGE;
		}
		else {
			frequency = (LOW_FREQ+((P.VALX[paraIndexLfoRate])*FREQ_RANGE));
		}
		if (currentWave==TABLES[5]) {
			frequency *= 2; 
		}
		indexOffset = indexOffset+P.CONTROL_BUFFER_SIZE*FastCalc.ensureRange(frequency, LOW_FREQ, HI_FREQ);
		if (indexOffset>=WAVE_LENGHT) {
			indexOffset -= WAVE_LENGHT;
		}
		index = (int)indexOffset;
		if (delayIncrementValue<1) {
			delayIncrementValue += slope;
		    slope += curve;
			if (delayIncrementValue>1) {
				delayIncrementValue = 1;
			}
			delayValue = delayIncrementValue*delayIncrementValue*delayIncrementValue*delayIncrementValue; 
		}
	}
	
}
