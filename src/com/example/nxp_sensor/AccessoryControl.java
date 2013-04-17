package com.example.nxp_sensor;



import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.content.Intent;
import android.content.IntentFilter;


import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbAccessory;



public class AccessoryControl {

	public static final String ACTION_USB_PERMISSION = 
			"com.example.nxp_sensor.action.USB_PERMISSION";	
	 
	/*
	 * Message indexes for messages sent from the Accessory
	 */
	public static final byte MESSAGE_IN_SENDTEMP = 0;
	public static final byte MESSAGE_IN_SENDHUM   = 1;	
	public static final byte MESSAGE_IN_SENDLIGHT   = 2;
	
	/*
	 * Message indexes for messages sent to the Accessory
	 */
	public static final byte MESSAGE_OUT_READTEMP    = 10;
	public static final byte MESSAGE_OUT_READHUM    = 11;
	public static final byte MESSAGE_OUT_READLIGHT     = 12;
	static int counter = 0 ;


	
	/*
	 * Special messages to/from the accessory
	 */
	
	
	/** 
	 * Sent to the accessory to indicate that the application is 
	 * ready to receive data.
	 */
	private static final byte MESSAGE_CONNECT = 98;	
	/** 
	 * Sent to the accessory to indicate that the application is 
	 * closing. The accessory acks the message using the same
	 * ID/index.
	 */
	private static final byte MESSAGE_DISCONNECT = 99;
	
	/*
	 * 
	 */
	public static final int MESSAGE_RGB_VAL_RED = 0x01;
	public static final int MESSAGE_RGB_VAL_BLUE = 0x02;
	public static final int MESSAGE_RGB_VAL_GREEN = 0x04;  
	
	
	/** Manufacturer string expected from the Accessory */
	private static final String ACC_MANUF = "NXP B.V.";
	/** Model string expected from the Accessory */
	private static final String ACC_MODEL = "NXP ultimate Sensor Board";
	
	/** Tag used for logging */
	private static final String TAG = "Acc Control nxp_sensor ";
	
	public enum OpenStatus {
		CONNECTED, REQUESTING_PERMISSION, UNKNOWN_ACCESSORY, NO_ACCESSORY, NO_PARCEL
	}
	
	
	private boolean permissionRequested = false;	
	private boolean isOpen = false;
	private UsbManager usbManager;
	private UsbAccessory[] accList ;
	private Context context;
	
	private ParcelFileDescriptor parcelFileDescriptor; 
	private FileOutputStream accOutputStream;	
	private Receiver receiver;
	private Handler handler;
	

	private AccessoryControl() {		
	}
	
	public AccessoryControl(Context context, Handler handler) {
		this();
		this.handler = handler;
		this.context = context;
		usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE); 		
		
	}



	/**
	 * Open the accessory and establish a connection. This method will
	 * check if there is any accessory connected to the device, request
	 * permissions if necessary and then establish input and output 
	 * streams to/from the accessory.
	 * 
	 * @return status of the Open call. 
	 */
	
	public OpenStatus open() {
		
		if (isOpen) {
			return OpenStatus.CONNECTED;
		}
	
 //remove , take acc from the intent from the USB service , fail when you are called from here
		UsbAccessory[] accList = usbManager.getAccessoryList();
		if (accList != null && accList.length > 0) {

			if (usbManager.hasPermission(accList[0])) {
				return open(accList[0]);
			}
			else if (!permissionRequested) {

				PendingIntent permissionIntent = PendingIntent.getBroadcast(
						context, 0,	new Intent(ACTION_USB_PERMISSION), 0);				
				
				Log.i(TAG, "Requesting USB permission");
				
				usbManager.requestPermission(accList[0], permissionIntent);
				permissionRequested = true;
				
				return OpenStatus.REQUESTING_PERMISSION;
			}
		}
				
		return OpenStatus.NO_ACCESSORY;
	}
	
	/**
	 * Open an accessory. This method should be called if an accessory
	 * object has already been obtained. The method will establish the 
	 * connection and start receiver thread.
	 * 
	 * @param accessory an instance of UsbAccessory
	 * @return status of the Open call
	 */
	public OpenStatus open(UsbAccessory accessory) {

		if (isOpen) {
			return OpenStatus.CONNECTED;
		}
			
		// check if it is a known and supported accessory 
		if (!ACC_MANUF.equals(accessory.getManufacturer()) 
				|| !ACC_MODEL.equals(accessory.getModel())) {
			
			Log.i(TAG, "Unknown accessory: " + accessory.getManufacturer() 
					+ ", " + accessory.getModel());
			
			return OpenStatus.UNKNOWN_ACCESSORY;
		}
		
		parcelFileDescriptor = usbManager.openAccessory(accessory); 
		if (parcelFileDescriptor != null) {
						
			accOutputStream = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());		
			receiver = new Receiver(new FileInputStream(parcelFileDescriptor.getFileDescriptor()));
			
			isOpen = true;
			new Thread(receiver).start();
			
			
			// notify the accessory that we are now ready to receive data
			writeCommand(MESSAGE_CONNECT, 0, 0);
			
			return OpenStatus.CONNECTED;
		}

		Log.i(TAG, "Couldn't get any ParcelDescriptor");
		return OpenStatus.NO_PARCEL;
	}
	
	/**
	 * Close the connection with the accessory.
	 */
	public void close() {
				
		if (!isOpen) {
			return;
		}
						
		permissionRequested = false;
		isOpen = false;
		
		try {
			receiver.close();
			accOutputStream.close();
			parcelFileDescriptor.close();
		} catch (IOException ioe) {
			Log.w(TAG, "Got exception when closing", ioe);
		}
	}
	
	/**
	 * Call this method when the application is closing. This method
	 * will notify the accessory that the application is about to
	 * be closed.
	 */
	public void appIsClosing() {
		if (!isOpen) {
			return;
		}
		
		Log.i(TAG, "Sending Disconnect message to accessory");
		writeCommand(AccessoryControl.MESSAGE_DISCONNECT, 0, 0);
		long t = System.currentTimeMillis() + 5000;
		try {
			while (!receiver.done && System.currentTimeMillis() < t) {
				Thread.sleep(200);
			}
		} catch(InterruptedException ie) {	
		}
		
	}
	
	/** 
	 * Write/send a command to the accessory. For simplicity all messages
	 * are 3 bytes long; command index and two data bytes.
	 * 
	 * @param cmd - command index
	 * @param hiVal - first data byte
	 * @param loVal - second data byte
	 */
	public void writeCommand(int cmd, int hiVal, int loVal) {
		byte[] buffer = new byte[3];
		
		if (!isOpen) {
			return;
		}
		
		// for simplicity all messages are 3 bytes long
		
		buffer[0] = (byte)cmd;
		buffer[1] = (byte)hiVal;
		buffer[2] = (byte)loVal;
		
		try {
			synchronized(accOutputStream) {
				accOutputStream.write(buffer);
			}
		} catch(IOException ioe) {		
		}

	}
	

	/*
	 * The receiver thread. This Thread is responsible for reading
	 * data from the Accessory and dispatching messages to the Handler
	 * (UI thread)
	 */


	private class Receiver implements Runnable  {

		private FileInputStream inputStream;
		private boolean done = false;
		
		Receiver(FileInputStream inputStream) {
			this.inputStream = inputStream;
		}
		
		public void run() {

			int numRead = 0;
			int pos = 0;
			
			byte[] buffer = new byte[16384];
					
			Log.i(TAG, "Receiver.run");
			
			try {
				
				while(!done) {
					
					numRead = inputStream.read(buffer);
					counter = counter + numRead ;
					pos = 0;
					int asInt ;

					
					//asInt = (buffer[0] & 0xFF) 
				    //        | ((buffer[1] & 0xFF) << 8) ;
					//result = Float.intBitsToFloat(asInt);
				
					while(pos < numRead) {
						int len = numRead - pos;
						
						switch(buffer[pos]) {
						case AccessoryControl.MESSAGE_IN_SENDTEMP:

							if (len >= 3) {
								Message m = Message.obtain(handler, AccessoryControl.MESSAGE_IN_SENDTEMP);
								asInt = (buffer[pos + 1] & 0xFF) 
							            | ((buffer[pos + 2] & 0xFF) << 8) 
							            | ((buffer[pos + 3] & 0xFF) << 16) 
							            | ((buffer[pos + 4]) << 24);
								//m.arg1 = Float.intBitsToFloat(asInt);
								m.obj = Float.intBitsToFloat(asInt);
	
								m.arg1 = toInt(buffer[pos + 1], buffer[pos + 2]);
								handler.sendMessage(m);
							}
							//pos += 3;
							pos += 5;
							break;
						case AccessoryControl.MESSAGE_IN_SENDHUM:

							if (len >= 2) {
								Message m = Message.obtain(handler, AccessoryControl.MESSAGE_IN_SENDHUM);
								m.arg1 = toInt(buffer[pos + 1], buffer[pos + 2]);
								asInt = (buffer[pos + 1] & 0xFF) 
							            | ((buffer[pos + 2] & 0xFF) << 8) 
							            | ((buffer[pos + 3] & 0xFF) << 16) 
							            | ((buffer[pos + 4]) << 24);
								//m.arg1 = Float.intBitsToFloat(asInt);
								m.obj = Float.intBitsToFloat(asInt);
								handler.sendMessage(m);
							}
							//pos += 3;
							pos += 5;
							break;				

						case AccessoryControl.MESSAGE_IN_SENDLIGHT:

							if (len >= 2) {
								Message m = Message.obtain(handler, AccessoryControl.MESSAGE_IN_SENDLIGHT);
								m.arg1 = toInt(buffer[pos + 1], buffer[pos + 2]);
								asInt = (buffer[pos + 1] & 0xFF) 
							            | ((buffer[pos + 2] & 0xFF) << 8) 
							            | ((buffer[pos + 3] & 0xFF) << 16) 
							            | ((buffer[pos + 4]) << 24);
								//m.arg1 = Float.intBitsToFloat(asInt);
								m.obj = Float.intBitsToFloat(asInt);
								handler.sendMessage(m);
							}
							//pos += 3;
							pos += 5;
							break;				
							
						case AccessoryControl.MESSAGE_DISCONNECT:

							Log.i(TAG, "Received Disconnect (ACK) message from accessory");
							
							// We want to make sure the Receive thread ends at this point
							// and doesn't start reading data
							done = true;
							pos = numRead;
							break;				
														
						default:
							// invalid message (or out of sync)

							Log.w(TAG, "Unknown command: " + buffer[pos]);
							writeCommand(AccessoryControl.MESSAGE_DISCONNECT, 0, 0);
							writeCommand(AccessoryControl.MESSAGE_CONNECT, 0, 0);
							pos += len;
							break;
						}
						
					}
					
					
				}
				
			} catch (IOException ioe) {
				Log.w(TAG, "IOException ");
				writeCommand(AccessoryControl.MESSAGE_DISCONNECT, 0, 0);
				writeCommand(AccessoryControl.MESSAGE_CONNECT, 0, 0);

				
			}
						
		}
		
		/**
		 * Close the receiver thread.
		 */
		public void close() {
			done = true;
			
			try {
				inputStream.close();
			} catch(IOException ioe) {				
			}
		}
		
		/** Convert two bytes to an integer */
		private int toInt(byte hi, byte lo) {
			return (( (int)(hi&0xff) << 8) | (int)(lo&0xff));
		}
				
	};
	

}
