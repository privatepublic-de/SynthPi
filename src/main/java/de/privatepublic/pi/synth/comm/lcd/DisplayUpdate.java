package de.privatepublic.pi.synth.comm.lcd;

import org.apache.commons.lang3.StringUtils;

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
	
	protected String formatLine(String s, int len, boolean leftPad) {
		if (s==null) {
			s="";
		}
		if (s.length()>len) {
			return s.substring(0, len);
		}
		if (leftPad) {
			return StringUtils.leftPad(s, len);
		}
		else {
			return StringUtils.rightPad(s, len);
		}
	}
	
	protected abstract void sendUpdate() throws SerialPortException, InterruptedException;
}
