package com.dunderklubben.vesselcontrol;

import java.io.IOException;
import android.view.SurfaceView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.VideoView;

public class MainActivity extends Activity implements SensorEventListener  {
	PowerManager.WakeLock wakeLock;
	SharedPreferences sharedPrefs;
	
	private boolean paused;
	private byte lastX, lastY;
	
	//Controls
	private EditText txtIp;
	private Button btnConnect;
	private VesselClient client;
	private SensorManager sensorManager;
	private TextView lblNotification;
	
	//Settings
	private boolean stay_awake;
	private int threshold;
	
	private OnSharedPreferenceChangeListener settingsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
		  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
			  Log.d("SKIP", "Setting changed: " + key);
			  if(key.equalsIgnoreCase("stay_awake")) {
					stay_awake = prefs.getBoolean("stay_awake", true);
					if(stay_awake)
						wakeLock.acquire();
					else
						wakeLock.release();
				}		
				else if(key.equalsIgnoreCase("threshold")) {
			        threshold = Integer.parseInt(sharedPrefs.getString("threshold", "5"));
				}
		  }
	};

	
    @TargetApi(14)
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);        
        setContentView(R.layout.activity_main);
        
        //Get system services
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.registerOnSharedPreferenceChangeListener(settingsListener);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE); 
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);              
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "VesselControl");
        
        //Get preferences
        stay_awake = sharedPrefs.getBoolean("stay_awake", true);	
        threshold = Integer.parseInt(sharedPrefs.getString("threshold", "5"));
        
        //Get controls
        lblNotification = (TextView)findViewById(R.id.lblNotification);
        btnConnect = (Button)findViewById(R.id.btnConnect);
        txtIp = (EditText)findViewById(R.id.txtIp);        
        
        //Init misc values
        client = new VesselClient();
        lastX = 0; 
        lastY = 0;
        paused = false;
        
        if(stay_awake)
        	wakeLock.acquire();
        
        
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {    
    	switch (item.getItemId()) {   
			case R.id.menu_settings:   
				startActivity(new Intent(this, SettingsActivity.class));    
				return true;    
		}
		return false;
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	if(client.isConnected()) {
    		client.close();
    		sensorManager.unregisterListener(this);
    		paused = true;
    	}	
    	if(stay_awake)
    		wakeLock.release();
    	
    	PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(settingsListener);
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	if(stay_awake)
    		wakeLock.acquire();
    	
    	if(paused) {
    		connect(null);
    		paused = false;
    	}
    	PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(settingsListener);
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	if(stay_awake)
    		wakeLock.release();
    	if(client.isConnected()) {
    		client.close();
    		sensorManager.unregisterListener(this);
    		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(settingsListener);
    	}
    }
    //Click event that tries to connect to the given server
    public void connect(View v) {
    	lblNotification.setText("");
		btnConnect.setEnabled(false);
    	if(!client.isConnected()) {
    		if(client.connect(txtIp.getText().toString())) {
    			btnConnect.setText("Disconnect");
    			sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
    		} else {
    			lblNotification.setText("Could not connect to the server");
    		}
    	} else {
    		client.close();
    		sensorManager.unregisterListener(this);
    		btnConnect.setText("Connect");
    	}
    	btnConnect.setEnabled(true);
    }

	public void onAccuracyChanged(Sensor sensor, int accuracy) {		
	}
	//Event that fetches the accelerometer values and sends them to the arduino
	public void onSensorChanged(SensorEvent event) {
		if(client.isConnected()) {
			float x = event.values[0], y = event.values[1];
			byte byteX = mapX(x, 0, 180), byteY = mapY(y, 0, 200);
			//{number, value, direction}
			byte bX[] = {(byte)((int)1), byteX, (byte)((int)0)}, bY[] = {(byte)((int)4), byteY, (byte)((int)0)};
			
			if(byteX < lastX-threshold || byteX > lastX+threshold) {
				client.send(bX);
				lastX = byteX; 
			}
			if(byteY < lastY-threshold || byteY > lastY+threshold ) {
				client.send(bY);
				lastY = byteY;
			}
		}
		else
			sensorManager.unregisterListener(this);
	}
	//Maps a float value (-7.0 - 7.0) to a byte value (0 - 180)
	//keeping the ratio i.e -7.0 will become 0 and 7.0 will become 180 and
	//thus 0 will become 90 
	public byte mapX(float value, int bMIN, int bMAX) {
		float fMAX = 7, fMIN = -7;
		//int bMAX = 180, bMIN = 0;
		value = Math.max(fMIN,Math.min(fMAX, value));
		
		int result = (int)((value - fMIN) * (bMAX - bMIN) / (fMAX - fMIN) + bMIN);
		
		return (byte)result;
	}
	public byte mapY(float value, int bMIN, int bMAX) {
		float fMAX = 7, fMIN = -7;
		//int bMAX = 180, bMIN = 0;
		value = Math.max(fMIN,Math.min(fMAX, value));
		
		int result = (int)((value - fMIN) * (bMAX - bMIN) / (fMAX - fMIN) + bMIN);
		return (byte)result;
	}
}
