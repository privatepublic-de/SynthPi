package de.privatepublic.pi.synth.comm.lcd;

import java.awt.Color;

import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.lcd.LCD.Cmd;
import jssc.SerialPortException;

public class SleepUpdate extends MessageUpdate {

	@Override
	protected void sendUpdate() throws SerialPortException,
			InterruptedException {
		update(P.LAST_LOADED_PATCH_NAME, "PATCH", Color.GREEN);
		super.sendUpdate();
		LCD.send(serialPort, Cmd.BRIGHTNESS, 0x02);
	}

}
