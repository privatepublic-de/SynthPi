package de.privatepublic.pi.synth;

import java.awt.EventQueue;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
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
import de.privatepublic.pi.synth.comm.lcd.LCD;
import de.privatepublic.pi.synth.comm.web.JettyWebServerInterface;
import de.privatepublic.pi.synth.util.IOUtils;




public class SynthPi {

	static final Logger logger = LoggerFactory.getLogger(SynthPi.class);

	private static AppWindow window;
	private static boolean HEADLESS = false;
	private static boolean USE_SCREENSAVER = false;
	
	public static void main(String[] args) {

		CommandLine cmdline = parseCommandLine(args);
		PresetHandler.initDirectories();
		PresetHandler.loadSettings();
		readCommandLineSettings(cmdline); // command line arguments overwrite local user settings
		
		logger.info("Configuration:");
		logger.info("  Audio system: {}", P.AUDIO_SYSTEM_NAME);
		logger.info("  Audio buffer size: {}", P.SAMPLE_BUFFER_SIZE);
		logger.info("  MIDI channel: {}", P.MIDI_CHANNEL+1);
		logger.info("  MIDI pitch bend mode: {}", P.FIX_STRANGE_MIDI_PITCH_BEND?0:1);
		logger.info("  Web interface: http://{}:{}", IOUtils.localV4InetAddressDisplayString(), P.PORT_HTTP);
		logger.info("  Start web browser: {}", JettyWebServerInterface.DISABLE_BROWSER_START?"no":"yes");
		if (HEADLESS) {
			logger.info("  Starting HEADLESS without ui window!");	
		}
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
					// private float averaged;
					private float[] recent = new float[5];
					private int pos = 0;
					@Override
					public void run() {
						recent[pos] = SynthPiAudioClient.LOAD;
						window.setLoad(0.2f*(recent[0]+recent[1]+recent[2]+recent[3]+recent[4]));
						pos = ++pos%5;
					}}, 1000, 50);
			}
		} catch (Exception e1) {
			logger.error(e1.getMessage(),e1);
		}

		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() {
		    	setScreensaver(false);
		    	LCD.shutdown();
		    	PresetHandler.saveCurrentPatch();
		    	logger.info("Shutting down ...");
		    	SynthPiAudioClient.shutdownAudio();
		    	if (SHUTDOWN_ON_EXIT) {
		    		try {
						SynthPi.uiMessage("Trying to shut down system...");
						logger.info("Trying to shut down!");
						Runtime.getRuntime().exec("sudo shutdown now");
					} catch (IOException e) {
						SynthPi.uiMessage("System shutdown failed!");
						logger.warn("Shutdown failed", e);
					}
		    	}
		    }
		 });
		
	}
	
	public static boolean SHUTDOWN_ON_EXIT = false;

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
	
	
	public static void uiLCDMessage(final String line1, String patchCategory, String patchId) {
		final String line2 = StringUtils.overlay(StringUtils.rightPad(patchCategory, 15), patchId, 15-patchId.length(), 15);
		LCD.message(line1, line2, FancyParam.COLOR_SYSTEM_MESSAGE);
		if (HEADLESS) {
			return;
		}
		if (window!=null) {
			EventQueue.invokeLater(new Runnable() {
		        public void run() {
		        	window.lcdMessage(line1, line2, FancyParam.COLOR_SYSTEM_MESSAGE);
		        }
		    });
		}
	}
	
	public static void uiLCDMessage(final String line1, final String line2) {
		LCD.message(line1, line2, FancyParam.COLOR_SYSTEM_MESSAGE);
		if (HEADLESS) {
			return;
		}
		if (window!=null) {
			EventQueue.invokeLater(new Runnable() {
		        public void run() {
		        	window.lcdMessage(line1, line2, FancyParam.COLOR_SYSTEM_MESSAGE);
		        }
		    });
		}
	}
	
	public static void uiLCDMessage(final int paramindex) {
		if (paramindex==0) {
			return;
		}
		LCD.displayParameter(paramindex);
		if (HEADLESS) {
			return;
		}
		if (window!=null) {
			EventQueue.invokeLater(new Runnable() {
		        public void run() {
		        	window.lcdMessage(FancyParam.nameOf(paramindex), FancyParam.valueOf(paramindex), FancyParam.colorOf(paramindex));
		        }
		    });
		}
	}
	

	public static void setScreensaver(boolean on) {
		if (USE_SCREENSAVER) {
			try {
				if (on) {
					Runtime.getRuntime().exec("/home/pi/display_dark.sh");
				}
				else {
					Runtime.getRuntime().exec("/home/pi/display_bright.sh");
				}
			}
			catch (IOException e) {
				logger.warn("Could not set display brightness", e);
			}
		}
	}

	
	
	final static String ARG_HELP = "help";
	final static String ARG_MIDI_CHANNEL = "midich";
	final static String ARG_PORT_HTTP = "httpport";
	final static String ARG_SETTINGS_DIR = "settingsdir";
	final static String ARG_PITCH_BEND_MODE = "pitchbendmode";
	final static String ARG_DISABLE_WEB_CACHE = "disablewebcache";
	final static String ARG_DISABLE_BROWSER_START = "disablebrowserstart";
	final static String ARG_HEADLESS = "headless";
	final static String ARG_SCREENSAVER = "screensaver";
	final static String ARG_USE_JACK_AUDIO_SERVER = "usejackaudioserver";
	final static String ARG_AUDIO_BUFFER_SIZE = "audiobuffersize";
	final static String ARG_OPEN_BROWSER_COMMAND = "openbrowsercmd";
	final static String ARG_LCD_PORT = "lcdport";
	
	@SuppressWarnings("static-access") // prevent warnings because of implementation of commons-cli
	private static CommandLine parseCommandLine(String[] args) {
		CommandLineParser parser = new BasicParser();
		Options options = new Options();
		options.addOption(OptionBuilder.withArgName(ARG_HELP).withDescription("Print this help message and exit").create(ARG_HELP));
		options.addOption(OptionBuilder.withArgName("channel").hasArg().withDescription("MIDI channel number (1-16, default 1)").create(ARG_MIDI_CHANNEL));
		options.addOption(OptionBuilder.withArgName("port").hasArg().withDescription("Port number for the included web server (1024-65535, default "+P.PORT_HTTP+")").create(ARG_PORT_HTTP));
		options.addOption(OptionBuilder.withArgName("0/1").hasArg().withDescription("Set specific MIDI pitch bend mode. Try this if strange pitch bending effects occur; e.g. on Mac OS systems use 0.").create(ARG_PITCH_BEND_MODE));
		options.addOption(OptionBuilder.withDescription("(only for development)").create(ARG_DISABLE_WEB_CACHE));
		options.addOption(OptionBuilder.withDescription("Don't start web browser on launch").create(ARG_DISABLE_BROWSER_START));
		options.addOption(OptionBuilder.withDescription("Don't open user interface window").create(ARG_HEADLESS));
		options.addOption(OptionBuilder.withDescription("Use screensaver scripts").create(ARG_SCREENSAVER));
		options.addOption(OptionBuilder.withDescription("Use JACK audio server for playback. Fails if JACK isn't installed and started.").create(ARG_USE_JACK_AUDIO_SERVER));
		options.addOption(OptionBuilder.withArgName("size").hasArg().withDescription("Playback audio buffer size. Smaller values for less latency, higher values for less drop-outs and crackles (default 64)").create(ARG_AUDIO_BUFFER_SIZE));
		options.addOption(OptionBuilder.withArgName("cmd").hasArg().withDescription("Command line to open web browser.").create(ARG_OPEN_BROWSER_COMMAND));
		options.addOption(OptionBuilder.withArgName("directory").hasArg().withDescription("Directory to store settings and patches.").create(ARG_SETTINGS_DIR));
		options.addOption(OptionBuilder.withArgName("name").hasArg().withDescription("Name of serial port with LCD display.").create(ARG_LCD_PORT));
		try {
			CommandLine commandline = parser.parse(options, args);
			if (commandline.hasOption(ARG_HELP)) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp( "java -jar SynthPi.jar", options);
				System.exit(0);
			}
			
			HEADLESS = commandline.hasOption(ARG_HEADLESS);
			USE_SCREENSAVER = commandline.hasOption(ARG_SCREENSAVER);
			
			if (commandline.hasOption(ARG_USE_JACK_AUDIO_SERVER)) {
				P.AUDIO_SYSTEM_NAME = "JACK";
			}
			
			
			P.CUSTOM_SETTINGS_DIR = commandline.getOptionValue(ARG_SETTINGS_DIR);
			P.CUSTOM_BROWSER_COMMAND = commandline.getOptionValue(ARG_OPEN_BROWSER_COMMAND);
			
			JettyWebServerInterface.DISABLE_CACHING = commandline.hasOption(ARG_DISABLE_WEB_CACHE);
			JettyWebServerInterface.DISABLE_BROWSER_START = commandline.hasOption(ARG_DISABLE_BROWSER_START);
			
			Integer httpPort = getOptionInt(commandline, ARG_PORT_HTTP, 1024, 65535);
			if (httpPort!=null) {
				P.PORT_HTTP = httpPort;
			}
			
			if (JettyWebServerInterface.DISABLE_CACHING) {
				logger.info("  Web caching disabled for development");
			}
			String lcdPort = commandline.getOptionValue(ARG_LCD_PORT);
			LCD.init(lcdPort!=null?lcdPort:"/dev/cu.usbmodem14341");
			
			if (args.length>0) {
				uiMessage("Started with command line parameters: "+StringUtils.join(args, ' '));
			}
			return commandline;
			
		} catch (ParseException e) {
			logger.error("Error parsing command line options", e);
			uiMessage("Error reading command line options: "+e.getMessage());
		}
		return null;
	}
	
	private static void readCommandLineSettings(CommandLine commandline) {
		if (commandline!=null) {
			Integer pitchBendMode = getOptionInt(commandline, ARG_PITCH_BEND_MODE, 0, 1);
			if (pitchBendMode!=null) {
				P.FIX_STRANGE_MIDI_PITCH_BEND = pitchBendMode==1;
			}
			
			Integer midiChannel = getOptionInt(commandline, ARG_MIDI_CHANNEL, 1, 16);
			if (midiChannel!=null) {
				P.MIDI_CHANNEL = midiChannel-1;
			}
			
			Integer bufferSize = getOptionInt(commandline, ARG_AUDIO_BUFFER_SIZE, 16, 4096);
			if (bufferSize!=null) {
				P.SAMPLE_BUFFER_SIZE = bufferSize;
			}
			
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
