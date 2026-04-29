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

	// Polynomial sine: replaces a 48001-entry SINE_TABLE with a degree-9 Taylor
	// approximation of sin(x) on [-π/2, π/2] reached via quadrant-fold of the
	// phase fraction. Worst-case error ~3.6e-6 — well below the 16-bit audio
	// LSB (1.5e-5) and inaudible at output. Removes ~192 KB of L2 traffic per
	// active note and makes the inner harmonic loop a candidate for auto-/
	// explicit SIMD (no gather).
	private static final float TWO_PI = (float)(Math.PI * 2);
	private static final float INV_SR = 1f / P.SAMPLE_RATE_HZ;
	private static final float C1 = 1f / 6f;
	private static final float C2 = 1f / 120f;
	private static final float C3 = 1f / 5040f;
	private static final float C4 = 1f / 362880f;

	private static float polySin(float phaseSamples) {
		float q = phaseSamples * INV_SR;          // phase fraction, q in [0, 1)
		if (q >= 0.5f) q -= 1f;                   // reduce to [-0.5, 0.5)
		if (q > 0.25f) q = 0.5f - q;              // fold to [-0.25, 0.25]
		else if (q < -0.25f) q = -0.5f - q;
		final float x = TWO_PI * q;               // x in [-π/2, π/2]
		final float x2 = x * x;
		return x * (1f - x2 * (C1 - x2 * (C2 - x2 * (C3 - x2 * C4))));
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
	private static final int HARMONICS_COUNT_EFFECTIVE = P.LOW_BUDGET_ADDITIVE?HARMONIC_FACTORS_SEMIS.length/2:HARMONIC_FACTORS_SEMIS.length;
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
		for (int i=0;i<HARMONICS_COUNT_EFFECTIVE;i++) {
			outValue += sines[i].value(P.OSC1_WAVE, LFO.lfoAmountAdd(sampleNo, modwave1amount, modEnvelope, modenv1waveamount), false);
		}
		am_buffer[sampleNo] = outValue;
		syncOnFrameBuffer[sampleNo] = sines[5].cycleStart;
		return outValue * volume;
	}

	@Override
	public float processSample2nd(int sampleNo, float volume, boolean[] syncOnFrameBuffer, float[] am_buffer, EnvADSR modEnvelope) {
		if (P.LOW_BUDGET_ADDITIVE) {
			return outValue * volume;	
		}
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
		for (int i=0;i<HARMONICS_COUNT_EFFECTIVE;i++) {
			outValue += sines[i].value(P.OSC2_WAVE, LFO.lfoAmountAdd(sampleNo, modwave2amount, modEnvelope, modenv1waveamount), sync);
		}
		if (ampmod) {
			return (am_buffer[sampleNo]*outValue*volume);
		}
		else {
			return outValue*volume;
		}
	}

	/**
	 * Buffer-rate variant of {@link #processSample1st}. Caller pre-renders
	 * {@code modEnvBuf}.
	 */
	public void processBuffer1st(final int nframes, final float volume, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final float[] modEnvBuf, final float[] outBuf) {
		final float pitchBend = P.PITCH_BEND_FACTOR;
		final float pitchDepth = P.VALXC[P.MOD_PITCH_AMOUNT];
		final float pitchModEnvDepth = P.VALXC[P.MOD_ENV1_PITCH_AMOUNT];
		final float wfDepth = P.VALXC[P.MOD_WAVE1_AMOUNT];
		final float wfModEnvDepth = P.VALXC[P.MOD_ENV1_WAVE_AMOUNT];
		final float modAmount = P.MOD_AMOUNT_COMBINED;
		final float wfBase = P.VAL[P.OSC1_WAVE];
		final int harmonicsN = HARMONICS_COUNT_EFFECTIVE;
		final Sine[] s = sines;

		float effFreq = effectiveFrequency;
		final float targetFreq = targetFrequency;
		final float glide = glideStepSize;

		for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
			if (effFreq != targetFreq) {
				if (effFreq < targetFreq) effFreq += glide;
				else if (effFreq > targetFreq) effFreq -= glide;
				if (Math.abs(effFreq - targetFreq) < glide) effFreq = targetFreq;
			}
			final float lfoVal = LFO.GLOBAL.bufferedValueAt(sampleNo);
			final float modEnvVal = modEnvBuf[sampleNo];
			frequencyStepSize = effFreq * ((1 - lfoVal*modAmount*pitchDepth) + modEnvVal*pitchModEnvDepth) * pitchBend;
			final float waveformMod = lfoVal*modAmount*wfDepth + modEnvVal*wfModEnvDepth;
			// Hoist VOLUME_MAP index/fract once per sample — same for all 15 harmonics.
			final float vIndex = FastCalc.ensureRange(wfBase + waveformMod, 0f, 1f) * VOLUMES_COUNT;
			final int volBase = (int)vIndex;
			final float volFract = vIndex - volBase;
			float acc = 0;
			for (int i=0; i<harmonicsN; i++) {
				acc += s[i].valueOfPrecomputed(volBase, volFract, false);
			}
			outValue = acc;
			am_buffer[sampleNo] = acc;
			syncOnFrameBuffer[sampleNo] = s[5].cycleStart;
			outBuf[sampleNo] = acc * volume;
		}

		effectiveFrequency = effFreq;
	}

	/**
	 * Buffer-rate variant of {@link #processSample2nd}. See {@link #processBuffer1st}.
	 */
	public void processBuffer2nd(final int nframes, final float volume, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final float[] modEnvBuf, final float[] outBuf) {
		if (P.LOW_BUDGET_ADDITIVE) {
			// Match per-sample behavior: returns the (untouched) instance outValue × volume each sample.
			final float v = outValue * volume;
			for (int i=0; i<nframes; i++) outBuf[i] = v;
			return;
		}
		final float pitchBend = P.PITCH_BEND_FACTOR;
		final float pitchDepth = P.VALXC[P.MOD_PITCH_AMOUNT];
		final float pitchModEnvDepth = P.VALXC[P.MOD_ENV1_PITCH_AMOUNT];
		final float pitch2Depth = P.VALXC[P.MOD_PITCH2_AMOUNT];
		final float pitch2ModEnvDepth = P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT];
		final float wfDepth = P.VALXC[P.MOD_WAVE2_AMOUNT];
		final float wfModEnvDepth = P.VALXC[P.MOD_ENV1_WAVE_AMOUNT];
		final float modAmount = P.MOD_AMOUNT_COMBINED;
		final float ampModAmt = P.VALC[P.MOD_ENV1_AM_AMOUNT];
		final float osc2AmBase = P.VAL[P.OSC2_AM];
		final boolean osc2AmIs = P.IS[P.OSC2_AM];
		final boolean osc2SyncIs = P.IS[P.OSC2_SYNC];
		final float wfBase = P.VAL[P.OSC2_WAVE];
		final int harmonicsN = HARMONICS_COUNT_EFFECTIVE;
		final Sine[] s = sines;

		float effFreq = effectiveFrequency;
		boolean ampmodLocal = ampmod;
		final float targetFreq = targetFrequency;
		final float glide = glideStepSize;

		for (int sampleNo=0; sampleNo<nframes; sampleNo++) {
			if (ampmodLocal && !osc2AmIs) {
				effFreq = targetFreq;
			}
			final float modEnvVal = modEnvBuf[sampleNo];
			final float ampamt = FastCalc.ensureRange(osc2AmBase + modEnvVal*ampModAmt, 0, 1);
			ampmodLocal = ampamt > 0 || ampModAmt != 0;
			if (ampmodLocal) {
				effFreq = targetFreq * (ampamt * 4);
			}
			else {
				if (effFreq != targetFreq) {
					if (effFreq < targetFreq) effFreq += glide;
					else if (effFreq > targetFreq) effFreq -= glide;
					if (Math.abs(effFreq - targetFreq) < glide) effFreq = targetFreq;
				}
			}
			final float lfoVal = LFO.GLOBAL.bufferedValueAt(sampleNo);
			final float pitchLfo = (1 - lfoVal*modAmount*pitchDepth) + modEnvVal*pitchModEnvDepth;
			final float pitchAsymm = ((lfoVal+1)*modAmount*0.5f*pitch2Depth) + 1 + modEnvVal*pitch2ModEnvDepth;
			frequencyStepSize = effFreq * pitchLfo * pitchBend * pitchAsymm;
			final boolean sync = osc2SyncIs && syncOnFrameBuffer[sampleNo];
			final float waveformMod = lfoVal*modAmount*wfDepth + modEnvVal*wfModEnvDepth;
			// Hoist VOLUME_MAP index/fract once per sample — same for all 15 harmonics.
			final float vIndex = FastCalc.ensureRange(wfBase + waveformMod, 0f, 1f) * VOLUMES_COUNT;
			final int volBase = (int)vIndex;
			final float volFract = vIndex - volBase;
			float acc = 0;
			for (int i=0; i<harmonicsN; i++) {
				acc += s[i].valueOfPrecomputed(volBase, volFract, sync);
			}
			outValue = acc;
			outBuf[sampleNo] = ampmodLocal ? am_buffer[sampleNo]*acc*volume : acc*volume;
		}

		effectiveFrequency = effFreq;
		ampmod = ampmodLocal;
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
		
		public final float value(final int waveformparaindex, final float waveformmod, final boolean sync) {
			return valueOf(P.VAL[waveformparaindex], waveformmod, sync);
		}

		/**
		 * Same as {@link #value(int, float, boolean)} but takes the waveform parameter
		 * value directly. The buffer-rate caller hoists {@code P.VAL[OSC*_WAVE]} once
		 * per sample instead of re-reading it for every harmonic.
		 */
		public final float valueOf(final float waveformval, final float waveformmod, final boolean sync) {
			final float vIndex = FastCalc.ensureRange((waveformval+waveformmod), 0f, 1f)*VOLUMES_COUNT;
			final int vBase = (int)vIndex;
			return valueOfPrecomputed(vBase, vIndex - vBase, sync);
		}

		/**
		 * Per-harmonic kernel with {@code volBase}/{@code volFract} already computed
		 * by the caller. Used by the buffer-rate path so the
		 * {@code FastCalc.ensureRange + multiply + cast + subtract} chain runs once
		 * per sample instead of once per harmonic (×{@code HARMONICS_COUNT_EFFECTIVE}).
		 */
		public final float valueOfPrecomputed(final int volBase, final float volFract, final boolean sync) {
			float vol = VOLUME_MAP[volBase][harmIndex];
			vol += ((VOLUME_MAP[volBase+1][harmIndex]-vol)*volFract);
			if (sync) {
				lphase = 0;
			}
			final float outValue = polySin(lphase);
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
