package de.privatepublic.pi.synth.comm.lcd;

import java.awt.Color;

import org.apache.commons.lang3.StringUtils;

import de.privatepublic.pi.synth.FancyParam;
import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.lcd.LCD.Cmd;
import jssc.SerialPortException;

public class ParamUpdate extends DisplayUpdate {

	private String line1;
	private String line2;
	private Color color;
	private int upperChar = 0x20;
	private int lowerChar = 0x20;
	private boolean isNegative = false;
	private static boolean recentPolarity = false;
	
	public void update(int paramindex) {
		line1 = FancyParam.nameOf(paramindex);
		line2 = FancyParam.valueOf(paramindex)+"  "+StringUtils.leftPad(String.valueOf(P.VAL_RAW_MIDI[paramindex]), 3, '0');
		color = FancyParam.colorOf(paramindex);
		isNegative = P.IS_BIPOLAR[paramindex] && P.VALC[paramindex]<0;
		int v = (int)(P.VAL[paramindex]*16);
		if (v>7) {
			upperChar = v-8==0?32:v-9;
			lowerChar = 7;
		}
		else {
			// _ 0 1 2 3 4 5 6 7
			if (isNegative) {
				lowerChar = v;
			}
			else {
				lowerChar = v==0?32:v-1;
			}
		}
		if (P.IS_BIPOLAR[paramindex] && v>7) {
			lowerChar = 32;
		}
	}
	
	
	@Override
	protected void sendUpdate() throws SerialPortException, InterruptedException {
		LCD.send(serialPort, Cmd.HOME);
		Thread.sleep(10);
		LCD.sendString(serialPort, line1);
		Thread.sleep(10);
		LCD.send(serialPort, Cmd.MOVE_TO, 2, 2);
		LCD.sendString(serialPort, line2);
		Thread.sleep(10);
		LCD.send(serialPort, Cmd.MOVE_TO, 16, 1);
		if (recentPolarity!=isNegative) {
			if (isNegative) {
				LCD.send(serialPort, Cmd.SELECT_BANK, 2);
			}
			else {
				LCD.send(serialPort, Cmd.SELECT_BANK, 1);
			}
			recentPolarity = isNegative;
		}
		serialPort.writeInt(upperChar); // clear if no param message
		LCD.send(serialPort, Cmd.MOVE_TO, 16, 2);
		serialPort.writeInt(lowerChar); // clear if no param message
		Thread.sleep(10);
		LCD.changeColor(serialPort, color);
	}

}
