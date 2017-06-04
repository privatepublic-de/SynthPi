package de.privatepublic.pi.synth.comm.lcd;

import de.privatepublic.pi.synth.comm.lcd.LCD.Cmd;
import jssc.SerialPortException;

public class KeypressUpdate extends DisplayUpdate {

	boolean status;
	boolean recentStatus;
	
	public void update(int keyspressed) {
		status = keyspressed>0;
	}
	
	@Override
	protected void sendUpdate() throws SerialPortException, InterruptedException {
		boolean renderStatus = status;
		if (recentStatus!=renderStatus) {
			LCD.send(serialPort, Cmd.MOVE_TO, 16, 2);
			Thread.sleep(10);
			serialPort.writeInt(renderStatus?'*':0x20); // 0xA5 = middle dot
			Thread.sleep(10);
			LCD.send(serialPort, Cmd.MOVE_TO, 16, 2);
			Thread.sleep(10);
			recentStatus = renderStatus;
		}
	}

}
