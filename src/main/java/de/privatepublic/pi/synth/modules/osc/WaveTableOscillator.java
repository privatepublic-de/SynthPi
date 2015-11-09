package de.privatepublic.pi.synth.modules.osc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.IPitchBendReceiver;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.mod.EnvADSR;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.util.FastCalc;

public class WaveTableOscillator extends OscillatorBase implements IPitchBendReceiver {

	private float tableIndex = 0;
	private final int wavesetparamindex;
//	private static float[] ignoreBuffer = new float[P.SAMPLE_BUFFER_SIZE];
	
	public WaveTableOscillator(boolean primaryOrSecondary) {
		super(primaryOrSecondary);
		wavesetparamindex = primaryOrSecondary?P.OSC1_WAVE_SET:P.OSC2_WAVE_SET;
		MidiHandler.registerReceiver(this);
	}
	

	private boolean ampmod;
	
	
	@Override
	public float processSample1st(final int sampleNo, final float vol, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
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
		final float freq = effectiveFrequency*LFO.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR;
		final float waveform = P.VAL[P.OSC1_WAVE] + LFO.lfoAmountAdd(sampleNo, P.VALXC[P.MOD_WAVE1_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_WAVE_AMOUNT]);
		final float waveformpos;
		if (waveform>1) {
			waveformpos = WaveTables.WAVE_INDEX_MAX;
		}
		else if (waveform<0) {
			waveformpos = 0;
		}
		else {
			waveformpos = waveform * WaveTables.WAVE_INDEX_MAX;
		}
		final int waveformBase = (int)waveformpos;
		final float waveformFract = waveformpos-waveformBase;
		final int wavesetindex = (int)(P.VAL[wavesetparamindex]*WaveTables.WAVE_SET_COUNT);// TODO calculate in P.set
		final int octindex = WaveTables.OCT_INDEXES_FOR_FREQUENCY[(int)freq];
		final int indexBase = (int)tableIndex;
		final float indexFrac = tableIndex-indexBase;
		final float[] wave1 =   WaveTables.WAVES[wavesetindex][waveformBase][octindex];
		float val = wave1[indexBase];
		val += ((wave1[indexBase+1]-val)*indexFrac);
		if (waveformBase<WaveTables.WAVE_INDEX_MAX) {
			final float[] wave2 =   WaveTables.WAVES[wavesetindex][waveformBase+1][octindex];
			float val2 = wave2[indexBase];
			val2 += ((wave2[indexBase+1]-val2)*indexFrac);
			val = (val*(1-waveformFract) + val2*waveformFract);
		}
		am_buffer[sampleNo] = val;
		tableIndex += WaveTables.TABLE_INCREMENT*freq;
		if (tableIndex >= WaveTables.TABLE_LENGTH) {
			tableIndex -= WaveTables.TABLE_LENGTH;
			syncOnFrameBuffer[sampleNo] = true;
		}
		else {
			syncOnFrameBuffer[sampleNo] = false;
		}
		return val*vol;
	}
	
	@Override
	public float processSample2nd(final int sampleNo, final float vol, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
		// second osc
		if (ampmod && !P.IS[P.OSC2_AM]) {
			effectiveFrequency = targetFrequency;
		}
		final float ampModAmount = P.VALC[P.MOD_ENV1_AM_AMOUNT];
		final float ampamount = FastCalc.ensureRange(P.VAL[P.OSC2_AM]+modEnvelope.outValue*ampModAmount, 0, 1);
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
			if (P.IS[P.OSC2_SYNC] && syncOnFrameBuffer[sampleNo]) {
				tableIndex = 0;
			}
		}
		final float freq = 
				effectiveFrequency*LFO.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR
				* LFO.lfoAmountAsymm(sampleNo, P.VALXC[P.MOD_PITCH2_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT]);
		
		final float waveform = P.VAL[P.OSC2_WAVE] + LFO.lfoAmountAdd(sampleNo, P.VALXC[P.MOD_WAVE2_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_WAVE_AMOUNT]);
		final float waveformpos;
		if (waveform>1) {
			waveformpos = WaveTables.WAVE_INDEX_MAX;
		}
		else if (waveform<0) {
			waveformpos = 0;
		}
		else {
			waveformpos = waveform * WaveTables.WAVE_INDEX_MAX;
		}
		final int waveformBase = (int)waveformpos;
		final float waveformFract = waveformpos-waveformBase;
		final int wavesetindex = (int)(P.VAL[wavesetparamindex]*WaveTables.WAVE_SET_COUNT);// TODO calculate in P.set
		final int octindex = WaveTables.OCT_INDEXES_FOR_FREQUENCY[(int)freq];
		final int indexBase = (int)tableIndex;
		final float indexFrac = tableIndex-indexBase;
		final float[] wave1 =   WaveTables.WAVES[wavesetindex][waveformBase][octindex];
		float val = wave1[indexBase];
		val += ((wave1[indexBase+1]-val)*indexFrac);
		if (waveformBase<WaveTables.WAVE_INDEX_MAX) {
			final float[] wave2 =   WaveTables.WAVES[wavesetindex][waveformBase+1][octindex];
			float val2 = wave2[indexBase];
			val2 += ((wave2[indexBase+1]-val2)*indexFrac);
			val = (val*(1-waveformFract) + val2*waveformFract);
		}
		
		tableIndex += WaveTables.TABLE_INCREMENT*freq;
		if (tableIndex >= WaveTables.TABLE_LENGTH) {
			tableIndex -= WaveTables.TABLE_LENGTH;
		}
		return ampmod ? am_buffer[sampleNo]*val*vol : val*vol;
	}
	
	// sadly the above copy & paste coding approach is much more performant!
	
//	@Override
//	public float processSample1st(final int sampleNo, final float vol, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
//		if (effectiveFrequency!=targetFrequency) {
//			if (effectiveFrequency<targetFrequency) {
//				effectiveFrequency += glideStepSize;
//			}
//			else if (effectiveFrequency>targetFrequency) {
//				effectiveFrequency -= glideStepSize;
//			}
//			if (Math.abs(effectiveFrequency-targetFrequency)<glideStepSize) {
//				effectiveFrequency = targetFrequency;
//			}
//		}
//		final float freq = effectiveFrequency*P.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR;
//		final float waveform = P.VAL[P.OSC1_WAVE] + P.lfoAmountAdd(sampleNo, P.VALXC[P.MOD_WAVE_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_WAVE_AMOUNT]);
//		return process(freq, waveform, sampleNo, am_buffer, syncOnFrameBuffer)*vol;
//	}
//	
//	@Override
//	public float processSample2nd(final int sampleNo, final float vol, final boolean[] syncOnFrameBuffer, final float[] am_buffer, final EnvADSR modEnvelope) {
//		// second osc
//		final float ampmodVal = P.VAL[P.OSC2_AM];
//		if (ampmod && ampmodVal==0) {
//			effectiveFrequency = targetFrequency;
//		}
//		ampmod = ampmodVal>0;
//		if (ampmod) {
//			effectiveFrequency = targetFrequency*(ampmodVal*4);			
//		}
//		else {
//			if (effectiveFrequency!=targetFrequency) {
//				if (effectiveFrequency<targetFrequency) {
//					effectiveFrequency += glideStepSize;
//				}
//				else if (effectiveFrequency>targetFrequency) {
//					effectiveFrequency -= glideStepSize;
//				}
//				if (Math.abs(effectiveFrequency-targetFrequency)<glideStepSize) {
//					effectiveFrequency = targetFrequency;
//				}
//			}
//			if (P.IS[P.OSC2_SYNC] && syncOnFrameBuffer[sampleNo]) {
//				tableIndex = 0;
//			}
//		}
//		final float freq = 
//				effectiveFrequency*P.lfoAmount(sampleNo, P.VALXC[P.MOD_PITCH_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH_AMOUNT])*P.PITCH_BEND_FACTOR
//				* P.lfoAmountAsymm(sampleNo, P.VALXC[P.MOD_PITCH2_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_PITCH2_AMOUNT]);
//		final float waveform = P.VAL[P.OSC2_WAVE] + P.lfoAmountAdd(sampleNo, P.VALXC[P.MOD_WAVE_AMOUNT], modEnvelope, P.VALXC[P.MOD_ENV1_WAVE_AMOUNT]);
//		final float val = process(freq, waveform, sampleNo, ignoreBuffer, syncOnFrameBuffer);
//		return ampmod ? am_buffer[sampleNo]*val*vol : val*vol;
//	}
//	
//	
//	private float process(final float freq, final float waveform, final int sampleNo, final float[] am_buffer, final boolean[] syncOnFrameBuffer) {
//		final float waveformpos;
//		if (waveform>1) {
//			waveformpos = WaveTables.WAVE_INDEX_MAX;
//		}
//		else if (waveform<0) {
//			waveformpos = 0;
//		}
//		else {
//			waveformpos = waveform * WaveTables.WAVE_INDEX_MAX;
//		}
//		final int waveformBase = (int)waveformpos;
//		final float waveformFract = waveformpos-waveformBase;
//		final int wavesetindex = (int)(P.VAL[wavesetparamindex]*WaveTables.WAVE_SET_COUNT);// TODO calculate in P.set
//		final int octindex = WaveTables.OCT_INDEXES_FOR_FREQUENCY[(int)freq];
//		final int indexBase = (int)tableIndex;
//		final float indexFrac = tableIndex-indexBase;
//		final float[] wave1 =   WaveTables.WAVES[wavesetindex][waveformBase][octindex];
//		float val = wave1[indexBase];
//		val += ((wave1[indexBase+1]-val)*indexFrac);
//		if (waveformBase<WaveTables.WAVE_INDEX_MAX) {
//			final float[] wave2 =   WaveTables.WAVES[wavesetindex][waveformBase+1][octindex];
//			float val2 = wave2[indexBase];
//			val2 += ((wave2[indexBase+1]-val2)*indexFrac);
//			val = (val*(1-waveformFract) + val2*waveformFract);
//		}
//		am_buffer[sampleNo] = val;
//		tableIndex += WaveTables.TABLE_INCREMENT*freq;
//		if (tableIndex >= WaveTables.TABLE_LENGTH) {
//			tableIndex -= WaveTables.TABLE_LENGTH;
//			syncOnFrameBuffer[sampleNo] = true;
//		}
//		else {
//			syncOnFrameBuffer[sampleNo] = false;
//		}
//		return val;
//	}
	
	
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(WaveTableOscillator.class);

	@Override
	public void onPitchBend() {
		setTargetFrequency(frequency);
	}

	
}
