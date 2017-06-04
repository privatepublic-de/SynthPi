package de.privatepublic.pi.synth.comm.lcd;

import java.awt.Color;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import jssc.SerialPort;
import jssc.SerialPortException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LCD {

	private static final Logger log = LoggerFactory.getLogger(LCD.class);
	
	private static LCD instance = null;
	
	public static void init(String portName) {
		instance = new LCD(portName);
	}
	
	public static void message(String line1, String line2, Color color) {
		if (isActive()) {
			instance.updateMessage(line1, line2, color);
		}
	}
	
	public static void displayParameter(int paramindex) {
		if (isActive()) {
			instance.updateParam(paramindex);
		}
	}
	
	public static void displayKeypress(int keysCount) {
		if (isActive()) {
			instance.updateKeypress(keysCount);
		}
	}
	
	public static void markDirtyPatch(boolean dirty) {
		if (isActive()) {
			instance.updateDirty(dirty);
		}
	}
	
	private static boolean isActive() {
		return instance!=null && instance.active;
	}
	
	private OutputThread outputThread;	
	private SerialPort serialPort;
	private boolean active;
	
	private ParamUpdate param = new ParamUpdate();
	private KeypressUpdate keypress = new KeypressUpdate();
	private MessageUpdate message = new MessageUpdate();
	private DirtyUpdate dirtyupdate = new DirtyUpdate();
	
	private LCD(String portName) {
		serialPort = new SerialPort(portName);
		try {
			serialPort.openPort();
			serialPort.setParams(SerialPort.BAUDRATE_9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			DisplayUpdate.initSerialPort(serialPort);
			outputThread = new OutputThread();
			active = true;
			send(Cmd.CLR);
			send(Cmd.SCROLL_OFF);
			send(Cmd.CONTRAST, 200);
			send(Cmd.CURSOR_UNDERLINE_OFF, 200);
    		outputThread.start();
    		log.debug("Started LCD update thread");
		} catch (SerialPortException e) {
			log.debug("Could not open serial port to LCD display: {}", e.getMessage());
		}
	}
	
	private void updateParam(int paramindex) {
		param.update(paramindex);
		outputThread.addMessage(param);
	}
	
	private void updateDirty(boolean dirty) {
		dirtyupdate.update(dirty);
		outputThread.addMessage(dirtyupdate);
	}
	
	private void updateKeypress(int keyspressed) {
		keypress.update(keyspressed);
		outputThread.addMessage(keypress);
	}
	
	private void updateMessage(String line1, String line2, Color color) {
		message.update(line1, line2, color);
		outputThread.addMessage(message);
	}

	private class OutputThread extends Thread {
		private final BlockingQueue<DisplayUpdate> queue = new LinkedBlockingQueue<DisplayUpdate>();
		private boolean running = true;
		private Timer timer;
		private boolean isDimmed = true;;

		public OutputThread() {
			this.setPriority(Thread.NORM_PRIORITY);
			setName("LCDOutputThread");
		}
		
		public void addMessage(DisplayUpdate update) {
			queue.add(update);
		}

		@Override
		public void run() {
			try {
				send(Cmd.SELECT_BANK, 1);
				Thread.sleep(100);
				while(running) {
					try {
						DisplayUpdate msg = queue.take();
						if (queue.isEmpty() || queue.peek()!=msg) {
							if (timer!=null) {
								timer.cancel();
							}
							timer = new Timer();
							timer.schedule(new TimerTask() {
								@Override
								public void run() {
									try {
										send(Cmd.BRIGHTNESS, 0x02);
										isDimmed = true;
									} catch (SerialPortException e) {
									}
								}
							}, 60000);
							if (isDimmed) {
								send(Cmd.BRIGHTNESS, 0x80);
								Thread.sleep(20);
								isDimmed = false;
							}
							msg.send();
						}
					} catch (SerialPortException e) {
						log.debug("LCD Error writing to serial port", e);
					} 
				}
			}
			catch (InterruptedException | SerialPortException e) {
				log.debug("LCD update thread terminated");
			}
		}
		
		

	}
	
	private synchronized void send(Cmd cmd) throws SerialPortException {
		LCD.send(serialPort, cmd);
	}
	
	private synchronized void send(Cmd cmd, int... params) throws SerialPortException {
		LCD.send(serialPort, cmd, params);
	}
	
	private static synchronized void send(SerialPort serialPort, Cmd cmd) throws SerialPortException {
		if (isActive()) {
			serialPort.writeInt(0xFE);
			serialPort.writeInt(cmd.getByte());
		}
	}
	
	protected static synchronized void send(SerialPort serialPort, Cmd cmd, int... params) throws SerialPortException {
		if (isActive()) {
			send(serialPort, cmd);
			if (params!=null) {
				serialPort.writeIntArray(params);
			}
		}
		return;
	}
	
	private static Color recentColor = null;
	
	protected static synchronized void changeColor(SerialPort serialPort, Color color) throws SerialPortException, InterruptedException {
		if (!color.equals(recentColor)) {
			recentColor = color;
			LCD.send(serialPort, Cmd.COLOR, color.getRed(), color.getGreen(), color.getBlue());
			Thread.sleep(10);
		}
	}
	
	protected static void sendString(SerialPort serialPort, String s) throws SerialPortException {
		try {
			byte[] values = s.getBytes("iso-8859-1");
			// remove 0xfe command
			for (int i=0;i<values.length;i++) {
				if (values[i]==-2 /*signed 0xFE*/ || values[i]==0xfe) { values[i]='_'; }
			}
			serialPort.writeBytes(values);
		} catch (UnsupportedEncodingException e) {
		}
	}
	
	public static void shutdown() {
		if (isActive() && instance.serialPort.isOpened()) {
			instance.outputThread.running = false;
			instance.outputThread.interrupt();
			try {
				LCD.send(instance.serialPort, Cmd.BRIGHTNESS, 0x20);
				LCD.send(instance.serialPort, Cmd.CLR);
				LCD.send(instance.serialPort, Cmd.CURSOR_UNDERLINE_OFF);
				Thread.sleep(40);
				instance.serialPort.writeString(" Bye-bye!                 ");
				Thread.sleep(40);
				LCD.send(instance.serialPort, Cmd.COLOR, 0xff, 0, 0);
				Thread.sleep(40);
				instance.serialPort.closePort();
			} catch (SerialPortException | InterruptedException e) {
				log.debug("Strange", e);
			} 
		}
	}
	
	protected static enum Cmd {
		HOME(0x48), 
		MOVE_TO(0x47), 
		CLR(0x58), 
		BRIGHTNESS(0x99), 
		CONTRAST(0x50), 
		COLOR(0xD0),
		SCROLL_OFF(0x52), 
		SELECT_BANK(0xC0), 
		CREATE_CHAR_IN_BANK(0xC1),
		WRITE_SPLASH_SCREEN(0x40),
		CURSOR_UNDERLINE_ON(0x4a),
		CURSOR_UNDERLINE_OFF(0x4b);
		
		private int b;
		Cmd(int b) {
			this.b = b;
		}
		public int getByte() {
			return b;
		}
	}
	
	public static void main(String[] args) {
		SerialPort serialPort = new SerialPort("/dev/cu.usbmodem14341");
		try {
			serialPort.openPort();
			serialPort.setParams(SerialPort.BAUDRATE_9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			
			serialPort.writeIntArray(new int[] { 0xfe, Cmd.CLR.getByte()});
			serialPort.writeString("Writing splash  screen...");
			// set splash screen
			Thread.sleep(100);
			serialPort.writeIntArray(new int[] { 0xfe, Cmd.WRITE_SPLASH_SCREEN.getByte() });
			Thread.sleep(100);
			String splash = 
					"                "+
					"... booting ... ";
			for (byte b :(splash).getBytes(StandardCharsets.ISO_8859_1)) {
				serialPort.writeByte(b);
				Thread.sleep(100);
			}
			Thread.sleep(100);
			serialPort.writeIntArray(new int[] { 0xfe, Cmd.CLR.getByte()});
			serialPort.writeString(splash);
		} catch (SerialPortException | InterruptedException e) {
			log.debug("Could not open serial port to LCD display: {}", e.getMessage());
		}
	}
	
	
}
