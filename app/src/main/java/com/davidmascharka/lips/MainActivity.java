package com.davidmascharka.lips;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.mascharka.indoorlocalization.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;

/**
 *  Copyright 2015 David Mascharka
 * 
 * This file is part of LIPS (Learning-based Indoor Positioning System).
 *
 *  LIPS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  LIPS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with LIPS.  If not, see <http://www.gnu.org/licenses/>.
 */

/** 
 * @author David Mascharka (david.mascharka@drake.edu)
 * 
 * Main activity for the IndoorLocalization application
 * 
 * Presents the user a grid of specifiable size to allow the user to collect data
 * Hardcoded WiFi access points for the building/room/area of interest are all that
 * needs to be changed to customize this class for another building
 * 
 * Some legacy code present that allows the user to switch buildings -> won't affect
 * the function of the code at all. Choices still come up but don't do anything important
 *
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener,
	SelectBuildingDialogFragment.SelectBuildingDialogListener,
	SelectRoomSizeDialogFragment.SelectRoomSizeDialogListener {
	
	// Preferences for storing user options such as room size and building
	private static final String PREFS_NAME = "IndoorLocalizationPrefs";
	
	// Code used when the user launches an intent to select a map
	private static final int GET_MAP_REQUEST = 0;

	//@author Mahesh Gaya added this tag for debugging purposes
	private String TAG = "Permission Test: ";

	// Class members for each sensor reading of interest
	private float accelerometerX;
	private float accelerometerY;
	private float accelerometerZ;
	private float magneticX;
	private float magneticY;
	private float magneticZ;
	private float light;
	private float rotationX;
	private float rotationY;
	private float rotationZ;
	private float[] rotation;
	private float[] inclination;
	private float[] orientation;
	
	private SensorManager sensorManager;
	private List<Sensor> sensorList;
	
	// Members for taking WiFi scans and storing the results
	private WifiManager wifiManager;
	private List<ScanResult> scanResults;
	private LinkedHashMap<String, Integer> wifiReadings;
	
	// Whether the user initiated a scan -> used to determine whether to store the datapoint
	// since the system or another app can initiate a scan at any time. Don't want to store
	// those points.
	private boolean userInitiatedScan;
	
	// Members for accessing location data
	private LocationManager locationManager;
	private LocationListener locationListener;
	private Location location;

	// User options
	private String building;
	private int roomWidth;
	private int roomLength;
	private boolean displayMap;

    private static final int MY_PERMISSIONS = 12;

	//@author Mahesh Gaya added these for permissions
	private static final int REQUEST_LOCATION = 110;
	private static final int REQUEST_WRITE_STORAGE = 112;
	private static final int REQUEST_WIFI = 114;
	
	// Will listen for broadcasts from the WiFi manager. When a scan has finished, the
	// onReceive method will be called which will store a datapoint if the user initiated
	// the scan.
	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateScanResults();
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestMyPermissions();
        }
		
		getPreferences();

		setContentView(R.layout.activity_main);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new MainFragment()).commit();
		}
		
		rotation = new float[9];
		inclination = new float[9];
		orientation = new float[3];
		
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		
		locationListener = new LocationListener() {

			@Override
			public void onLocationChanged(Location location) {
				updateLocation(location);
			}

			@Override
			public void onStatusChanged(String provider, int status,
					Bundle extras) {}

			@Override
			public void onProviderEnabled(String provider) {}

			@Override
			public void onProviderDisabled(String provider) {}
		};
		
		wifiReadings = new LinkedHashMap<String, Integer>();
		resetWifiReadings(building);
		
		userInitiatedScan = false;
	}
	/**
	 * Callback received when a permissions request has been completed.
	 * @author Mahesh Gaya
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
										   @NonNull int[] grantResults) {
		if (requestCode == REQUEST_LOCATION){
			//received permissions for GPS
			Log.i(TAG, "Received permissions for GPS");

		} else if (requestCode == REQUEST_WRITE_STORAGE){
			//received permissions for External storage
			Log.i(TAG, "Received permissions for writing to External Storage");

		} else if (requestCode == REQUEST_WIFI){
			//received permissions for WIFI
			Log.i(TAG, "Received permissions for WIFI");

		} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}

	}

   // @TargetApi(23)
    private void requestMyPermissions() {
        /* //this does not work
		if ((checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) ||
                (checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) !=
                        PackageManager.PERMISSION_GRANTED) ||
                (checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE) !=
                        PackageManager.PERMISSION_GRANTED) ||
                (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_WIFI_STATE, android.Manifest.permission.CHANGE_WIFI_STATE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS);
        }
        */
		//@author Mahesh Gaya added new permission statement
		if (ActivityCompat.shouldShowRequestPermissionRationale(this,
				Manifest.permission.ACCESS_FINE_LOCATION)
				|| ActivityCompat.shouldShowRequestPermissionRationale(this,
				Manifest.permission.ACCESS_WIFI_STATE)
				|| ActivityCompat.shouldShowRequestPermissionRationale(this,
				Manifest.permission.CHANGE_WIFI_STATE)
				|| ActivityCompat.shouldShowRequestPermissionRationale(this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE)){
			Toast.makeText(this, "GPS, WIFI, and Storage permissions are required for this app.", Toast.LENGTH_LONG).show();
		} else {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION );
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_WIFI_STATE,
				Manifest.permission.CHANGE_WIFI_STATE}, REQUEST_WIFI);
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
		}

    }
	
	@Override
	public void onResume() {
		super.onResume();
		
		// Set building textview to the building the user has selected
		TextView buildingText = (TextView) findViewById(R.id.text_building);
		buildingText.setText("Building: " + building);
		
		// Set room size textview to the room size the user has selected
		TextView roomSizeText = (TextView) findViewById(R.id.text_room_size);
		roomSizeText.setText("Room size: " + roomWidth + " x " + roomLength);
		
		// Set grid options
		GridView grid = (GridView) findViewById(R.id.gridView);
		grid.setGridSize(roomWidth, roomLength);
		grid.setDisplayMap(displayMap);
		
		// Register to get sensor updates from all the available sensors
		sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
		for (Sensor sensor : sensorList) {
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
		}
		
		// Enable wifi if it is not
		if (!wifiManager.isWifiEnabled()) {
			Toast.makeText(this, "WiFi not enabled. Enabling...", Toast.LENGTH_SHORT).show();
			wifiManager.setWifiEnabled(true);
		}
		
		// Request location updates from gps and the network
		//@author Mahesh Gaya added permission if-statment
		if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
			Log.i(TAG, "Permissions have NOT been granted. Requesting permissions.");
			requestMyPermissions();
		} else  {
			Log.i(TAG, "Permissions have already been granted. Getting location from GPS and Network");
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
					0, 0, locationListener);
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
					0, 0, locationListener);
		}

		
		registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
	}
	
	@Override
	public void onPause() {
		// Stop receiving updates
		sensorManager.unregisterListener(this);
		//@author Mahesh Gaya added permission if-statment
		if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE)
				!= PackageManager.PERMISSION_GRANTED
				|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
			Log.i(TAG, "Permissions have NOT been granted. Requesting permissions.");
			requestMyPermissions();
		} else  {
			Log.i(TAG, "Permissions have already been granted. Removing Updates from Location Manager");
            locationManager.removeUpdates(locationListener);
        }
		unregisterReceiver(receiver);
		
		savePreferences();
		
		super.onPause();
	}
	
	/* In order to make sure we have up-to-date WiFi readings, start a
	 * scan when user clicks the button. When the scan is finished, the
	 * data will be saved by the updateScanResults() method called from 
	 * the BroadcastReceiver.
	 */
	public void saveReading(View view) {
		userInitiatedScan = true;
		if (wifiManager.startScan()) {
			Toast.makeText(this, "Started WiFi scan", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(this, "Couldn't start WiFi scan", Toast.LENGTH_SHORT).show();
		}
		
		Button button = (Button) findViewById(R.id.button_confirm);
		button.setClickable(false);
	}
	
	// Helper method to keep track of the most up-to-date location
	private void updateLocation(Location location) {
		this.location = location;
	}
	
	/* 
	 * Writes all the information to the file /sdcard/indoor_localization/dataset.txt
	 * This is called from the BroadcastReceiver. There is one awkward situation as
	 * a result of doing it this way. This method will be called any time the application
	 * is running and a WiFi scan is performed. So if another app scans for WiFi or the
	 * system decides to perform a scan, this method will run. It also runs as soon as
	 * the application starts if WiFi is disabled - enabling it initiates a scan.
	 * 
	 * This issue is fixed for now by adding a boolean indicating whether the user initiated
	 * the scan from this application. Set to true on the button click and false at the end
	 * of this method. The results will only be saved if the scan was user-initiated
	 */
	private void updateScanResults() {
		if (userInitiatedScan) {
			//Toast.makeText(this, "Scan finished", Toast.LENGTH_SHORT).show();
			
			resetWifiReadings(building);
	
			scanResults = wifiManager.getScanResults();
			for (ScanResult result : scanResults) {
				if (wifiReadings.get(result.BSSID) != null) {
					wifiReadings.put(result.BSSID, result.level);
				} else { // BSSID wasn't programmed in - notify user
					//Toast.makeText(this, "This BSSID is new: " + result.BSSID,
					//		Toast.LENGTH_SHORT).show();
				}
			}
			
			// Get a filehandle for /sdcard/indoor_localization/dataset_BUILDING.txt
			File root = Environment.getExternalStorageDirectory();
			File dir = new File(root.getAbsolutePath() + "/indoor_localization");
			dir.mkdirs();
			File file = new File(dir, "dataset_" + building + ".txt");
			
			try {
				FileOutputStream outputStream = new FileOutputStream(file, true);
				PrintWriter writer = new PrintWriter(outputStream);
				
				writer.print(accelerometerX + "," + accelerometerY + "," + accelerometerZ +
						"," + magneticX + "," + magneticY + "," + magneticZ + "," + light +
						"," + rotationX + "," + rotationY + "," + rotationZ + "," +
						orientation[0] + "," + orientation[1] + "," + orientation[2]);
	
				for (String key : wifiReadings.keySet()) {
					writer.print("," + wifiReadings.get(key));
    				}
				
				if (location != null) {
					writer.print("," + location.getLatitude() + "," + location.getLongitude() + 
						"," + location.getAccuracy());
				} else {
					//@author Mahesh Gaya added permission if-statment
					if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
							!= PackageManager.PERMISSION_GRANTED
							|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
							!= PackageManager.PERMISSION_GRANTED
							|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE)
							!= PackageManager.PERMISSION_GRANTED
							|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
							!= PackageManager.PERMISSION_GRANTED) {
						Log.i(TAG, "Permissions have NOT been granted. Requesting permissions.");
						requestMyPermissions();
					} else  {
						Log.i(TAG, "Permissions have already been granted. Getting last known location from GPS");
                        location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    }

					if (location != null) {
						writer.print("," + location.getLatitude() + "," +
								location.getLongitude() + "," + location.getAccuracy());
					} else {
						//@author Mahesh Gaya added permission if-statment
						if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
								!= PackageManager.PERMISSION_GRANTED
								|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE)
								!= PackageManager.PERMISSION_GRANTED
								|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE)
								!= PackageManager.PERMISSION_GRANTED
								|| ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
								!= PackageManager.PERMISSION_GRANTED) {
							Log.i(TAG, "Permissions have NOT been granted. Requesting permissions.");
							requestMyPermissions();
						} else  {
							Log.i(TAG, "Permssions have already been granted. Getting last know location from network");
                            location = locationManager.getLastKnownLocation(
                                    LocationManager.NETWORK_PROVIDER);
                        }

						if (location != null) {
							writer.print("," + location.getLatitude() + "," +
									location.getLongitude() + "," + location.getAccuracy());
						} else {
							Toast.makeText(this, "Location was null", Toast.LENGTH_SHORT).show();
							writer.print(",?,?,?");
						}
					}
				}
				
				TextView xposition = (TextView) findViewById(R.id.text_xposition);
				TextView yposition = (TextView) findViewById(R.id.text_yposition);
				writer.print("," + xposition.getText().toString().substring(3));
				writer.print("," + yposition.getText().toString().substring(3));
				
				writer.print(" %" + (new Timestamp(System.currentTimeMillis())).toString());
				
				writer.print("\n\n");

				writer.flush();
				writer.close();
				
				Toast.makeText(this, "Done saving datapoint", Toast.LENGTH_SHORT).show();
				userInitiatedScan = false;
			} catch (Exception e) {
				Toast.makeText(this, "There was an error", Toast.LENGTH_SHORT).show();
				Log.e("ERROR", Log.getStackTraceString(e));
			}
		}
		
		Button button = (Button) findViewById(R.id.button_confirm);
		button.setClickable(true);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
		// Set the display map option to the appropriate check state
		menu.getItem(3).setChecked(displayMap);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
			case R.id.action_reset:
				resetDatafile();
				break;
			case R.id.action_select_building:
				showSelectBuildingDialog();
				break;
			case R.id.action_select_room_size:
				new SelectRoomSizeDialogFragment().show(getSupportFragmentManager(),
						"RoomSize");
				break;
			case R.id.action_display_map:
				displayMap = !displayMap;
				item.setChecked(displayMap);
				((GridView) findViewById(R.id.gridView)).setDisplayMap(displayMap);
				break;
			case R.id.action_select_map:
				// Launch an intent to select the map the user wants to display
				Intent selectMapIntent = new Intent();
				selectMapIntent.setAction(Intent.ACTION_GET_CONTENT);
				selectMapIntent.setType("image/*");
				selectMapIntent.addCategory(Intent.CATEGORY_OPENABLE);
				
				if (selectMapIntent.resolveActivity(getPackageManager()) != null) {
					startActivityForResult(selectMapIntent, GET_MAP_REQUEST);
				}
				break;
			case R.id.action_start_tracker:
				Intent intent = new Intent(this, TrackerActivity.class);
				startActivity(intent);
				break;
			default:
				super.onOptionsItemSelected(item);
				break;
		}
		return true;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == GET_MAP_REQUEST && resultCode == RESULT_OK) {
			// The image request was handled fine. Set the map the user wants
			
			Uri selectedMapUri = data.getData();
			((GridView) findViewById(R.id.gridView)).setMapUri(selectedMapUri);
		}
	}
	
	private void showSelectBuildingDialog() {
		DialogFragment dialog = new SelectBuildingDialogFragment();
		dialog.show(getSupportFragmentManager(), "SelectBuildingDialogFragment");
	}
	
	@Override
	public void onBuildingChanged(String building) {
		this.building = building;
		
		TextView buildingText = (TextView) findViewById(R.id.text_building);
		buildingText.setText("Building: " + building);
	}

	@Override
	public void onRoomSizeChanged(int width, int length) {
		roomWidth = width;
		roomLength = length;
		
		GridView grid = (GridView) findViewById(R.id.gridView);
		grid.setGridSize(roomWidth, roomLength);
		
		TextView roomSizeText = (TextView) findViewById(R.id.text_room_size);
		roomSizeText.setText("Room size: " + roomWidth + " x " + roomLength);
	}
	
	// Resets the data file to blank with the device and order of data as a header
	private void resetDatafile() {
		File root = Environment.getExternalStorageDirectory();
		File dir = new File(root.getAbsolutePath() + "/indoor_localization");
		dir.mkdirs();
		File file = new File(dir, "dataset_" + building + ".txt");
		try {
			FileOutputStream outputStream = new FileOutputStream(file);
			PrintWriter writer = new PrintWriter(outputStream);
			writer.println("%Data collected by " + android.os.Build.MODEL + 
					"\n%Format of data: Accelerometer X, Accelerometer Y, Accelerometer Z, " +
					"Magnetic X, Magnetic Y, Magnetic Z, Light, Rotation X, Rotation Y, " +
					"Rotation Z, Orientation X, Orientation Y, Orientation Z, WIFI NETWORKS " +
					"BSSID, Frequency, Signal level, Latitude, Longitude\n\n");
			writer.flush();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static class MainFragment extends Fragment {
		public MainFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container, false);
			return rootView;
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		/*
		 * Because Android doesn't let us query a sensor reading whenever we want so
		 * we have to keep track of the readings at all times. Here we just update
		 * the class members with the values associated with each reading we're
		 * interested in.
		 */
		switch (event.sensor.getType()) {
			case Sensor.TYPE_ACCELEROMETER:
				accelerometerX = event.values[0];
				accelerometerY = event.values[1];
				accelerometerZ = event.values[2];
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				magneticX = event.values[0];
				magneticY = event.values[1];
				magneticZ = event.values[2];
				break;
			case Sensor.TYPE_LIGHT:
				light = event.values[0];
				break;
			case Sensor.TYPE_ROTATION_VECTOR:
				rotationX = event.values[0];
				rotationY = event.values[1];
				rotationZ = event.values[2];
				break;
			default:
				break;
		}
		
		SensorManager.getRotationMatrix(rotation, inclination, 
				new float[] {accelerometerX, accelerometerY, accelerometerZ}, 
				new float[] {magneticX, magneticY, magneticZ});
		orientation = SensorManager.getOrientation(rotation, orientation);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	
	private void savePreferences() {
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		
		SharedPreferences.Editor editor = prefs.edit();
		
		editor.putInt(getPackageName() + ".width", roomWidth);
		editor.putInt(getPackageName() + ".length", roomLength);
		editor.putString(getPackageName() + ".building", building);
		editor.putBoolean(getPackageName() + ".displayMap", displayMap);
		
		editor.commit();
	}
	
	private void getPreferences() {
		SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
		
		roomWidth = prefs.getInt(getPackageName() + ".width", 7);
		roomLength = prefs.getInt(getPackageName() + ".length", 9);
		building = prefs.getString(getPackageName() + ".building", "Howard");
		displayMap = prefs.getBoolean(getPackageName() + ".displayMap", false);
	}
	
	// TODO make pretty
	private void resetWifiReadings(String building) {
		// Empty out the wifi readings hashmap. Otherwise if you switch buildings in in the
		// middle of a session the access points for both buildings will be stored to the
		// data file and mess up the arff file
		wifiReadings.clear();
		
		// The readings ending in :00, :01, :02, and :03 are in the 2.4 GHz band
		// The readings ending in :0c, :0d, :0e, and :0f are in the 5 GHz band
		switch (building) {
			case "Howard":
				wifiReadings.put("00:00:00:00:00:00", 0);

				wifiReadings.put("00:18:74:88:d4:00", 0);
				wifiReadings.put("00:18:74:88:d4:01", 0);
				wifiReadings.put("00:18:74:88:d4:02", 0);
				wifiReadings.put("00:18:74:88:d4:03", 0);

				wifiReadings.put("00:18:74:88:d4:0c", 0);
				wifiReadings.put("00:18:74:88:d4:0d", 0);
				wifiReadings.put("00:18:74:88:d4:0e", 0);
				wifiReadings.put("00:18:74:88:d4:0f", 0);

				wifiReadings.put("00:18:74:89:95:70", 0);
				wifiReadings.put("00:18:74:89:95:71", 0);
				wifiReadings.put("00:18:74:89:95:72", 0);
				wifiReadings.put("00:18:74:89:95:73", 0);

				wifiReadings.put("00:18:74:89:95:7c", 0);
				wifiReadings.put("00:18:74:89:95:7d", 0);
				wifiReadings.put("00:18:74:89:95:7e", 0);
				wifiReadings.put("00:18:74:89:95:7f", 0);

				wifiReadings.put("00:18:74:89:ab:10", 0);
				wifiReadings.put("00:18:74:89:ab:11", 0);
				wifiReadings.put("00:18:74:89:ab:12", 0);
				wifiReadings.put("00:18:74:89:ab:13", 0);

				wifiReadings.put("00:18:74:89:ab:1c", 0);
				wifiReadings.put("00:18:74:89:ab:1d", 0);
				wifiReadings.put("00:18:74:89:ab:1e", 0);
				wifiReadings.put("00:18:74:89:ab:1f", 0);

				wifiReadings.put("00:18:74:8b:ce:30", 0);
				wifiReadings.put("00:18:74:8b:ce:31", 0);
				wifiReadings.put("00:18:74:8b:ce:32", 0);
				wifiReadings.put("00:18:74:8b:ce:33", 0);

				wifiReadings.put("00:18:74:8b:d6:80", 0);
				wifiReadings.put("00:18:74:8b:d6:81", 0);
				wifiReadings.put("00:18:74:8b:d6:82", 0);
				wifiReadings.put("00:18:74:8b:d6:83", 0);

				wifiReadings.put("00:18:74:8b:d6:8c", 0);
				wifiReadings.put("00:18:74:8b:d6:8d", 0);
				wifiReadings.put("00:18:74:8b:d6:8e", 0);
				wifiReadings.put("00:18:74:8b:d6:8f", 0);

				wifiReadings.put("00:1b:90:54:d2:d0", 0);
				wifiReadings.put("00:1b:90:54:d2:d1", 0);
				wifiReadings.put("00:1b:90:54:d2:d2", 0);
				wifiReadings.put("00:1b:90:54:d2:d3", 0);

				wifiReadings.put("00:1b:90:54:d2:dc", 0);
				wifiReadings.put("00:1b:90:54:d2:dd", 0);
				wifiReadings.put("00:1b:90:54:d2:de", 0);
				wifiReadings.put("00:1b:90:54:d2:df", 0);

				wifiReadings.put("00:24:37:8a:43:00", 0);

				wifiReadings.put("00:25:9c:6a:9b:fe", 0);

				wifiReadings.put("00:26:98:fe:1a:30", 0);
				wifiReadings.put("00:26:98:fe:1a:31", 0);
				wifiReadings.put("00:26:98:fe:1a:32", 0);
				wifiReadings.put("00:26:98:fe:1a:33", 0);

				wifiReadings.put("00:30:44:16:9a:5d", 0);

				wifiReadings.put("02:90:7f:b0:9c:fa", 0);

				wifiReadings.put("22:c4:e1:de:e2:1c", 0);

				wifiReadings.put("30:85:a9:8b:e1:08", 0);

				wifiReadings.put("40:8b:07:60:bc:f4", 0);

				wifiReadings.put("40:8b:07:dd:d4:e4", 0);

				wifiReadings.put("66:2a:2f:53:7c:99", 0);

				wifiReadings.put("bc:14:01:5c:a1:88", 0);

				wifiReadings.put("c8:b3:73:25:11:29", 0);

				wifiReadings.put("d0:57:4c:08:f8:01", 0);
				wifiReadings.put("d0:57:4c:08:f8:02", 0);
				wifiReadings.put("d0:57:4c:08:f8:03", 0);

				wifiReadings.put("fe:b3:a2:b7:87:99", 0);

				wifiReadings.put("fe:ff:a8:cb:ae:ad", 0);
				break;
			case "Cowles":
				wifiReadings.put("00:17:0f:8d:c3:e0", 0);
				wifiReadings.put("00:17:0f:8d:c3:e1", 0);
				wifiReadings.put("00:17:0f:8d:c3:e2", 0);
				wifiReadings.put("00:17:0f:8d:c3:e3", 0);

				wifiReadings.put("00:17:0f:8d:c3:f0", 0);
				wifiReadings.put("00:17:0f:8d:c3:f1", 0);
				wifiReadings.put("00:17:0f:8d:c3:f2", 0);
				wifiReadings.put("00:17:0f:8d:c3:f3", 0);

				wifiReadings.put("00:18:74:88:df:20", 0);
				wifiReadings.put("00:18:74:88:df:21", 0);
				wifiReadings.put("00:18:74:88:df:22", 0);
				wifiReadings.put("00:18:74:88:df:23", 0);

				wifiReadings.put("00:18:74:89:58:e0", 0);
				wifiReadings.put("00:18:74:89:58:e1", 0);
				wifiReadings.put("00:18:74:89:58:e2", 0);
				wifiReadings.put("00:18:74:89:58:e3", 0);
				wifiReadings.put("00:18:74:89:58:ec", 0);
				wifiReadings.put("00:18:74:89:58:ed", 0);
				wifiReadings.put("00:18:74:89:58:ee", 0);
				wifiReadings.put("00:18:74:89:58:ef", 0);

				wifiReadings.put("00:18:74:89:59:90", 0);
				wifiReadings.put("00:18:74:89:59:91", 0);
				wifiReadings.put("00:18:74:89:59:92", 0);
				wifiReadings.put("00:18:74:89:59:93", 0);
				wifiReadings.put("00:18:74:89:59:9c", 0);
				wifiReadings.put("00:18:74:89:59:9d", 0);
				wifiReadings.put("00:18:74:89:59:9e", 0);
				wifiReadings.put("00:18:74:89:59:9f", 0);

				wifiReadings.put("00:18:74:89:a9:20", 0);
				wifiReadings.put("00:18:74:89:a9:21", 0);
				wifiReadings.put("00:18:74:89:a9:22", 0);
				wifiReadings.put("00:18:74:89:a9:23", 0);
				wifiReadings.put("00:18:74:89:a9:2c", 0);
				wifiReadings.put("00:18:74:89:a9:2d", 0);
				wifiReadings.put("00:18:74:89:a9:2e", 0);
				wifiReadings.put("00:18:74:89:a9:2f", 0);

				wifiReadings.put("00:18:74:8b:90:f0", 0);
				wifiReadings.put("00:18:74:8b:90:f1", 0);
				wifiReadings.put("00:18:74:8b:90:f2", 0);
				wifiReadings.put("00:18:74:8b:90:f3", 0);
				wifiReadings.put("00:18:74:8b:90:fc", 0);
				wifiReadings.put("00:18:74:8b:90:fd", 0);
				wifiReadings.put("00:18:74:8b:90:fe", 0);
				wifiReadings.put("00:18:74:8b:90:ff", 0);

				wifiReadings.put("00:18:74:8b:c8:d0", 0);
				wifiReadings.put("00:18:74:8b:c8:d1", 0);
				wifiReadings.put("00:18:74:8b:c8:d2", 0);
				wifiReadings.put("00:18:74:8b:c8:d3", 0);
				wifiReadings.put("00:18:74:8b:c8:dc", 0);
				wifiReadings.put("00:18:74:8b:c8:dd", 0);
				wifiReadings.put("00:18:74:8b:c8:de", 0);
				wifiReadings.put("00:18:74:8b:c8:df", 0);

				wifiReadings.put("00:18:74:8b:cb:b0", 0);
				wifiReadings.put("00:18:74:8b:cb:b1", 0);
				wifiReadings.put("00:18:74:8b:cb:b2", 0);
				wifiReadings.put("00:18:74:8b:cb:b3", 0);
				wifiReadings.put("00:18:74:8b:cb:bc", 0);
				wifiReadings.put("00:18:74:8b:cb:bd", 0);
				wifiReadings.put("00:18:74:8b:cb:be", 0);
				wifiReadings.put("00:18:74:8b:cb:bf", 0);

				wifiReadings.put("00:18:74:8b:d0:f0", 0);
				wifiReadings.put("00:18:74:8b:d0:f1", 0);
				wifiReadings.put("00:18:74:8b:d0:f2", 0);
				wifiReadings.put("00:18:74:8b:d0:f3", 0);
				wifiReadings.put("00:18:74:8b:d0:fc", 0);
				wifiReadings.put("00:18:74:8b:d0:fd", 0);
				wifiReadings.put("00:18:74:8b:d0:fe", 0);
				wifiReadings.put("00:18:74:8b:d0:ff", 0);

				wifiReadings.put("00:18:74:8b:d5:30", 0);
				wifiReadings.put("00:18:74:8b:d5:31", 0);
				wifiReadings.put("00:18:74:8b:d5:32", 0);
				wifiReadings.put("00:18:74:8b:d5:33", 0);
				wifiReadings.put("00:18:74:8b:d5:3c", 0);
				wifiReadings.put("00:18:74:8b:d5:3d", 0);
				wifiReadings.put("00:18:74:8b:d5:3e", 0);
				wifiReadings.put("00:18:74:8b:d5:3f", 0);

				wifiReadings.put("00:1c:0f:83:42:f0", 0);
				wifiReadings.put("00:1c:0f:83:42:f1", 0);
				wifiReadings.put("00:1c:0f:83:42:f2", 0);
				wifiReadings.put("00:1c:0f:83:42:f3", 0);
				wifiReadings.put("00:1c:0f:83:42:fc", 0);
				wifiReadings.put("00:1c:0f:83:42:fd", 0);
				wifiReadings.put("00:1c:0f:83:42:fe", 0);
				wifiReadings.put("00:1c:0f:83:42:ff", 0);

				wifiReadings.put("00:1c:57:88:31:f0", 0);
				wifiReadings.put("00:1c:57:88:31:f1", 0);
				wifiReadings.put("00:1c:57:88:31:f2", 0);
				wifiReadings.put("00:1c:57:88:31:f3", 0);
				wifiReadings.put("00:1c:57:88:31:fc", 0);
				wifiReadings.put("00:1c:57:88:31:fd", 0);
				wifiReadings.put("00:1c:57:88:31:fe", 0);
				wifiReadings.put("00:1c:57:88:31:ff", 0);

				wifiReadings.put("00:24:c3:32:dc:20", 0);
				wifiReadings.put("00:24:c3:32:dc:21", 0);
				wifiReadings.put("00:24:c3:32:dc:22", 0);
				wifiReadings.put("00:24:c3:32:dc:23", 0);
				wifiReadings.put("00:24:c3:32:dc:2c", 0);
				wifiReadings.put("00:24:c3:32:dc:2d", 0);
				wifiReadings.put("00:24:c3:32:dc:2e", 0);
				wifiReadings.put("00:24:c3:32:dc:2f", 0);

				wifiReadings.put("00:27:0d:eb:c2:c0", 0);
				wifiReadings.put("00:27:0d:eb:c2:c1", 0);
				wifiReadings.put("00:27:0d:eb:c2:c2", 0);
				wifiReadings.put("00:27:0d:eb:c2:c3", 0);
				wifiReadings.put("00:27:0d:eb:c2:cc", 0);
				wifiReadings.put("00:27:0d:eb:c2:cd", 0);
				wifiReadings.put("00:27:0d:eb:c2:ce", 0);
				wifiReadings.put("00:27:0d:eb:c2:cf", 0);

				wifiReadings.put("08:cc:68:63:70:e0", 0);
				wifiReadings.put("08:cc:68:63:70:e1", 0);
				wifiReadings.put("08:cc:68:63:70:e2", 0);
				wifiReadings.put("08:cc:68:63:70:e3", 0);
				wifiReadings.put("08:cc:68:63:70:ec", 0);
				wifiReadings.put("08:cc:68:63:70:ed", 0);
				wifiReadings.put("08:cc:68:63:70:ee", 0);
				wifiReadings.put("08:cc:68:63:70:ef", 0);

				wifiReadings.put("08:cc:68:90:fd:00", 0);
				wifiReadings.put("08:cc:68:90:fd:01", 0);
				wifiReadings.put("08:cc:68:90:fd:02", 0);
				wifiReadings.put("08:cc:68:90:fd:03", 0);
				wifiReadings.put("08:cc:68:90:fd:0c", 0);
				wifiReadings.put("08:cc:68:90:fd:0d", 0);
				wifiReadings.put("08:cc:68:90:fd:0e", 0);
				wifiReadings.put("08:cc:68:90:fd:0f", 0);

				wifiReadings.put("08:cc:68:b9:7d:00", 0);
				wifiReadings.put("08:cc:68:b9:7d:01", 0);
				wifiReadings.put("08:cc:68:b9:7d:02", 0);
				wifiReadings.put("08:cc:68:b9:7d:03", 0);
				wifiReadings.put("08:cc:68:b9:7d:0c", 0);
				wifiReadings.put("08:cc:68:b9:7d:0d", 0);
				wifiReadings.put("08:cc:68:b9:7d:0e", 0);
				wifiReadings.put("08:cc:68:b9:7d:0f", 0);

				wifiReadings.put("08:cc:68:b9:8c:00", 0);
				wifiReadings.put("08:cc:68:b9:8c:01", 0);
				wifiReadings.put("08:cc:68:b9:8c:02", 0);
				wifiReadings.put("08:cc:68:b9:8c:03", 0);
				wifiReadings.put("08:cc:68:b9:8c:0c", 0);
				wifiReadings.put("08:cc:68:b9:8c:0d", 0);
				wifiReadings.put("08:cc:68:b9:8c:0e", 0);
				wifiReadings.put("08:cc:68:b9:8c:0f", 0);

				wifiReadings.put("08:cc:68:da:56:80", 0);
				wifiReadings.put("08:cc:68:da:56:81", 0);
				wifiReadings.put("08:cc:68:da:56:82", 0);
				wifiReadings.put("08:cc:68:da:56:83", 0);
				wifiReadings.put("08:cc:68:da:56:8c", 0);
				wifiReadings.put("08:cc:68:da:56:8d", 0);
				wifiReadings.put("08:cc:68:da:56:8e", 0);
				wifiReadings.put("08:cc:68:da:56:8f", 0);

				wifiReadings.put("20:3a:07:38:34:b0", 0);
				wifiReadings.put("20:3a:07:38:34:b1", 0);
				wifiReadings.put("20:3a:07:38:34:b2", 0);
				wifiReadings.put("20:3a:07:38:34:b3", 0);
				wifiReadings.put("20:3a:07:38:34:bc", 0);
				wifiReadings.put("20:3a:07:38:34:bd", 0);
				wifiReadings.put("20:3a:07:38:34:be", 0);
				wifiReadings.put("20:3a:07:38:34:bf", 0);
				break;
			case "Cartwright":
				wifiReadings.put("00:00:00:00:00:00", 0);
				wifiReadings.put("00:0f:66:2d:03:21", 0);
				wifiReadings.put("00:11:24:9e:89:b3", 0);
				wifiReadings.put("00:11:50:17:2d:37", 0);
				wifiReadings.put("00:12:17:cf:60:09", 0);
				wifiReadings.put("00:17:0f:8d:c3:e0", 0);
				wifiReadings.put("00:17:0f:8d:c3:e1", 0);
				wifiReadings.put("00:17:0f:8d:c3:e2", 0);
				wifiReadings.put("00:18:74:88:df:20", 0);
				wifiReadings.put("00:18:74:88:df:21", 0);
				wifiReadings.put("00:18:74:88:df:22", 0);
				wifiReadings.put("00:18:74:88:df:23", 0);
				wifiReadings.put("00:18:74:8b:da:70", 0);
				wifiReadings.put("00:18:74:8b:da:71", 0);
				wifiReadings.put("00:18:74:8b:da:72", 0);
				wifiReadings.put("00:18:74:8b:da:73", 0);
				wifiReadings.put("00:1c:0f:83:40:f0", 0);
				wifiReadings.put("00:1c:0f:83:40:f1", 0);
				wifiReadings.put("00:1c:0f:83:40:f2", 0);
				wifiReadings.put("00:1c:0f:83:40:f3", 0);
				wifiReadings.put("00:1c:0f:83:42:50", 0);
				wifiReadings.put("00:1c:0f:83:42:52", 0);
				wifiReadings.put("00:1c:0f:83:fd:50", 0);
				wifiReadings.put("00:1c:0f:83:fd:51", 0);
				wifiReadings.put("00:1c:0f:83:fd:52", 0);
				wifiReadings.put("00:1c:0f:83:fd:53", 0);
				wifiReadings.put("00:1c:0f:83:fe:b0", 0);
				wifiReadings.put("00:1c:0f:83:fe:b1", 0);
				wifiReadings.put("00:1c:0f:83:fe:b2", 0);
				wifiReadings.put("00:1c:0f:83:fe:b3", 0);
				wifiReadings.put("00:1c:57:88:13:91", 0);
				wifiReadings.put("00:1c:57:88:13:a0", 0);
				wifiReadings.put("00:1c:57:88:13:a1", 0);
				wifiReadings.put("00:1c:57:88:13:a2", 0);
				wifiReadings.put("00:1c:57:88:13:a3", 0);
				wifiReadings.put("00:1c:57:88:1d:30", 0);
				wifiReadings.put("00:1c:57:88:1d:31", 0);
				wifiReadings.put("00:1c:57:88:1d:32", 0);
				wifiReadings.put("00:1c:57:88:1d:33", 0);
				wifiReadings.put("00:1c:57:88:25:c0", 0);
				wifiReadings.put("00:1c:57:88:25:c2", 0);
				wifiReadings.put("00:1c:57:88:25:c3", 0);
				wifiReadings.put("00:1c:57:88:2f:30", 0);
				wifiReadings.put("00:1c:57:88:2f:31", 0);
				wifiReadings.put("00:1c:57:88:2f:32", 0);
				wifiReadings.put("00:1c:57:88:2f:33", 0);
				wifiReadings.put("00:1c:57:88:30:a0", 0);
				wifiReadings.put("00:1c:57:88:30:a2", 0);
				wifiReadings.put("00:1c:f0:8e:87:09", 0);
				wifiReadings.put("00:22:10:a7:ac:70", 0);
				wifiReadings.put("00:22:75:9d:d6:34", 0);
				wifiReadings.put("00:23:75:25:2c:d0", 0);
				wifiReadings.put("00:24:7b:aa:98:02", 0);
				wifiReadings.put("00:25:83:34:dd:d1", 0);
				wifiReadings.put("00:25:83:34:dd:d3", 0);
				wifiReadings.put("00:26:99:4f:5f:e0", 0);
				wifiReadings.put("00:26:99:4f:5f:e1", 0);
				wifiReadings.put("00:26:99:4f:5f:e2", 0);
				wifiReadings.put("00:26:99:4f:5f:e3", 0);
				wifiReadings.put("00:26:99:4f:5f:e4", 0);
				wifiReadings.put("00:27:0d:4a:72:20", 0);
				wifiReadings.put("00:27:0d:4a:72:21", 0);
				wifiReadings.put("00:27:0d:4a:72:22", 0);
				wifiReadings.put("00:27:0d:4a:74:00", 0);
				wifiReadings.put("00:27:0d:4a:74:01", 0);
				wifiReadings.put("00:27:0d:4a:74:02", 0);
				wifiReadings.put("00:27:0d:4a:74:0d", 0);
				wifiReadings.put("00:27:0d:4a:76:a0", 0);
				wifiReadings.put("00:27:0d:4a:76:a1", 0);
				wifiReadings.put("00:27:0d:4a:77:00", 0);
				wifiReadings.put("00:27:0d:4a:77:01", 0);
				wifiReadings.put("00:27:0d:4a:77:c0", 0);
				wifiReadings.put("00:27:0d:4a:77:c1", 0);
				wifiReadings.put("00:27:0d:4a:77:c2", 0);
				wifiReadings.put("00:27:0d:4a:7a:c0", 0);
				wifiReadings.put("00:27:0d:4a:7a:c1", 0);
				wifiReadings.put("00:27:0d:4a:7a:c2", 0);
				wifiReadings.put("00:27:0d:4a:7b:40", 0);
				wifiReadings.put("00:27:0d:4a:7c:31", 0);
				wifiReadings.put("00:27:0d:4a:7c:32", 0);
				wifiReadings.put("00:27:0d:4a:7c:e1", 0);
				wifiReadings.put("00:27:0d:4a:7c:e2", 0);
				wifiReadings.put("00:27:0d:4a:7c:ed", 0);
				wifiReadings.put("00:27:0d:4a:7c:ef", 0);
				wifiReadings.put("00:27:0d:4a:7d:80", 0);
				wifiReadings.put("00:27:0d:4a:7d:81", 0);
				wifiReadings.put("00:27:0d:4a:7d:82", 0);
				wifiReadings.put("00:27:0d:4a:7e:d0", 0);
				wifiReadings.put("00:27:0d:4a:7e:d1", 0);
				wifiReadings.put("00:27:0d:4a:7e:d2", 0);
				wifiReadings.put("00:27:0d:4a:7e:de", 0);
				wifiReadings.put("00:27:0d:4a:7e:df", 0);
				wifiReadings.put("00:27:0d:eb:bd:90", 0);
				wifiReadings.put("00:27:0d:eb:bd:91", 0);
				wifiReadings.put("00:27:0d:eb:bd:92", 0);
				wifiReadings.put("00:27:0d:eb:c2:c0", 0);
				wifiReadings.put("00:27:0d:eb:c2:c1", 0);
				wifiReadings.put("00:27:0d:eb:c2:c2", 0);
				wifiReadings.put("00:27:0d:eb:c2:cd", 0);
				wifiReadings.put("00:27:0d:eb:c2:ce", 0);
				wifiReadings.put("00:27:0d:eb:c2:cf", 0);
				wifiReadings.put("00:27:0d:eb:cb:a0", 0);
				wifiReadings.put("00:27:0d:eb:cb:a1", 0);
				wifiReadings.put("00:27:0d:eb:cb:a2", 0);
				wifiReadings.put("00:8e:f2:a8:ea:28", 0);
				wifiReadings.put("02:aa:4b:7c:a8:b8", 0);
				wifiReadings.put("08:86:3b:80:cf:6e", 0);
				wifiReadings.put("08:cc:68:63:72:b0", 0);
				wifiReadings.put("08:cc:68:63:72:b1", 0);
				wifiReadings.put("08:cc:68:63:72:b2", 0);
				wifiReadings.put("08:cc:68:63:72:b3", 0);
				wifiReadings.put("08:cc:68:63:72:bc", 0);
				wifiReadings.put("08:cc:68:63:72:bd", 0);
				wifiReadings.put("08:cc:68:63:72:be", 0);
				wifiReadings.put("08:cc:68:63:72:bf", 0);
				wifiReadings.put("08:cc:68:da:27:d0", 0);
				wifiReadings.put("08:cc:68:da:27:d1", 0);
				wifiReadings.put("08:cc:68:da:27:d2", 0);
				wifiReadings.put("08:cc:68:da:27:d3", 0);
				wifiReadings.put("08:cc:68:da:34:60", 0);
				wifiReadings.put("08:cc:68:da:34:61", 0);
				wifiReadings.put("08:cc:68:da:34:62", 0);
				wifiReadings.put("08:cc:68:da:34:63", 0);
				wifiReadings.put("08:cc:68:da:56:80", 0);
				wifiReadings.put("08:cc:68:da:56:81", 0);
				wifiReadings.put("08:cc:68:da:56:82", 0);
				wifiReadings.put("08:cc:68:da:56:83", 0);
				wifiReadings.put("1c:af:f7:2f:df:c0", 0);
				wifiReadings.put("20:25:64:76:3f:7e", 0);
				wifiReadings.put("20:aa:4b:4a:c4:db", 0);
				wifiReadings.put("20:aa:4b:4a:c4:dc", 0);
				wifiReadings.put("20:aa:4b:7c:a8:b7", 0);
				wifiReadings.put("20:bb:c0:e7:0c:40", 0);
				wifiReadings.put("20:bb:c0:e7:0c:41", 0);
				wifiReadings.put("20:bb:c0:e7:0c:42", 0);
				wifiReadings.put("20:bb:c0:e7:0c:43", 0);
				wifiReadings.put("20:bb:c0:e7:0c:4c", 0);
				wifiReadings.put("20:bb:c0:e7:0c:4d", 0);
				wifiReadings.put("20:bb:c0:e7:0c:4e", 0);
				wifiReadings.put("20:bb:c0:e7:0c:4f", 0);
				wifiReadings.put("2e:23:7d:b1:4c:7d", 0);
				wifiReadings.put("30:46:9a:5d:9f:72", 0);
				wifiReadings.put("38:60:77:1d:a2:f2", 0);
				wifiReadings.put("38:60:77:24:29:e3", 0);
				wifiReadings.put("38:60:77:24:29:f3", 0);
				wifiReadings.put("38:60:77:24:2b:27", 0);
				wifiReadings.put("38:60:77:24:2d:c3", 0);
				wifiReadings.put("38:60:77:24:2e:a3", 0);
				wifiReadings.put("38:60:77:24:2f:b3", 0);
				wifiReadings.put("38:60:77:24:30:57", 0);
				wifiReadings.put("38:60:77:24:30:63", 0);
				wifiReadings.put("38:60:77:24:30:db", 0);
				wifiReadings.put("38:60:77:24:30:df", 0);
				wifiReadings.put("38:60:77:24:73:d3", 0);
				wifiReadings.put("38:60:77:24:74:ab", 0);
				wifiReadings.put("38:60:77:24:92:2f", 0);
				wifiReadings.put("38:60:77:24:92:93", 0);
				wifiReadings.put("38:60:77:24:9b:e3", 0);
				wifiReadings.put("40:4a:03:f1:69:3b", 0);
				wifiReadings.put("40:8b:07:21:2f:14", 0);
				wifiReadings.put("44:94:fc:84:2e:d4", 0);
				wifiReadings.put("44:94:fc:84:2e:d6", 0);
				wifiReadings.put("48:f8:b3:0d:6d:fc", 0);
				wifiReadings.put("48:f8:b3:0d:6e:27", 0);
				wifiReadings.put("50:06:04:bb:53:20", 0);
				wifiReadings.put("50:06:04:bb:53:21", 0);
				wifiReadings.put("50:06:04:bb:53:22", 0);
				wifiReadings.put("50:06:04:bb:53:23", 0);
				wifiReadings.put("50:06:04:bb:53:2c", 0);
				wifiReadings.put("50:06:04:bb:53:2d", 0);
				wifiReadings.put("50:06:04:bb:53:2e", 0);
				wifiReadings.put("50:06:04:bb:53:2f", 0);
				wifiReadings.put("50:06:04:bb:83:50", 0);
				wifiReadings.put("50:06:04:bb:83:51", 0);
				wifiReadings.put("50:06:04:bb:83:52", 0);
				wifiReadings.put("50:06:04:bb:83:53", 0);
				wifiReadings.put("50:06:04:bb:83:5c", 0);
				wifiReadings.put("50:06:04:bb:83:5d", 0);
				wifiReadings.put("50:06:04:bb:83:5e", 0);
				wifiReadings.put("50:06:04:bb:83:5f", 0);
				wifiReadings.put("50:06:04:c2:d6:10", 0);
				wifiReadings.put("50:06:04:c2:d6:11", 0);
				wifiReadings.put("50:06:04:c2:d6:12", 0);
				wifiReadings.put("50:06:04:c2:d6:13", 0);
				wifiReadings.put("50:06:04:c2:d6:1c", 0);
				wifiReadings.put("50:06:04:c2:d6:1d", 0);
				wifiReadings.put("50:06:04:c2:d6:1e", 0);
				wifiReadings.put("50:06:04:c2:d6:1f", 0);
				wifiReadings.put("56:e7:d1:bf:61:c6", 0);
				wifiReadings.put("64:d9:89:d4:49:71", 0);
				wifiReadings.put("64:d9:89:d4:49:72", 0);
				wifiReadings.put("64:d9:89:d4:49:73", 0);
				wifiReadings.put("64:d9:89:d4:49:74", 0);
				wifiReadings.put("64:d9:89:d4:49:75", 0);
				wifiReadings.put("64:d9:89:d4:49:7a", 0);
				wifiReadings.put("64:d9:89:d4:49:7b", 0);
				wifiReadings.put("64:d9:89:d4:49:7c", 0);
				wifiReadings.put("64:d9:89:d4:49:7d", 0);
				wifiReadings.put("64:d9:89:d4:49:7e", 0);
				wifiReadings.put("64:d9:89:d4:49:7f", 0);
				wifiReadings.put("64:d9:89:d4:64:d1", 0);
				wifiReadings.put("64:d9:89:d4:64:d2", 0);
				wifiReadings.put("64:d9:89:d4:64:d3", 0);
				wifiReadings.put("64:d9:89:d4:64:d4", 0);
				wifiReadings.put("64:d9:89:d4:64:d5", 0);
				wifiReadings.put("64:d9:89:d4:64:da", 0);
				wifiReadings.put("64:d9:89:d4:64:db", 0);
				wifiReadings.put("64:d9:89:d4:64:dc", 0);
				wifiReadings.put("64:d9:89:d4:64:dd", 0);
				wifiReadings.put("64:d9:89:d4:64:de", 0);
				wifiReadings.put("64:d9:89:d4:64:df", 0);
				wifiReadings.put("64:d9:89:d4:9b:e1", 0);
				wifiReadings.put("64:d9:89:d4:9b:e2", 0);
				wifiReadings.put("64:d9:89:d4:9b:e3", 0);
				wifiReadings.put("64:d9:89:d4:9b:e4", 0);
				wifiReadings.put("64:d9:89:d4:9b:e5", 0);
				wifiReadings.put("64:d9:89:d4:9b:ea", 0);
				wifiReadings.put("64:d9:89:d4:9b:eb", 0);
				wifiReadings.put("64:d9:89:d4:9b:ec", 0);
				wifiReadings.put("64:d9:89:d4:9b:ed", 0);
				wifiReadings.put("64:d9:89:d4:9b:ee", 0);
				wifiReadings.put("64:d9:89:d4:9b:ef", 0);
				wifiReadings.put("64:d9:89:d4:a0:81", 0);
				wifiReadings.put("64:d9:89:d4:a0:82", 0);
				wifiReadings.put("64:d9:89:d4:a0:83", 0);
				wifiReadings.put("64:d9:89:d4:a0:84", 0);
				wifiReadings.put("64:d9:89:d4:a0:85", 0);
				wifiReadings.put("64:d9:89:d4:a0:8a", 0);
				wifiReadings.put("64:d9:89:d4:a0:8b", 0);
				wifiReadings.put("64:d9:89:d4:a0:8c", 0);
				wifiReadings.put("64:d9:89:d4:a0:8d", 0);
				wifiReadings.put("64:d9:89:d4:a0:8e", 0);
				wifiReadings.put("64:d9:89:d4:a0:8f", 0);
				wifiReadings.put("64:d9:89:d4:a2:61", 0);
				wifiReadings.put("64:d9:89:d4:a2:62", 0);
				wifiReadings.put("64:d9:89:d4:a2:63", 0);
				wifiReadings.put("64:d9:89:d4:a2:64", 0);
				wifiReadings.put("64:d9:89:d4:a2:65", 0);
				wifiReadings.put("64:d9:89:d4:a2:6a", 0);
				wifiReadings.put("64:d9:89:d4:a2:6b", 0);
				wifiReadings.put("64:d9:89:d4:a2:6c", 0);
				wifiReadings.put("64:d9:89:d4:a2:6d", 0);
				wifiReadings.put("64:d9:89:d4:a2:6e", 0);
				wifiReadings.put("64:d9:89:d4:a2:6f", 0);
				wifiReadings.put("68:b6:fc:4a:7a:78", 0);
				wifiReadings.put("68:b6:fc:4a:7a:79", 0);
				wifiReadings.put("68:b6:fc:4a:d9:78", 0);
				wifiReadings.put("68:b6:fc:4a:d9:79", 0);
				wifiReadings.put("68:b6:fc:a3:6a:68", 0);
				wifiReadings.put("68:b6:fc:a3:6a:69", 0);
				wifiReadings.put("68:b6:fc:a9:dd:e9", 0);
				wifiReadings.put("68:b6:fc:fe:16:58", 0);
				wifiReadings.put("68:b6:fc:fe:16:59", 0);
				wifiReadings.put("70:10:5c:82:b0:80", 0);
				wifiReadings.put("70:10:5c:82:b0:81", 0);
				wifiReadings.put("70:10:5c:82:b0:82", 0);
				wifiReadings.put("70:10:5c:82:b0:83", 0);
				wifiReadings.put("70:10:5c:82:b0:8c", 0);
				wifiReadings.put("70:10:5c:82:b0:8d", 0);
				wifiReadings.put("70:10:5c:82:b0:8e", 0);
				wifiReadings.put("70:10:5c:82:b0:8f", 0);
				wifiReadings.put("7c:05:07:01:64:7e", 0);
				wifiReadings.put("a0:cf:5b:a2:8e:21", 0);
				wifiReadings.put("a0:cf:5b:a2:8e:22", 0);
				wifiReadings.put("a0:cf:5b:a2:8e:23", 0);
				wifiReadings.put("a0:cf:5b:a2:8e:24", 0);
				wifiReadings.put("a0:cf:5b:a2:8e:25", 0);
				wifiReadings.put("a0:cf:5b:a2:8e:2a", 0);
				wifiReadings.put("a0:cf:5b:a2:8e:2b", 0);
				wifiReadings.put("a0:cf:5b:a2:8e:2c", 0);
				wifiReadings.put("a0:cf:5b:a2:8e:2d", 0);
				wifiReadings.put("a0:cf:5b:a2:8e:2e", 0);
				wifiReadings.put("a0:cf:5b:a2:8e:2f", 0);
				wifiReadings.put("b4:e9:b0:b5:a3:60", 0);
				wifiReadings.put("b4:e9:b0:b5:a3:61", 0);
				wifiReadings.put("b4:e9:b0:b5:a3:62", 0);
				wifiReadings.put("b4:e9:b0:b5:a3:63", 0);
				wifiReadings.put("b4:e9:b0:b5:a3:6c", 0);
				wifiReadings.put("b4:e9:b0:b5:a3:6d", 0);
				wifiReadings.put("b4:e9:b0:b5:a3:6e", 0);
				wifiReadings.put("b4:e9:b0:b5:a3:6f", 0);
				wifiReadings.put("b4:e9:b0:b5:c7:b0", 0);
				wifiReadings.put("b4:e9:b0:b5:c7:b1", 0);
				wifiReadings.put("b4:e9:b0:b5:c7:b2", 0);
				wifiReadings.put("b4:e9:b0:b5:c7:b3", 0);
				wifiReadings.put("b4:e9:b0:b5:c7:bc", 0);
				wifiReadings.put("b4:e9:b0:b5:c7:bd", 0);
				wifiReadings.put("b4:e9:b0:b5:c7:be", 0);
				wifiReadings.put("b4:e9:b0:b5:c7:bf", 0);
				wifiReadings.put("b4:e9:b0:b5:cf:20", 0);
				wifiReadings.put("b4:e9:b0:b5:cf:21", 0);
				wifiReadings.put("b4:e9:b0:b5:cf:22", 0);
				wifiReadings.put("b4:e9:b0:b5:cf:23", 0);
				wifiReadings.put("b4:e9:b0:b5:cf:2c", 0);
				wifiReadings.put("b4:e9:b0:b5:cf:2d", 0);
				wifiReadings.put("b4:e9:b0:b5:cf:2e", 0);
				wifiReadings.put("b4:e9:b0:b5:cf:2f", 0);
				wifiReadings.put("b4:e9:b0:b5:db:10", 0);
				wifiReadings.put("b4:e9:b0:b5:db:11", 0);
				wifiReadings.put("b4:e9:b0:b5:db:12", 0);
				wifiReadings.put("b4:e9:b0:b5:db:13", 0);
				wifiReadings.put("b4:e9:b0:b5:db:1c", 0);
				wifiReadings.put("b4:e9:b0:b5:db:1d", 0);
				wifiReadings.put("b4:e9:b0:b5:db:1e", 0);
				wifiReadings.put("b4:e9:b0:b5:db:1f", 0);
				wifiReadings.put("b8:62:1f:44:0b:53", 0);
				wifiReadings.put("dc:a5:f4:64:ac:41", 0);
				wifiReadings.put("e0:69:95:2f:42:71", 0);
				wifiReadings.put("e0:69:95:ff:f9:f9", 0);
				wifiReadings.put("e0:69:95:ff:fb:15", 0);
				wifiReadings.put("e8:40:f2:1d:c4:df", 0);
				wifiReadings.put("e8:40:f2:43:8e:09", 0);
				wifiReadings.put("ec:1a:59:8a:92:bd", 0);
				wifiReadings.put("ee:1a:59:8a:92:be", 0);
				wifiReadings.put("ee:43:f6:31:f7:34", 0);
				wifiReadings.put("f0:29:29:2b:d9:50", 0);
				wifiReadings.put("f0:29:29:2b:d9:51", 0);
				wifiReadings.put("f0:29:29:2b:d9:52", 0);
				wifiReadings.put("f0:29:29:2b:d9:53", 0);
				wifiReadings.put("f0:29:29:2b:d9:5c", 0);
				wifiReadings.put("f0:29:29:2b:d9:5d", 0);
				wifiReadings.put("f0:29:29:2b:d9:5e", 0);
				wifiReadings.put("f0:29:29:2b:d9:5f", 0);
				wifiReadings.put("f0:d1:a9:0e:b1:84", 0);
				wifiReadings.put("f8:4f:57:41:a8:80", 0);
				wifiReadings.put("f8:4f:57:41:a8:81", 0);
				wifiReadings.put("f8:4f:57:41:a8:82", 0);
				wifiReadings.put("f8:4f:57:41:a8:83", 0);
				wifiReadings.put("f8:4f:57:66:16:40", 0);
				wifiReadings.put("f8:4f:57:66:16:41", 0);
				wifiReadings.put("fe:f5:28:a8:b2:6c", 0);
				wifiReadings.put("fe:ff:a8:cb:ae:ad", 0);
				break;
			default:
				break;
		}
	}
}
