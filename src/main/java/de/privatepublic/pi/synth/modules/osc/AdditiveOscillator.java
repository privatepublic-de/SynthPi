package de.privatepublic.pi.synth.modules.osc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.IPitchBendReceiver;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.util.FastCalc;

public class AdditiveOscillator extends OscillatorBase implements IPitchBendReceiver {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(AdditiveOscillator.class);
	
	private static final float[] SINE_TABLE = new float[(int)P.SAMPLE_RATE_HZ+1];
	static {
		for (int i=0;i<SINE_TABLE.length;i++) {
			SINE_TABLE[i] = (float) Math.sin(Math.PI*2*(i/(float)SINE_TABLE.length));
		}
	}
	/*

 No.  	 Harmonic		Interval  	 	Footage 
 1  	 Sub-Fund		Sub-Octave		16' 
 2  	 Sub-Third		Sub-Third		5 1/3' 
 3  	 Fundamental	Unison			8' 
 4  	 2nd Harmonic	Octave			4' 
 5  	 3rd Harmonic	Twelfth			2 2/3' 
 6  	 4th Harmonic	Fifteenth		2' 
 7  	 5th Harmonic	Seventeenth		1 3/5' 
 8  	 6th Harmonic	Nineteenth		1 1/3' 
 9  	 8th Harmonic	Twenty-Second	1' 
	 
	 semi
	 sub-oct	-12
	 sub-third	+7 
	 twelfth	+19
	 seventnth	+28
	 ninetnth	+31
	 */
	
	private static final int[] HARMONIC_FACTORS_SEMIS = new int[] {
			-12, // sub octave 
			7, 19, 28, 31, // drawbar harmonics
			0, // fundamental
			0, 0, 0, 0, 0, 0, 0, 0, 0 //,
			// 0, 0, 0, 0, 0, 0
		};
	
	private static final float[][] VOLUME_MAP = new float[][]{
		// sub, sub3rd, 12th, 17th, 19th, fund, ...octaves...
		new float[] { 1, 1, 0, 0, 0,  1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
		new float[] { 1,.5f, 0,.5f,.8f,  0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
		new float[] { 1, 0, 0, 0, 0, .3f, 0,.6f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
		new float[] {.6f,.6f,.5f,.5f, 1,  1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
		
		// saw
		new float[] { 0, 0, 0, 0, 0,  1, 1f/2, 1f/3, 1f/4, 1f/5, 1f/6, 1f/7, 1f/8, 1f/9, 1f/10, 1f/11, 1f/12, 1f/13, 1f/14, 1f/15, 1f/16, 1f/17 },
		new float[] { 1, 0, 0, 0, 0,  1, 1f/2, 1f/3, 1f/4, 1f/5, 1f/6, 1f/7, 1f/8, 1f/9, 1f/10, 1f/11, 1f/12, 1f/13, 1f/14, 1f/15, 1f/16, 1f/17 },
		// square
		new float[] { 0, 0, 0, 0, 0,  1, 0, 1f/3, 0, 1f/5, 0, 1f/7, 0, 1f/9, 0, 1f/11, 0, 1f/13, 0, 1f/15, 0, 1f/17 },
		new float[] { 1, 0, 0, 0, 0,  1, 0, 1f/3, 0, 1f/5, 0, 1f/7, 0, 1f/9, 0, 1f/11, 0, 1f/13, 0, 1f/15, 0, 1f/17 },
		// last is doubled for array access
		new float[] { 1, 0, 0, 0, 0,  1, 0, 1f/3, 0, 1f/5, 0, 1f/7, 0, 1f/9, 0, 1f/11, 0, 1f/13, 0, 1f/15, 0, 1f/17 }
	};
	private static final int HARMONICS_COUNT = HARMONIC_FACTORS_SEMIS.length;
	private static final int VOLUMES_COUNT = VOLUME_MAP.length-2;
	
	private Sine[] sines = new Sine[HARMONICS_COUNT];
	
	public AdditiveOscillator(boolean primaryOrSecondary) {
		super(primaryOrSecondary);
		MidiHandler.registerReceiver(this);
		for (int i=0;i<HARMONICS_COUNT;i++) {
			int semis = HARMONIC_FACTORS_SEMIS[i];
			float factor;
			if (semis!=0) {
				factor = (float) Math.pow(Math.pow(2d, HARMONIC_FACTORS_SEMIS[i]), 1/12d);
			}
			else {
				factor = i-4;
			}
			sines[i] = new Sine(i, factor);
//			log.debug("{} -> {}", i, factor);
		}
	}
	
	private float frequencyStepSize = 0;
	private float outValue;
	private boolean ampmod, sync; 
	private float ampModAmount, ampamount, modwave1amount, modwave2amount, modenv1waveamount;
	
	@Override
	public float processSample1st(int sampleNo, float volume, boolean[] syncOnFrameBuffer, float[] am_buffer, EnvADSR modEnvelope) {
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
		frequencyStepSize = effectiveFrequency*LFO.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR;
		outValue = 0;
		modwave1amount = P.VALXC[P.MOD_WAVE1_AMOUNT];
		modenv1waveamount = P.VALXC[P.MOD_ENV1_WAVE_AMOUNT];
		for (int i=0;i<HARMONICS_COUNT;i++) {
			outValue += sines[i].value(P.OSC1_WAVE, LFO.lfoAmountAdd(sampleNo, modwave1amount, modEnvelope, modenv1waveamount), false);
		}
		am_buffer[sampleNo] = outValue;
		syncOnFrameBuffer[sampleNo] = sines[5].cycleStart;
		return outValue * volume;
	}

	@Override
	public float processSample2nd(int sampleNo, float volume, boolean[] syncOnFrameBuffer, float[] am_buffer, EnvADSR modEnvelope) {
		// second osc
		if (ampmod && !P.IS[P.OSC2_AM]) {
			effectiveFrequency = targetFrequency;
		}
		ampModAmount = P.VALC[P.MOD_ENV1_AM_AMOUNT];
		ampamount = FastCalc.ensureRange(P.VAL[P.OSC2_AM]+modEnvelope.outValue*ampModAmount, 0, 1);
		ampmod = ampamount>0 || ampModAmount!=0;
		if (ampmod) {
			effectiveFrequency = targetFrequency*((ampamount*4));			
		}
		else {
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
		}
		frequencyStepSize = 
				effectiveFrequency*LFO.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR
				* LFO.lfoAmountAsymm(sampleNo, P.VALXC[P.MOD_PITCH2_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT]);
		outValue = 0;
		sync = P.IS[P.OSC2_SYNC] && syncOnFrameBuffer[sampleNo];
		modwave2amount = P.VALXC[P.MOD_WAVE2_AMOUNT];
		modenv1waveamount = P.VALXC[P.MOD_ENV1_WAVE_AMOUNT];
		for (int i=0;i<HARMONICS_COUNT;i++) {
			outValue += sines[i].value(P.OSC2_WAVE, LFO.lfoAmountAdd(sampleNo, modwave2amount, modEnvelope, modenv1waveamount), sync);
		}
		if (ampmod) {
			return (am_buffer[sampleNo]*outValue*volume);
		}
		else {
			return outValue*volume;
		}
	}

	@Override
	public void onPitchBend() {
		setTargetFrequency(frequency);
	}
	
	
	private class Sine {
		private final float factor;
		private float lphase = 0;
		private int harmIndex = 0;
		private boolean cycleStart = true;
		
		public Sine(int harmIndex, float factor) {
			this.factor = factor;
			this.harmIndex = harmIndex;
		}
		
		private float volIndex, volFract, vol, indexFrac, outValue;
		private int volBase, indexBase;
		
		public final float value(final int waveformparaindex, final float waveformmod, final boolean sync) {
			volIndex = FastCalc.ensureRange((P.VAL[waveformparaindex]+waveformmod), 0f, 1f)*VOLUMES_COUNT;
			volBase = (int)volIndex;
			volFract = volIndex-volBase;
			vol = VOLUME_MAP[volBase][harmIndex];
			vol += ((VOLUME_MAP[volBase+1][harmIndex]-vol)*volFract);
			if (sync) {
				lphase = 0;
			}
			indexBase = (int)lphase;
			indexFrac = lphase-indexBase;
			outValue = SINE_TABLE[indexBase];
			outValue += ((SINE_TABLE[indexBase+1]-outValue)*indexFrac);
			lphase = lphase+frequencyStepSize*factor;
			cycleStart = false;
			while (lphase>=P.SAMPLE_RATE_HZ) {
				lphase -= P.SAMPLE_RATE_HZ;
				cycleStart = true;
			}
			return outValue * vol;
		}
		
	}
	
	public static int[] renderVolumes(int oscno) {
		int[] result = new int[50];
		float harmVal = (oscno==1)?P.VAL[P.OSC1_WAVE]:P.VAL[P.OSC2_WAVE];
		float volIndex = FastCalc.ensureRange(harmVal, 0f, 1f)*VOLUMES_COUNT;
		int volBase = (int)volIndex;
		float volFract = volIndex-volBase;
		float div = HARMONICS_COUNT/50f;
		for (int i=0;i<50;i++) {
			int harmIndex = (int)(i*div);
			float vol = VOLUME_MAP[volBase][harmIndex];
			vol += ((VOLUME_MAP[volBase+1][harmIndex]-vol)*volFract);
			result[i] = -1-(int)Math.round((vol)*36);
		}
		return result;
	}
	
}
