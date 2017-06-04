package de.privatepublic.pi.synth.comm.lcd;

import de.privatepublic.pi.synth.comm.lcd.LCD.Cmd;
import jssc.SerialPortException;

public class DirtyUpdate extends DisplayUpdate {

	boolean initialized = false;
	boolean dirtyStatus;
	boolean recentStatus;
	
	public void update(boolean dirty) {
		dirtyStatus = dirty;
	}
	
	@Override
	protected void sendUpdate() throws SerialPortException, InterruptedException {
		boolean renderStatus = dirtyStatus;
		if (!initialized || recentStatus!=renderStatus) {
			if (renderStatus) {
				LCD.send(serialPort, Cmd.CURSOR_UNDERLINE_ON);
			}
			else {
				LCD.send(serialPort, Cmd.CURSOR_UNDERLINE_OFF);
			}
			recentStatus = renderStatus;
			initialized = true;
		}
	}

}
