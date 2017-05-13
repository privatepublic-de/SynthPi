package de.privatepublic.pi.synth.comm;

import java.awt.Color;
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
		return instance!=null && instance.outputThread!=null;
	}
	
	private OutputThread outputThread;	
	private SerialPort serialPort;	
	
	private LCD(String portName) {
		serialPort = new SerialPort(portName);
		try {
			serialPort.openPort();
			serialPort.setParams(SerialPort.BAUDRATE_9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_RTSCTS_OUT);
			serialPort.writeIntArray(new int[] {0xFE, 0x58}); // clear screen
			serialPort.writeIntArray(new int[] {0xFE, 0x52}); // autoscroll off
			serialPort.writeIntArray(new int[] {0xFE, 0x50, 200}); // contrast
			outputThread = new OutputThread(serialPort);
    		outputThread.start();
    		log.debug("Started LCD update thread");
    		outputThread.addMessage(new Message("SynthPi is","up and running!",Color.DARK_GRAY, Message.Type.LONG));
		} catch (SerialPortException e) {
			log.debug("Could not open serial port to LCD display: {}", e.getMessage());
		}
	}
	

	private static class Message {
		
		private static Color recentCol = null;
		
		enum Type { NORMAL, LONG, STATUS, PARAM };
		private String line1;
		private String line2;
		private Color color;
		private Type type;
		private boolean status = false;
		private static boolean recentStatus = false;
		private int upperChar = 0x20;
		private int lowerChar = 0x20;

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
		
		public Message(boolean status) {
			type = Type.STATUS;
			this.status = status;
		}
		
		public Message(int paramindex) {
			type = Type.PARAM;
			line1 = FancyParam.nameOf(paramindex);
			line2 = FancyParam.valueOf(paramindex);
			color = FancyParam.colorOf(paramindex);
			int v = (int)(P.VAL[paramindex]*16);
			if (v>7) {
				upperChar = v-8;
				lowerChar = 7;
			}
			else {
				lowerChar = v;
			}
		}
		
		public void send(SerialPort serialPort) throws SerialPortException, InterruptedException {
			switch(type) {
			case NORMAL:
			case LONG:
			case PARAM:
				serialPort.writeIntArray(new int[] { 0xFE, 0x48 }); // home
				Thread.sleep(40);
				serialPort.writeString(getLine1());
				Thread.sleep(20);
				serialPort.writeIntArray(new int[] { 0xFE, 0x47, 1, 2 });
				serialPort.writeString(getLine2());
				Thread.sleep(20);
				if (type==Type.PARAM) {
					serialPort.writeIntArray(new int[] { 0xFE, 0x47, 16, 1 });
					serialPort.writeInt(upperChar);
					serialPort.writeIntArray(new int[] { 0xFE, 0x47, 16, 2 });
					serialPort.writeInt(lowerChar);
					Thread.sleep(20);
				}
				Color col = getColor();
				if (col!=null && !col.equals(recentCol)) {
					serialPort.writeIntArray(new int[] { 0xFE, 0xD0, col.getRed(), col.getGreen(), col.getBlue() });
					recentCol = col;
				}
				if (type==Message.Type.LONG) {
					Thread.sleep(1500);
				}
				else {
					Thread.sleep(40);
				}
				break;
			case STATUS:
				if (recentStatus!=status) {
					serialPort.writeIntArray(new int[] { 0xFE, 0x47, 15, 2 }); // set cursor
					Thread.sleep(40);
					serialPort.writeInt(status?'*':0x20);
					Thread.sleep(40);
					recentStatus = status;
				}
				break;
			}
		}

		public String getLine1() {
			return normalize(line1, type!=Type.STATUS?15:1);
		}

		public String getLine2() {
			return normalize(line2, 14);
		}

		public Color getColor() {
			return color;
		}
		
		public Type getType() {
			return type;
		}

		private String normalize(String s, int len) {
			if (s==null) {
				s="";
			}
			if (s.length()>len) {
				return s.substring(0, len);
			}
			if (type!=Type.STATUS) {
				return StringUtils.rightPad(s, len);
			}
			else {
				return StringUtils.leftPad(s, len);
			}
				
		}

	}

	private static class OutputThread extends Thread {
		private final BlockingQueue<Message> queue = new LinkedBlockingQueue<Message>();
		private SerialPort serialPort;
		private boolean running = true;
		private Timer timer;

		public OutputThread(SerialPort serialPort) {
			this.serialPort = serialPort;
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
										serialPort.writeIntArray(new int[] {0xFE, 0x99, 0x01});
									} catch (SerialPortException e) {
									} // brightness
								}
							}, 60000);
							serialPort.writeIntArray(new int[] {0xFE, 0x99, 0x80}); // brightness
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
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 0,   0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0xf });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 1,   0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0xf, 0xf });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 2,   0x0, 0x0, 0x0, 0x0, 0x0, 0xf, 0xf, 0xf });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 3,   0x0, 0x0, 0x0, 0x0, 0xf, 0xf, 0xf, 0xf });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 4,   0x0, 0x0, 0x0, 0xf, 0xf, 0xf, 0xf, 0xf });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 5,   0x0, 0x0, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 6,   0x0, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 7,   0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf, 0xf });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC0, 1});
			Thread.sleep(20);
		}

	}
	
	
	public static void shutdown() {
		if (isActive() && instance.serialPort.isOpened()) {
			instance.outputThread.running = false;
			instance.outputThread.interrupt();
			try {
				instance.serialPort.writeIntArray(new int[] {0xFE, 0x99, 0x20});
				instance.serialPort.writeIntArray(new int[] {0xFE, 0x58}); // clear screen
				Thread.sleep(80);
				instance.serialPort.writeIntArray(new int[] { 0xFE, 0x48 }); // home
				instance.serialPort.writeString("SynthPi finished  Bye-bye!");
				instance.serialPort.writeIntArray(new int[] { 0xFE, 0xD0, 0xff, 0, 0 });
				instance.serialPort.closePort();
			} catch (SerialPortException | InterruptedException e) {
				log.debug("Strange", e);
			} 
		}
	}
	
	
	
	
	public static void main(String[] args) {
		SerialPort serialPort = new SerialPort("/dev/cu.usbmodem14341");
		try {
			serialPort.openPort();
			serialPort.setParams(SerialPort.BAUDRATE_9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_RTSCTS_OUT);
			serialPort.writeIntArray(new int[] {0xFE, 0x58}); // clear screen
			serialPort.writeIntArray(new int[] {0xFE, 0x52}); // autoscroll off
			serialPort.writeIntArray(new int[] {0xFE, 0x50, 200}); // contrast
			serialPort.writeIntArray(new int[] {0xFE, 0x99, 0x80}); // brightness
			//
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 0,   0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0xf });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 1,   0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0xf, 0x0 });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 2,   0x0, 0x0, 0x0, 0x0, 0x0, 0xf, 0x0, 0x0 });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 3,   0x0, 0x0, 0x0, 0x0, 0xf, 0x0, 0x0, 0x0 });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 4,   0x0, 0x0, 0x0, 0xf, 0x0, 0x0, 0x0, 0x0 });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 5,   0x0, 0x0, 0xf, 0x0, 0x0, 0x0, 0x0, 0x0 });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 6,   0x0, 0xf, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0 });
			Thread.sleep(20);
			serialPort.writeIntArray(new int[] {0xFE, 0xC1, 1, 7,   0xf, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0 });
			Thread.sleep(20);
			
			serialPort.writeIntArray(new int[] {0xFE, 0xC0, 1});
			Thread.sleep(20);

			serialPort.writeIntArray(new int[] { 0, 1, 2, 3, 4, 5, 6, 7 });
			
			
		} catch (SerialPortException | InterruptedException e) {
			log.debug("Could not open serial port to LCD display: {}", e.getMessage());
		}
	}
	
	
	
}
