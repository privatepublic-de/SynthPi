package de.privatepublic.pi.synth.modules.mod;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;


public class LFO {

	private static final Logger log = LoggerFactory.getLogger(LFO.class); 

	public final static int WAVE_COUNT = 6;
	public final static int WAVE_COUNT_CALC = WAVE_COUNT-1;
	public final static String[] WAVE_NAMES = new String[] {"SIN","TRG","RUP","RDN", "SQR", "SH"};
	public static int WAVE_LENGHT = (int)P.SAMPLE_RATE_HZ;
	public final static float LOW_FREQ = 0.03f;
	public final static float HI_FREQ = 30;
	public final static float FREQ_RANGE = HI_FREQ-LOW_FREQ;
	private final static float[][] TABLES = new float[WAVE_COUNT][];
	
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
			for (int i=0;i<WAVE_LENGHT;++i) {
				val = i<WAVE_LENGHT/2?0:1;
				TABLES[4][i] = val;
			}

			// sample & hold
			TABLES[5] = new float[WAVE_LENGHT];
			Random rand = new Random(1234567);
			float a = 0.075f;
			float b = 1-a;
			float z = 0;
			for (int i=0;i<WAVE_LENGHT;++i) {
				z = (2*rand.nextFloat()-1) * b + (z * a); // simple lowpass
				TABLES[5][i] = z;
			}

			log.debug("Created LFO waves with {} samples", (int)P.SAMPLE_RATE_HZ);
		}
	}
	
	private float indexOffset = 0;
	private float tableIndexIncrement = 0;
	private float[] currentWave = TABLES[0];
	private final int paraIndexLfoType;
	private final int paraIndexLfoRate;

	public static final LFO GLOBAL = new LFO();
	
	// 0 - 2
	// filter
	public static float lfoAmount(final int sampleIndex, final float depth) {
		return (1-GLOBAL.valueAt(sampleIndex)*P.MOD_AMOUNT_COMBINED*depth);
	}
	
	// 0 - 2 + env
	// oscillators pitch modulation
	public static float lfoAmount(final int sampleIndex, final float depth, final EnvADSR modEnv, final float modEnvDepth) {
		// caution! copy & paste
		return (1-GLOBAL.valueAt(sampleIndex)*P.MOD_AMOUNT_COMBINED*depth)+modEnv.outValue*modEnvDepth;
	}
	
	// -1 - 1 + env
	// oscillators wave form modulation
	public static float lfoAmountAdd(final int sampleIndex, final float depth, final EnvADSR modEnv, final float modEnvDepth) {
		// caution! copy & paste
		return (GLOBAL.valueAt(sampleIndex)*P.MOD_AMOUNT_COMBINED*depth)+modEnv.outValue*modEnvDepth;
	}
	
	// -1 - 1
	// voice volume modulation
	public static float lfoAmountAdd(final int sampleIndex, final float depth) {
		// caution! copy & paste
		return (GLOBAL.valueAt(sampleIndex)*P.MOD_AMOUNT_COMBINED*depth);
	}
	
	// 0 - 2 + env
	// oscillators pitch2 modulation
	public static float lfoAmountAsymm(final int sampleIndex, final float depth, final EnvADSR modEnv, final float modEnvDepth) {
		// caution! copy & paste
		return (((GLOBAL.valueAt(sampleIndex)+1)*P.MOD_AMOUNT_COMBINED*.5f*depth)+1)+modEnv.outValue*modEnvDepth;
	}
	
	public LFO() {
		init();
		paraIndexLfoRate = P.MOD_RATE;
		paraIndexLfoType = P.MOD_LFO_TYPE;
		currentWave = TABLES[(int)(P.VAL[paraIndexLfoType]*WAVE_COUNT_CALC)];
	}
	
	public LFO(int paraIndexLfoRate, int paraIndexLfoType) {
		init();
		this.paraIndexLfoRate = paraIndexLfoRate;
		this.paraIndexLfoType = paraIndexLfoType;
		currentWave = TABLES[(int)(P.VAL[paraIndexLfoType]*WAVE_COUNT_CALC)];
	}
	
	public void nextBufferSlice(final int nframes) {
		currentWave = TABLES[(int)(P.VAL[paraIndexLfoType]*WAVE_COUNT_CALC)];
		tableIndexIncrement = (LOW_FREQ+(P.VALX[paraIndexLfoRate]*FREQ_RANGE));
		if (currentWave==TABLES[5]) {
			tableIndexIncrement /= P.SAMPLE_RATE_HZ;
		}
		indexOffset = indexOffset+nframes*tableIndexIncrement;
		if (indexOffset>WAVE_LENGHT) {
			indexOffset -= WAVE_LENGHT;
		}
	}
	
//	private float idx;
	
	public float valueAt(final int index) {
		int idx = (int)(indexOffset + tableIndexIncrement*index);
		if (idx>=WAVE_LENGHT) {
			idx -= WAVE_LENGHT;
		}
		return currentWave[idx];
	}
	
	
	public void reset() {
		indexOffset = 0;
	}
	
}
