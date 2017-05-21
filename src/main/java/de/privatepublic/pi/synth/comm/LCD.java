package de.privatepublic.pi.synth.comm;

import java.awt.Color;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import jssc.SerialPort;
import jssc.SerialPortException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.privatepublic.pi.synth.FancyParam;
import de.privatepublic.pi.synth.P;

public class LCD {

	private static final Logger log = LoggerFactory.getLogger(LCD.class);
	
	private static LCD instance = null;
	
	public static void init(String portName) {
		instance = new LCD(portName);
	}
	
	public static void message(String line1, String line2, Color color) {
		if (isActive()) {
			instance.outputThread.addMessage(new Message(line1, line2, color));
		}
	}
	
	public static void displayParameter(int paramindex) {
		if (isActive()) {
			instance.outputThread.addMessage(new Message(paramindex));
		}
	}
	
	public static void displayInfo(int keysCount) {
		if (isActive()) {
			instance.outputThread.addMessage(new Message(keysCount>0));
		}
	}
	
	private static boolean isActive() {
		return instance!=null && instance.active;
	}
	
	private OutputThread outputThread;	
	private SerialPort serialPort;
	private boolean active;
	
	private LCD(String portName) {
		serialPort = new SerialPort(portName);
		try {
			serialPort.openPort();
			serialPort.setParams(SerialPort.BAUDRATE_9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_RTSCTS_OUT);
			outputThread = new OutputThread();
			active = true;
			send(Cmd.CLR);
			send(Cmd.SCROLL_OFF);
			send(Cmd.CONTRAST, 200);
    		outputThread.start();
    		log.debug("Started LCD update thread");
    		outputThread.addMessage(new Message("SynthPi is","up and running!",Color.DARK_GRAY, Message.Type.LONG));
    		outputThread.addMessage(new Message(Message.Type.SET_SPLASHSCREEN));
		} catch (SerialPortException e) {
			log.debug("Could not open serial port to LCD display: {}", e.getMessage());
		}
	}
	

	private static class Message {
		
		private static Color recentCol = null;
		
		enum Type { NORMAL, LONG, STATUS, PARAM, SET_SPLASHSCREEN };
		private String line1;
		private String line2;
		private Color color;
		private Type type;
		private boolean status = false;
		private static boolean recentStatus = false;
		private int upperChar = 0x20;
		private int lowerChar = 0x20;
		private boolean isNegative = false;
		private static boolean recentPolarity = false;

		public Message(String line1, String line2, Color color) {
			this.line1 = line1;
			this.line2 = line2;
			this.color = color;
			type = Type.NORMAL;
		}
		
		public Message(String line1, String line2, Color color, Type type) {
			this.line1 = line1;
			this.line2 = line2;
			this.color = color;
			this.type = type;
		}
		
		public Message(Type type) {
			this.type = type;
		}
		
		public Message(boolean status) {
			type = Type.STATUS;
			this.status = status;
		}
		
		public Message(int paramindex) {
			type = Type.PARAM;
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
		
		public void send(SerialPort serialPort) throws SerialPortException, InterruptedException {
			switch(type) {
			case SET_SPLASHSCREEN:
				serialPort.writeIntArray(new int[] {0xFE, 0x40 });
				Thread.sleep(20);
				for (byte b :("----SynthPi-----"+"privatepublic.de").getBytes(StandardCharsets.US_ASCII)) {
					serialPort.writeByte(b);
					Thread.sleep(100);
				}
				break;
			case NORMAL:
			case LONG:
			case PARAM:
				LCD.send(serialPort, Cmd.HOME);
				Thread.sleep(10);
				LCD.sendString(serialPort, getLine1());
				Thread.sleep(10);
				LCD.send(serialPort, Cmd.MOVE_TO, 2, 2);
				LCD.sendString(serialPort, getLine2());
				Thread.sleep(10);
				LCD.send(serialPort, Cmd.MOVE_TO, 16, 1);
				if (type==Type.PARAM && recentPolarity!=isNegative) {
					if (isNegative) {
						LCD.send(serialPort, Cmd.SELECT_BANK, 2);
					}
					else {
						LCD.send(serialPort, Cmd.SELECT_BANK, 1);
					}
					recentPolarity = isNegative;
				}
				serialPort.writeInt(type==Type.PARAM?upperChar:32); // clear if no param message
				LCD.send(serialPort, Cmd.MOVE_TO, 16, 2);
				serialPort.writeInt(type==Type.PARAM?lowerChar:32); // clear if no param message
				Thread.sleep(10);
				Color col = getColor();
				if (col!=null && !col.equals(recentCol)) {
					LCD.send(serialPort, Cmd.COLOR, col.getRed(), col.getGreen(), col.getBlue());
					recentCol = col;
				}
				if (type==Message.Type.LONG) {
					Thread.sleep(1500);
				}
				else {
					Thread.sleep(10);
				}
				break;
			case STATUS:
				if (recentStatus!=status) {
					LCD.send(serialPort, Cmd.MOVE_TO, 1, 2);
					Thread.sleep(10);
					serialPort.writeInt(status?'*':0x20); // 0xA5 = middle dot
					Thread.sleep(10);
					recentStatus = status;
				}
				break;
			}
		}

		public String getLine1() {
			return normalize(line1, type!=Type.STATUS?15:1, type==Type.PARAM?true:false);
		}

		public String getLine2() {
			return normalize(line2, 14, type==Type.PARAM?true:false);
		}

		public Color getColor() {
			return color;
		}
		
		public Type getType() {
			return type;
		}

		private String normalize(String s, int len, boolean leftPad) {
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
		
	}

	private class OutputThread extends Thread {
		private final BlockingQueue<Message> queue = new LinkedBlockingQueue<Message>();
		private boolean running = true;
		private Timer timer;

		public OutputThread() {
			this.setPriority(Thread.NORM_PRIORITY);
			setName("LCDOutputThread");
		}
		
		public void addMessage(Message msg) {
			queue.add(msg);
		}

		@Override
		public void run() {
			try {
				initCharset();
				while(running) {
					try {
						Message msg = queue.take();
						if (queue.isEmpty() || queue.peek().getType()!=msg.getType()) {
							if (timer!=null) {
								timer.cancel();
							}
							timer = new Timer();
							timer.schedule(new TimerTask() {
								@Override
								public void run() {
									try {
										send(Cmd.BRIGHTNESS, 0x02);
									} catch (SerialPortException e) {
									} // brightness
								}
							}, 60000);
							send(Cmd.BRIGHTNESS, 0x80);
							Thread.sleep(20);
							msg.send(serialPort);
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
		
		private void initCharset() throws SerialPortException, InterruptedException {
			Thread.sleep(40);
			// up/positive, bank 1
			send(Cmd.CREATE_CHAR_IN_BANK, 1, 0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0xf);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 1, 1, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0xf, 0xf);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 1, 2, 0x0, 0x0, 0x0, 0x0, 0x0, 0xf, 0xf, 0xf);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 1, 3, 0x0, 0x0, 0x0, 0x0, 0xf, 0xf, 0xf, 0xf);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 1, 4, 0x0, 0x0, 0x0, 0xf, 0xf, 0xf, 0xf, 0xf);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 1, 5, 0x0, 0x0, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 1, 6, 0x0, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 1, 7, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf);
			Thread.sleep(20);
			// down/negative, bank 2
			send(Cmd.CREATE_CHAR_IN_BANK, 2, 7, 0xa, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 2, 6, 0xa, 0x5, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 2, 5, 0xa, 0x5, 0xa, 0x0, 0x0, 0x0, 0x0, 0x0);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 2, 4, 0xa, 0x5, 0xa, 0x5, 0x0, 0x0, 0x0, 0x0);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 2, 3, 0xa, 0x5, 0xa, 0x5, 0xa, 0x0, 0x0, 0x0);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 2, 2, 0xa, 0x5, 0xa, 0x5, 0xa, 0x5, 0x0, 0x0);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 2, 1, 0xa, 0x5, 0xa, 0x5, 0xa, 0x5, 0xa, 0x0);
			Thread.sleep(20);
			send(Cmd.CREATE_CHAR_IN_BANK, 2, 0, 0xa, 0x5, 0xa, 0x5, 0xa, 0x5, 0xa, 0x5);
			Thread.sleep(20);
			send(Cmd.SELECT_BANK, 1);
			Thread.sleep(20);
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
	
	private static synchronized void send(SerialPort serialPort, Cmd cmd, int... params) throws SerialPortException {
		if (isActive()) {
			send(serialPort, cmd);
			if (params!=null) {
				serialPort.writeIntArray(params);
			}
		}
		return;
	}
	
	private static void sendString(SerialPort serialPort, String s) throws SerialPortException {
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
				Thread.sleep(80);
				instance.serialPort.writeString(" Bye-bye!                 ");
				LCD.send(instance.serialPort, Cmd.COLOR, 0xff, 0, 0);
				instance.serialPort.closePort();
			} catch (SerialPortException | InterruptedException e) {
				log.debug("Strange", e);
			} 
		}
	}
	
	private static enum Cmd {
		HOME(0x48), 
		MOVE_TO(0x47), 
		CLR(0x58), 
		BRIGHTNESS(0x99), 
		CONTRAST(0x50), 
		COLOR(0xD0),
		SCROLL_OFF(0x52), 
		SELECT_BANK(0xC0), 
		CREATE_CHAR_IN_BANK(0xC1);
		
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
			serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_RTSCTS_OUT);
			
			// splash screen text
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0x40 });
			Thread.sleep(20);
			for (byte b :("----SynthPi-----"+"privatepublic.de").getBytes(StandardCharsets.US_ASCII)) {
				serialPort.writeByte(b);
				Thread.sleep(100);
			}
			//                      ----------------
			
			
		} catch (SerialPortException | InterruptedException e) {
			log.debug("Could not open serial port to LCD display: {}", e.getMessage());
		}
	}
	
	
	
}
