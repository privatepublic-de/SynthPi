package de.privatepublic.pi.synth.modules.fx;

public class HalfBandFilter {

    private final float b0s1, b1s1, b2s1, a1s1, a2s1;
    private final float b0s2, b1s2, b2s2, a1s2, a2s2;
    private float w1s1, w2s1, w1s2, w2s2;

    public HalfBandFilter(float sampleRate) {
        // 4th-order Butterworth LP via bilinear transform
        // cutoff = 0.45 × base rate at 2× oversampled rate → normalized 0.225
        double fc = 0.45 * sampleRate / (2.0 * sampleRate);
        double K  = Math.tan(Math.PI * fc);
        double K2 = K * K;
        // Butterworth pole-pair Q values: 1/(2*cos(θ)) for θ = π/8, 3π/8
        double Q1 = 1.0 / (2.0 * Math.cos(Math.PI / 8.0));
        double Q2 = 1.0 / (2.0 * Math.cos(3.0 * Math.PI / 8.0));

        double d1 = K2 + K / Q1 + 1.0;
        b0s1 = (float) (K2 / d1);
        b1s1 = 2f * b0s1;
        b2s1 = b0s1;
        a1s1 = (float) (2.0 * (K2 - 1.0) / d1);
        a2s1 = (float) ((K2 - K / Q1 + 1.0) / d1);

        double d2 = K2 + K / Q2 + 1.0;
        b0s2 = (float) (K2 / d2);
        b1s2 = 2f * b0s2;
        b2s2 = b0s2;
        a1s2 = (float) (2.0 * (K2 - 1.0) / d2);
        a2s2 = (float) ((K2 - K / Q2 + 1.0) / d2);
    }

    public float process(float x) {
        // Two cascaded biquads, Direct Form II Transposed
        float y1 = b0s1 * x + w1s1;
        w1s1 = b1s1 * x - a1s1 * y1 + w2s1;
        w2s1 = b2s1 * x - a2s1 * y1;
        float y2 = b0s2 * y1 + w1s2;
        w1s2 = b1s2 * y1 - a1s2 * y2 + w2s2;
        w2s2 = b2s2 * y1 - a2s2 * y2;
        return y2;
    }

    public void reset() {
        w1s1 = w2s1 = w1s2 = w2s2 = 0f;
    }
}
