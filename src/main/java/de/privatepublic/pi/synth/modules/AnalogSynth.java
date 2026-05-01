package de.privatepublic.pi.synth.modules;

import java.nio.FloatBuffer;
import java.util.List;

import javax.sound.midi.ShortMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.IMidiNoteReceiver;
import de.privatepublic.pi.synth.comm.MidiHandler;
import de.privatepublic.pi.synth.modules.fx.Chorus;
import de.privatepublic.pi.synth.modules.fx.DelayBase;
import de.privatepublic.pi.synth.modules.fx.DigitalDelay;
import de.privatepublic.pi.synth.modules.fx.DistortionExp;
import de.privatepublic.pi.synth.modules.fx.TapeDelay;
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
	private final IProcessor limiter = new Limiter(2, 100);
	private final TapeDelay tapeDelay = new TapeDelay();
	private final DigitalDelay digitalDelay = new DigitalDelay();
	private DelayBase activeDelay = tapeDelay;
	
	public AnalogSynth() {
		for (int i=0;i<P.POLYPHONY_MAX;++i) {
			voices[i] = new AnalogSynthVoice();
		}
		MidiHandler.registerReceiver(this);
	}
	
	@Override
	public void process(final List<FloatBuffer> outbuffers, final int nframes) {
		// Control-rate chunking: slice the audio buffer into fixed CONTROL_BUFFER_SIZE
		// chunks. For each chunk, render LFO.GLOBAL for the chunk's worth of samples,
		// run all voices on that chunk, then advance LFO state. This puts every voice's
		// per-chunk hoisted reads (in their processBuffer1st/2nd) at control rate
		// (~3 kHz at default settings) — a finer modulation grain than the previous
		// per-buffer rate (~375 Hz) — without changing the FX chain, which still runs
		// once per audio buffer on the assembled stereo output.
		final int chunkLen = P.CONTROL_BUFFER_SIZE;
		for (int chunkStart = 0; chunkStart < nframes; chunkStart += chunkLen) {
			// Clamp the last chunk if nframes isn't a multiple of CONTROL_BUFFER_SIZE
			// (defaults are: 128 / 16 = 8 even chunks). Without this, a user-set
			// -audiobuffersize that isn't a multiple of 16 would write past nframes.
			final int thisChunkLen = Math.min(chunkLen, nframes - chunkStart);
			LFO.GLOBAL.renderBuffer(thisChunkLen);
			for (int v = 0; v < P.POLYPHONY; v++) {
				voices[v].process(outputs, chunkStart, thisChunkLen);
			}
			LFO.GLOBAL.nextBufferSlice(thisChunkLen);
		}
		distort.process(nframes, outputs);
		chorus.process(nframes, outputs);
		// Select between tape and digital delay based on P.DELAY_TYPE.
		// On a swap, clear the newly-selected delay's buffer so stale audio
		// from a prior selection can't leak through. Both instances are
		// preallocated so the switch itself never allocates.
		final DelayBase wantDelay = P.IS[P.DELAY_TYPE] ? digitalDelay : tapeDelay;
		if (activeDelay != wantDelay) {
			wantDelay.initPatch();
			activeDelay = wantDelay;
		}
		activeDelay.process(nframes, outputs);
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
				LFO.GLOBAL.reset();
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

		public int midiNoteNumber;
		public float frequency;
		public AnalogSynthVoice voice;

		private static final int MAX_KEYS = P.POLYPHONY_MAX + 4;
		private static final Key[] KEY_POOL = new Key[MAX_KEYS];
		private static int keyCount = 0;

		static {
			for (int i = 0; i < MAX_KEYS; i++) {
				KEY_POOL[i] = new Key();
			}
		}

		public static Key pressKey(int midiNo, AnalogSynthVoice voice) {
			final int slot = keyCount < MAX_KEYS ? keyCount++ : MAX_KEYS - 1;
			final Key k = KEY_POOL[slot];
			k.midiNoteNumber = midiNo;
			k.voice = voice;
			k.frequency = P.MIDI_NOTE_FREQUENCY_HZ[midiNo];
			return k;
		}

		public static Key releaseKey(int midiNo) {
			Key removek = null;
			int removeIdx = -1;
			for (int i = 0; i < keyCount; i++) {
				final Key k = KEY_POOL[i];
				if (k.midiNoteNumber == midiNo && k.voice.lastMidiNote == midiNo) {
					removek = k;
					removeIdx = i;
				}
			}
			if (removeIdx >= 0) {
				final Key freed = KEY_POOL[removeIdx];
				System.arraycopy(KEY_POOL, removeIdx + 1, KEY_POOL, removeIdx, keyCount - 1 - removeIdx);
				keyCount--;
				KEY_POOL[keyCount] = freed;
			}
			for (int i = 0; i < keyCount; i++) {
				if (KEY_POOL[i].midiNoteNumber == midiNo) {
					final Key freed = KEY_POOL[i];
					System.arraycopy(KEY_POOL, i + 1, KEY_POOL, i, keyCount - 1 - i);
					keyCount--;
					KEY_POOL[keyCount] = freed;
					break;
				}
			}
			return removek;
		}

		public static int pressedKeyCount() {
			return keyCount;
		}

		public static boolean keyIsPressed(AnalogSynthVoice voice) {
			for (int i = 0; i < keyCount; i++) {
				if (KEY_POOL[i].voice == voice) return true;
			}
			return false;
		}

		public static Key lastKey() {
			return keyCount > 0 ? KEY_POOL[keyCount - 1] : null;
		}
	}
	

}
