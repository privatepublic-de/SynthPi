package de.privatepublic.pi.synth.modules.fx;

import de.privatepublic.pi.synth.P;

public class DistortionExp implements IProcessor {

	private static final float MAX_GAIN = 5;

	private float gain, gainDry, gainWet;

	// Pre-allocated 2× oversampling buffers — no heap allocation in process()
	private final float[] osL;
	private final float[] osR;
	// Anti-imaging filters (upsampling path, one per channel)
	private final HalfBandFilter aifL, aifR;
	// Anti-aliasing filters (decimation path, one per channel)
	private final HalfBandFilter aafL, aafR;

	private boolean wasActive = false;

	public DistortionExp() {
		osL  = new float[P.SAMPLE_BUFFER_SIZE * 2];
		osR  = new float[P.SAMPLE_BUFFER_SIZE * 2];
		aifL = new HalfBandFilter(P.SAMPLE_RATE_HZ);
		aifR = new HalfBandFilter(P.SAMPLE_RATE_HZ);
		aafL = new HalfBandFilter(P.SAMPLE_RATE_HZ);
		aafR = new HalfBandFilter(P.SAMPLE_RATE_HZ);
	}

	@Override
	public final void process(final int bufferLen, final float[][] buffers) {
		gain    = 1f + MAX_GAIN * P.VAL[P.OVERDRIVE];
		gainDry = P.VALMIXHIGH[P.OVERDRIVE];
		gainWet = P.VALMIXLOW[P.OVERDRIVE];

		if (!P.IS[P.OVERDRIVE]) {
			wasActive = false;
			return;
		}

		if (!wasActive) {
			// Flush stale filter state to avoid a click when overdrive turns on
			aifL.reset(); aifR.reset();
			aafL.reset(); aafR.reset();
			wasActive = true;
		}

		final float[] bufL = buffers[0];
		final float[] bufR = buffers[1];
		final int osLen = bufferLen * 2;

		// --- Upsample: linear interpolation + anti-imaging LP ---
		for (int i = 0; i < bufferLen; i++) {
			final float xL  = bufL[i];
			final float xR  = bufR[i];
			final float xLn = (i + 1 < bufferLen) ? bufL[i + 1] : xL;
			final float xRn = (i + 1 < bufferLen) ? bufR[i + 1] : xR;
			osL[2 * i]     = aifL.process(xL);
			osL[2 * i + 1] = aifL.process(0.5f * (xL + xLn));
			osR[2 * i]     = aifR.process(xR);
			osR[2 * i + 1] = aifR.process(0.5f * (xR + xRn));
		}

		// --- Distort at 2× sample rate ---
		for (int i = 0; i < osLen; i++) {
			float xd = osL[i] * gain;
			osL[i] = osL[i] * gainDry + (xd / (1f + Math.abs(xd))) * gainWet;
			xd     = osR[i] * gain;
			osR[i] = osR[i] * gainDry + (xd / (1f + Math.abs(xd))) * gainWet;
		}

		// --- Downsample: AA filter all samples, keep even-indexed outputs ---
		// The filter must see every sample in order to maintain state continuity.
		for (int i = 0; i < bufferLen; i++) {
			bufL[i] = aafL.process(osL[2 * i]);
			aafL.process(osL[2 * i + 1]);   // advance state, output discarded
			bufR[i] = aafR.process(osR[2 * i]);
			aafR.process(osR[2 * i + 1]);   // advance state, output discarded
		}
	}
}
