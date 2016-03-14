package de.privatepublic.pi.synth.modules;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Vector;

import javax.sound.midi.ShortMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.IMidiNoteReceiver;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.fx.Chorus;
import de.privatepublic.pi.synth.modules.fx.Delay;
import de.privatepublic.pi.synth.modules.fx.DistortionExp;
import de.privatepublic.pi.synth.modules.fx.Freeverb;
import de.privatepublic.pi.synth.modules.fx.IProcessor;
import de.privatepublic.pi.synth.modules.fx.Limiter;
import de.privatepublic.pi.synth.modules.mod.LFO;

public class AnalogSynth implements ISynth, IMidiNoteReceiver {

	
	public static float lastTriggeredFrequency = 440;

	private final AnalogSynthVoice[] voices = new AnalogSynthVoice[P.POLYPHONY_MAX];

	private static final float FINAL_GAIN = P.FINAL_GAIN_FACTOR;
	
	private final float[][] outputs = new float[][]{new float[P.SAMPLE_BUFFER_SIZE], new float[P.SAMPLE_BUFFER_SIZE]};
	private final float[] outputL = outputs[0];
	private final float[] outputR = outputs[1];
	
	private final IProcessor chorus = new Chorus(25);
	private final IProcessor distort = new DistortionExp();
	private final IProcessor reverb = new Freeverb(P.SAMPLE_RATE_HZ, P.SAMPLE_BUFFER_SIZE);
	private final IProcessor limiter = new Limiter(20, 500);
	private final IProcessor delay = new Delay();
	
	public AnalogSynth() {
		for (int i=0;i<P.POLYPHONY_MAX;++i) {
			voices[i] = new AnalogSynthVoice();
		}
		MidiHandler.registerReceiver(this);
	}
	
	@Override
	public void process(final List<FloatBuffer> outbuffers, final int nframes) {
		for (int i=0; i<P.POLYPHONY; i++) {
			voices[i].process(outputs, nframes);
		}
		distort.process(nframes, outputs);
		chorus.process(nframes, outputs);
		delay.process(nframes, outputs);
		reverb.process(nframes, outputs);
		if (P.LIMITER_ENABLED) {
			limiter.process(nframes, outputs);
		}
		final float gain =  P.VALX[P.VOLUME]*FINAL_GAIN;
		final FloatBuffer outL = outbuffers.get(0);
		final FloatBuffer outR = outbuffers.get(1);
		for (int i=0;i<nframes;i++) {
			outL.put((float)(outputL[i] * gain));
			outR.put((float)(outputR[i] * gain));
			// clear while copying
			outputL[i] = outputR[i] = 0;
		}
		LFO.GLOBAL.nextBufferSlice(nframes);
	}

	
	@Override
	public void onMidiNoteMessage(ShortMessage msg, int command, int data1, int data2, long timeStampMidi) {
		final boolean isMono = P.IS[P.OSC_MONO];
		final long timeStamp = System.nanoTime();
		if (command==ShortMessage.NOTE_ON) {
			float vel = data2/127f;
			if (P.VAL[P.MIDI_VELOCITY_CURVE]<.3334) {
				// linear
			} else if (P.VAL[P.MIDI_VELOCITY_CURVE]<.6667) {
				// soft
				vel = (float) (vel==0?vel:Math.pow(vel, 1/4f));
			} else {
				// hard
				vel = vel*vel*vel*vel;
			}
			// find free oscillator
			AnalogSynthVoice selectedVoice = null;
			if (isMono) {
				selectedVoice = voices[0];
			}
			else {
				// look if anyone is already playing this note
				for (int i=0;i<P.POLYPHONY;i++) {
					if (voices[i].lastMidiNote==data1) {
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
						if (weight<min) {
							selectedVoice = voices[i];
							min = weight;
						}
					}
				}
			}
			Key pk = Key.pressKey(data1, selectedVoice);
			if (isMono) {
				if (Key.pressedKeyCount()>1) {
					selectedVoice.triggerFreq(data1, pk.frequency, vel, timeStamp);
//					log.debug("TriggeredFreq @", selectedVoice);
				}
				else {
					selectedVoice.trigger(data1, pk.frequency, vel, timeStamp);
//					log.debug("Triggered @", selectedVoice);
				}
			}
			else {
				selectedVoice.trigger(data1, pk.frequency, vel, timeStamp);
//				log.debug("Triggered @", selectedVoice);
			}
			if (P.IS[P.MOD_LFO_RESET]) {
				LFO.GLOBAL.reset();
			}
//			lastTriggeredFrequency = pk.frequency;
		}
		else if (command==ShortMessage.NOTE_OFF) {
			//			log.debug("Note off @", AU.MIDI_NOTE_NAME[data1]);
			Key releasedKey = Key.releaseKey(data1);
			if (isMono && Key.pressedKeyCount()>0) {
				Key tk = Key.lastKey();
				tk.voice.triggerFreq(tk.midiNoteNumber, tk.frequency, 1, timeStamp);
				lastTriggeredFrequency = tk.frequency;
			}
			else {
				if (!P.PEDAL) {
					if (releasedKey!=null && releasedKey.voice!=null)
					releasedKey.voice.noteOff();
				}
			}
		}
		
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
				if (k.midiNoteNumber == midiNo) {
					removek = k;
				}
			}
			if (removek!=null) {
				PRESSED_KEYS.remove(removek);
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
