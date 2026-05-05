package de.privatepublic.pi.synth;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

import org.jaudiolibs.audioservers.AudioClient;
import org.jaudiolibs.audioservers.AudioConfiguration;
import org.jaudiolibs.audioservers.AudioServer;
import org.jaudiolibs.audioservers.AudioServerProvider;
import org.jaudiolibs.audioservers.ext.ClientID;
import org.jaudiolibs.audioservers.ext.Connections;
import org.jaudiolibs.audioservers.ext.Device;
import org.jaudiolibs.audioservers.javasound.JSTimingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.comm.MidiPlayback;
import de.privatepublic.pi.synth.modules.AnalogSynth;
import de.privatepublic.pi.synth.modules.ISynth;
import de.privatepublic.pi.synth.modules.mod.LFO;
import de.privatepublic.pi.synth.modules.osc.WaveTables;

public class SynthPiAudioClient implements AudioClient {
	
	private static SynthPiAudioClient client;
	private static AudioServer server; 
	
	public static void start() throws Exception {
		if ("JavaSound".equals(P.AUDIO_SYSTEM_NAME)) {
			P.FINAL_GAIN_FACTOR = 1.1f; // JavaSound: limiter handles peaks, no scaledry boost needed
		}
		log.info("Configuring audio client with {}", P.AUDIO_SYSTEM_NAME);
		AudioServerProvider provider = null;
		for (AudioServerProvider p : ServiceLoader.load(AudioServerProvider.class)) {
			if (P.AUDIO_SYSTEM_NAME.equals(p.getLibraryName())) {
				provider = p;
				break;
			}
		}
		if (provider == null) {
			throw new NullPointerException(
					"No AudioServer found that matches : " + P.AUDIO_SYSTEM_NAME);
		}

		client = new SynthPiAudioClient();

		// JSAudioServerProvider.findOutputDevice() searches the AudioConfiguration
		// extensions for a Device instance. JSDevice is package-private, so we
		// retrieve it via the provider's own findAll() rather than constructing one.
		Device selectedDevice = null;
		if ("JavaSound".equals(P.AUDIO_SYSTEM_NAME) && P.AUDIO_DEVICE_NAME != null && !P.AUDIO_DEVICE_NAME.isEmpty()) {
			for (Device d : provider.findAll(Device.class)) {
				if (P.AUDIO_DEVICE_NAME.equals(d.getName())) {
					selectedDevice = d;
					break;
				}
			}
			if (selectedDevice == null) {
				log.warn("Audio device '{}' not found, using default", P.AUDIO_DEVICE_NAME);
			} else {
				log.info("Using audio device: {}", selectedDevice.getName());
			}
		}

		Object[] extensions = selectedDevice != null
			? new Object[] { true, new ClientID("SynthPi"), selectedDevice, Connections.OUTPUT, JSTimingMode.Estimated }
			: new Object[] { true, new ClientID("SynthPi"), Connections.OUTPUT, JSTimingMode.Estimated };

		AudioConfiguration config = new AudioConfiguration((float)P.SAMPLE_RATE_HZ,
			0, // input channels
			2, // output channels
			P.SAMPLE_BUFFER_SIZE, // buffer size
			extensions
		);

		server = provider.createServer(config, client);

		Thread runner = new Thread(new Runnable() {
			public void run() {
				try {
					server.run();
				} catch (Exception ex) {
					log.error("Error while running audio server", ex);
				}
			}
		}, "AudioServerRunner");
		runner.setPriority(Thread.MAX_PRIORITY);
		log.info("Starting audio ...");
		runner.start();
	}

	public static List<String> getAvailableOutputDeviceNames() {
		List<String> names = new ArrayList<>();
		AudioFormat fmt = new AudioFormat(P.SAMPLE_RATE_HZ, 16, 2, true, false);
		DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, fmt);
		for (Mixer.Info info : AudioSystem.getMixerInfo()) {
			if (AudioSystem.getMixer(info).isLineSupported(lineInfo)) {
				names.add(info.getName());
			}
		}
		return names;
	}

	public static void shutdownAudio() {
		if (server!=null) {
			server.shutdown();
			log.info("Audio connection terminated.");
		}
	}

	private ISynth synthengine;
	
	public SynthPiAudioClient() {
		
	}
	
	@Override
	public void configure(AudioConfiguration context) throws Exception {
		log.info("Audio configured:\n{}", context);
		P.SAMPLE_BUFFER_SIZE = context.getMaxBufferSize();
		P.SAMPLE_RATE_HZ = context.getSampleRate();
		P.MILLIS_PER_SAMPLE_FRAME = 1000f/P.SAMPLE_RATE_HZ;
		bufferTimeNS = (long)(((double) context.getMaxBufferSize() / context.getSampleRate()) * 1e9);
		WaveTables.init();
		LFO.init();
		synthengine = new AnalogSynth();
		if (P.MIDI_FILE_NAME!=null) {
			MidiPlayback.INSTANCE.playMIDI(P.MIDI_FILE_NAME);
		}
		else {
			MidiPlayback.INSTANCE.stopMIDI();
		}
		// JIT warmup: drive the entire audio path through silent buffers so hot
		// methods reach C1/C2 before the real-time deadline starts.
		log.info("JIT warmup...");
		FloatBuffer wL = FloatBuffer.allocate(P.SAMPLE_BUFFER_SIZE);
		FloatBuffer wR = FloatBuffer.allocate(P.SAMPLE_BUFFER_SIZE);
		List<FloatBuffer> warmupBuffers = new ArrayList<>(2);
		warmupBuffers.add(wL);
		warmupBuffers.add(wR);
		for (int i = 0; i < 500; i++) {
			wL.rewind();
			wR.rewind();
			P.interpolate();
			synthengine.process(warmupBuffers, P.SAMPLE_BUFFER_SIZE);
		}
		log.info("JIT warmup complete.");
		SynthPi.uiMessage("Audio system: "+P.AUDIO_SYSTEM_NAME);
		SynthPi.uiMessage("Audio buffer size: "+P.SAMPLE_BUFFER_SIZE);
		SynthPi.uiMessage("Sample rate: "+(int)P.SAMPLE_RATE_HZ+" Hz");
		SynthPi.uiMessage("MIDI channel: "+(P.MIDI_CHANNEL+1));
		SynthPi.uiMessage("SynthPi is up and running!");
		log.info("Synth is up and running!");
	}

	public static volatile float LOAD = 0;
	public static volatile int MISSED_DEADLINES = 0;
	private static long bufferTimeNS = 0;
	private long lastCallbackNanos = 0;
	
	@Override
	public boolean process(long time, List<FloatBuffer> inputs, List<FloatBuffer> outputs, int nframes) {
		final long nowNanos = System.nanoTime();
		if (lastCallbackNanos > 0 && (nowNanos - lastCallbackNanos) > bufferTimeNS * 3 / 2) {
			MISSED_DEADLINES++;
		}
		lastCallbackNanos = nowNanos;
		if (synthengine!=null) {
			try {
				long startnanos = System.nanoTime();
				P.interpolate();
				synthengine.process(outputs, nframes);
				LOAD = (System.nanoTime()-startnanos)/(float)bufferTimeNS;
			}
			catch (Exception e) {
				log.error("Error processing", e);
				return true;
			}
		}
		return true;
	}

	@Override
	public void shutdown() {
	}
	
	private static final Logger log = LoggerFactory.getLogger(SynthPiAudioClient.class);
}
