package de.privatepublic.pi.synth.comm.lcd;

import java.awt.Color;

import de.privatepublic.pi.synth.FancyParam;
import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.lcd.LCD.Cmd;
import jssc.SerialPortException;

public class SleepUpdate extends MessageUpdate {

	@Override
	protected void sendUpdate() throws SerialPortException,
			InterruptedException {
		update(P.LAST_LOADED_PATCH_NAME, "PATCH", FancyParam.COLOR_SYSTEM_MESSAGE);
		super.sendUpdate();
		LCD.send(serialPort, Cmd.BRIGHTNESS, 0x02);
	}

}
