package de.privatepublic.pi.synth.comm;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

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

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.PresetHandler;
import de.privatepublic.pi.synth.Randomizer;
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
	private int param_selected = -1;
	private int programNumber = -1; // first change should hit 0
	private List<Receiver> updateReceivers = new ArrayList<Receiver>();
	
	private MidiHandler() {
		log.info("Initializing MIDI handler");
		s_receiver = new MidiInputFilter();
		Timer timer = new Timer("MIDIWatcher", true);
		timer.schedule(new TimerTask() {
			int detectedDevices = 0;
			boolean isFirstScan = true;
			Vector<MidiDevice> openedDevices = new Vector<MidiDevice>();
			@Override
			public void run() {
				MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
				if (infos.length!=detectedDevices) {
					log.info("MIDI device change. Scanning devices...");
					StringBuilder opened = new StringBuilder();
						for (int i=0; i<infos.length; i++) {
							if ("Gervill".equals(infos[i].getName())) {
								continue;
							}
							try {
								MidiDevice device = MidiSystem.getMidiDevice(infos[i]);
								if (!openedDevices.contains(device)) {
									try {
//										log.debug("Handling {}", infos[i].toString());
										Receiver r = device.getReceiver();
										if (!device.isOpen())
											device.open();
										if (device.isOpen()) {
											updateReceivers.add(r);
											// log.debug("Added updateReceiver for {}", infos[i]);
										}
									} catch(Exception e) {
										log.warn(e.getMessage());
									}
									try {
										Transmitter trans = device.getTransmitter();
										trans.setReceiver(s_receiver);
										if (!device.isOpen()) {
											device.open();
											openedDevices.add(device);
											if (opened.length()>0) {
												opened.append(", ");
											}
											opened.append(device.getDeviceInfo().getName());
										}
										
									} catch(MidiUnavailableException e) { 
									}
									
								}
							} catch (MidiUnavailableException e) {
								// ignore silently
							} catch (Exception e) { log.info("Error opening MIDI device: {} {}", infos[i], e); }
						}
						if (opened.length()>0) {
							log.info("Opened MIDI devices: {}", opened);
							SynthPi.uiMessage("MIDI devices opened: "+ opened);
							if (!isFirstScan) {
								SynthPi.uiLCDMessage(opened.toString(), "MIDI Devices");
							}
						}
						isFirstScan = false;
						// check removed devices
						Vector<MidiDevice> removedDev = new Vector<MidiDevice>();
						for (MidiDevice dev:openedDevices) {
							boolean stillThere = false;
							for (int i=0; i<infos.length; i++) {
								try {
									MidiDevice device = MidiSystem.getMidiDevice(infos[i]);
									if (dev==device) {
										stillThere = true;
										break;
									}
								} catch (Exception e) { log.info("Missing MIDI device: {}", infos[i]); }
							}
							if (!stillThere) {
								removedDev.add(dev);
							}
						}
						for (MidiDevice remDev:removedDev) {
							try {
								remDev.close();
							} catch (Exception e) { /* fail silently */ }
							openedDevices.remove(remDev);
							log.info("Removed {}", remDev.getDeviceInfo());
							SynthPi.uiMessage("MIDI device closed: "+remDev.getDeviceInfo());
						}
				}
				detectedDevices = infos.length;
			}
		}, 0, 5000);
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
	
	
	private class MidiInputFilter implements Receiver {
		public void send(final MidiMessage msg, final long timeStamp) {
			if (msg instanceof ShortMessage) {
				final ShortMessage smsg = (ShortMessage)msg;
				if (smsg.getChannel()!=P.MIDI_CHANNEL) {
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
					if (data1==CC_PARAM_SELECT) {
//						param_selected = P.PARAMETER_ORDER[Math.min((int)(data2/127.0*P.PARAMETER_ORDER.length-1)+1, P.PARAMETER_ORDER.length-1)];
						param_selected = P.PARAMETER_ORDER[Math.min(data2,P.PARAMETER_ORDER.length-1)];
						ControlMessageDispatcher.INSTANCE.updateSelectedParam(param_selected);
						MidiHandler.INSTANCE.updateMIDIDevicesSelectedParam();
					}
					else if (data1==CC_PARAM_VALUE && param_selected>=0) {
						P.setFromMIDI(param_selected, data2);
						ControlMessageDispatcher.INSTANCE.update(param_selected);
						ControlMessageDispatcher.INSTANCE.updateSelectedParam(param_selected);
					}
					else if (data1==CC_PROGRAM_CHANGE) {
						if (data2==0) {
							if (programNumber==0) {
								PresetHandler.initPatch();
								ControlMessageDispatcher.INSTANCE.updateAllParams();
								return;
							}
							programNumber = programNumber>0?programNumber-1:0;
						}
						else if (data2==127) {
							programNumber = programNumber<127?(programNumber+1):127;
						}
						else {
							// ignore
							return;
						}
						programNumber = PresetHandler.loadPatchFromProgramNumber(programNumber);
						ControlMessageDispatcher.INSTANCE.updateAllParams();
					}
					else if (data1==CC_RANDOMIZE) {
						if (data2>0) {
							Randomizer.randomize();
							ControlMessageDispatcher.INSTANCE.updateAllParams();
						}
					}
					else if (data1==CC_LIMITER_ENABLE) {
						if (data2>0) {
							P.LIMITER_ENABLED = !P.LIMITER_ENABLED;
							if (P.LIMITER_ENABLED) {
								SynthPi.uiLCDMessage("Limiter", "ENABLED");
							}
							else {
								SynthPi.uiLCDMessage("Limiter", "OFF");
							}
						}
					}
					else {
						int pIndex = INDEX_OF_MIDI_CC[data1];
						P.setFromMIDI(pIndex, data2);
						if (pIndex==P.OSC2_TUNING || pIndex==P.OSC2_TUNING_FINE) {
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
						if (data1!=CC_MOD_WHEEL || P.HTTP_SEND_PERFORMACE_DATA) { // don't send mod_wheel if not needed
							updateStatus(data1);
						}
					}
				}
				else if (command==ShortMessage.CHANNEL_PRESSURE) {
					P.CHANNEL_PRESSURE_TARGET = data1/127f;
					if (P.IS[P.MOD_PRESS_PITCH_AMOUNT] || P.IS[P.MOD_PRESS_PITCH2_AMOUNT]) {
						sendPitchBendNotification();
					}
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
					PresetHandler.loadPatchFromProgramNumber(data1);
					ControlMessageDispatcher.INSTANCE.updateAllParams();
				}
			}

		}
		public void close() {}
		
		private void updateStatus(int ccNumber) {
			ControlMessageDispatcher.INSTANCE.update(INDEX_OF_MIDI_CC[ccNumber]);
		}
	}
	
	public void updateMIDIDevices(int paramindex) {
		int ccno = MIDI_CC_FOR_INDEX[paramindex];
		if (ccno>0) {
			try {
				ShortMessage msg = new ShortMessage(ShortMessage.CONTROL_CHANGE, P.MIDI_CHANNEL, ccno, P.VAL_RAW_MIDI[paramindex]);
				for (Receiver receiver: updateReceivers) {
					receiver.send(msg, -1);
				}
			} catch (Exception e) {
			}
		}
	}
	
	public void updateMIDIDevicesSelectedParam() {
			try {
				ShortMessage msg = new ShortMessage(ShortMessage.CONTROL_CHANGE, P.MIDI_CHANNEL, CC_PARAM_VALUE, P.VAL_RAW_MIDI[param_selected]);
				for (Receiver receiver: updateReceivers) {
					receiver.send(msg, -1);
				}
			} catch (Exception e) {
			}
	}
	
	private static final int CC_MOD_WHEEL = 1;
	private static final int CC_SUSTAIN = 64;
	
	
	public static int CC_PARAM_SELECT = 102;
	public static int CC_PARAM_VALUE = 103;
	public static int CC_PROGRAM_CHANGE = 104;
	public static int CC_RANDOMIZE = 105;
	public static int CC_LIMITER_ENABLE = 111;
	private static final int[] INDEX_OF_MIDI_CC = new int[128];
	private static final int[] MIDI_CC_FOR_INDEX = new int[P.PARAM_STORE_SIZE];
	static {
		INDEX_OF_MIDI_CC[CC_MOD_WHEEL] = P.MOD_WHEEL;
		MIDI_CC_FOR_INDEX[P.MOD_WHEEL] = CC_MOD_WHEEL;
		InputStream in = MidiHandler.class.getResourceAsStream("/midimaps/default.map");
		try {
			List<String> lines = IOUtils.readLines(in, "utf-8");
			for (String line:lines) {
				line = line.trim();
				if (line.length()==0) {
					continue; 
				}
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
							    MIDI_CC_FOR_INDEX[pindex] = ccno;
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
	
}