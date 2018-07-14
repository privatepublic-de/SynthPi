package de.privatepublic.pi.synth.comm;

public interface IMidiClockReceiver {
	public void onMidiClockActivationChange(boolean isActive);
	public void on24PPQClock();
}
