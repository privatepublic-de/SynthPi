package de.privatepublic.pi.synth.modules.osc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.util.IOUtils;

public class WaveTables {

	public static final int TABLE_LENGTH = 16*1024;
	public static float TABLE_INCREMENT = TABLE_LENGTH/P.SAMPLE_RATE_HZ;
	public static final int WAVE_SET_COUNT = 5;
//	public static final int WAVE_SET_COUNT_FACTOR = WAVE_SET_COUNT-1;
	public static final String[] WAVE_SET_NAMES = new String[]{"Basic analog","Overtones","Strings","Keyboards","Voices"};
	public static final String[] WAVE_SET_NAMES_SHORT = new String[]{"analog","ovrtne","string","keybrd","voices"};

	
	private static final int OCTAVE_COUNT = 7;
	private static final int FIRST_OCTAVE_MAX_FREQ = 55;
	private static final int NUMBER_WAVES = 7;
	protected static final int WAVE_INDEX_MAX = NUMBER_WAVES-1;
	
	
	public static float[][][][] WAVES = new float[WAVE_SET_COUNT][NUMBER_WAVES][OCTAVE_COUNT][TABLE_LENGTH+1]; // [waveset][waveform][octave][data]
	
	public static void init() {
		try {
			loadWavetables("waves", 2);
			loadFancyTables("keyb/", 2, new String[]{"AKWF_piano_0004.wav","AKWF_piano_0009.wav","AKWF_epiano_0049.wav","AKWF_epiano_0054.wav","AKWF_clavinet_0005.wav","AKWF_eorgan_0003.wav","AKWF_eorgan_0026.wav"});
			loadFancyTables("strings/", 3, new String[]{"AKWF_cello_0019.wav","AKWF_cello_0001.wav","AKWF_violin_0001.wav","AKWF_violin_0002.wav","AKWF_violin_0005.wav","AKWF_cheeze_0004.wav","AKWF_cheeze_0006.wav"});
			loadFancyTables("vox/", 4, new String[]{
					"AKWF_hvoice_0009.wav",
					"a.wav",
					"e.wav",
					"i.wav",
					"o.wav",
					"u.wav",
					"AKWF_hvoice_0080.wav"});
			log.info("Loaded all wavetables.");
			return;
		} catch (IOException e) {
			log.error("Couldn't load wave tables!");
		}
		log.debug("Creating wave tables ...");
		HarmonicAmplitudeFunction aSine = new HarmonicAmplitudeFunction() {
			@Override
			public float ampFactor(int harmonicNo) {
				// sine
				if (harmonicNo==1) {
					return 1;
				};
				return 0;
			}

			@Override
			public void init() {}

			@Override
			public String description() {
				return "Sine";
			}
		};
		HarmonicAmplitudeFunction aTriangle =  new HarmonicAmplitudeFunction() {
			private float sign = -1.0f;
			@Override
			public float ampFactor(int harmonicNo) {
				if (harmonicNo%2==0) { 
					return 0;
				}
				// only odd numbers, alternating sign
				sign = -sign;
				return sign/(harmonicNo*harmonicNo);
			}
			@Override
			public void init() {
				sign = -1.0f;
			}
			@Override
			public String description() {
				return "Triangle";
			}
		};
		HarmonicAmplitudeFunction aSaw = new HarmonicAmplitudeFunction() {
			@Override
			public float ampFactor(int harmonicNo) {
				return 1.0f/harmonicNo;
			}

			@Override
			public void init() {}

			@Override
			public String description() {
				return "Sawtooth";
			}
		};
		HarmonicAmplitudeFunction aSquare = new HarmonicAmplitudeFunction() {
			@Override
			public float ampFactor(int harmonicNo) {
				if (harmonicNo%2==0) { 
					return 0;
				}
				// only odd numbers
				return 1.0f/harmonicNo;
			}

			@Override
			public void init() {}

			@Override
			public String description() {
				return "Square";
			}
		};
		
		final float nyquistfreq = P.SAMPLE_RATE_HZ/2.0f;
		float[] sineTable = createTable(1, aSine); // sine always only 1 harmonic
		for (int oct=0;oct<OCTAVE_COUNT;oct++) {
			final float maxfreq = (float) (FIRST_OCTAVE_MAX_FREQ*Math.pow(2, oct));
			final int noHarmonics = (int)(nyquistfreq/maxfreq);
			// first set
			WAVES[0][0][oct] = sineTable;
			WAVES[0][1][oct] = createTable(noHarmonics, aTriangle);
			WAVES[0][2][oct] = createTable(noHarmonics, aSaw);
			WAVES[0][3][oct] = createTable(noHarmonics, aSquare);
			WAVES[0][4][oct] = createPulse(noHarmonics, 0.25f, WAVES[0][2][oct]);
			WAVES[0][5][oct] = createPulse(noHarmonics, 0.125f, WAVES[0][2][oct]);
			WAVES[0][6][oct] = createPulse(noHarmonics, 0.015625f, WAVES[0][2][oct]);
			// second set
			WAVES[1][0][oct] = createTableWithOvertones(noHarmonics, 1, aTriangle);
			WAVES[1][1][oct] = createTableWithOvertones(noHarmonics, 3, aTriangle);
			WAVES[1][2][oct] = createTableWithOvertones(noHarmonics, 4, aTriangle);
			WAVES[1][3][oct] = createTableWithOvertones(noHarmonics, 5, aTriangle);
			WAVES[1][4][oct] = createTableWithOvertones(noHarmonics, 6, aTriangle);
			WAVES[1][5][oct] = createTableWithOvertones(noHarmonics, 7, aTriangle);
			WAVES[1][6][oct] = createTableWithOvertones(noHarmonics, 8, aTriangle);
		}
	}
	
	protected static final int[] OCT_INDEXES_FOR_FREQUENCY = new int[26000];
	
	static {
		for (int i=0;i<OCT_INDEXES_FOR_FREQUENCY.length;++i) {
			int octindex = 6; 
			if (i<55) {
				octindex = 0;
			}
			else if (i<110) {
				octindex = 1;
			}
			else if (i<220) {
				octindex = 2;
			}
			else if (i<440) {
				octindex = 3;
			}
			else if (i<880) {
				octindex = 4;
			}
			else if (i<1760) {
				octindex = 5;
			}
			OCT_INDEXES_FOR_FREQUENCY[i] = octindex;
		}
	}
	

	
	private static float[] createTable(int numberHarmonics, HarmonicAmplitudeFunction haf) {
		log.debug("Creating wave \"{}\" with {} harmonics (max {} Hz)", haf.description(), numberHarmonics, P.SAMPLE_RATE_HZ/2.0/ numberHarmonics);
		final float[] result = new float[TABLE_LENGTH+1];
		final float frqinc = PI2/TABLE_LENGTH;
		final float sigindex = PI2/numberHarmonics;
		final float sigma = (float) (numberHarmonics>1?Math.sin(sigindex)/sigindex:1f);
		float maxa = 0;
		for (int t=0;t<TABLE_LENGTH;t++) {
			haf.init();
			for (int n=1;n<numberHarmonics+1;++n) {
				result[t] += haf.ampFactor(n)*sigma*Math.sin(n*(t*frqinc));
			}
			maxa = Math.max(maxa, Math.abs(result[t]));
		}
		result[TABLE_LENGTH] = result[0];
		if (maxa>1) {
			// normalize
			final float factor = 1.0f/maxa;
			for (int t=0;t<result.length;t++) {
				result[t] *= factor;
			}
		}
		return result;
	}
	
	private static float[] createPulse(int numberHarmonics, float dutycycle, float[] saw) {
		log.debug("Creating pulse wave with {} harmonics (duty cycle {})", numberHarmonics, dutycycle);
		final float[] result = new float[TABLE_LENGTH+1];
		final int phaseshift1 = 0;//TABLE_LENGTH/2;
		final int phaseshift2 = phaseshift1+(int)(TABLE_LENGTH*dutycycle); 
		float maxa = 0;
		for (int t=0;t<TABLE_LENGTH;t++) {
			result[t] = saw[(t+phaseshift1)%TABLE_LENGTH]-saw[(t+phaseshift2)%TABLE_LENGTH];
			maxa = Math.max(maxa, Math.abs(result[t]));
		}
		result[TABLE_LENGTH] = result[0];
		// normalize
		final float factor = 1.0f/maxa;
		for (int t=0;t<result.length;t++) {
			result[t] *= factor;
		}
		return result;
	}
	
	private static float[] createTableWithOvertones(int numberHarmonics, int overtones, HarmonicAmplitudeFunction haf) {
		log.debug("Creating overtones {} with {} harmonics (max {} Hz)", overtones, numberHarmonics, P.SAMPLE_RATE_HZ/2.0/ numberHarmonics);
		final float[] result = new float[TABLE_LENGTH+1];
		final float frqinc = PI2/TABLE_LENGTH;
		final float sigindex = PI2/numberHarmonics;
		final float sigma = (float) (numberHarmonics>1?Math.sin(sigindex)/sigindex:1);
		float maxa = 0;
		float baseamp = 1;
		float topot = overtones-1;
		for (int ot=0;ot<overtones;ot++) {
			baseamp = topot==0?1:(float)(ot/topot)*(ot/topot);
			for (int t=0;t<TABLE_LENGTH;t++) {
				haf.init();
				for (int n=1;n<numberHarmonics+1;++n) {
					result[t] += baseamp*haf.ampFactor(n)*sigma*Math.sin((ot+n)*(t*frqinc));
				}
				maxa = Math.max(maxa, Math.abs(result[t]));
			}
		}
		result[TABLE_LENGTH] = result[0];
		if (maxa>1) {
			// normalize
			final float factor = 1.0f/maxa;
			for (int t=0;t<result.length;t++) {
				result[t] *= factor;
			}
		}
		return result;
	}
	
	
	
	@SuppressWarnings("unused")
	private static void writeWavetablesToFile(String filenamePrefix) {
		String filename = filenamePrefix+"-"+WAVES.length+"-"+WAVES[0].length+"-"+WAVES[0][0].length+"-"+WAVES[0][0][0].length+".tables";
		log.debug("Writing tables to file: {}", filename);
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		DataOutputStream dos = null;
		try {
			fos = new FileOutputStream(filename);
			bos = new BufferedOutputStream(fos);
			dos = new DataOutputStream(bos);
			for (int i0=0; i0<WAVES.length; i0++) {
				for (int i1=0; i1<WAVES[0].length; i1++) {
					for (int i2=0; i2<WAVES[0][0].length; i2++) {
						for (int i3=0; i3<WAVES[0][0][0].length; i3++) {
							dos.writeDouble(WAVES[i0][i1][i2][i3]);
						}
					}
				}	
			}
			dos.close();
		}
		catch(Exception e) {
			log.error("Error writing tables", e);
		}          
	}
	
	private static void loadWavetables(String filenamePrefix, int wavesetcount) throws IOException {
		String filename = filenamePrefix+"-"+wavesetcount+"-"+WAVES[0].length+"-"+WAVES[0][0].length+"-"+WAVES[0][0][0].length+".tables";
		log.debug("Loading wave tables: {}", filename);
		InputStream in = WaveTables.class.getResourceAsStream("/audio/"+filename);
		if (in==null) {
			throw new IOException("Wave tables "+filename+" not found!");
		}
		BufferedInputStream bis = new BufferedInputStream(in);
		DataInputStream dis = new DataInputStream(bis);
		for (int i0=0; i0<wavesetcount; i0++) {
			for (int i1=0; i1<WAVES[0].length; i1++) {
				for (int i2=0; i2<WAVES[0][0].length; i2++) {
					for (int i3=0; i3<WAVES[0][0][0].length; i3++) {
						WAVES[i0][i1][i2][i3] = (float)dis.readDouble();
					}
				}
			}	
		}
		dis.close();
	}
	
	
	public static void loadFancyTables(String folderName, int position, String... filenames) {
		float[][] ft = new float[filenames.length][];
		for (int i=0;i<filenames.length;i++) {
			ft[i] = stretchTable(folderName+filenames[i]);
		}
		log.debug("Loading wave tables {}", folderName);
		// assign tables to all octaves
		for (int oc=0;oc<OCTAVE_COUNT;oc++) {
			for (int i=0;i<NUMBER_WAVES;i++) {
				WAVES[position][i][oc] = ft[i];
			}			
		}
	}
	
	private static float[] stretchTable(String filename) {
		URL in = WaveTables.class.getResource("/audio/"+filename);
		float[] buf = IOUtils.readWavData(in);
		return stretch(buf, WaveTables.TABLE_LENGTH);
	}
	
	public static float[] stretch(float[] inBuffer, int targetlength) {
		if (targetlength<inBuffer.length) {
			throw new IllegalStateException("Target length must be larger than source buffer!");
		}
		float[] outBuffer = new float[targetlength+1];
		float inc = inBuffer.length/(float)targetlength;
		float inPos = 0;
		for (int i=0;i<targetlength;i++) {
			final int indexBase = (int)inPos;
			final float indexFrac = inPos-indexBase;
			outBuffer[i] = inBuffer[indexBase]+(inBuffer[(indexBase+1)%inBuffer.length]-inBuffer[indexBase])*indexFrac;
			inPos += inc;
		}
		outBuffer[targetlength] = outBuffer[0];
		return outBuffer;
	}
	
	public static int[] waveValues(int size, float waveset, float waveform) {
		final int wavesetindex = (int)(waveset*WAVE_SET_COUNT);
		final float waveformindex = waveform * WAVE_INDEX_MAX;
		final int waveformBase = (int)waveformindex;
		final float waveformFract = waveformindex-waveformBase;
		final float increment = TABLE_LENGTH/(float)size;
		float pos = 0;
		int count = 0;
		final int[] result = new int[size];
		while (count<size) {
			float val1 = WAVES[wavesetindex][waveformBase][0][(int)pos];
			float val2 = (waveformBase<WAVE_INDEX_MAX)?WAVES[wavesetindex][waveformBase+1][0][(int)pos]:0;
			float val = (val1*(1-waveformFract) + val2*waveformFract);
			result[count] = (int)Math.round((val+1)*18)+1;
			pos += increment;
			count++;
		}
		return result;
	} 


	private static interface HarmonicAmplitudeFunction {
		public void init();
		public float ampFactor(int harmonicNo);
		public String description();
	}

	private static final float PI2 = (float) (Math.PI*2);
	private static final Logger log = LoggerFactory.getLogger(WaveTables.class);
}
