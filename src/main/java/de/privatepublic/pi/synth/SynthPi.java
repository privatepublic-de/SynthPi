package de.privatepublic.pi.synth;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;
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
	private static String[] MAIN_ARGS = new String[0];

	public static void main(String[] args) {
		MAIN_ARGS = args;

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
			PresetHandler.loadRecentPatch();
			MidiHandler.init();
			SynthPiAudioClient.start();
			JettyWebServerInterface.init();
			if (window!=null) {
			Timer timer = new Timer("LoadWatch", true);
			timer.schedule(new TimerTask() {
				private float[] recent = new float[5];
				private int pos = 0;
				private int lastMissed = 0;
				private int missDecayTicks = 0;
				@Override
				public void run() {
					recent[pos] = SynthPiAudioClient.LOAD;
					window.setLoad(0.2f*(recent[0]+recent[1]+recent[2]+recent[3]+recent[4]));
					int missed = SynthPiAudioClient.MISSED_DEADLINES;
					if (missed != lastMissed) { missDecayTicks = 10; lastMissed = missed; }
					if (missDecayTicks > 0) missDecayTicks--;
					window.setJitter(missDecayTicks > 0);
					pos = ++pos%5;
				}}, 1000, 50);
			}
		} catch (Exception e1) {
			logger.error(e1.getMessage(),e1);
		}
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() {
		    	PresetHandler.saveCurrentPatch();
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
		        		window.appendMessage("> "+qmsg+ "\n");
			        }
			    });
			}
			EventQueue.invokeLater(new Runnable() {
		        public void run() {
		        	window.appendMessage("> "+message+ "\n");
		        }
		    });
		}
		else {
			queuedMessages.add(message);
		}
	}
	
	public static void scheduleRestart() {
		Thread t = new Thread(() -> {
			try {
				Thread.sleep(300); // let the /restarting message reach clients
				List<String> command = buildRestartCommand();
				logger.info("Restarting: {}", String.join(" ", command));
				new ProcessBuilder(command).inheritIO().start();
				Thread.sleep(100);
			} catch (Exception e) {
				logger.error("Restart failed", e);
			}
			System.exit(0);
		}, "restart-thread");
		t.setDaemon(true);
		t.start();
	}

	private static List<String> buildRestartCommand() throws URISyntaxException {
		String javaExe = ProcessHandle.current().info().command()
			.orElse(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath());

		List<String> cmd = new ArrayList<>();
		cmd.add(javaExe);
		// preserve JVM flags but strip the JDWP debug agent — when launched from an
		// IDE it has suspend=y which would cause the restarted process to hang waiting
		// for a debugger connection that never comes
		for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
			if (!arg.contains("jdwp") && !arg.contains("dt_socket") && !arg.contains("dt_shmem")) {
				cmd.add(arg);
			}
		}
		// detect fat-jar vs classpath launch
		File src = new File(SynthPi.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		if (src.isFile() && src.getName().endsWith(".jar")) {
			cmd.add("-jar");
			cmd.add(src.getAbsolutePath());
		} else {
			cmd.add("-cp");
			cmd.add(ManagementFactory.getRuntimeMXBean().getClassPath());
			cmd.add(SynthPi.class.getName());
		}
		Collections.addAll(cmd, MAIN_ARGS);
		if (!cmd.contains("-disablebrowserstart")) {
			cmd.add("-disablebrowserstart");
		}
		return cmd;
	}

	private static void parseCommandLine(String[] args) {

		// create the command line parser
		CommandLineParser parser = new DefaultParser();

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
		final String ARG_OPEN_BROWSER_COMMAND = "openbrowsercmd";
		final String ARG_LOW_BUDGET_ADDITIVE = "lowbudgetadditive";
		Options options = new Options();
		options.addOption(Option.builder(ARG_HELP).desc("Print this help message and exit").get());
		options.addOption(Option.builder(ARG_MIDI_CHANNEL).argName("channel").hasArg().desc("MIDI channel number (1-16, default 1)").get());
		options.addOption(Option.builder(ARG_PORT_HTTP).argName("port").hasArg().desc("Port number for the included web server (1024-65535, default "+P.PORT_HTTP+")").get());
		options.addOption(Option.builder(ARG_MIDI_FILENAME).argName("file").hasArg().desc("MIDI file to play in loop. Good for testing.").get());
		options.addOption(Option.builder(ARG_PITCH_BEND_MODE).argName("0/1").hasArg().desc("Set specific MIDI pitch bend mode. Try this if strange pitch bending effects occur; e.g. on Mac OS systems use 0.").get());
		options.addOption(Option.builder(ARG_DISABLE_WEB_CACHE).desc("(only for development)").get());
		options.addOption(Option.builder(ARG_DISABLE_BROWSER_START).desc("Don't start web browser on launch").get());
		options.addOption(Option.builder(ARG_HEADLESS).desc("Don't open user interface window").get());
		options.addOption(Option.builder(ARG_USE_JACK_AUDIO_SERVER).desc("Use JACK audio server for playback. Fails if JACK isn't installed and started.").get());
		options.addOption(Option.builder(ARG_LOW_BUDGET_ADDITIVE).desc("Low budget additive (more description please).").get());
		options.addOption(Option.builder(ARG_AUDIO_BUFFER_SIZE).argName("size").hasArg().desc("Playback audio buffer size. Smaller values for less latency, higher values for less drop-outs and crackles (default 64)").get());
		options.addOption(Option.builder(ARG_OPEN_BROWSER_COMMAND).argName("cmd").hasArg().desc("Command line to open web browser.").get());
		try {
			CommandLine commandline = parser.parse(options, args);
			if (commandline.hasOption(ARG_HELP)) {
				try {
					HelpFormatter.builder().get().printHelp("java -jar SynthPi.jar", "", options, "", false);
				} catch (IOException e) {
					logger.error("Error printing help", e);
				}
				System.exit(0);
			}
			
			HEADLESS = commandline.hasOption(ARG_HEADLESS);
			
			if (commandline.hasOption(ARG_USE_JACK_AUDIO_SERVER)) {
				P.AUDIO_SYSTEM_NAME = "JACK";
			}
			
			P.LOW_BUDGET_ADDITIVE = commandline.hasOption(ARG_LOW_BUDGET_ADDITIVE);
			
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
			
			P.CUSTOM_BROWSER_COMMAND = commandline.getOptionValue(ARG_OPEN_BROWSER_COMMAND);
			
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
				Integer result = Integer.valueOf(commandline.getOptionValue(optionKey));
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
