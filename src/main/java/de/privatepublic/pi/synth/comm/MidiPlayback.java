package de.privatepublic.pi.synth.comm;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Transmitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.P;

public class MidiPlayback implements MetaEventListener {

	private static final Logger log = LoggerFactory.getLogger(MidiPlayback.class);
	
	public static final MidiPlayback INSTANCE = new MidiPlayback();
	
	private MidiPlayer player;

	public void playMIDI(InputStream instream) {
		stopMIDI();
		player = new MidiPlayer();
		Sequence sequence = player.getSequence(instream);
		Sequencer sequencer = player.getSequencer(); 
		try {
			Transmitter seqTrans = sequencer.getTransmitter();
			seqTrans.setReceiver(MidiHandler.s_receiver);
		} catch (MidiUnavailableException e) {
			log.error("Could not start MIDI file", e);
			return;
		}
		log.debug("Started MIDI playback");
		player.play(sequence, true);
	}
	
	public void playMIDI(String filename) {
		log.debug("Loading MIDI file {}", filename);
		try {
			playMIDI(new FileInputStream(new File(filename)));
		} catch (FileNotFoundException e) {
			log.error("File not found", e);
		}
	}
	
	public void stopMIDI() {
		if (player!=null) {
			player.stop();
			player.close();
		}
	}

	/**
	 * This method is called by the sound system when a meta event occurs. In
	 * this case, when the end-of-track meta event is received, the drum track
	 * is turned on.
	 */
	public void meta(MetaMessage event) {
		if (event.getType() == MidiPlayer.END_OF_TRACK_MESSAGE) {
			log.debug("MIDI file finished");
		}
	}

}

class MidiPlayer implements MetaEventListener {
	private static final Logger log = LoggerFactory.getLogger(MidiPlayer.class);
	// Midi meta event
	public static final int END_OF_TRACK_MESSAGE = 47;

	private Sequencer sequencer;

	private boolean loop;

	private boolean paused;

	/**
	 * Creates a new MidiPlayer object.
	 */
	public MidiPlayer() {
		try {
			sequencer = MidiSystem.getSequencer(false);
			sequencer.open();
			sequencer.addMetaEventListener(this);
		} catch (MidiUnavailableException ex) {
			sequencer = null;
			log.warn("Error", ex);
		}
	}

	/**
	 * Loads a sequence from the file system. Returns null if an error occurs.
	 */
	public Sequence getSequence(String filename) {
		try {
			return getSequence(new FileInputStream(filename));
		} catch (IOException ex) {
			log.warn("Error", ex);
			return null;
		}
	}

	/**
	 * Loads a sequence from an input stream. Returns null if an error occurs.
	 */
	public Sequence getSequence(InputStream is) {
		try {
			if (!is.markSupported()) {
				is = new BufferedInputStream(is);
			}
			Sequence s = MidiSystem.getSequence(is);
			is.close();
			return s;
		} catch (InvalidMidiDataException ex) {
			log.warn("Error", ex);
			return null;
		} catch (IOException ex) {
			log.warn("Error", ex);
			return null;
		}
	}

	/**
	 * Plays a sequence, optionally looping. This method returns immediately.
	 * The sequence is not played if it is invalid.
	 */
	public void play(Sequence sequence, boolean loop) {
		if (sequencer != null && sequence != null && sequencer.isOpen()) {
			try {
				sequencer.setSequence(sequence);
				this.loop = loop;
				if (loop) {
					sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
					sequencer.setLoopStartPoint(0);
					sequencer.setLoopEndPoint(-1);
				}
				MidiHandler.INSTANCE.seqLoopStart();
				sequencer.start();
			} catch (InvalidMidiDataException ex) {
				log.warn("Error", ex);
			}
		}
	}

	/**
	 * This method is called by the sound system when a meta event occurs. In
	 * this case, when the end-of-track meta event is received, the sequence is
	 * restarted if looping is on.
	 */
	public void meta(MetaMessage event) {
		if (event.getType() == END_OF_TRACK_MESSAGE) {
			if (sequencer != null && sequencer.isOpen() && loop) {
				P.resetPerformanceControllers();
				sequencer.start();
			}
		}
	}

	/**
	 * Stops the sequencer and resets its position to 0.
	 */
	public void stop() {
		if (sequencer != null && sequencer.isOpen()) {
			sequencer.stop();
			sequencer.setMicrosecondPosition(0);
			MidiHandler.INSTANCE.seqLoopEnd();
			P.resetPerformanceControllers();
		}
	}

	/**
	 * Closes the sequencer.
	 */
	public void close() {
		if (sequencer != null && sequencer.isOpen()) {
			sequencer.close();
			MidiHandler.INSTANCE.seqLoopEnd();
		}
	}

	/**
	 * Gets the sequencer.
	 */
	public Sequencer getSequencer() {
		return sequencer;
	}

	/**
	 * Sets the paused state. Music may not immediately pause.
	 */
	public void setPaused(boolean paused) {
		if (this.paused != paused && sequencer != null && sequencer.isOpen()) {
			this.paused = paused;
			if (paused) {
				sequencer.stop();
				MidiHandler.INSTANCE.seqLoopEnd();
			} else {
				sequencer.start();
				MidiHandler.INSTANCE.seqLoopStart();
			}
		}
	}

	/**
	 * Returns the paused state.
	 */
	public boolean isPaused() {
		return paused;
	}

}
