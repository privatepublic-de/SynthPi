package de.privatepublic.pi.synth.comm;

import javax.sound.midi.ShortMessage;

public interface IMidiNoteReceiver {
	public void onMidiNoteMessage(ShortMessage msg, int command, int data1, int data2, long timeStamp);
	public void onPedalUp();
	public void onPedalDown();
}