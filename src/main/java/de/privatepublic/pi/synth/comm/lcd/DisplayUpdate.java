package de.privatepublic.pi.synth.comm.lcd;

import jssc.SerialPort;
import jssc.SerialPortException;

public abstract class DisplayUpdate {

	protected static SerialPort serialPort;
	
	public static void initSerialPort(SerialPort port) {
		DisplayUpdate.serialPort = port;
	}
	
	public void send() throws SerialPortException, InterruptedException {
		if (serialPort!=null) {
			sendUpdate();
		}
	}
	
	protected abstract void sendUpdate() throws SerialPortException, InterruptedException;
}
