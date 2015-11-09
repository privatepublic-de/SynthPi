package de.privatepublic.pi.synth.comm;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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

	public static void init() {
		INSTANCE = new MidiHandler();
	}
	
	public static void registerReceiver(IMidiNoteReceiver receiver) {
		if (!noteReceivers.contains(receiver)) {
			noteReceivers.add(receiver);
//			log.debug("Added note receiver: @", receiver.getClass().getSimpleName());
		}
	}
	
	public static void removeReceiver(IMidiNoteReceiver receiver) {
		noteReceivers.remove(receiver);
//		log.debug("Removed note receiver: @", receiver.getClass().getSimpleName());
	}
	
	public static void registerReceiver(IPitchBendReceiver receiver) {
		if (!pitchbendReceivers.contains(receiver)) {
			pitchbendReceivers.add(receiver);
//			log.debug("Added pitch bend receiver: @", receiver.getClass().getSimpleName());
		}
	}
	
	public static void removeReceiver(IPitchBendReceiver receiver) {
		pitchbendReceivers.remove(receiver);
//		log.debug("Removed pitch bend receiver: @", receiver.getClass().getSimpleName());
	}
	
	public static void sendPitchBendNotification() {
		for (IPitchBendReceiver rc:pitchbendReceivers) {
			rc.onPitchBend();
		}
	}
	
	private static List<IMidiNoteReceiver>noteReceivers = new ArrayList<IMidiNoteReceiver>();
	private static List<IPitchBendReceiver>pitchbendReceivers = new ArrayList<IPitchBendReceiver>();
	public static Receiver s_receiver;
	private boolean receiveAllChannels = false;
	private boolean isLearnMode = false;
	private int learnParameterIndex = 0;
	
	private MidiHandler() {
		log.info("Initializing MIDI handler");
		MidiDevice device;
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		for (int i = 0; i < infos.length; i++) {
			try {
				device = MidiSystem.getMidiDevice(infos[i]);
				Transmitter trans = device.getTransmitter();
				MidiInputFilter receiver = new MidiInputFilter(device.getDeviceInfo().toString());
				trans.setReceiver(receiver);
				s_receiver = receiver;
				device.open();
				log.info("Opened MIDI device: {}", device.getDeviceInfo());
			} catch (MidiUnavailableException e) {}
		}
	}
	
	public void sendNote(int number, boolean on) {
		try {
			ShortMessage msg = new ShortMessage(on?ShortMessage.NOTE_ON:ShortMessage.NOTE_OFF, P.MIDI_CHANNEL, number, 96);
			s_receiver.send(msg, 0);
		} catch (InvalidMidiDataException e) {
			// shouldn't happen
		}
	}
	
	public void sendSustainPedal(boolean on) {
		try {
			ShortMessage msg = new ShortMessage(ShortMessage.CONTROL_CHANGE, P.MIDI_CHANNEL, 64, on?127:0);
			s_receiver.send(msg, 0);
		} catch (InvalidMidiDataException e) {
			// shouldn't happen
		}
	}
	
	public void seqLoopStart() {
		receiveAllChannels = true;
	}
	
	public void seqLoopEnd() {
		receiveAllChannels = false;
	}
	
	private class MidiInputFilter implements Receiver {
		@SuppressWarnings("unused")
		public String name;
		public MidiInputFilter(String name) {
			this.name = name;
		}
		public void send(final MidiMessage msg, final long timeStamp) {
			if (msg instanceof ShortMessage) {
				final ShortMessage smsg = (ShortMessage)msg;
				if (smsg.getChannel()!=P.MIDI_CHANNEL && !receiveAllChannels) {
					return;
				}
				int command = smsg.getCommand();
				final int data1 = smsg.getData1();
				final int data2 = smsg.getData2();
				if (command==ShortMessage.NOTE_ON || command==ShortMessage.NOTE_OFF) { 
					if (command==ShortMessage.NOTE_ON && data2==0) {
						// interpret note on with velocity 0 as note off
						command = ShortMessage.NOTE_OFF;
					}
					for (IMidiNoteReceiver rc:noteReceivers) {
						rc.onMidiNoteMessage(smsg, command, data1, data2, timeStamp);
					}
				}
				else if (command==ShortMessage.CONTROL_CHANGE) {
					if (isLearnMode) {
						storeLearnedCCNumber(data1);
					}
					// TODO mod wheel extra handling!
					P.setFromMIDI(INDEX_OF_MIDI_CC[data1], data2);
					if (data1==CC_OSC2_DETUNE || data1==CC_OSC2_DETUNE_FINE) {
						sendPitchBendNotification();
					}
					if (data1==CC_SUSTAIN) {
						boolean old = P.PEDAL;
						P.PEDAL = data2>63;
						if (old && !P.PEDAL) {
							for (IMidiNoteReceiver rc:noteReceivers) {
								rc.onPedalUp();
							}
						}
						if (!old && P.PEDAL) {
							for (IMidiNoteReceiver rc:noteReceivers) {
								rc.onPedalDown();
							}
						}
					}
					updateStatus(data1);
				}
				else if (command==ShortMessage.PITCH_BEND) {
					int val = (data1+(data2<<7)) - 8192;
					if (P.FIX_STRANGE_MIDI_PITCH_BEND) {
						val = (data1+(data2<<7));
						if (val>8191) {
							val = val-16384;
						}
					}

					final float amount = val/8192f;
					P.VAL[P.PITCH_BEND] = Math.signum(amount)*(amount*amount);
					P.VAL_RAW_MIDI[P.PITCH_BEND] = val+8192; // 0 - 8192 - 16383
					P.PITCH_BEND_FACTOR = (float) (Math.pow(2d, (P.BEND_RANGE_CENTS*P.VAL[P.PITCH_BEND])/P.OCTAVE_CENTS));
					sendPitchBendNotification();
				}
				else if (command==ShortMessage.PROGRAM_CHANGE) {
					log.debug("Program change to {}", data1);
//					PresetHandler.loadPatch((char)('a'+(data1%PresetHandler.MAX_PATCH_NUMBER)));
					PresetHandler.loadPatchFromProgramNumber(data1);
//					OSCHandler.instance().sendAll(true);
					ControlMessageDispatcher.INSTANCE.updateAllParams();
				}
			}

		}
		public void close() {}
		
		private void updateStatus(int ccNumber) {
			ControlMessageDispatcher.INSTANCE.update(INDEX_OF_MIDI_CC[ccNumber]);
//			if (OSCHandler.instance()!=null && ccNumber>-1 && ccNumber<128) {
//				OSCHandler.instance().sendParamChange(INDEX_OF_MIDI_CC[ccNumber], false);
//			}
			
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
//	private static final int CC_LOCAL_ON_OFF = 122;
	
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
			for (String line:lines) {
				if (line.startsWith("#")) {
					if (line.startsWith("##")) {
						log.info("Loading MIDI CC map: {}", line);
					}
				}
				else {
					String[] parts = line.split("=");
					try {
						if (parts.length==2) {
							String key = parts[0].trim();
							int ccno = Integer.parseInt(parts[1].trim());
							Field f = P.class.getField(key);
							Class<?> t = f.getType();
							if(t==int.class){
							    int pindex = f.getInt(null);
							    INDEX_OF_MIDI_CC[ccno] = pindex;
							    if (pindex==P.OSC2_TUNING) {
							    	CC_OSC2_DETUNE = ccno;
							    }
							    else if (pindex==P.OSC2_TUNING_FINE) {
							    	CC_OSC2_DETUNE_FINE = ccno;
							    }
							}
							else {
								throw new Exception("Wrong parameter key error");	
							}
						}
						else {
							throw new Exception("Line format error");
						}
					} catch (Exception e) {
						log.debug("Error reading line: \"{}\", {}", line, e.getMessage());
					}
				}
			}
		} catch (IOException e) {
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
		// find path's parameter #
		int paramIndex = 0;
		for (int i=0;i<P.PARAM_STORE_SIZE;i++) {
			if (path.equals(P.OSC_PATH[i])) {
				paramIndex = i;
				break;
			}
		}
		if (paramIndex>0) {
			learnParameterIndex = paramIndex;
			isLearnMode = true;
			log.info("Started MIDI learning for {}", path);
		}
		else {
			log.warn("No mapping found for path {} to MIDI learn.", path);
		}
	}
	
	private void storeLearnedCCNumber(int ccNumber) {
		if (isLearnMode && learnParameterIndex>0) {
			if (INDEX_OF_MIDI_CC[ccNumber]!=learnParameterIndex) {
				// clear all occurences of learned parameterIndex
				for (int i=0;i<INDEX_OF_MIDI_CC.length;i++) {
					if (INDEX_OF_MIDI_CC[i]==learnParameterIndex) {
						INDEX_OF_MIDI_CC[i] = P.UNUSED;
					}
				}
				INDEX_OF_MIDI_CC[ccNumber] = learnParameterIndex;
				log.info("Learned MIDI CC #{}", ccNumber);
				SynthPi.uiMessage("Mapped MIDI CC#"+ccNumber+" to "+FancyParam.nameOf(learnParameterIndex));
			}
		}
		else {
			log.warn("Learn mode not correctly initialised.");
		}
	}
	
	
}