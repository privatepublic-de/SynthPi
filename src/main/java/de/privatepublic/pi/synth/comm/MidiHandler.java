package de.privatepublic.pi.synth.comm;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.FancyParam;
import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.PresetHandler;
import de.privatepublic.pi.synth.SynthPi;

public class MidiHandler {

	private static final Logger log = LoggerFactory.getLogger(MidiHandler.class);
	public static MidiHandler INSTANCE;

	// -------------------------------------------------------------------------
	// Port configuration DTO — shared between MidiHandler and PresetHandler

	public static class PortConfig {
		public final String key;
		public final String name;
		public final String vendor;
		public boolean enabled;
		public int channel; // 0 = all channels, 1–16 = specific channel
		public boolean available; // runtime only, not persisted to disk

		public PortConfig(String key, String name, String vendor, boolean enabled, int channel) {
			this.key = key;
			this.name = name;
			this.vendor = vendor;
			this.enabled = enabled;
			this.channel = channel;
		}

		/** Stable key derived from device name + vendor (no unique OS-level ID in JavaSound). */
		public static String makeKey(MidiDevice.Info info) {
			String v = info.getVendor() != null ? info.getVendor().trim() : "";
			return v.isEmpty() ? info.getName() : info.getName() + " [" + v + "]";
		}
	}

	/** Populated by PresetHandler.updateSettings() before MidiHandler.init() runs. */
	public static List<PortConfig> SAVED_PORT_CONFIGS = new ArrayList<>();

	// -------------------------------------------------------------------------

	public static void init() {
		INSTANCE = new MidiHandler();
	}

	public static void registerReceiver(IMidiNoteReceiver receiver) {
		if (!noteReceivers.contains(receiver)) {
			noteReceivers.add(receiver);
		}
	}

	public static void removeReceiver(IMidiNoteReceiver receiver) {
		noteReceivers.remove(receiver);
	}

	public static void registerReceiver(IPitchBendReceiver receiver) {
		if (!pitchbendReceivers.contains(receiver)) {
			pitchbendReceivers.add(receiver);
		}
	}

	public static void removeReceiver(IPitchBendReceiver receiver) {
		pitchbendReceivers.remove(receiver);
	}

	public static void sendPitchBendNotification() {
		for (IPitchBendReceiver rc : pitchbendReceivers) {
			rc.onPitchBend();
		}
	}

	private static List<IMidiNoteReceiver> noteReceivers = new ArrayList<>();
	private static List<IPitchBendReceiver> pitchbendReceivers = new ArrayList<>();
	public static Receiver s_receiver;

	private volatile int detectedDeviceCount = 0;
	private final Map<String, MidiDevice> openedDevices = new LinkedHashMap<>();
	private final Map<String, Transmitter> openedTransmitters = new LinkedHashMap<>();
	private final Map<String, PortConfig> portConfigs = new LinkedHashMap<>();

	private boolean isLearnMode = false;
	private int learnParameterIndex = 0;
	private int param_selected = -1;

	private MidiHandler() {
		log.info("Initializing MIDI handler");
		s_receiver = new MidiInputFilter();

		for (PortConfig cfg : SAVED_PORT_CONFIGS) {
			portConfigs.put(cfg.key, cfg);
		}

		Timer timer = new Timer("MIDIWatcher", true);
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
				if (infos.length == detectedDeviceCount) return;
				log.info("MIDI device change. Scanning devices...");

				Set<String> currentKeys = new HashSet<>();
				for (MidiDevice.Info info : infos) {
					try {
						MidiDevice device = MidiSystem.getMidiDevice(info);
						if (device.getMaxTransmitters() == 0) continue;
						String key = PortConfig.makeKey(info);
						currentKeys.add(key);
						synchronized (MidiHandler.this) {
							if (!openedDevices.containsKey(key)) {
								PortConfig cfg = portConfigs.get(key);
								if (cfg != null && cfg.enabled) {
									openDevice(key, device);
								}
							}
						}
					} catch (MidiUnavailableException e) { /* skip */ }
				}

				synchronized (MidiHandler.this) {
					List<String> removed = new ArrayList<>();
					for (String key : openedDevices.keySet()) {
						if (!currentKeys.contains(key)) {
							removed.add(key);
						}
					}
					for (String key : removed) {
						closeDevice(key);
					}
				}
				detectedDeviceCount = infos.length;
			}
		}, 0, 5000);
	}

	// Must be called with this monitor held.
	private void openDevice(String key, MidiDevice device) {
		try {
			int channel = portConfigs.containsKey(key) ? portConfigs.get(key).channel : 0;
			device.open();
			Transmitter trans = device.getTransmitter();
			trans.setReceiver(new PortReceiver(channel));
			openedDevices.put(key, device);
			openedTransmitters.put(key, trans);
			log.info("Opened MIDI device: {}", device.getDeviceInfo());
			SynthPi.uiMessage("MIDI device opened: " + device.getDeviceInfo());
		} catch (MidiUnavailableException e) {
			log.warn("Could not open MIDI device {}: {}", key, e.getMessage());
		}
	}

	// Must be called with this monitor held.
	private void closeDevice(String key) {
		Transmitter trans = openedTransmitters.remove(key);
		if (trans != null) {
			try { trans.close(); } catch (Exception e) { /* ignore */ }
		}
		MidiDevice device = openedDevices.remove(key);
		if (device != null) {
			try { device.close(); } catch (Exception e) { /* ignore */ }
			log.info("Closed MIDI device: {}", key);
			SynthPi.uiMessage("MIDI device closed: " + key);
		}
	}

	/**
	 * Returns a merged list of saved port configs (with available flag) plus any
	 * currently detected devices that don't yet have a saved config entry.
	 * Called by PresetHandler.settingsJSONObject() to populate the UI.
	 */
	public List<PortConfig> getPortInfos() {
		// Build live device key set first (outside the lock; MidiSystem call can be slow).
		Set<String> liveKeys = new HashSet<>();
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		for (MidiDevice.Info info : infos) {
			try {
				MidiDevice device = MidiSystem.getMidiDevice(info);
				if (device.getMaxTransmitters() != 0) {
					liveKeys.add(PortConfig.makeKey(info));
				}
			} catch (MidiUnavailableException e) { /* skip */ }
		}

		List<PortConfig> result = new ArrayList<>();
		Set<String> savedKeys = new HashSet<>();
		synchronized (this) {
			for (PortConfig cfg : portConfigs.values()) {
				PortConfig copy = new PortConfig(cfg.key, cfg.name, cfg.vendor, cfg.enabled, cfg.channel);
				copy.available = liveKeys.contains(cfg.key);
				result.add(copy);
				savedKeys.add(cfg.key);
			}
		}
		// Append newly detected devices not yet in saved configs.
		for (MidiDevice.Info info : infos) {
			String key = PortConfig.makeKey(info);
			if (savedKeys.contains(key) || !liveKeys.contains(key)) continue;
			PortConfig cfg = new PortConfig(key, info.getName(),
					info.getVendor() != null ? info.getVendor().trim() : "",
					false, 0);
			cfg.available = true;
			result.add(cfg);
		}
		return result;
	}

	/**
	 * Applies a new port configuration received from the UI.
	 * Opens newly-enabled devices, closes newly-disabled ones.
	 * Called by PresetHandler.updateSettings() at runtime (after init).
	 */
	public synchronized void applyPortConfig(List<PortConfig> configs) {
		portConfigs.clear();
		for (PortConfig cfg : configs) {
			portConfigs.put(cfg.key, cfg);
		}

		Map<String, MidiDevice> available = new LinkedHashMap<>();
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		for (MidiDevice.Info info : infos) {
			try {
				MidiDevice device = MidiSystem.getMidiDevice(info);
				if (device.getMaxTransmitters() != 0) {
					available.put(PortConfig.makeKey(info), device);
				}
			} catch (MidiUnavailableException e) { /* skip */ }
		}

		for (PortConfig cfg : portConfigs.values()) {
			if (cfg.enabled && !openedDevices.containsKey(cfg.key) && available.containsKey(cfg.key)) {
				openDevice(cfg.key, available.get(cfg.key));
			} else if (!cfg.enabled && openedDevices.containsKey(cfg.key)) {
				closeDevice(cfg.key);
			}
		}

		// Force the timer to re-scan on its next tick so detectedDeviceCount stays accurate.
		detectedDeviceCount = -1;
	}

	public void sendNote(int number, boolean on) {
		try {
			ShortMessage msg = new ShortMessage(on ? ShortMessage.NOTE_ON : ShortMessage.NOTE_OFF, P.MIDI_CHANNEL, number, 96);
			s_receiver.send(msg, 0);
		} catch (InvalidMidiDataException e) {
			// can't happen with valid note numbers
		}
	}

	public void sendSustainPedal(boolean on) {
		try {
			ShortMessage msg = new ShortMessage(ShortMessage.CONTROL_CHANGE, P.MIDI_CHANNEL, 64, on ? 127 : 0);
			s_receiver.send(msg, 0);
		} catch (InvalidMidiDataException e) {
			// can't happen
		}
	}

	public void seqLoopStart() { }
	public void seqLoopEnd() { }

	// -------------------------------------------------------------------------
	// Per-port channel filter — sits between a device's Transmitter and MidiInputFilter

	private class PortReceiver implements Receiver {
		private final int portChannel; // 0 = all, 1–16

		PortReceiver(int portChannel) {
			this.portChannel = portChannel;
		}

		@Override
		public void send(MidiMessage msg, long timeStamp) {
			if (portChannel == 0 || !(msg instanceof ShortMessage)
					|| ((ShortMessage) msg).getChannel() == portChannel - 1) {
				s_receiver.send(msg, timeStamp);
			}
		}

		@Override
		public void close() { }
	}

	// -------------------------------------------------------------------------
	// Core MIDI message handler (channel filtering is now done upstream in PortReceiver)

	private class MidiInputFilter implements Receiver {
		public void send(final MidiMessage msg, final long timeStamp) {
			if (msg instanceof ShortMessage) {
				final ShortMessage smsg = (ShortMessage) msg;
				int command = smsg.getCommand();
				final int data1 = smsg.getData1();
				final int data2 = smsg.getData2();
				if (command == ShortMessage.NOTE_ON || command == ShortMessage.NOTE_OFF) {
					if (command == ShortMessage.NOTE_ON && data2 == 0) {
						command = ShortMessage.NOTE_OFF;
					}
					for (IMidiNoteReceiver rc : noteReceivers) {
						rc.onMidiNoteMessage(smsg, command, data1, data2, timeStamp);
					}
				} else if (command == ShortMessage.CONTROL_CHANGE) {
					if (isLearnMode) {
						storeLearnedCCNumber(data1);
					}
					if (data1 == CC_PARAM_SELECT) {
						param_selected = CC_PARAM_MAP[Math.min(data2, CC_PARAM_MAP.length - 1)];
						ControlMessageDispatcher.INSTANCE.updateSelectedParam(param_selected);
					} else if (data1 == CC_PARAM_VALUE && param_selected >= 0) {
						P.setFromMIDI(param_selected, data2);
						ControlMessageDispatcher.INSTANCE.update(param_selected);
						ControlMessageDispatcher.INSTANCE.updateSelectedParam(param_selected);
					} else {
						P.setFromMIDI(INDEX_OF_MIDI_CC[data1], data2);
						if (data1 == CC_OSC2_DETUNE || data1 == CC_OSC2_DETUNE_FINE) {
							sendPitchBendNotification();
						}
						if (data1 == CC_SUSTAIN) {
							boolean old = P.PEDAL;
							P.PEDAL = data2 > 63;
							if (old && !P.PEDAL) {
								for (IMidiNoteReceiver rc : noteReceivers) {
									rc.onPedalUp();
								}
							}
							if (!old && P.PEDAL) {
								for (IMidiNoteReceiver rc : noteReceivers) {
									rc.onPedalDown();
								}
							}
						}
						if (data1 != CC_MOD_WHEEL || P.HTTP_SEND_PERFORMACE_DATA) {
							updateStatus(data1);
						}
					}
				} else if (command == ShortMessage.PITCH_BEND) {
					int val = (data1 + (data2 << 7)) - 8192;
					if (P.FIX_STRANGE_MIDI_PITCH_BEND) {
						val = (data1 + (data2 << 7));
						if (val > 8191) {
							val = val - 16384;
						}
					}
					final float amount = val / 8192f;
					P.VAL[P.PITCH_BEND] = Math.signum(amount) * (amount * amount);
					P.VAL_RAW_MIDI[P.PITCH_BEND] = val + 8192;
					P.PITCH_BEND_FACTOR = (float) (Math.pow(2d, (P.BEND_RANGE_CENTS * P.VAL[P.PITCH_BEND]) / P.OCTAVE_CENTS));
					sendPitchBendNotification();
				} else if (command == ShortMessage.PROGRAM_CHANGE) {
					log.debug("Program change to {}", data1);
					PresetHandler.loadPatchFromProgramNumber(data1);
					ControlMessageDispatcher.INSTANCE.updateAllParams();
				} else if (command == ShortMessage.CHANNEL_PRESSURE) {
					P.CHANNEL_PRESSURE_TARGET = data1 / 127f;
				}
			}
		}

		public void close() { }

		private void updateStatus(int ccNumber) {
			ControlMessageDispatcher.INSTANCE.update(INDEX_OF_MIDI_CC[ccNumber]);
		}
	}


	private static final int CC_MOD_WHEEL = 1;
	private static final int CC_SUSTAIN = 64;

	// Controller numbers for iControl-32 default mapping
	private static final int CC_VOLUME = 7;
	private static final int CC_FILTER_FREQ = 73;
	private static final int CC_FILTER_RESONANCE = 9;
	private static final int CC_FILTER_ENV_DEPTH = 10;
	private static final int CC_FILTER_TYPE = 72;

	private static final int CC_FILTER_ENV_A = 79;
	private static final int CC_FILTER_ENV_D = 78;
	private static final int CC_FILTER_ENV_S = 26;
	private static final int CC_FILTER_ENV_R = 27;

	private static final int CC_OSC1_WAVE = 14;
	private static final int CC_OSC2_WAVE = 15;
	private static int CC_OSC2_DETUNE = 16;
	public static final int CC_OSC2_DETUNE_VALUE_RANGE = 127;
	private static final int CC_OSC_NOISE_LEVEL = 20;
	private static int CC_OSC2_DETUNE_FINE = 17;

	private static final int CC_AMP_ENV_A = 74;
	private static final int CC_AMP_ENV_D = 71;
	private static final int CC_AMP_ENV_S = 18;
	private static final int CC_AMP_ENV_R = 107;

	private static final int CC_AMP_OSC_MIX = 28;

	private static final int CC_SET_OSC_SYNC = 108;
	private static final int CC_SET_OSC_MONO = 109;
	private static final int CC_SET_AMP_VEL = 110;
	private static final int CC_SET_FILTER1_VEL = 111;

	private static final int CC_MOD_RATE = 19;
	private static final int CC_MOD_DEPTH_FILTER1 = 22;
	private static final int CC_MOD_DEPTH_PITCH = 25;

	private static final int CC_OSC_RINGMOD = 70;
	private static final int CC_OSC_PORTAMENTO_TIME = 5;

	public static int CC_PARAM_SELECT = 26;
	public static int CC_PARAM_VALUE = 27;
	private static final int[] CC_PARAM_MAP = new int[] {
		P.UNUSED,
		P.OSC_MODE,
		P.OSC1_WAVE,
		P.OSC1_WAVE_SET,
		P.OSC2_WAVE,
		P.OSC2_WAVE_SET,
		P.OSC_1_2_MIX,
		P.OSC_NOISE_LEVEL,
		P.OSC2_TUNING,
		P.OSC2_TUNING_FINE,
		P.OSC_GAIN,
		P.OSC2_SYNC,
		P.OSC2_AM,
		P.OSC_GLIDE_RATE,
		P.OSC_MONO,

		P.FILTER_PARALLEL,
		P.FILTER_PARALLEL_MIX,

		P.FILTER1_ON,
		P.FILTER1_TRACK_KEYBOARD,
		P.FILTER1_FREQ,
		P.FILTER1_RESONANCE,
		P.FILTER1_TYPE,
		P.FILTER1_ENV_A,
		P.FILTER1_ENV_D,
		P.FILTER1_ENV_S,
		P.FILTER1_ENV_R,
		P.FILTER1_ENV_DEPTH,
		P.FILTER1_ENV_VELOCITY_SENS,
		P.FILTER1_ENV_LOOP,
		P.FILTER1_OVERLOAD,

		P.FILTER2_ON,
		P.FILTER2_TRACK_KEYBOARD,
		P.FILTER2_FREQ,
		P.FILTER2_RESONANCE,
		P.FILTER2_TYPE,
		P.FILTER2_ENV_A,
		P.FILTER2_ENV_D,
		P.FILTER2_ENV_S,
		P.FILTER2_ENV_R,
		P.FILTER2_ENV_DEPTH,
		P.FILTER2_ENV_VELOCITY_SENS,
		P.FILTER2_ENV_LOOP,
		P.FILTER2_OVERLOAD,

		P.MOD_RATE,
		P.MOD_AMOUNT_BASE,
		P.MOD_PITCH_AMOUNT,
		P.MOD_PITCH2_AMOUNT,
		P.MOD_WAVE1_AMOUNT,
		P.MOD_WAVE2_AMOUNT,
		P.MOD_FILTER1_AMOUNT,
		P.MOD_FILTER2_AMOUNT,
		P.MOD_VOL_AMOUNT,
		P.MOD_LFO_TYPE,
		P.MOD_LFO_RESET,
		P.MOD_ENV1_A,
		P.MOD_ENV1_D,
		P.MOD_ENV1_S,
		P.MOD_ENV1_R,
		P.MOD_ENV1_LOOP,
		P.MOD_ENV1_PITCH_AMOUNT,
		P.MOD_ENV1_PITCH2_AMOUNT,
		P.MOD_ENV1_WAVE_AMOUNT,
		P.MOD_ENV1_NOISE_AMOUNT,
		P.MOD_ENV1_AM_AMOUNT,

		P.AMP_ENV_A,
		P.AMP_ENV_D,
		P.AMP_ENV_S,
		P.AMP_ENV_R,
		P.AMP_ENV_VELOCITY_SENS,
		P.AMP_ENV_LOOP,
		P.OVERDRIVE,
		P.CHORUS_DEPTH,
		P.SPREAD,
		P.DELAY_WET,
		P.DELAY_RATE,
		P.DELAY_FEEDBACK,
		P.REVERB_ONE_KNOB,
		P.VOLUME
	};

	private static final int[] INDEX_OF_MIDI_CC = new int[128];
	static {
		INDEX_OF_MIDI_CC[CC_VOLUME] = P.VOLUME;
		INDEX_OF_MIDI_CC[CC_MOD_WHEEL] = P.MOD_WHEEL;
		INDEX_OF_MIDI_CC[CC_FILTER_FREQ] = P.FILTER1_FREQ;
		INDEX_OF_MIDI_CC[CC_FILTER_RESONANCE] = P.FILTER1_RESONANCE;
		INDEX_OF_MIDI_CC[CC_FILTER_TYPE] = P.FILTER1_TYPE;
		INDEX_OF_MIDI_CC[CC_FILTER_ENV_A] = P.FILTER1_ENV_A;
		INDEX_OF_MIDI_CC[CC_FILTER_ENV_D] = P.FILTER1_ENV_D;
		INDEX_OF_MIDI_CC[CC_FILTER_ENV_S] = P.FILTER1_ENV_S;
		INDEX_OF_MIDI_CC[CC_FILTER_ENV_R] = P.FILTER1_ENV_R;
		INDEX_OF_MIDI_CC[CC_FILTER_ENV_DEPTH] = P.FILTER1_ENV_DEPTH;
		INDEX_OF_MIDI_CC[CC_OSC1_WAVE] = P.OSC1_WAVE;
		INDEX_OF_MIDI_CC[CC_OSC2_WAVE] = P.OSC2_WAVE;
		INDEX_OF_MIDI_CC[CC_OSC2_DETUNE] = P.OSC2_TUNING;
		INDEX_OF_MIDI_CC[CC_OSC_NOISE_LEVEL] = P.OSC_NOISE_LEVEL;
		INDEX_OF_MIDI_CC[CC_AMP_ENV_A] = P.AMP_ENV_A;
		INDEX_OF_MIDI_CC[CC_AMP_ENV_D] = P.AMP_ENV_D;
		INDEX_OF_MIDI_CC[CC_AMP_ENV_S] = P.AMP_ENV_S;
		INDEX_OF_MIDI_CC[CC_AMP_ENV_R] = P.AMP_ENV_R;
		INDEX_OF_MIDI_CC[CC_AMP_OSC_MIX] = P.OSC_1_2_MIX;
		INDEX_OF_MIDI_CC[CC_OSC2_DETUNE_FINE] = P.OSC2_TUNING_FINE;
		INDEX_OF_MIDI_CC[CC_OSC_RINGMOD] = P.OSC2_AM;
		INDEX_OF_MIDI_CC[CC_OSC_PORTAMENTO_TIME] = P.OSC_GLIDE_RATE;
		INDEX_OF_MIDI_CC[CC_MOD_RATE] = P.MOD_RATE;
		INDEX_OF_MIDI_CC[CC_MOD_DEPTH_FILTER1] = P.MOD_FILTER1_AMOUNT;
		INDEX_OF_MIDI_CC[CC_MOD_DEPTH_PITCH] = P.MOD_PITCH_AMOUNT;
		INDEX_OF_MIDI_CC[CC_SET_OSC_SYNC] = P.OSC2_SYNC;
		INDEX_OF_MIDI_CC[CC_SET_OSC_MONO] = P.OSC_MONO;
		INDEX_OF_MIDI_CC[CC_SET_AMP_VEL] = P.AMP_ENV_VELOCITY_SENS;
		INDEX_OF_MIDI_CC[CC_SET_FILTER1_VEL] = P.FILTER1_ENV_VELOCITY_SENS;

		InputStream in = MidiHandler.class.getResourceAsStream("/midimaps/default.map");
		try {
			List<String> lines = IOUtils.readLines(in, "utf-8");
			for (String line : lines) {
				if (line.startsWith("#")) {
					if (line.startsWith("##")) {
						log.info("Loading MIDI CC map: {}", line);
					}
				} else {
					String[] parts = line.split("=");
					try {
						if (parts.length == 2) {
							String key = parts[0].trim();
							int ccno = Integer.parseInt(parts[1].trim());
							Field f = P.class.getField(key);
							Class<?> t = f.getType();
							if (t == int.class) {
								int pindex = f.getInt(null);
								INDEX_OF_MIDI_CC[ccno] = pindex;
								if (pindex == P.OSC2_TUNING) {
									CC_OSC2_DETUNE = ccno;
								} else if (pindex == P.OSC2_TUNING_FINE) {
									CC_OSC2_DETUNE_FINE = ccno;
								}
							} else {
								throw new Exception("Wrong parameter key error");
							}
						} else {
							throw new Exception("Line format error");
						}
					} catch (Exception e) {
						log.debug("Error reading line: \"{}\", {}", line, e.getMessage());
					}
				}
			}
		} catch (UncheckedIOException e) {
			log.error("Error loading midi parameter mapping - using basic default!");
		}
	}

	public static int[] getMidiMappings() {
		return INDEX_OF_MIDI_CC;
	}

	public void stopLearnMode() {
		if (isLearnMode) {
			isLearnMode = false;
			log.info("MIDI learn mode stopped.");
		}
	}

	public void startLearnMode(String path) {
		int paramIndex = 0;
		for (int i = 0; i < P.PARAM_STORE_SIZE; i++) {
			if (path.equals(P.OSC_PATH[i])) {
				paramIndex = i;
				break;
			}
		}
		if (paramIndex > 0) {
			learnParameterIndex = paramIndex;
			isLearnMode = true;
			log.info("Started MIDI learning for {}", path);
		} else {
			if (path.equals("/option/midicontrol/select")) {
				learnParameterIndex = -1;
				isLearnMode = true;
				log.info("Started MIDI learning for {}", path);
			} else if (path.equals("/option/midicontrol/value")) {
				learnParameterIndex = -2;
				isLearnMode = true;
				log.info("Started MIDI learning for {}", path);
			} else {
				log.warn("No mapping found for path {} to MIDI learn.", path);
			}
		}
	}

	private void storeLearnedCCNumber(int ccNumber) {
		if (isLearnMode) {
			if (learnParameterIndex == -1 && CC_PARAM_SELECT != ccNumber) {
				CC_PARAM_SELECT = ccNumber;
				INDEX_OF_MIDI_CC[ccNumber] = P.UNUSED;
				SynthPi.uiMessage("Mapped MIDI CC#" + ccNumber + " to value select function.");
			} else if (learnParameterIndex == -2 && CC_PARAM_VALUE != ccNumber) {
				CC_PARAM_VALUE = ccNumber;
				INDEX_OF_MIDI_CC[ccNumber] = P.UNUSED;
				SynthPi.uiMessage("Mapped MIDI CC#" + ccNumber + " to value change function.");
			} else if (INDEX_OF_MIDI_CC[ccNumber] != learnParameterIndex) {
				for (int i = 0; i < INDEX_OF_MIDI_CC.length; i++) {
					if (INDEX_OF_MIDI_CC[i] == learnParameterIndex) {
						INDEX_OF_MIDI_CC[i] = P.UNUSED;
					}
				}
				INDEX_OF_MIDI_CC[ccNumber] = learnParameterIndex;
				log.info("Learned MIDI CC #{}", ccNumber);
				SynthPi.uiMessage("Mapped MIDI CC#" + ccNumber + " to " + FancyParam.nameOf(learnParameterIndex));
			}
		} else {
			log.warn("Learn mode not correctly initialised.");
		}
	}
}
