package de.privatepublic.pi.synth.util;

public final class FastCalc {
	
	public static final float exp(float x) {
		//		final long tmp = (long) (1512775 * val + 1072632447);
		//		return float.longBitsTofloat(tmp << 32);
		x = 1f + x / 256f;
		x *= x; x *= x; x *= x; x *= x;
		x *= x; x *= x; x *= x; x *= x;
		return x;
	}
	
//	public static float pow(float a, float b) {
//		final long tmp = (long) (9076650 * (a - 1) / (a + 1 + 4 * (Math.sqrt(a))) * b + 1072632447);
//		return float.longBitsTofloat(tmp << 32);
//	}
	
	public static final float pow(final float a, final float b) {
		final long tmp = Double.doubleToLongBits(a);
		final long tmp2 = (long)(b * (tmp - 4606921280493453312L)) + 4606921280493453312L;
		return (float) Double.longBitsToDouble(tmp2);
	}
	
	public static float asymmSaturate(float sampleValue, float driveAmount) {
		float drive = sampleValue*(1f + driveAmount);
		float dsquare = drive*drive;
		return drive * (27+dsquare)/(27+9*dsquare);
	}
	
	public static final float ensureRange(final float val, final float min, final float max) {
		return val<min?min:(val>max?max:val);
	}
	
	public static float PI2 = (float) (2*Math.PI);
	
	public static final float sin(float phase) {
		float out;
		if (phase < -Math.PI) {
		    phase += PI2;
		}
		else {
			if (phase >  Math.PI) {
			    phase -= PI2;
			}
		}
		if (phase < 0) {
		    out = 1.27323954f * phase + .405284735f * phase * phase;
		    if (out < 0) {
		        out = .225f * (out *-out - out) + out;
		    }
		    else {
		        out = .225f * (out * out - out) + out;
		    }
		}
		else {
		    out = 1.27323954f * phase - 0.405284735f * phase * phase;
		    if (out < 0) {
		        out = .225f * (out *-out - out) + out;
		    }
		    else {
		        out = .225f * (out * out - out) + out;
		    }
		}
		return out;
	}
	
	public static class InterpolatedArrayAccess {
		
		private final float[] data;
		
		public InterpolatedArrayAccess(float[] data) {
			this.data = data;
		}
		
		public float valueAt(float pos) {
			final int indexBase = (int)pos;
			final float indexFrac = pos-indexBase;
			float outValue = data[indexBase];
			outValue += ((data[indexBase+1]-outValue)*indexFrac);
			return outValue;
		}
		
		public float valueAt(int pos) {
			return data[pos];
		}
		
		
	}
	
	
	public static void main(String[] args) {
		
		float val = -2;
		while (val<10) {
			System.out.println(val+" "+ensureRange(val, 0, 8));
			val += .23;
		}
		
		
		System.out.println("\nExp");
		for (float i=-40;i<41;i++) {
			float x = (i / 20f);
			float x1 = (float) Math.exp(x);
			float x2 = exp(x);
			float diff = x2-x1;
			System.out.format("%+.2f: %.8f - %.8f = %+.8f%n", x, x1, x2, diff);
		}
		System.out.println("\nPow");
		for (float i=0;i<41;i++) {
			float x = (i / 20f);
			float x1 = (float) Math.pow(x, 0.25);
			float x2 = pow(x, 0.25f);
			float diff = x2-x1;
			System.out.format("%+.2f: %.8f - %.8f = %+.8f%n", x, x1, x2, diff);
		}
		
	}

}
