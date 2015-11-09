package de.privatepublic.pi.synth;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Vector;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.comm.web.JettyWebServerInterface;
import de.privatepublic.pi.synth.util.IOUtils;




public class SynthPi {

	static final Logger logger = LoggerFactory.getLogger(SynthPi.class);

	private static AppWindow window;
	private static boolean HEADLESS = false;
	
	public static void main(String[] args) {

		PresetHandler.loadSettings();
		parseCommandLine(args); // command line arguments overwrite local user settings
		
		if (!HEADLESS) {
			try {
				EventQueue.invokeAndWait(new Runnable() {
					public void run() {
						try {
							window = new AppWindow();
						} catch (Exception e) {
							logger.error("Error creating window", e);
						}
					}
				});
			} catch (InvocationTargetException | InterruptedException e1) {
				logger.error("Error creating window", e1);
			}
		}
		
		try {
			MidiHandler.init();
			SynthPiAudioClient.start();
			JettyWebServerInterface.init();
		} catch (Exception e1) {
			logger.error(e1.getMessage(),e1);
		}
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() {
		    	logger.info("Shutting down ...");
		    	SynthPiAudioClient.shutdownAudio();
		    }
		 });
		
	}

	private static List<String> queuedMessages = new Vector<String>();
	
	public static void uiMessage(final String message) {
		if (HEADLESS) {
			return;
		}
		if (window!=null) {
			while (queuedMessages.size()>0) {
				final String qmsg = queuedMessages.remove(0);
				EventQueue.invokeLater(new Runnable() {
			        public void run() {
		        		window.appendMessage("] "+qmsg+ "\n");
			        }
			    });
			}
			EventQueue.invokeLater(new Runnable() {
		        public void run() {
		        	window.appendMessage("] "+message+ "\n");
		        }
		    });
		}
		else {
			queuedMessages.add(message);
		}
	}
	
	@SuppressWarnings("static-access") // prevent warnings because of implementation of commons-cli
	private static void parseCommandLine(String[] args) {
		
		// create the command line parser
		CommandLineParser parser = new BasicParser();

		// create the Options
		final String ARG_HELP = "help";
		final String ARG_MIDI_CHANNEL = "midich";
		final String ARG_PORT_HTTP = "httpport";
		final String ARG_MIDI_FILENAME = "midifile";
		final String ARG_PITCH_BEND_MODE = "pitchbendmode";
		final String ARG_DISABLE_WEB_CACHE = "disablewebcache";
		final String ARG_DISABLE_BROWSER_START = "disablebrowserstart";
		final String ARG_HEADLESS = "headless";
		final String ARG_USE_JACK_AUDIO_SERVER = "usejackaudioserver";
		final String ARG_AUDIO_BUFFER_SIZE = "audiobuffersize";
		Options options = new Options();
		options.addOption(OptionBuilder.withArgName(ARG_HELP).withDescription("Print this help message and exit").create(ARG_HELP));
		options.addOption(OptionBuilder.withArgName("channel").hasArg().withDescription("MIDI channel number (1-16, default 1)").create(ARG_MIDI_CHANNEL));
		options.addOption(OptionBuilder.withArgName("port").hasArg().withDescription("Port number for the included web server (1024-65535, default "+P.PORT_HTTP+")").create(ARG_PORT_HTTP));
		options.addOption(OptionBuilder.withArgName("file").hasArg().withDescription("MIDI file to play in loop. Good for testing.").create(ARG_MIDI_FILENAME));
		options.addOption(OptionBuilder.withArgName("0/1").hasArg().withDescription("Set specific MIDI pitch bend mode. Try this if strange pitch bending effects occur; e.g. on Mac OS systems use 0.").create(ARG_PITCH_BEND_MODE));
		options.addOption(OptionBuilder.withDescription("(only for development)").create(ARG_DISABLE_WEB_CACHE));
		options.addOption(OptionBuilder.withDescription("Don't start web browser on launch").create(ARG_DISABLE_BROWSER_START));
		options.addOption(OptionBuilder.withDescription("Don't open user interface window").create(ARG_HEADLESS));
		options.addOption(OptionBuilder.withDescription("Use JACK audio server for playback. Fails if JACK isn't installed and started.").create(ARG_USE_JACK_AUDIO_SERVER));
		options.addOption(OptionBuilder.withArgName("size").hasArg().withDescription("Playback audio buffer size. Smaller values for less latency, higher values for less drop-outs and crackles (default 64)").create(ARG_AUDIO_BUFFER_SIZE));
		
		try {
			CommandLine commandline = parser.parse(options, args);
			if (commandline.hasOption(ARG_HELP)) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp( "java -jar SynthPi.jar", options);
				System.exit(0);
			}
			
			HEADLESS = commandline.hasOption(ARG_HEADLESS);
			
			if (commandline.hasOption(ARG_USE_JACK_AUDIO_SERVER)) {
				P.AUDIO_SYSTEM_NAME = "JACK";
			}
			
			Integer bufferSize = getOptionInt(commandline, ARG_AUDIO_BUFFER_SIZE, 16, 4096);
			if (bufferSize!=null) {
				P.SAMPLE_BUFFER_SIZE = bufferSize;
			}
			
			JettyWebServerInterface.DISABLE_CACHING = commandline.hasOption(ARG_DISABLE_WEB_CACHE);
			JettyWebServerInterface.DISABLE_BROWSER_START = commandline.hasOption(ARG_DISABLE_BROWSER_START);
			
			Integer pitchBendMode = getOptionInt(commandline, ARG_PITCH_BEND_MODE, 0, 1);
			if (pitchBendMode!=null) {
				P.FIX_STRANGE_MIDI_PITCH_BEND = pitchBendMode==1;
			}
			
			Integer midiChannel = getOptionInt(commandline, ARG_MIDI_CHANNEL, 1, 16);
			if (midiChannel!=null) {
				P.MIDI_CHANNEL = midiChannel-1;
			}
			Integer httpPort = getOptionInt(commandline, ARG_PORT_HTTP, 1024, 65535);
			if (httpPort!=null) {
				P.PORT_HTTP = httpPort;
			}
			String midifile = commandline.getOptionValue(ARG_MIDI_FILENAME);
			logger.info("Configuration:");
			logger.info("  Audio system: {}", P.AUDIO_SYSTEM_NAME);
			logger.info("  Audio buffer size: {}", P.SAMPLE_BUFFER_SIZE);
			logger.info("  MIDI channel: {}", P.MIDI_CHANNEL+1);
			logger.info("  MIDI pitch bend mode: {}", P.FIX_STRANGE_MIDI_PITCH_BEND?0:1);
			if (midifile!=null) {
				P.MIDI_FILE_NAME = midifile;
				logger.info("  MIDI file to play: {}", P.MIDI_FILE_NAME);
			}
			logger.info("  Web interface: http://{}:{}", IOUtils.localV4InetAddressDisplayString(), P.PORT_HTTP);
			logger.info("  Start web browser: {}", JettyWebServerInterface.DISABLE_BROWSER_START?"no":"yes");
			if (HEADLESS) {
				logger.info("  Starting HEADLESS without ui window!");	
			}
			if (JettyWebServerInterface.DISABLE_CACHING) {
				logger.info("  Web caching disabled for development");
			}
			if (args.length>0) {
				uiMessage("Started with command line parameters: "+StringUtils.join(args, ' '));
			}
			
		} catch (ParseException e) {
			logger.error("Error parsing command line options", e);
			uiMessage("Error reading command line options: "+e.getMessage());
		}
		
	}
	
	
	private static Integer getOptionInt(CommandLine commandline, String optionKey, int min, int max) {
		if (commandline.hasOption(optionKey)) {
			try {
				Integer result = new Integer(commandline.getOptionValue(optionKey));
				if (result<min || result>max) {
					logger.debug("Argument value -{} out of range ({}-{})", optionKey, min, max);	
				}
				else {
					return result;
				}
			} catch (NumberFormatException e) {
				logger.debug("Wrong argument value: -{} {}", optionKey, commandline.getOptionValue(optionKey));
			}
		}
		return null;
	}
	
}
