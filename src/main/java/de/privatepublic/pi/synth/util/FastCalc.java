package de.privatepublic.pi.synth.util;

public final class FastCalc {

	public static final float pow(final float a, final float b) {
		final long tmp = Double.doubleToLongBits(a);
		final long tmp2 = (long)(b * (tmp - 4606921280493453312L)) + 4606921280493453312L;
		return (float) Double.longBitsToDouble(tmp2);
	}
	
	public static final float ensureRange(final float val, final float min, final float max) {
		return val<min?min:(val>max?max:val);
	}
	
}
