package de.privatepublic.pi.synth.comm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.midi.ShortMessage;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.FancyParam;
import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.PresetHandler;
import de.privatepublic.pi.synth.Randomizer;
import de.privatepublic.pi.synth.SynthPi;
import de.privatepublic.pi.synth.comm.web.SynthSocket;
import de.privatepublic.pi.synth.modules.osc.AdditiveOscillator;
import de.privatepublic.pi.synth.modules.osc.WaveTables;

public class ControlMessageDispatcher implements IMidiNoteReceiver, IPitchBendReceiver {
	
	private static final Logger log = LoggerFactory.getLogger(ControlMessageDispatcher.class);
	public static ControlMessageDispatcher INSTANCE = new ControlMessageDispatcher();
	
	private static enum CommandMessage {
		UPDATE_REQUEST("/command/updaterequest"),
		RANDOMIZE_PATCH("/command/randomize"),
		INIT_PATCH("/command/initpatch"),
		LIST_PATCHES("/command/listpatches"),
		LOAD_PATCH("/command/loadpatch"),
		SAVE_INFO("/command/getsaveinfo"),
		SAVE_PATCH("/command/savepatch"),
		OSC1_TOGGLE_WAVESET("/command/osc/1/togglewaveset"),
		OSC2_TOGGLE_WAVESET("/command/osc/2/togglewaveset"),
		SEQ_STOP("/command/sequence/stop"),
		SEQ_START("/command/sequence/start"),
		MIDI_LEARN_START("/command/learn/start"),
		MIDI_LEARN_STOP("/command/learn/stop"),
		LOAD_SETTINGS("/command/settings/load"),
		SAVE_SETTINGS("/command/settings/save"),
		PARAMETER_MESSAGE("\\uNkNoWn");
		
		private String path;
		private CommandMessage(String path) {
			this.path = path;
		}
		
		public static CommandMessage find(String path) {
			if (!path.endsWith("=0")) {
				for (CommandMessage cm:CommandMessage.values()) {
					if (path.startsWith(cm.path)) {
						return cm;
					}
				}
			}
			return PARAMETER_MESSAGE;
		}
		
	};
	
	private ControlMessageDispatcher() {
		MidiHandler.registerReceiver((IPitchBendReceiver)this);
		MidiHandler.registerReceiver((IMidiNoteReceiver)this);
		log.info("Initialized control message dispatcher");
	}
	
	/**
	 * Handles given osc message setting parameters and updating labels.
	 * @param msg
	 * @param session
	 */
	public void handleOscMessage(String msg, Session session) {
		if (msg!=null && msg.length()>0) {
			String[] parts = msg.split("=");
			if (msg.startsWith("/play/note/")) {
				String[] noteparts = msg.split("\\/|=");
				int notenumber = Integer.valueOf(noteparts[3]);
				MidiHandler.INSTANCE.sendNote(notenumber, msg.charAt(msg.length()-1)=='1');
				return;
			} else if (msg.startsWith("/play/sustain")) {
				MidiHandler.INSTANCE.sendSustainPedal(msg.charAt(msg.length()-1)=='1');
				return;
			}
			
			CommandMessage command = CommandMessage.find(msg);
			switch (command) {
			case UPDATE_REQUEST:
				updateAllParams();
				break;
			case RANDOMIZE_PATCH:
				Randomizer.randomize();
				updateAllParams();
				break;
			case INIT_PATCH:
				PresetHandler.initPatch();
				updateAllParams();
				break;
			case LIST_PATCHES:
				session.getRemote().sendStringByFuture("/patchlist="+PresetHandler.listPatchFiles(false));
				break;
			case LOAD_PATCH:
				Matcher matcher = Pattern.compile("\\/([^\\/]+?)=").matcher(msg);
				if (matcher.find()) {
					if (PresetHandler.loadPatchWithId(matcher.group(1))) {
						updateAllParams();
					}
				}
				break;
			case SAVE_INFO:
				session.getRemote().sendStringByFuture("/saveinfo="+PresetHandler.patchSaveInfo().toString());
				break;
			case SAVE_PATCH:
				String val = msg.substring(msg.indexOf('=')+1);
				if (PresetHandler.saveCurrentOrDeletePatch(val)) {
					// on delete send saveinfo again to update ui list
					session.getRemote().sendStringByFuture("/saveinfo="+PresetHandler.patchSaveInfo().toString());
				}
				break;
			case OSC1_TOGGLE_WAVESET:
			case OSC2_TOGGLE_WAVESET:
				int pindex = (command==CommandMessage.OSC1_TOGGLE_WAVESET)?P.OSC1_WAVE_SET:P.OSC2_WAVE_SET;
				int idx1 = (int)(P.VAL[pindex]*WaveTables.WAVE_SET_COUNT);
				P.set(pindex, ((idx1+1)%WaveTables.WAVE_SET_COUNT)/(float)WaveTables.WAVE_SET_COUNT);
				update(session, pindex);
				break;
			case SEQ_START:
				Matcher matcherseq = Pattern.compile("\\/([^\\/]+?)=").matcher(msg);
				if (matcherseq.find()) {
					String number = matcherseq.group(1);
					if (number.equals("1")) {
						MidiPlayback.INSTANCE.playMIDI(MidiPlayback.class.getResourceAsStream("/midiseq/simple-sequence1.mid"));
					}
					else if (number.equals("2")) {
						MidiPlayback.INSTANCE.playMIDI(MidiPlayback.class.getResourceAsStream("/midiseq/pad-sequence1.mid"));
					}
					SynthPi.uiMessage("Playing MIDI sequence #"+number);
				}
				break;
			case SEQ_STOP:
				MidiPlayback.INSTANCE.stopMIDI();
				SynthPi.uiMessage("Stopped MIDI sequence");
				break;
			case MIDI_LEARN_START:
				MidiHandler.INSTANCE.startLearnMode(parts[1]);
				break;
			case MIDI_LEARN_STOP:
				MidiHandler.INSTANCE.stopLearnMode();
				break;
			case LOAD_SETTINGS:
				session.getRemote().sendStringByFuture("/settings="+PresetHandler.settingsJSON());
				break;
			case SAVE_SETTINGS:
				String settingsJSON = msg.substring(msg.indexOf('=')+1);
				PresetHandler.updateAndSaveSettings(settingsJSON);
				updateAllParams();
				break;
			case PARAMETER_MESSAGE: // it's possibly a parameter message
				if (parts.length==2) {
					float value = Float.valueOf(parts[1]);
					String incomingpath = parts[0];
					for (int i=0;i<P.PARAM_STORE_SIZE;i++) {
						if (incomingpath.equals(P.OSC_PATH[i])) {
							P.set(i, value);
							if (i==P.OSC2_TUNING || i==P.OSC2_TUNING_FINE) {
								MidiHandler.sendPitchBendNotification();
							}
							update(session, i);
							break;
						}
					}
				}
				break;
			default:
				break;
			}
		}
	}
	
	public void updateSelectedParam(int paramSelected) {
		sendToAll("/paramselected="+P.OSC_PATH[paramSelected]);
	}
	
	/**
	 * Send complete update of all parameters to all receivers.
	 */
	public void updateAllParams() {
		// complete update to all receivers
		updateAllParams(null);
	}
	
	/**
	 * Send complete update of all parameters to a specific websocket session.
	 * @param session
	 */
	public void updateAllParams(Session session) {
		// complete update to specific web socket session
		StringBuilder completeMsg = new StringBuilder();
		for (int i=0;i<P.PARAM_STORE_SIZE;++i) {
			completeMsg.append(createValueAndLabelMessage(i));
		}
		// meta data
		completeMsg.append("/config/performancedata="+P.HTTP_SEND_PERFORMACE_DATA+"\n");
		completeMsg.append("/patch/name="+P.LAST_LOADED_PATCH_NAME);
		String completeMessage = completeMsg.toString();
		synchronized (SynthSocket.ACTIVE_SESSIONS) {
			for (Session aSession:SynthSocket.ACTIVE_SESSIONS) {
				if (!aSession.isOpen()) continue;
				if (session!=null && aSession==session) {
					aSession.getRemote().sendStringByFuture(completeMessage);
				}
				else {
					aSession.getRemote().sendStringByFuture(completeMessage);
				}
			}
		}
	}
	
	/**
	 * Send update for a specific parameter to all receivers.
	 * @param paramIndex
	 */
	public void update(int paramIndex) {
		// all osc-aware thingys are updated with value and label
		update(null, paramIndex);
	}
	
	/**
	 * Send update for a specific parameter to all receivers. 
	 * Given web socket session receives only labels and not the actual parameter value.
	 * @param session
	 * @param paramIndex
	 */
	public void update(Session session, int paramIndex) {
		// all osc-aware thingys are updated with value and label, matching websocket session only gets label update
		String labelMsg = createLabelMessage(paramIndex);
		String labelAndValueMsg = createValueAndLabelMessage(paramIndex);
		synchronized (SynthSocket.ACTIVE_SESSIONS) {
			for (Session aSession:SynthSocket.ACTIVE_SESSIONS) {
				if (!aSession.isOpen()) continue;
				if (aSession==session) {
					aSession.getRemote().sendStringByFuture(labelMsg);
				}
				else {
					aSession.getRemote().sendStringByFuture(labelAndValueMsg);
				}
			}
		}
	}
	
	public void sendToAll(String msg) {
		synchronized (SynthSocket.ACTIVE_SESSIONS) {
			for (Session aSession:SynthSocket.ACTIVE_SESSIONS) {
				if (!aSession.isOpen()) continue;
				aSession.getRemote().sendStringByFuture(msg);
			}
		}
	}
	
	private String createValueAndLabelMessage(int paramIndex) {
		String path = P.OSC_PATH[paramIndex];
		if (path!=null) {
			StringBuilder msgValueAndLabel = new StringBuilder(path);
			msgValueAndLabel.append('=');
			msgValueAndLabel.append((float)P.TARGET_VAL[paramIndex]);
			msgValueAndLabel.append('\n');
			msgValueAndLabel.append(createLabelMessage(paramIndex));
			return msgValueAndLabel.toString();
		}
		else {
			return "";
		}
	}
	
	private String createLabelMessage(int paramIndex) {
		String path = P.OSC_PATH[paramIndex];
		if (path!=null) {
			StringBuilder msgLabel = new StringBuilder("/label");
			msgLabel.append(path);
			msgLabel.append('=');
			msgLabel.append(FancyParam.valueOf(paramIndex));
			msgLabel.append('\n');
			// special case for enums
			if (paramIndex==P.FILTER1_TYPE || paramIndex==P.FILTER2_TYPE || paramIndex==P.FILTER_PARALLEL || paramIndex==P.MOD_LFO_TYPE || paramIndex==P.OSC_MODE) {
				msgLabel.append("/option");
				msgLabel.append(path);
				msgLabel.append('/');
				if (paramIndex==P.FILTER_PARALLEL) {
					msgLabel.append(P.VAL_FILTER_ROUTING);
				}
				else if (paramIndex==P.OSC_MODE) {
					msgLabel.append(P.VAL_OSCILLATOR_MODE);
				}
				else {
					msgLabel.append(FancyParam.valueOf(paramIndex));
				}
				msgLabel.append("=1\n");
				if (paramIndex==P.OSC_MODE) {
					msgLabel.append(createLabelMessage(P.OSC1_WAVE));
					msgLabel.append(createLabelMessage(P.OSC2_WAVE));
				}
			}
			else if ((P.VAL_OSCILLATOR_MODE!=P.OscillatorMode.EXITER) && ( paramIndex==P.OSC1_WAVE || paramIndex==P.OSC2_WAVE || paramIndex==P.OSC1_WAVE_SET || paramIndex==P.OSC2_WAVE_SET)) {
				// send waveform values	
				int oscNumber = (paramIndex==P.OSC1_WAVE || paramIndex==P.OSC1_WAVE_SET)?1:2;
				int[] values;
				if (P.VAL_OSCILLATOR_MODE==P.OscillatorMode.VIRTUAL_ANALOG) {
					values = (oscNumber==1)?WaveTables.waveValues(50, P.VAL[P.OSC1_WAVE_SET], P.VAL[P.OSC1_WAVE]):WaveTables.waveValues(50, P.VAL[P.OSC2_WAVE_SET],  P.VAL[P.OSC2_WAVE]);
				}
				else {
					values = AdditiveOscillator.renderVolumes(oscNumber);
				}
				StringBuilder valuesOut = new StringBuilder();
				for (int i=0;i<values.length;i++) {
					if (i>0) { valuesOut.append(','); }
					valuesOut.append(values[i]);
				}
				msgLabel.append("/waveform/osc");
				msgLabel.append(oscNumber);
				msgLabel.append('=');
				msgLabel.append(valuesOut);
				msgLabel.append('\n');
			}
			return msgLabel.toString();
		}
		else {
			return "";
		}
	}

	@Override
	public void onMidiNoteMessage(ShortMessage msg, int command, int data1,
			int data2, long timeStamp) {
		if (P.HTTP_SEND_PERFORMACE_DATA) {
			StringBuffer wsmsg = new StringBuffer("/play/note/");
			wsmsg.append(data1);
			wsmsg.append('=');
			if (command==ShortMessage.NOTE_ON) {
				wsmsg.append('1');
			}
			else if (command==ShortMessage.NOTE_OFF) {
				wsmsg.append('0');
			}
			wsmsg.append('\n');
			sendToAll(wsmsg.toString());
		}
	}

	@Override
	public void onPedalUp() {
		if (P.HTTP_SEND_PERFORMACE_DATA)
			sendToAll("/play/sustain=0\n");
	}
	
	@Override
	public void onPedalDown() {
		if (P.HTTP_SEND_PERFORMACE_DATA)
			sendToAll("/play/sustain=1\n");
	}

	@Override
	public void onPitchBend() {
		if (P.HTTP_SEND_PERFORMACE_DATA)
			sendToAll("/play/mod/pitchbend="+(P.VAL_RAW_MIDI[P.PITCH_BEND] / 16383d)+"\n");
	}
	
}
