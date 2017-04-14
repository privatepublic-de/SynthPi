package de.privatepublic.pi.synth;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.ServiceLoader;

import org.jaudiolibs.audioservers.AudioClient;
import org.jaudiolibs.audioservers.AudioConfiguration;
import org.jaudiolibs.audioservers.AudioServer;
import org.jaudiolibs.audioservers.AudioServerProvider;
import org.jaudiolibs.audioservers.ext.ClientID;
import org.jaudiolibs.audioservers.ext.Connections;
import org.jaudiolibs.audioservers.javasound.JSTimingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.modules.AnalogSynth;
import de.privatepublic.pi.synth.modules.mod.LFO;

public class SynthPiAudioClient implements AudioClient {
	
	private static SynthPiAudioClient client;
	private static AudioServer server; 
	
	public static void start() throws Exception {
		if ("JavaSound".equals(P.AUDIO_SYSTEM_NAME)) {
			P.FINAL_GAIN_FACTOR = 0.2f; // JavaSound needs more head room
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

		AudioConfiguration config = new AudioConfiguration((float)P.SAMPLE_RATE_HZ, 
			0, // input channels
			2, // output channels
			P.SAMPLE_BUFFER_SIZE, // buffer size
			new Object[] {
				true,
				// extensions
				new ClientID("SynthPi"), Connections.OUTPUT,
				JSTimingMode.Estimated
			}
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

	public static void shutdownAudio() {
		if (server!=null) {
			server.shutdown();
			log.info("Audio connection terminated.");
		}
	}

	private AnalogSynth synthengine;
	
	public SynthPiAudioClient() {
		
	}
	
	@Override
	public void configure(AudioConfiguration context) throws Exception {
		log.info("Audio configured:\n{}", context);
		P.SAMPLE_BUFFER_SIZE = context.getMaxBufferSize();
		P.SAMPLE_RATE_HZ = context.getSampleRate();
		P.CONTROL_BUFFER_SIZE = (int)(P.SAMPLE_RATE_HZ/3000);
		P.MILLIS_PER_SAMPLE_FRAME = 1000f/P.SAMPLE_RATE_HZ;
		P.CONTROL_RATE_HZ = P.SAMPLE_RATE_HZ/P.CONTROL_BUFFER_SIZE;
		P.MILLIS_PER_CONTROL_FRAME = 1000f/P.CONTROL_RATE_HZ;
		bufferTimeNS = (long)(((double) context.getMaxBufferSize() / context.getSampleRate()) * 1e9);
		LFO.init();
		synthengine = new AnalogSynth();
		SynthPi.uiMessage("Audio system: "+P.AUDIO_SYSTEM_NAME);
		SynthPi.uiMessage("Audio buffer size: "+P.SAMPLE_BUFFER_SIZE);
		SynthPi.uiMessage("Sample rate: "+(int)P.SAMPLE_RATE_HZ+" Hz");
		SynthPi.uiMessage("MIDI channel: "+(P.MIDI_CHANNEL+1));
		SynthPi.uiMessage("SynthPi is up and running!");
		log.info("Synth is up and running!");
	}

	public static float LOAD = 0;
	private static long bufferTimeNS = 0;
	
	@Override
	public boolean process(long time, List<FloatBuffer> inputs, List<FloatBuffer> outputs, int nframes) {
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
