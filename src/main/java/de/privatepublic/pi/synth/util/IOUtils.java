package de.privatepublic.pi.synth.util;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.Vector;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.slf4j.LoggerFactory;

public class IOUtils {
	
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(IOUtils.class);

	public static float[] readWavData(URL wavDataURL) {
		
		Vector<Float> outBuffer = new Vector<Float>(600);
		
		int totalFramesRead = 0;
		// somePathName is a pre-existing string whose value was
		// based on a user selection.
		try {
			AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavDataURL);
			int bytesPerFrame = audioInputStream.getFormat().getFrameSize();
			if (bytesPerFrame == AudioSystem.NOT_SPECIFIED) {
				// some audio formats may have unspecified frame size
				// in that case we may read any amount of bytes
				bytesPerFrame = 1;
			} 
			// Set an arbitrary buffer size of 1024 frames.
			int numBytes = 1024 * bytesPerFrame; 
			byte[] audioBytes = new byte[numBytes];
			try {
				int numBytesRead = 0;
				int numFramesRead = 0;
				// Try to read numBytes bytes from the file.
				while ((numBytesRead = audioInputStream.read(audioBytes)) != -1) {
					// Calculate the number of frames actually read.
					numFramesRead = numBytesRead / bytesPerFrame;
					totalFramesRead += numFramesRead;
					ByteBuffer bb = ByteBuffer.wrap(audioBytes, 0, numBytesRead);
					bb.order( ByteOrder.LITTLE_ENDIAN);
					while( bb.hasRemaining()) {
					   short vals = bb.getShort();
					   float val = (float)vals/Short.MAX_VALUE;
					   outBuffer.add(val);
					}
					
				}
			} catch (IOException e) {
				log.error("Error reading wav data", e);
			}
		} catch (IOException e) {
			log.error("Error reading wav data", e);
		} catch (UnsupportedAudioFileException e1) {
			log.error("Error reading wav data", e1);
		}
		float[] result = new float[totalFramesRead];
		for (int i=0;i<outBuffer.size();i++) {
			result[i] = outBuffer.elementAt(i);
		}
		return result;
	}

	public static void writeToFileBinary(String filename, float[] dob) {
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		DataOutputStream dos = null;
		try
		{
			fos = new FileOutputStream(filename);
			bos = new BufferedOutputStream(fos);
			dos = new DataOutputStream(bos);
			for (int i = 0; i < dob.length; i ++)
			{
				dos.writeFloat((float)dob[i]);
			}
			dos.close();
		}
		catch(Exception e)
		{
			System.out.println(e);
		}          
	}

	private static InetAddress inetAddressV4Cache = null;

	public static String localV4InetAddressDisplayString() {
		InetAddress addr = localV4InetAddress();
		if (addr!=null) {
			String [] parts = addr.toString().split("\\/");
			if (parts.length>1) {
				return parts[1];
			}
			else {
				return parts[0];
			}
		}
		return "127.0.0.1";
	}
	
	public static InetAddress localV4InetAddress() {
		if (inetAddressV4Cache!=null) {
			return inetAddressV4Cache;
		}
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()){
			    NetworkInterface current = interfaces.nextElement();
			    if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue;
			    Enumeration<InetAddress> addresses = current.getInetAddresses();
			    while (addresses.hasMoreElements()){
			        InetAddress current_addr = addresses.nextElement();
			        if (current_addr.isLoopbackAddress()) continue;
			        if (current_addr instanceof Inet4Address) {
			        	inetAddressV4Cache = current_addr;
			        }
			    }
			}
		} catch (SocketException e) {
		}
		return inetAddressV4Cache;
	}

}
