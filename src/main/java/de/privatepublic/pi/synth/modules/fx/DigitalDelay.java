package de.privatepublic.pi.synth.modules.fx;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.modules.mod.LFO;

/**
 * Clean stereo digital delay with independent left/right tap times.
 * Sonically contrasted with {@link TapeDelay}: self-feedback per channel
 * (no ping-pong cross-fed loop), and {@code DELAY_RATE} drives the left
 * tap while {@code DELAY_RATE_RIGHT} drives the right tap, giving wide
 * stereo placement when the two rates differ.
 *
 * <p>Per-buffer linear rate ramping avoids zipper noise on rate changes.
 * A future phase will move the rate update to {@link #controlTick()} for
 * finer-grained crossfading.
 */
public class DigitalDelay extends DelayBase {

	private final int delayLineSize = (int)(P.SAMPLE_RATE_HZ*2);
	private final int delayLineSizeUnder = delayLineSize-1;
	private final float[] delayLineL = new float[delayLineSize];
	private final float[] delayLineR = new float[delayLineSize];
	private int writeIndex = 0;

	private float lastrateL = 0;
	private float lastrateR = 0;

	@Override
	public void initPatch() {
		for (int i=0; i<delayLineSize; i++) {
			delayLineL[i] = 0;
			delayLineR[i] = 0;
		}
		writeIndex = 0;
		lastrateL = 0;
		lastrateR = 0;
	}

	@Override
	public void process(final int bufferLen, final float[][] buffers) {
		final float feedback = P.VAL[P.DELAY_FEEDBACK];
		final float lfoAdd = LFO.lfoAmountAdd(0, P.VALXC[P.MOD_DELAY_TIME_AMOUNT]);
		final float baseL  = delayLineSizeUnder*(.001f+.999f*P.VALX[P.DELAY_RATE]);
		final float baseR  = delayLineSizeUnder*(.001f+.999f*P.VALX[P.DELAY_RATE_RIGHT]);
		final float rateL  = Math.max(1f, Math.min(delayLineSizeUnder, baseL + baseL*0.5f*lfoAdd));
		final float rateR  = Math.max(1f, Math.min(delayLineSizeUnder, baseR + baseR*0.5f*lfoAdd));
		final float deltaL = (rateL - lastrateL) / bufferLen;
		final float deltaR = (rateR - lastrateR) / bufferLen;
		final float wet = P.VAL[P.DELAY_WET];
		final float wetInv = 1-wet;
		final float[] bufL = buffers[0];
		final float[] bufR = buffers[1];
		float in1, in2, readindexL, readindexR, indexFractL, indexFractR;
		float valL0, valL1, valR0, valR1, outL, outR;
		int indexBaseL, indexBaseR;
		for (int i=0;i<bufferLen;i++) {
			in1 = bufL[i];
			in2 = bufR[i];
			if (++writeIndex==delayLineSize) writeIndex=0;
			readindexL = (writeIndex - lastrateL);
			if (readindexL<0) readindexL += delayLineSize;
			readindexR = (writeIndex - lastrateR);
			if (readindexR<0) readindexR += delayLineSize;
			indexBaseL = (int)readindexL;
			indexFractL = readindexL - indexBaseL;
			indexBaseR = (int)readindexR;
			indexFractR = readindexR - indexBaseR;
			final int indexBase1L = indexBaseL+1 < delayLineSize ? indexBaseL+1 : 0;
			final int indexBase1R = indexBaseR+1 < delayLineSize ? indexBaseR+1 : 0;
			valL0 = delayLineL[indexBaseL];
			valL1 = delayLineL[indexBase1L];
			valR0 = delayLineR[indexBaseR];
			valR1 = delayLineR[indexBase1R];
			final float tappedL = valL0 + (valL1-valL0)*indexFractL;
			final float tappedR = valR0 + (valR1-valR0)*indexFractR;
			// self-feedback (per-channel), input mixed in mono-sum to feed both lines
			final float inSum = in1 + in2;
			delayLineL[writeIndex] = inSum + tappedL*feedback;
			delayLineR[writeIndex] = inSum + tappedR*feedback;
			outL = tappedL*wet;
			outR = tappedR*wet;
			bufL[i] = in1*wetInv+outL;
			bufR[i] = in2*wetInv+outR;
			lastrateL += deltaL;
			lastrateR += deltaR;
		}
		lastrateL = rateL;
		lastrateR = rateR;
	}
}
