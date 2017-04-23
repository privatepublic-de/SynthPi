package de.privatepublic.pi.synth.modules.fx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.P.FilterType;
import de.privatepublic.pi.synth.util.FastCalc;

public class StateVariableFilter {

	private static final float DOUBLE_SAMPLE_RATE = P.SAMPLE_RATE_HZ*2;
	
	private FilterType type;
	
	public StateVariableFilter(FilterType type, float cutoff, float resonance) {
		if (type==FilterType.LOWPASS24) {
			throw new IllegalArgumentException("Filter type not supported.");
		}
		this.type = type;
		frq = cutoff;
		Q = resonance;
		update();
	}
	
	public void setCutoff(float frequency) {
		frq = frequency;
		update();
	}
	
	public void setResonance(float resonance) {
		Q = resonance;
		update();
	}
	
	private void update() {
		f1 = (float) (2.0*Math.sin(Math.PI*(frq/DOUBLE_SAMPLE_RATE)));  // the fs*2 is because it's float sampled
		damp = (float) Math.min(2.0*(1.0 - FastCalc.pow(Q, 0.25f)), Math.min(2.0f, 2.0f/f1 - f1*0.5f));
	}

	private float frq, Q;
	private float f1, damp;
	private float notch, low, high, band, out;
	
	@SuppressWarnings("incomplete-switch")
	public float processSample(final float sampleValue) {
		notch = sampleValue - damp*band;
		low   = low + f1*band;
		high  = notch - low;
		band  = f1*high + band;// - drive*band*band*band;
		switch (type) {
		case LOWPASS:
			out = low;
			break;
		case BANDPASS:
			out = band;
			break;
		case HIGHPASS:
			out = high;
			break;
		case NOTCH:
			out = notch;
			break;
		case ACID:
		default:
			out = low+band+high;
		}
		notch = sampleValue - damp*band;
		low   = low + f1*band;
		high  = notch - low;
		band  = f1*high + band;// - drive*band*band*band;
		switch (type) {
		case LOWPASS:
			return out + low;
		case BANDPASS:
			return out + band;
		case HIGHPASS:
			return out + high;
		case NOTCH:
			return out + notch;
		}
		return out + low+band+high;

	}
	
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(MultiModeFilter.class);

	
	public void processBuffer(float[] buffer, int startPos, float depth) {
		for (int i=0;i<P.CONTROL_BUFFER_SIZE;i++) {
			int pos = i + startPos;
			buffer[pos] += processSample(buffer[pos])*depth;
		}		
	}
}
