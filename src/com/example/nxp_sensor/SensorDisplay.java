package com.example.nxp_sensor;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.graphics.Color;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbAccessory;


import com.example.nxp_sensor.AccessoryControl.OpenStatus;
import com.example.nxp_sensor.AccessoryControl;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.app.Activity;
import android.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

public class SensorDisplay extends Activity {
	
	private AccessoryControl accessoryControl;
	
	private EditText txtboxTemp;
	private EditText txtboxHum;
	private EditText txtboxLum;

	
	/** TAG used for logging messages */
	private static final String TAG = "com.example.nxp_sensor";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sensor_display);
		
        /*
         * Register a receiver for permission (granted/not granted)
         * messages and Accessory detached messages.
         */
		accessoryControl = new AccessoryControl(this, handler);

        txtboxTemp = (EditText)findViewById(R.id.editTextTemp);
        txtboxHum = (EditText)findViewById(R.id.editTextHum);
        txtboxLum = (EditText)findViewById(R.id.editTextLum);

        IntentFilter filter = new IntentFilter(AccessoryControl.ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        registerReceiver(receiver, filter);		 
 

	}
	@Override
	protected void onResume() {
		super.onResume();
		
		Log.i(TAG, "onResume");
		
		/*
		 * The application has been resumed. Try to open
		 * and access the accessory.
		 */		
		
		disconnected();

		OpenStatus status = accessoryControl.open();
		if (status == OpenStatus.CONNECTED) {
			connected();
		}
		else if(status != OpenStatus.REQUESTING_PERMISSION 
				&& status != OpenStatus.NO_ACCESSORY) {
			showError(status);
		}
					
	}	
	@Override
	protected void onPause() {

		Log.i(TAG, "onPause");	
		
		/*
		 * The application is about to be paused/closed. Make sure
		 * the accessory gets a notification about this.
		 */
		
		accessoryControl.appIsClosing();				
		accessoryControl.close();
		super.onPause();
	
	}	
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		Log.i(TAG, "onDestroy");		
		
		/*
		 * The application is completely closed and removed from memory.
		 * Remove all resources which in this case means unregistering the
		 * broadcast receiver.
		 */
		
		unregisterReceiver(receiver);
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.sensor_display, menu);
		return true;
	}
	
	/**************************************************************************
	 * Anonymous classes 
	 *************************************************************************/	

	/*
	 * The Android UI Toolkit is not thread-safe which means that only the 
	 * UI Thread may manipulate the User Interface. 
	 * 
	 * A Handler allows a thread to send a message to another thread's 
	 * message queue. When the Handler is created it will be associated
	 * with the thread that is creating the Handler. For the Handler below
	 * this means the main (UI) thread.
	 * 
	 * Since messages from the Accessory are received by a separate thread
	 * those messages must be sent to the Handler below which is responsible
	 * for updating the UI.
	 */
	
	private final Handler handler = new Handler() {


		@Override
		public void handleMessage(Message msg) {

			switch(msg.what) {
			case AccessoryControl.MESSAGE_IN_SENDTEMP: 
				txtboxTemp.setText("" + msg.arg1);

				break;
			case AccessoryControl.MESSAGE_IN_SENDHUM: 
				txtboxHum.setText("" + msg.arg1);
				break;
			case AccessoryControl.MESSAGE_IN_SENDLIGHT: 
				txtboxLum.setText("" + msg.arg1);
				break;								
		
			}
		}
	};
	
	/**************************************************************************
	 * Private methods  
	 *************************************************************************/
	
	/**
	 * Indicate in the UI that an accessory is connected.
	 */
	private void connected() {
		setTitle("Accessory Connected");
		//topLayout.setBackgroundColor(Color.rgb(0, 50, 0));
	}
	
	/**
	 * Indicate in the UI that an accessory isn't connected.
	 */	
	private void disconnected() {
		setTitle("Accessory Disconnected");	
		//topLayout.setBackgroundColor(Color.BLACK);
	}
	
	/**
	 * Show an error dialog as a response to opening an accessory.
	 */	
	private void showError(OpenStatus status) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Error: " + status.toString());

		AlertDialog d = builder.create();
		d.show();
	}
	
	/*
	 * A fundamental part of Android applications is intents. Intents are 
	 * messages between components and applications. 
	 * 
	 * The Broadcast receiver handles broadcast intents and the receiver
	 * below has been register in onCreate above with a filter to receive
	 * Accessory Detached and permission actions.
	 */
	
	private final BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {

			String action = intent.getAction();
			if (AccessoryControl.ACTION_USB_PERMISSION.equals(action)) {
				Log.i(TAG, "BroadcastReceiver , ACTION_USB_PERMISSION");
				UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
				
				if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {	

					Log.i(TAG, "Permission Granted");
					OpenStatus status = accessoryControl.open(accessory);
					if (status == OpenStatus.CONNECTED) {
						connected();
					}
					else {
						showError(status);
					}
				}
				else {
					Log.i(TAG, "Permission NOT Granted");
					disconnected();
				}
				
				
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {		
				Log.i(TAG, "Detached");
				disconnected();				
				accessoryControl.close();
			}
			
			
		}
		
	};		
		

}
