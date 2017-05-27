package de.privatepublic.pi.synth.modules;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Vector;

import javax.sound.midi.ShortMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.P.FilterType;
import de.privatepublic.pi.synth.comm.IMidiNoteReceiver;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.comm.lcd.LCD;
import de.privatepublic.pi.synth.modules.fx.Chorus;
import de.privatepublic.pi.synth.modules.fx.DigitalDelay;
import de.privatepublic.pi.synth.modules.fx.IProcessorStereo;
import de.privatepublic.pi.synth.modules.fx.Limiter;
import de.privatepublic.pi.synth.modules.fx.StateVariableFilter;
import de.privatepublic.pi.synth.modules.fx.TapeDelay;
import de.privatepublic.pi.synth.modules.mod.LFO;

public class AnalogSynth implements IMidiNoteReceiver {

	
	public static float lastTriggeredFrequency = 440;

	private final AnalogSynthVoice[] voices = new AnalogSynthVoice[P.POLYPHONY_MAX];

	private static final float FINAL_GAIN = P.FINAL_GAIN_FACTOR;
		
	private final float[][] outputs = new float[][]{new float[P.SAMPLE_BUFFER_SIZE], new float[P.SAMPLE_BUFFER_SIZE]};
	private final float[] outputL = outputs[0];
	private final float[] outputR = outputs[1];
	private final float[] renderBuffer = new float[P.SAMPLE_BUFFER_SIZE];
	
	private final IProcessorStereo chorus = new Chorus(80);
//	private final IProcessorMono distort = new DistortionExp();
	private final IProcessorStereo limiter = new Limiter(10, 500);
	private final StateVariableFilter booster = new StateVariableFilter(FilterType.BANDPASS, 80f, 1/3f);
	private final LFO lfo = new LFO(null);
	private final TapeDelay delayTape = new TapeDelay(lfo);
	private final DigitalDelay delayDigital = new DigitalDelay(lfo);
	
	private int numberBufferChunks = P.SAMPLE_BUFFER_SIZE/P.CONTROL_BUFFER_SIZE;
	
	public AnalogSynth() {
		for (int i=0;i<P.POLYPHONY_MAX;++i) {
			voices[i] = new AnalogSynthVoice();
		}
		MidiHandler.registerReceiver(this);
	}
	
	public void process(final List<FloatBuffer> outbuffers, final int nframes) {
		for (int chunkNo=0;chunkNo<numberBufferChunks;chunkNo++) {
			P.interpolate();
			lfo.controlTick();
			boolean usedelay2 = P.IS[P.DELAY_TYPE];
			if (usedelay2) delayDigital.controlTick(); else delayTape.controlTick();
			
			final int startPos = chunkNo*P.CONTROL_BUFFER_SIZE;
			for (int i=0; i<P.POLYPHONY; i++) {
				voices[i].process(renderBuffer, startPos);
			}
			// distort.process(renderBuffer, startPos);
			booster.processBuffer(renderBuffer, startPos, P.VAL[P.BASS_BOOSTER_LEVEL]);
			if (usedelay2) delayDigital.process(renderBuffer, outputs, startPos); else delayTape.process(renderBuffer, outputs, startPos);
			chorus.process(outputs, startPos);
			if (P.LIMITER_ENABLED) {
				limiter.process(outputs, startPos);
			}
		}
		
		final float gain =  P.VALX[P.VOLUME]*FINAL_GAIN;
		final FloatBuffer outL = outbuffers.get(0);
		final FloatBuffer outR = outbuffers.get(1);
		for (int i=0;i<nframes;i++) {
			outL.put((float)(outputL[i] * gain));
			outR.put((float)(outputR[i] * gain));
			// clear while copying
			renderBuffer[i] = outputL[i] = outputR[i] = 0;
		}
	}

	
	@Override
	public void onMidiNoteMessage(ShortMessage msg, int command, int noteNo, int midiVelocity, long timeStampMidi) {
		final boolean isMono = P.IS[P.OSC_MONO];
		final long timeStamp = System.nanoTime();
		if (command==ShortMessage.NOTE_ON) {
			float vel = midiVelocity/127f;
			if (P.VAL[P.MIDI_VELOCITY_CURVE]<.3334) {
				// linear
			} else if (P.VAL[P.MIDI_VELOCITY_CURVE]<.6667) {
				// soft
				vel = (float) (vel==0?vel:Math.pow(vel, 1/4f));
			} else {
				// hard
				vel = (float)Math.pow(vel,1.5);//vel*vel*vel*vel;
			}
			// find free oscillator
			AnalogSynthVoice selectedVoice = null;
			if (isMono) {
				selectedVoice = voices[0];
			}
			else {
				// look if anyone is already playing this note
				for (int i=0;i<P.POLYPHONY;i++) {
					if (voices[i].lastMidiNote==noteNo) {
						selectedVoice = voices[i];
						break;
					}
				}
				if (selectedVoice==null) {
					// now use weighting
					float min = Float.MAX_VALUE;
					selectedVoice = voices[0];
					for (int i=0;i<P.POLYPHONY;++i) {
						float weight = voices[i].captureWeight(timeStamp);
						if (weight<=min) {
							selectedVoice = voices[i];
							min = weight;
						}
					}
				}
			}
			int previousKeyCount = Key.pressedKeyCount();
			Key pk = Key.pressKey(noteNo, selectedVoice);
			if (isMono) {
				if (Key.pressedKeyCount()>1) {
					selectedVoice.triggerFreq(noteNo, pk.frequency, vel, timeStamp); // legato mode
//					log.debug("TriggeredFreq @", selectedVoice);
				}
				else {
					selectedVoice.trigger(noteNo, pk.frequency, vel, timeStamp);
//					log.debug("Triggered @", selectedVoice);
				}
			}
			else {
				selectedVoice.trigger(noteNo, pk.frequency, vel, timeStamp);
//				log.debug("Triggered @", selectedVoice);
			}
			if (P.IS[P.MOD_LFO_RESET]) {
				lfo.resetPhase();
			}
			if (previousKeyCount==0) {
				lfo.resetDelay();
			}
//			lastTriggeredFrequency = pk.frequency;
		}
		else if (command==ShortMessage.NOTE_OFF) {
			//			log.debug("Note off @", AU.MIDI_NOTE_NAME[data1]);
			Key releasedKey = Key.releaseKey(noteNo);
			if (isMono && Key.pressedKeyCount()>0) {
				Key tk = Key.lastKey();
				tk.voice.triggerFreq(tk.midiNoteNumber, tk.frequency, 1, timeStamp);
				lastTriggeredFrequency = tk.frequency;
			}
			else {
				if (!P.PEDAL) {
					if (releasedKey!=null && releasedKey.voice!=null) {
						releasedKey.voice.noteOff();
					}
				}
			}
		}
		LCD.displayKeypress(Key.pressedKeyCount());
	}
	
	@Override
	public void onPedalUp() {
//		log.debug("Killing notes");
		for (int i=0;i<voices.length;++i) {
			if (!Key.keyIsPressed(voices[i])) {
				voices[i].noteOff();
			}
		}
	}
	
	@Override
	public void onPedalDown() {
	}

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(AnalogSynth.class);


	private static class Key {
		
		private Key(int midiNoteNumber, AnalogSynthVoice voice) {
			this.midiNoteNumber = midiNoteNumber;
			this.voice = voice;
			frequency = P.MIDI_NOTE_FREQUENCY_HZ[midiNoteNumber];
		}
		
		
		public int midiNoteNumber;
		public float frequency;
		public AnalogSynthVoice voice;
		
		
		public static Key pressKey(int midiNo, AnalogSynthVoice voice) {
			Key newKey = new Key(midiNo, voice);
			PRESSED_KEYS.add(newKey);
			return newKey;
		}
		
		public static Key releaseKey(int midiNo) {
			Key removek = null;
			
			for (Key k:PRESSED_KEYS) {
				if (k.midiNoteNumber==midiNo && k.voice.lastMidiNote==midiNo) {
					removek = k;
				}
			}
			if (removek!=null) {
				PRESSED_KEYS.remove(removek);
			}
			for (Key k:PRESSED_KEYS) {
				if (k.midiNoteNumber==midiNo) {
					PRESSED_KEYS.remove(k);
					break;
				}
			}
			return removek;
		}
		
		public static int pressedKeyCount() {
			return PRESSED_KEYS.size();
		}
		
		public static boolean keyIsPressed(AnalogSynthVoice voice) {
			for (Key k:PRESSED_KEYS) {
				if (k.voice==voice) {
					return true;
				}
			}
			return false;
		}
		
		public static Key lastKey() {
			if (PRESSED_KEYS.size()>0) {
				return PRESSED_KEYS.get(PRESSED_KEYS.size()-1);
			}
			return null;
		}
		
		private static Vector<Key> PRESSED_KEYS = new Vector<Key>();
		
	}
	

}
