package de.privatepublic.pi.synth.comm.lcd;

import java.awt.Color;

import jssc.SerialPortException;
import de.privatepublic.pi.synth.comm.lcd.LCD.Cmd;

public class MessageUpdate extends DisplayUpdate {

	protected String line1;
	protected String line2;
	protected Color color;
	
	public void update(String line1, String line2, Color color) {
		this.line1 = line1;
		this.line2 = line2;
		this.color = color;
	}
	
	@Override
	protected void sendUpdate() throws SerialPortException, InterruptedException {
		String renderLine1 = formatLine(line1, 16, false);
		String renderLine2 = formatLine(line2, 15, false);
		Color renderColor = color;
		LCD.send(serialPort, Cmd.HOME);
		Thread.sleep(10);
		LCD.sendString(serialPort, renderLine1);
		Thread.sleep(10);
		LCD.send(serialPort, Cmd.MOVE_TO, 1, 2);
		LCD.sendString(serialPort, renderLine2);
		Thread.sleep(10);
		LCD.changeColor(serialPort, renderColor);
	}

}
