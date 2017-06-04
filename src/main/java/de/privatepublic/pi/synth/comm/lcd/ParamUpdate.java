package de.privatepublic.pi.synth.comm.lcd;

import java.awt.Color;

import jssc.SerialPortException;

import org.apache.commons.lang3.StringUtils;

import de.privatepublic.pi.synth.FancyParam;
import de.privatepublic.pi.synth.P;
import de.privatepublic.pi.synth.comm.lcd.LCD.Cmd;

public class ParamUpdate extends DisplayUpdate {

	private int paramindex = 0;
	
	public void update(int paramindex) {
		this.paramindex = paramindex;
	}
	
	@Override
	protected void sendUpdate() throws SerialPortException, InterruptedException {
		int renderIndex = paramindex;
		String value = FancyParam.valueOf(renderIndex);
		if (!P.IS[P.FILTER1_ON] && (renderIndex==P.FILTER1_FREQ || renderIndex==P.FILTER1_TYPE || renderIndex==P.FILTER1_OVERLOAD || renderIndex==P.FILTER1_RESONANCE || renderIndex==P.FILTER1_TRACK_KEYBOARD)) {
			// show in value that filter is turned off
			value = "off!".concat(value);
		}
		int val = P.VAL_RAW_MIDI[renderIndex]-(P.IS_BIPOLAR[renderIndex]?64:0);
		String line1 = formatLine(FancyParam.nameOf(renderIndex), 16, false);
		String line2 = 
				formatLine(value, 11, false)
				+StringUtils.leftPad(String.valueOf(val), 4, ' ');
		Color color = FancyParam.colorOf(renderIndex);
		LCD.send(serialPort, Cmd.HOME);
		Thread.sleep(10);
		LCD.sendString(serialPort, line1);
		Thread.sleep(10);
		LCD.send(serialPort, Cmd.MOVE_TO, 1, 2);
		LCD.sendString(serialPort, line2);
		Thread.sleep(10);
		LCD.changeColor(serialPort, color);
	}

}
