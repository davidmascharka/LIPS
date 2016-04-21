package com.davidmascharka.lips;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import weka.classifiers.functions.RBFRegressor;
import weka.classifiers.lazy.KStar;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.mascharka.indoorlocalization.R;

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
 * Tracking side of the application. This is what would be implemented as part of an
 * enterprise-scale application that actually wanted to perform live localization.
 * 
 * Most of this is unnecessary in an application that just wants to keep track of user
 * position. Take out the buttons, the view, etc. The only part we really care about
 * in getting user position is the sensor values, the attributes/instances,
 * and the updateScanResults method
 *
 * It may be desirable to break this logic off to run in a service so that
 * localization can be performed in the background so the user experiences
 * no delay on re-opening the application or to notify the user when
 * they reach an area of interest in, for example, navigation in a mall
 *
 */
public class TrackerActivity extends ActionBarActivity implements SensorEventListener,
SelectPartitionDialogFragment.SelectPartitionDialogListener {
	
	/**
	 * The accelerometer reading in the X direction
	 */
	private float accelerometerX;

	/**
	 * The accelerometer reading in the Y direction
	 */
	private float accelerometerY;

	/**
	 * The accelerometer reading in the Z direction
	 */
	private float accelerometerZ;

	/**
	 * Reading from the X direction of the magnetic sensor
	 */
	private float magneticX;

	/**
	 * Reading from the Y direction of the magnetic sensor
	 */
	private float magneticY;

	/**
	 * Reading from the Z direction of the magnetic sensor
	 */
	private float magneticZ;

	/**
	 * Reading of the intensity from the light sensor
	 */
	private float light;

	/**
	 * Amount of rotation in the X direction
	 */
	private float rotationX;

	/**
	 * Amount of rotation in the Y direction
	 */
	private float rotationY;

	/**
	 * Amount of rotation in the Z direction
	 */
	private float rotationZ;

	/**
	 * Array holding rotation values - for querying
	 */
	private float[] rotation;

	/**
	 * Array holding inclination values - for querying
	 */
	private float[] inclination;

	/**
	 * Array holding orientation values - for querying
	 */
	private float[] orientation;

	/**
	 * Displays the user's x coordinate (predicted)
	 */
	private TextView xText;

	/**
	 * Displays the user's y coordinate (predicted)
	 */
	private TextView yText;

	/**
	 * Overlays a grid so the user can more easily see where they are
	 */
	private GridView grid;

	/**
	 * Lets us query the sensors in the device
	 */
	private SensorManager sensorManager;

	/**
	 * Holds what sensors are embedded in the device
	 */
	private List<Sensor> sensorList;

	/**
	 * Lets us actively scan for WiFi signals
	 */
	private WifiManager wifiManager;

	/**
	 * Holds the results of an active scan for WiFi signals
	 */
	private List<ScanResult> scanResults;

	/**
	 * Holds each BSSID of interest and its signal strength
	 */
	private LinkedHashMap<String, Integer> wifiReadings;

	/**
	 * Lets us set up a listener for location changes
	 */
	private LocationManager locationManager;

	/**
	 * Listens for changes to reported location
	 */
	private LocationListener locationListener;

	/**
	 * The user's current location, as reported by GPS/Network
	 */
	private Location location;

	/**
	 * Worker thread for performing localization
	 */
	Thread t;

	/**
	 * Will listen for broadcasts from the WiFi manager. When a scan has finished, the
	 *onReceive method will be called which will recalculate the user's position
	 */
	BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateScanResults();
		}
	};

	/**
	 * Which building is the user in?
	 */
	private String building;

	/**
	 * X coordinate to display to the user to walk to (for evaluating)
	 */
	private float nextX = 0.0f;

	/**
	 * Y coordinate to display to the user to walk to (for evaluating)
	 */
	private float nextY = 0.0f;

	/**
	 * Predicted X position of the user
	 */
	private float predictedX;

	/**
	 * Predicted Y position of the user
	 */
	private float predictedY;

	private Timestamp time;
	
	File file;
	File valuesFile;
	FileOutputStream outputStream;
	FileOutputStream valuesOutputStream;
	PrintWriter writer;
	PrintWriter valuesWriter;

	private double predictedPartition;

	/**
	 * K* classifier for predicting x position
	 */
	KStar classifierXKStar;

	/**
	 * RBF regression classifier for predicting x position
	 */
	RBFRegressor classifierXRBFRegressor;

	/**
	 * K* classifier for predicting y position
	 */
	KStar classifierYKStar;

	/**
	 * RBF regression classifier for predicting y position
	 */
	RBFRegressor classifierYRBFRegressor;

	/**
	 * K* classifier for just the lower left portion of the building, predicting x
	 */
	KStar partitionLowerLeftX = null;

	/**
	 * K* classifier for just the lower right portion of the building, predicting x
	 */
	KStar partitionLowerRightX = null;

	/**
	 * K* classifier for just the upper left portion of the building, predicting x
	 */
	KStar partitionUpperLeftX = null;

	/**
	 * K* classifier for just the upper right portion of the building, predicting x
	 */
	KStar partitionUpperRightX = null;

	/**
	 * K* classifier for just the middle portion of the building, predicting x
	 */
	KStar partitionMiddleX = null;

	/**
	 * K* classifier for just the lower left portion of the building, predicting y
	 */
	KStar partitionLowerLeftY = null;

	/**
	 * K* classifier for just the lower right portion of the building, predicting y
	 */
	KStar partitionLowerRightY = null;

	/**
	 * K* classifier for just the upper left portion of the building, predicting y
	 */
	KStar partitionUpperLeftY = null;

	/**
	 * K* classifier for just the upper right portion of the building, predicting y
	 */
	KStar partitionUpperRightY = null;

	/**
	 * K* classifier for just the middle portion of the building, predicting y
	 */
	KStar partitionMiddleY = null;

	/**
	 * Random forest model to predict which portion of the building the user is in
	 * Determines if the user is in upper left, lower left, upper right, lower right, middle
	 */
	RandomForest partitionClassifier;

	/**
	 * Instance for classifying X position
	 */
	Instances xInstances;

	/**
	 * Instance for classifying Y position
	 */
	Instances yInstances;

	/**
	 * Instance for classifying partition
	 */
	Instances partitionInstances;

	// List of attributes we use for prediction
	// TODO: make this not be ugly
	Attribute attrAccelX = new Attribute("accelerometerX");
	Attribute attrAccelY = new Attribute("accelerometerY");
	Attribute attrAccelZ = new Attribute("accelerometerZ");
	Attribute attrMagneticX = new Attribute("magneticX");
	Attribute attrMagneticY = new Attribute("magneticY");
	Attribute attrMagneticZ = new Attribute("magneticZ");
	Attribute attrLight = new Attribute("light");
	Attribute attrRotationX = new Attribute("rotationX");
	Attribute attrRotationY = new Attribute("rotationY");
	Attribute attrRotationZ = new Attribute("rotationZ");
	Attribute attrOrientationX = new Attribute("orientationX");
	Attribute attrOrientationY = new Attribute("orientationY");
	Attribute attrOrientationZ = new Attribute("orientationZ");
	Attribute attrBSSID1 = new Attribute("BSSID1");
	Attribute attrBSSID2 = new Attribute("BSSID2");
	Attribute attrBSSID3 = new Attribute("BSSID3");
	Attribute attrBSSID4 = new Attribute("BSSID4");
	Attribute attrBSSID5 = new Attribute("BSSID5");
	Attribute attrBSSID6 = new Attribute("BSSID6");
	Attribute attrBSSID7 = new Attribute("BSSID7");
	Attribute attrBSSID8 = new Attribute("BSSID8");
	Attribute attrBSSID9 = new Attribute("BSSID9");
	Attribute attrBSSID10 = new Attribute("BSSID10");
	Attribute attrBSSID11 = new Attribute("BSSID11");
	Attribute attrBSSID12 = new Attribute("BSSID12");
	Attribute attrBSSID13 = new Attribute("BSSID13");
	Attribute attrBSSID14 = new Attribute("BSSID14");
	Attribute attrBSSID15 = new Attribute("BSSID15");
	Attribute attrBSSID16 = new Attribute("BSSID16");
	Attribute attrBSSID17 = new Attribute("BSSID17");
	Attribute attrBSSID18 = new Attribute("BSSID18");
	Attribute attrBSSID19 = new Attribute("BSSID19");
	Attribute attrBSSID20 = new Attribute("BSSID20");
	Attribute attrBSSID21 = new Attribute("BSSID21");
	Attribute attrBSSID22 = new Attribute("BSSID22");
	Attribute attrBSSID23 = new Attribute("BSSID23");
	Attribute attrBSSID24 = new Attribute("BSSID24");
	Attribute attrBSSID25 = new Attribute("BSSID25");
	Attribute attrBSSID26 = new Attribute("BSSID26");
	Attribute attrBSSID27 = new Attribute("BSSID27");
	Attribute attrBSSID28 = new Attribute("BSSID28");
	Attribute attrBSSID29 = new Attribute("BSSID29");
	Attribute attrBSSID30 = new Attribute("BSSID30");
	Attribute attrBSSID31 = new Attribute("BSSID31");
	Attribute attrBSSID32 = new Attribute("BSSID32");
	Attribute attrBSSID33 = new Attribute("BSSID33");
	Attribute attrBSSID34 = new Attribute("BSSID34");
	Attribute attrBSSID35 = new Attribute("BSSID35");
	Attribute attrBSSID36 = new Attribute("BSSID36");
	Attribute attrBSSID37 = new Attribute("BSSID37");
	Attribute attrBSSID38 = new Attribute("BSSID38");
	Attribute attrBSSID39 = new Attribute("BSSID39");
	Attribute attrBSSID40 = new Attribute("BSSID40");
	Attribute attrBSSID41 = new Attribute("BSSID41");
	Attribute attrBSSID42 = new Attribute("BSSID42");
	Attribute attrBSSID43 = new Attribute("BSSID43");
	Attribute attrBSSID44 = new Attribute("BSSID44");
	Attribute attrBSSID45 = new Attribute("BSSID45");
	Attribute attrBSSID46 = new Attribute("BSSID46");
	Attribute attrBSSID47 = new Attribute("BSSID47");
	Attribute attrBSSID48 = new Attribute("BSSID48");
	Attribute attrBSSID49 = new Attribute("BSSID49");
	Attribute attrBSSID50 = new Attribute("BSSID50");
	Attribute attrBSSID51 = new Attribute("BSSID51");
	Attribute attrBSSID52 = new Attribute("BSSID52");
	Attribute attrBSSID53 = new Attribute("BSSID53");
	Attribute attrBSSID54 = new Attribute("BSSID54");
	Attribute attrBSSID55 = new Attribute("BSSID55");
	Attribute attrBSSID56 = new Attribute("BSSID56");
	Attribute attrBSSID57 = new Attribute("BSSID57");
	Attribute attrBSSID58 = new Attribute("BSSID58");
	Attribute attrBSSID59 = new Attribute("BSSID59");
	Attribute attrBSSID60 = new Attribute("BSSID60");
	Attribute attrBSSID61 = new Attribute("BSSID61");
	Attribute attrBSSID62 = new Attribute("BSSID62");
	Attribute attrBSSID63 = new Attribute("BSSID63");
	Attribute attrBSSID64 = new Attribute("BSSID64");
	Attribute attrBSSID65 = new Attribute("BSSID65");
	Attribute attrBSSID66 = new Attribute("BSSID66");
	Attribute attrBSSID67 = new Attribute("BSSID67");
	Attribute attrBSSID68 = new Attribute("BSSID68");
	Attribute attrBSSID69 = new Attribute("BSSID69");
	Attribute attrBSSID70 = new Attribute("BSSID70");
	Attribute attrBSSID71 = new Attribute("BSSID71");
	Attribute attrBSSID72 = new Attribute("BSSID72");
	Attribute attrBSSID73 = new Attribute("BSSID73");
	Attribute attrBSSID74 = new Attribute("BSSID74");
	Attribute attrBSSID75 = new Attribute("BSSID75");
	Attribute attrBSSID76 = new Attribute("BSSID76");
	Attribute attrBSSID77 = new Attribute("BSSID77");
	Attribute attrBSSID78 = new Attribute("BSSID78");
	Attribute attrBSSID79 = new Attribute("BSSID79");
	Attribute attrBSSID80 = new Attribute("BSSID80");
	Attribute attrBSSID81 = new Attribute("BSSID81");
	Attribute attrBSSID82 = new Attribute("BSSID82");
	Attribute attrBSSID83 = new Attribute("BSSID83");
	Attribute attrBSSID84 = new Attribute("BSSID84");
	Attribute attrBSSID85 = new Attribute("BSSID85");
	Attribute attrBSSID86 = new Attribute("BSSID86");
	Attribute attrBSSID87 = new Attribute("BSSID87");
	Attribute attrBSSID88 = new Attribute("BSSID88");
	Attribute attrBSSID89 = new Attribute("BSSID89");
	Attribute attrBSSID90 = new Attribute("BSSID90");
	Attribute attrBSSID91 = new Attribute("BSSID91");
	Attribute attrBSSID92 = new Attribute("BSSID92");
	Attribute attrBSSID93 = new Attribute("BSSID93");
	Attribute attrBSSID94 = new Attribute("BSSID94");
	Attribute attrBSSID95 = new Attribute("BSSID95");
	Attribute attrBSSID96 = new Attribute("BSSID96");
	Attribute attrBSSID97 = new Attribute("BSSID97");
	Attribute attrBSSID98 = new Attribute("BSSID98");
	Attribute attrBSSID99 = new Attribute("BSSID99");
	Attribute attrBSSID100 = new Attribute("BSSID100");
	Attribute attrBSSID101 = new Attribute("BSSID101");
	Attribute attrBSSID102 = new Attribute("BSSID102");
	Attribute attrBSSID103 = new Attribute("BSSID103");
	Attribute attrBSSID104 = new Attribute("BSSID104");
	Attribute attrBSSID105 = new Attribute("BSSID105");
	Attribute attrBSSID106 = new Attribute("BSSID106");
	Attribute attrBSSID107 = new Attribute("BSSID107");
	Attribute attrBSSID108 = new Attribute("BSSID108");
	Attribute attrBSSID109 = new Attribute("BSSID109");
	Attribute attrBSSID110 = new Attribute("BSSID110");
	Attribute attrBSSID111 = new Attribute("BSSID111");
	Attribute attrBSSID112 = new Attribute("BSSID112");
	Attribute attrBSSID113 = new Attribute("BSSID113");
	Attribute attrBSSID114 = new Attribute("BSSID114");
	Attribute attrBSSID115 = new Attribute("BSSID115");
	Attribute attrBSSID116 = new Attribute("BSSID116");
	Attribute attrBSSID117 = new Attribute("BSSID117");
	Attribute attrBSSID118 = new Attribute("BSSID118");
	Attribute attrBSSID119 = new Attribute("BSSID119");
	Attribute attrBSSID120 = new Attribute("BSSID120");
	Attribute attrBSSID121 = new Attribute("BSSID121");
	Attribute attrBSSID122 = new Attribute("BSSID122");
	Attribute attrBSSID123 = new Attribute("BSSID123");
	Attribute attrBSSID124 = new Attribute("BSSID124");
	Attribute attrBSSID125 = new Attribute("BSSID125");
	Attribute attrBSSID126 = new Attribute("BSSID126");
	Attribute attrBSSID127 = new Attribute("BSSID127");
	Attribute attrBSSID128 = new Attribute("BSSID128");
	Attribute attrBSSID129 = new Attribute("BSSID129");
	Attribute attrBSSID130 = new Attribute("BSSID130");
	Attribute attrBSSID131 = new Attribute("BSSID131");
	Attribute attrBSSID132 = new Attribute("BSSID132");
	Attribute attrBSSID133 = new Attribute("BSSID133");
	Attribute attrBSSID134 = new Attribute("BSSID134");
	Attribute attrBSSID135 = new Attribute("BSSID135");
	Attribute attrBSSID136 = new Attribute("BSSID136");
	Attribute attrBSSID137 = new Attribute("BSSID137");
	Attribute attrBSSID138 = new Attribute("BSSID138");
	Attribute attrBSSID139 = new Attribute("BSSID139");
	Attribute attrBSSID140 = new Attribute("BSSID140");
	Attribute attrBSSID141 = new Attribute("BSSID141");
	Attribute attrBSSID142 = new Attribute("BSSID142");
	Attribute attrBSSID143 = new Attribute("BSSID143");
	Attribute attrBSSID144 = new Attribute("BSSID144");
	Attribute attrBSSID145 = new Attribute("BSSID145");
	Attribute attrBSSID146 = new Attribute("BSSID146");
	Attribute attrBSSID147 = new Attribute("BSSID147");
	Attribute attrBSSID148 = new Attribute("BSSID148");
	Attribute attrBSSID149 = new Attribute("BSSID149");
	Attribute attrBSSID150 = new Attribute("BSSID150");
	Attribute attrBSSID151 = new Attribute("BSSID151");
	Attribute attrBSSID152 = new Attribute("BSSID152");
	Attribute attrBSSID153 = new Attribute("BSSID153");
	Attribute attrBSSID154 = new Attribute("BSSID154");
	Attribute attrBSSID155 = new Attribute("BSSID155");
	Attribute attrBSSID156 = new Attribute("BSSID156");
	Attribute attrLatitude = new Attribute("latitude");
	Attribute attrLongitude = new Attribute("longitude");
	Attribute attrLocationAccuracy = new Attribute("locationAccuracy");
	Attribute attrXPosition = new Attribute("xPosition");
	Attribute attrYPosition = new Attribute("yPosition");
	List<String> values = Arrays.asList("upperleft", "lowerleft", "middle", "upperright", "lowerright");
	Attribute attrPartition = new Attribute("partition", values);
	ArrayList<Attribute> xClass = new ArrayList<Attribute>(173);
	ArrayList<Attribute> yClass = new ArrayList<Attribute>(173);
	ArrayList<Attribute> partitionClass = new ArrayList<Attribute>(173);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_tracker);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
			.add(R.id.container, new TrackerFragment()).commit();
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

		loadXClassifierModels();
		loadYClassifierModels();
		loadPartitionClassifierModels();

		setUpXInstances();
		setUpYInstances();
		setUpPartitionInstances();

		wifiReadings = new LinkedHashMap<String, Integer>();
		t = new Thread();
		t.start();

		//load5PartitionClassifiers = new Thread();
		//load5PartitionClassifiers.start();
	}

	// TODO: make pretty (i.e. hide this in another class)
	/**
	 * Adds the attributes to the x classification, sets up the xInstance to
	 * allow the classifier to predict x position from these attributes
	 */
	private void setUpXInstances() {
		xClass.add(attrAccelX);
		xClass.add(attrAccelY);
		xClass.add(attrAccelZ);
		xClass.add(attrMagneticX);
		xClass.add(attrMagneticY);
		xClass.add(attrMagneticZ);
		xClass.add(attrLight);
		xClass.add(attrRotationX);
		xClass.add(attrRotationY);
		xClass.add(attrRotationZ);
		xClass.add(attrOrientationX);
		xClass.add(attrOrientationY);
		xClass.add(attrOrientationZ);
		xClass.add(attrBSSID1);
		xClass.add(attrBSSID2);
		xClass.add(attrBSSID3);
		xClass.add(attrBSSID4);
		xClass.add(attrBSSID5);
		xClass.add(attrBSSID6);
		xClass.add(attrBSSID7);
		xClass.add(attrBSSID8);
		xClass.add(attrBSSID9);
		xClass.add(attrBSSID10);
		xClass.add(attrBSSID11);
		xClass.add(attrBSSID12);
		xClass.add(attrBSSID13);
		xClass.add(attrBSSID14);
		xClass.add(attrBSSID15);
		xClass.add(attrBSSID16);
		xClass.add(attrBSSID17);
		xClass.add(attrBSSID18);
		xClass.add(attrBSSID19);
		xClass.add(attrBSSID20);
		xClass.add(attrBSSID21);
		xClass.add(attrBSSID22);
		xClass.add(attrBSSID23);
		xClass.add(attrBSSID24);
		xClass.add(attrBSSID25);
		xClass.add(attrBSSID26);
		xClass.add(attrBSSID27);
		xClass.add(attrBSSID28);
		xClass.add(attrBSSID29);
		xClass.add(attrBSSID30);
		xClass.add(attrBSSID31);
		xClass.add(attrBSSID32);
		xClass.add(attrBSSID33);
		xClass.add(attrBSSID34);
		xClass.add(attrBSSID35);
		xClass.add(attrBSSID36);
		xClass.add(attrBSSID37);
		xClass.add(attrBSSID38);
		xClass.add(attrBSSID39);
		xClass.add(attrBSSID40);
		xClass.add(attrBSSID41);
		xClass.add(attrBSSID42);
		xClass.add(attrBSSID43);
		xClass.add(attrBSSID44);
		xClass.add(attrBSSID45);
		xClass.add(attrBSSID46);
		xClass.add(attrBSSID47);
		xClass.add(attrBSSID48);
		xClass.add(attrBSSID49);
		xClass.add(attrBSSID50);
		xClass.add(attrBSSID51);
		xClass.add(attrBSSID52);
		xClass.add(attrBSSID53);
		xClass.add(attrBSSID54);
		xClass.add(attrBSSID55);
		xClass.add(attrBSSID56);
		xClass.add(attrBSSID57);
		xClass.add(attrBSSID58);
		xClass.add(attrBSSID59);
		xClass.add(attrBSSID60);
		xClass.add(attrBSSID61);
		xClass.add(attrBSSID62);
		xClass.add(attrBSSID63);
		xClass.add(attrBSSID64);
		xClass.add(attrBSSID65);
		xClass.add(attrBSSID66);
		xClass.add(attrBSSID67);
		xClass.add(attrBSSID68);
		xClass.add(attrBSSID69);
		xClass.add(attrBSSID70);
		xClass.add(attrBSSID71);
		xClass.add(attrBSSID72);
		xClass.add(attrBSSID73);
		xClass.add(attrBSSID74);
		xClass.add(attrBSSID75);
		xClass.add(attrBSSID76);
		xClass.add(attrBSSID77);
		xClass.add(attrBSSID78);
		xClass.add(attrBSSID79);
		xClass.add(attrBSSID80);
		xClass.add(attrBSSID81);
		xClass.add(attrBSSID82);
		xClass.add(attrBSSID83);
		xClass.add(attrBSSID84);
		xClass.add(attrBSSID85);
		xClass.add(attrBSSID86);
		xClass.add(attrBSSID87);
		xClass.add(attrBSSID88);
		xClass.add(attrBSSID89);
		xClass.add(attrBSSID90);
		xClass.add(attrBSSID91);
		xClass.add(attrBSSID92);
		xClass.add(attrBSSID93);
		xClass.add(attrBSSID94);
		xClass.add(attrBSSID95);
		xClass.add(attrBSSID96);
		xClass.add(attrBSSID97);
		xClass.add(attrBSSID98);
		xClass.add(attrBSSID99);
		xClass.add(attrBSSID100);
		xClass.add(attrBSSID101);
		xClass.add(attrBSSID102);
		xClass.add(attrBSSID103);
		xClass.add(attrBSSID104);
		xClass.add(attrBSSID105);
		xClass.add(attrBSSID106);
		xClass.add(attrBSSID107);
		xClass.add(attrBSSID108);
		xClass.add(attrBSSID109);
		xClass.add(attrBSSID110);
		xClass.add(attrBSSID111);
		xClass.add(attrBSSID112);
		xClass.add(attrBSSID113);
		xClass.add(attrBSSID114);
		xClass.add(attrBSSID115);
		xClass.add(attrBSSID116);
		xClass.add(attrBSSID117);
		xClass.add(attrBSSID118);
		xClass.add(attrBSSID119);
		xClass.add(attrBSSID120);
		xClass.add(attrBSSID121);
		xClass.add(attrBSSID122);
		xClass.add(attrBSSID123);
		xClass.add(attrBSSID124);
		xClass.add(attrBSSID125);
		xClass.add(attrBSSID126);
		xClass.add(attrBSSID127);
		xClass.add(attrBSSID128);
		xClass.add(attrBSSID129);
		xClass.add(attrBSSID130);
		xClass.add(attrBSSID131);
		xClass.add(attrBSSID132);
		xClass.add(attrBSSID133);
		xClass.add(attrBSSID134);
		xClass.add(attrBSSID135);
		xClass.add(attrBSSID136);
		xClass.add(attrBSSID137);
		xClass.add(attrBSSID138);
		xClass.add(attrBSSID139);
		xClass.add(attrBSSID140);
		xClass.add(attrBSSID141);
		xClass.add(attrBSSID142);
		xClass.add(attrBSSID143);
		xClass.add(attrBSSID144);
		xClass.add(attrBSSID145);
		xClass.add(attrBSSID146);
		xClass.add(attrBSSID147);
		xClass.add(attrBSSID148);
		xClass.add(attrBSSID149);
		xClass.add(attrBSSID150);
		xClass.add(attrBSSID151);
		xClass.add(attrBSSID152);
		xClass.add(attrBSSID153);
		xClass.add(attrBSSID154);
		xClass.add(attrBSSID155);
		xClass.add(attrBSSID156);
		xClass.add(attrLatitude);
		xClass.add(attrLongitude);
		xClass.add(attrLocationAccuracy);
		xClass.add(attrXPosition);

		xInstances = new Instances("xPos", xClass, 1);
		xInstances.setClassIndex(172);
		xInstances.add(new DenseInstance(173));
	}

	// TODO this one too
	/**
	 * Adds the attributes to the y classification, sets up the yInstance to
	 * allow the classifier to predict y position from these attributes
	 */
	private void setUpYInstances() {
		yClass.add(attrAccelX);
		yClass.add(attrAccelY);
		yClass.add(attrAccelZ);
		yClass.add(attrMagneticX);
		yClass.add(attrMagneticY);
		yClass.add(attrMagneticZ);
		yClass.add(attrLight);
		yClass.add(attrRotationX);
		yClass.add(attrRotationY);
		yClass.add(attrRotationZ);
		yClass.add(attrOrientationX);
		yClass.add(attrOrientationY);
		yClass.add(attrOrientationZ);
		yClass.add(attrBSSID1);
		yClass.add(attrBSSID2);
		yClass.add(attrBSSID3);
		yClass.add(attrBSSID4);
		yClass.add(attrBSSID5);
		yClass.add(attrBSSID6);
		yClass.add(attrBSSID7);
		yClass.add(attrBSSID8);
		yClass.add(attrBSSID9);
		yClass.add(attrBSSID10);
		yClass.add(attrBSSID11);
		yClass.add(attrBSSID12);
		yClass.add(attrBSSID13);
		yClass.add(attrBSSID14);
		yClass.add(attrBSSID15);
		yClass.add(attrBSSID16);
		yClass.add(attrBSSID17);
		yClass.add(attrBSSID18);
		yClass.add(attrBSSID19);
		yClass.add(attrBSSID20);
		yClass.add(attrBSSID21);
		yClass.add(attrBSSID22);
		yClass.add(attrBSSID23);
		yClass.add(attrBSSID24);
		yClass.add(attrBSSID25);
		yClass.add(attrBSSID26);
		yClass.add(attrBSSID27);
		yClass.add(attrBSSID28);
		yClass.add(attrBSSID29);
		yClass.add(attrBSSID30);
		yClass.add(attrBSSID31);
		yClass.add(attrBSSID32);
		yClass.add(attrBSSID33);
		yClass.add(attrBSSID34);
		yClass.add(attrBSSID35);
		yClass.add(attrBSSID36);
		yClass.add(attrBSSID37);
		yClass.add(attrBSSID38);
		yClass.add(attrBSSID39);
		yClass.add(attrBSSID40);
		yClass.add(attrBSSID41);
		yClass.add(attrBSSID42);
		yClass.add(attrBSSID43);
		yClass.add(attrBSSID44);
		yClass.add(attrBSSID45);
		yClass.add(attrBSSID46);
		yClass.add(attrBSSID47);
		yClass.add(attrBSSID48);
		yClass.add(attrBSSID49);
		yClass.add(attrBSSID50);
		yClass.add(attrBSSID51);
		yClass.add(attrBSSID52);
		yClass.add(attrBSSID53);
		yClass.add(attrBSSID54);
		yClass.add(attrBSSID55);
		yClass.add(attrBSSID56);
		yClass.add(attrBSSID57);
		yClass.add(attrBSSID58);
		yClass.add(attrBSSID59);
		yClass.add(attrBSSID60);
		yClass.add(attrBSSID61);
		yClass.add(attrBSSID62);
		yClass.add(attrBSSID63);
		yClass.add(attrBSSID64);
		yClass.add(attrBSSID65);
		yClass.add(attrBSSID66);
		yClass.add(attrBSSID67);
		yClass.add(attrBSSID68);
		yClass.add(attrBSSID69);
		yClass.add(attrBSSID70);
		yClass.add(attrBSSID71);
		yClass.add(attrBSSID72);
		yClass.add(attrBSSID73);
		yClass.add(attrBSSID74);
		yClass.add(attrBSSID75);
		yClass.add(attrBSSID76);
		yClass.add(attrBSSID77);
		yClass.add(attrBSSID78);
		yClass.add(attrBSSID79);
		yClass.add(attrBSSID80);
		yClass.add(attrBSSID81);
		yClass.add(attrBSSID82);
		yClass.add(attrBSSID83);
		yClass.add(attrBSSID84);
		yClass.add(attrBSSID85);
		yClass.add(attrBSSID86);
		yClass.add(attrBSSID87);
		yClass.add(attrBSSID88);
		yClass.add(attrBSSID89);
		yClass.add(attrBSSID90);
		yClass.add(attrBSSID91);
		yClass.add(attrBSSID92);
		yClass.add(attrBSSID93);
		yClass.add(attrBSSID94);
		yClass.add(attrBSSID95);
		yClass.add(attrBSSID96);
		yClass.add(attrBSSID97);
		yClass.add(attrBSSID98);
		yClass.add(attrBSSID99);
		yClass.add(attrBSSID100);
		yClass.add(attrBSSID101);
		yClass.add(attrBSSID102);
		yClass.add(attrBSSID103);
		yClass.add(attrBSSID104);
		yClass.add(attrBSSID105);
		yClass.add(attrBSSID106);
		yClass.add(attrBSSID107);
		yClass.add(attrBSSID108);
		yClass.add(attrBSSID109);
		yClass.add(attrBSSID110);
		yClass.add(attrBSSID111);
		yClass.add(attrBSSID112);
		yClass.add(attrBSSID113);
		yClass.add(attrBSSID114);
		yClass.add(attrBSSID115);
		yClass.add(attrBSSID116);
		yClass.add(attrBSSID117);
		yClass.add(attrBSSID118);
		yClass.add(attrBSSID119);
		yClass.add(attrBSSID120);
		yClass.add(attrBSSID121);
		yClass.add(attrBSSID122);
		yClass.add(attrBSSID123);
		yClass.add(attrBSSID124);
		yClass.add(attrBSSID125);
		yClass.add(attrBSSID126);
		yClass.add(attrBSSID127);
		yClass.add(attrBSSID128);
		yClass.add(attrBSSID129);
		yClass.add(attrBSSID130);
		yClass.add(attrBSSID131);
		yClass.add(attrBSSID132);
		yClass.add(attrBSSID133);
		yClass.add(attrBSSID134);
		yClass.add(attrBSSID135);
		yClass.add(attrBSSID136);
		yClass.add(attrBSSID137);
		yClass.add(attrBSSID138);
		yClass.add(attrBSSID139);
		yClass.add(attrBSSID140);
		yClass.add(attrBSSID141);
		yClass.add(attrBSSID142);
		yClass.add(attrBSSID143);
		yClass.add(attrBSSID144);
		yClass.add(attrBSSID145);
		yClass.add(attrBSSID146);
		yClass.add(attrBSSID147);
		yClass.add(attrBSSID148);
		yClass.add(attrBSSID149);
		yClass.add(attrBSSID150);
		yClass.add(attrBSSID151);
		yClass.add(attrBSSID152);
		yClass.add(attrBSSID153);
		yClass.add(attrBSSID154);
		yClass.add(attrBSSID155);
		yClass.add(attrBSSID156);
		yClass.add(attrLatitude);
		yClass.add(attrLongitude);
		yClass.add(attrLocationAccuracy);
		yClass.add(attrYPosition);

		yInstances = new Instances("yPos", yClass, 1);
		yInstances.setClassIndex(172);
		yInstances.add(new DenseInstance(173));
	}

	// TODO and this one
	/**
	 * Adds the attributes to the partition classification, set partition up to
	 * allow the classifier to predict partition from these attributes
	 */
	private void setUpPartitionInstances() {
		partitionClass.add(attrAccelX);
		partitionClass.add(attrAccelY);
		partitionClass.add(attrAccelZ);
		partitionClass.add(attrMagneticX);
		partitionClass.add(attrMagneticY);
		partitionClass.add(attrMagneticZ);
		partitionClass.add(attrLight);
		partitionClass.add(attrRotationX);
		partitionClass.add(attrRotationY);
		partitionClass.add(attrRotationZ);
		partitionClass.add(attrOrientationX);
		partitionClass.add(attrOrientationY);
		partitionClass.add(attrOrientationZ);
		partitionClass.add(attrBSSID1);
		partitionClass.add(attrBSSID2);
		partitionClass.add(attrBSSID3);
		partitionClass.add(attrBSSID4);
		partitionClass.add(attrBSSID5);
		partitionClass.add(attrBSSID6);
		partitionClass.add(attrBSSID7);
		partitionClass.add(attrBSSID8);
		partitionClass.add(attrBSSID9);
		partitionClass.add(attrBSSID10);
		partitionClass.add(attrBSSID11);
		partitionClass.add(attrBSSID12);
		partitionClass.add(attrBSSID13);
		partitionClass.add(attrBSSID14);
		partitionClass.add(attrBSSID15);
		partitionClass.add(attrBSSID16);
		partitionClass.add(attrBSSID17);
		partitionClass.add(attrBSSID18);
		partitionClass.add(attrBSSID19);
		partitionClass.add(attrBSSID20);
		partitionClass.add(attrBSSID21);
		partitionClass.add(attrBSSID22);
		partitionClass.add(attrBSSID23);
		partitionClass.add(attrBSSID24);
		partitionClass.add(attrBSSID25);
		partitionClass.add(attrBSSID26);
		partitionClass.add(attrBSSID27);
		partitionClass.add(attrBSSID28);
		partitionClass.add(attrBSSID29);
		partitionClass.add(attrBSSID30);
		partitionClass.add(attrBSSID31);
		partitionClass.add(attrBSSID32);
		partitionClass.add(attrBSSID33);
		partitionClass.add(attrBSSID34);
		partitionClass.add(attrBSSID35);
		partitionClass.add(attrBSSID36);
		partitionClass.add(attrBSSID37);
		partitionClass.add(attrBSSID38);
		partitionClass.add(attrBSSID39);
		partitionClass.add(attrBSSID40);
		partitionClass.add(attrBSSID41);
		partitionClass.add(attrBSSID42);
		partitionClass.add(attrBSSID43);
		partitionClass.add(attrBSSID44);
		partitionClass.add(attrBSSID45);
		partitionClass.add(attrBSSID46);
		partitionClass.add(attrBSSID47);
		partitionClass.add(attrBSSID48);
		partitionClass.add(attrBSSID49);
		partitionClass.add(attrBSSID50);
		partitionClass.add(attrBSSID51);
		partitionClass.add(attrBSSID52);
		partitionClass.add(attrBSSID53);
		partitionClass.add(attrBSSID54);
		partitionClass.add(attrBSSID55);
		partitionClass.add(attrBSSID56);
		partitionClass.add(attrBSSID57);
		partitionClass.add(attrBSSID58);
		partitionClass.add(attrBSSID59);
		partitionClass.add(attrBSSID60);
		partitionClass.add(attrBSSID61);
		partitionClass.add(attrBSSID62);
		partitionClass.add(attrBSSID63);
		partitionClass.add(attrBSSID64);
		partitionClass.add(attrBSSID65);
		partitionClass.add(attrBSSID66);
		partitionClass.add(attrBSSID67);
		partitionClass.add(attrBSSID68);
		partitionClass.add(attrBSSID69);
		partitionClass.add(attrBSSID70);
		partitionClass.add(attrBSSID71);
		partitionClass.add(attrBSSID72);
		partitionClass.add(attrBSSID73);
		partitionClass.add(attrBSSID74);
		partitionClass.add(attrBSSID75);
		partitionClass.add(attrBSSID76);
		partitionClass.add(attrBSSID77);
		partitionClass.add(attrBSSID78);
		partitionClass.add(attrBSSID79);
		partitionClass.add(attrBSSID80);
		partitionClass.add(attrBSSID81);
		partitionClass.add(attrBSSID82);
		partitionClass.add(attrBSSID83);
		partitionClass.add(attrBSSID84);
		partitionClass.add(attrBSSID85);
		partitionClass.add(attrBSSID86);
		partitionClass.add(attrBSSID87);
		partitionClass.add(attrBSSID88);
		partitionClass.add(attrBSSID89);
		partitionClass.add(attrBSSID90);
		partitionClass.add(attrBSSID91);
		partitionClass.add(attrBSSID92);
		partitionClass.add(attrBSSID93);
		partitionClass.add(attrBSSID94);
		partitionClass.add(attrBSSID95);
		partitionClass.add(attrBSSID96);
		partitionClass.add(attrBSSID97);
		partitionClass.add(attrBSSID98);
		partitionClass.add(attrBSSID99);
		partitionClass.add(attrBSSID100);
		partitionClass.add(attrBSSID101);
		partitionClass.add(attrBSSID102);
		partitionClass.add(attrBSSID103);
		partitionClass.add(attrBSSID104);
		partitionClass.add(attrBSSID105);
		partitionClass.add(attrBSSID106);
		partitionClass.add(attrBSSID107);
		partitionClass.add(attrBSSID108);
		partitionClass.add(attrBSSID109);
		partitionClass.add(attrBSSID110);
		partitionClass.add(attrBSSID111);
		partitionClass.add(attrBSSID112);
		partitionClass.add(attrBSSID113);
		partitionClass.add(attrBSSID114);
		partitionClass.add(attrBSSID115);
		partitionClass.add(attrBSSID116);
		partitionClass.add(attrBSSID117);
		partitionClass.add(attrBSSID118);
		partitionClass.add(attrBSSID119);
		partitionClass.add(attrBSSID120);
		partitionClass.add(attrBSSID121);
		partitionClass.add(attrBSSID122);
		partitionClass.add(attrBSSID123);
		partitionClass.add(attrBSSID124);
		partitionClass.add(attrBSSID125);
		partitionClass.add(attrBSSID126);
		partitionClass.add(attrBSSID127);
		partitionClass.add(attrBSSID128);
		partitionClass.add(attrBSSID129);
		partitionClass.add(attrBSSID130);
		partitionClass.add(attrBSSID131);
		partitionClass.add(attrBSSID132);
		partitionClass.add(attrBSSID133);
		partitionClass.add(attrBSSID134);
		partitionClass.add(attrBSSID135);
		partitionClass.add(attrBSSID136);
		partitionClass.add(attrBSSID137);
		partitionClass.add(attrBSSID138);
		partitionClass.add(attrBSSID139);
		partitionClass.add(attrBSSID140);
		partitionClass.add(attrBSSID141);
		partitionClass.add(attrBSSID142);
		partitionClass.add(attrBSSID143);
		partitionClass.add(attrBSSID144);
		partitionClass.add(attrBSSID145);
		partitionClass.add(attrBSSID146);
		partitionClass.add(attrBSSID147);
		partitionClass.add(attrBSSID148);
		partitionClass.add(attrBSSID149);
		partitionClass.add(attrBSSID150);
		partitionClass.add(attrBSSID151);
		partitionClass.add(attrBSSID152);
		partitionClass.add(attrBSSID153);
		partitionClass.add(attrBSSID154);
		partitionClass.add(attrBSSID155);
		partitionClass.add(attrBSSID156);
		partitionClass.add(attrLatitude);
		partitionClass.add(attrLongitude);
		partitionClass.add(attrLocationAccuracy);
		partitionClass.add(attrPartition);

		partitionInstances = new Instances("partition", partitionClass, 1);
		partitionInstances.setClassIndex(172);
		partitionInstances.add(new DenseInstance(173));
	}

	@Override
	public void onResume() {
		super.onResume();

		File root = Environment.getExternalStorageDirectory();
		File dir = new File(root.getAbsolutePath() + "/indoor_localization");
		dir.mkdirs();
		file = new File(dir, "livetest_" + building + ".txt");
		valuesFile = new File(dir, "livetest_" + building + "_values.txt");
		try {
			outputStream = new FileOutputStream(file, true);
			valuesOutputStream = new FileOutputStream(valuesFile, true);
			writer = new PrintWriter(outputStream);
			valuesWriter = new PrintWriter(valuesOutputStream);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// Set building textview to the building the user has selected
		//TextView buildingText = (TextView) findViewById(R.id.text_building);
		//buildingText.setText("Building: " + building);

		// Set room size textview to the room size the user has selected
		//TextView roomSizeText = (TextView) findViewById(R.id.text_room_size);
		//roomSizeText.setText("Room size: " + roomWidth + " x " + roomLength);

		// Set grid options
		GridView grid = (GridView) findViewById(R.id.tracker_gridView);
		//grid.setGridSize(roomWidth, roomLength);
		grid.setGridSize(102, 64);
		grid.setCatchInput(false);
		//grid.setDisplayMap(displayMap);

		// Register to get sensor updates from all the available sensors
		sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
		for (Sensor sensor : sensorList) {
			sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
		}

		// Enable wifi if it is disabled
		if (!wifiManager.isWifiEnabled()) {
			Toast.makeText(this, "WiFi not enabled. Enabling...", Toast.LENGTH_SHORT).show();
			wifiManager.setWifiEnabled(true);
		}

        // TODO fix this to actually request permission
        try {
            // Request location updates from gps and the network
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    0, 0, locationListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    0, 0, locationListener);
        } catch (Exception e) {
            e.printStackTrace();
        }

		registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

		wifiManager.startScan();
		Toast.makeText(this, "Initiated scan", Toast.LENGTH_SHORT).show();	

		xText = (TextView) findViewById(R.id.tracker_text_xcoord);
		yText = (TextView) findViewById(R.id.tracker_text_ycoord);
	}

	@Override
	public void onPause() {
		// Stop receiving updates
		sensorManager.unregisterListener(this);

        // TODO fix this
        try {
            locationManager.removeUpdates(locationListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
		unregisterReceiver(receiver);
		
		writer.close();
		valuesWriter.close();

		//savePreferences();

		super.onPause();
	}

	/**
	 * Helper method to keep track of the most up-to-date location
	 */
	private void updateLocation(Location location) {
		this.location = location;
	}

	/**
	 * When a new WiFi scan comes in, get sensor values and predict position
	 */
	private void updateScanResults() {
		resetWifiReadings();

		scanResults = wifiManager.getScanResults();

		// Start another scan to recalculate user position
		wifiManager.startScan();
		
		time = new Timestamp(System.currentTimeMillis());
		
		for (ScanResult result : scanResults) {
			if (wifiReadings.get(result.BSSID) != null) {
				wifiReadings.put(result.BSSID, result.level);
			} // else BSSID wasn't programmed in
		}

		if (location == null) {
			location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		}
		if (location == null) {
			locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		}

		setInstanceValues();
		
		printValues();

		// this is where the magic happens
		// TODO clean up
		if (!t.isAlive()) {
			t = new Thread(new Runnable() {
				public void run() {
					Timestamp myTime = time;
					// This doesn't do anything -> classifierXKStar is null -> not loaded
					/*try {
						predictedX = (float) classifierXRBFRegressor.classifyInstance(xInstances.get(0));
					} catch (Exception e) {
						e.printStackTrace();
					}
					// Likewise, doesn't happen
					try {
						predictedY = (float) classifierYRBFRegressor.classifyInstance(yInstances.get(0));
					} catch (Exception e) {
						e.printStackTrace();
					}*/

					// Get the partition that the new instance is in
					// Use the classifier of the predicted partition to predict an x and y value for
					// the new instance if the classifier is loaded (not null)
					try {
						predictedPartition = partitionClassifier.classifyInstance(partitionInstances.get(0));
						//double[] dist = partitionClassifier.distributionForInstance(partitionInstances.get(0)); // gets the probability distribution for the instance
					} catch (Exception e) {
						e.printStackTrace();
					}

					String partitionString = partitionInstances.classAttribute().value((int) predictedPartition);
					if (partitionString.equals("upperleft")) {
						if (partitionUpperLeftX != null) {
							try {
								predictedX = (float) partitionUpperLeftX.classifyInstance(xInstances.get(0));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						if (partitionUpperLeftY != null) {
							try {
								predictedY = (float) partitionUpperLeftY.classifyInstance(yInstances.get(0));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					} else if (partitionString.equals("upperright")) {
						if (partitionUpperRightX != null) {
							try {
								predictedX = (float) partitionUpperRightX.classifyInstance(xInstances.get(0));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						if (partitionUpperRightY != null) {
							try {
								predictedY = (float) partitionUpperRightY.classifyInstance(yInstances.get(0));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					} else if (partitionString.equals("lowerleft")) {
						if (partitionLowerLeftX != null) {
							try {
								predictedX = (float) partitionLowerLeftX.classifyInstance(xInstances.get(0));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						if (partitionLowerLeftY != null) {
							try {
								predictedY = (float) partitionLowerLeftY.classifyInstance(yInstances.get(0));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					} else if (partitionString.equals("lowerright")) {
						if (partitionLowerRightX != null) {
							try {
								predictedX = (float) partitionLowerRightX.classifyInstance(xInstances.get(0));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						if (partitionLowerRightY != null) {
							try {
								predictedY = (float) partitionLowerRightY.classifyInstance(yInstances.get(0));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					} else if (partitionString.equals("middle")) {
						if (partitionMiddleX != null) {
							try {
								predictedX = (float) partitionMiddleX.classifyInstance(xInstances.get(0));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						if (partitionMiddleX != null) {
							try {
								predictedY = (float) partitionMiddleY.classifyInstance(yInstances.get(0));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}

					xText.post(new Runnable() {
						public void run() {
							xText.setText("X Position: " + predictedX);
						}
					});

					yText.post(new Runnable() {
						public void run() {
							yText.setText("Y Position: " + predictedY);
						}
					});
					
					// TODO: make this work -> grid is apparently null here. For whatever reason.
					/*runOnUiThread(new Runnable() {
						public void run() {
							grid.setUserPointCoords(predictedX, predictedY);
						}
					});*/
					
					
					// Unnecessary if you're not testing
					writer.print("(" + predictedX + "," + predictedY + ")");
					writer.print(" %" + myTime.toString() + "\t " + time.toString() +
							"\t" + new Timestamp(System.currentTimeMillis()) + "\n");
					writer.flush();
				}
			});
			t.setPriority(Thread.MIN_PRIORITY); // run in the background
			t.start();
		}
	}

	/**
	 * Take this out if you're not evaluating the classifier
	 * Displays to the user where the next point in the building is
	 * that they should walk to, takes a time for when the user got to
	 * this point
	 */
	private int pointCounter = 0;
	public void nextPoint(View view) {
		pointCounter++;

		File root = Environment.getExternalStorageDirectory();
		File dir = new File(root.getAbsolutePath() + "/indoor_localization");
		dir.mkdirs();
		File file = new File(dir, "livetest_" + building + ".txt");
		try {
			FileOutputStream outputStream = new FileOutputStream(file, true);
			PrintWriter writer = new PrintWriter(outputStream);
			writer.print("DONE: (" + nextX + "," + nextY + ")");
			writer.print(" %" + (new Timestamp(System.currentTimeMillis())).toString() + "\n\n");
			writer.flush();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		switch (pointCounter) {
		case 1:
			nextX = 4.5f;
			nextY = 1.5f;
			break;
		case 2:
			nextX = 4.5f;
			nextY = 52.5f;
			break;
		case 3:
			nextX = 21.5f;
			nextY = 52.5f;
			break;
		case 4:
			nextX = 21.5f;
			nextY = 29.5f;
			break;
		case 5:
			nextX = 4.5f;
			nextY = 29.5f;
			break;
		case 6:
			nextX = 4.5f;
			nextY = 15.5f;
			break;
		case 7:
			nextX = 22.5f;
			nextY = 15.5f;
			break;
		case 8:
			nextX = 22.5f;
			nextY = 21.5f;
			break;
		case 9:
			nextX = 37.5f;
			nextY = 21.5f;
			break;
		case 10:
			nextX = 37.5f;
			nextY = 26.5f;
			break;
		case 11:
			nextX = 65.5f;
			nextY = 26.5f;
			break;
		case 12:
			nextX = 65.5f;
			nextY = 9.5f;
			break;
		case 13:
			nextX = 98.5f;
			nextY = 9.5f;
			break;
		case 14:
			nextX = 98.5f;
			nextY = 47.5f;
			break;
		case 15:
			nextX = 84.5f;
			nextY = 47.5f;
			break;
		case 16:
			nextX = 84.5f;
			nextY = 28.5f;
			break;
		case 17:
			nextX = 32.5f;
			nextY = 28.5f;
			break;
		}

		TextView tv = (TextView) findViewById(R.id.tracker_text_goto);
		tv.setText("Go to (" + nextX + "," + nextY + ")");
	}

	public static class TrackerFragment extends Fragment {
		public TrackerFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_tracker, container, false);
			return rootView;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.tracker, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch (item.getItemId()) {
		case R.id.action_select_algorithm:
			// open x algorithm selection dialog
			// todo
			break;
		case R.id.action_select_partitioning:
			// open partition selection
			// todo something with this
			showSelectPartitionDialog();
			break;
		case R.id.action_start_data_collection:
			// start main activity
			Intent intent = new Intent(this, MainActivity.class);
			startActivity(intent);
			break;
		default:
			super.onOptionsItemSelected(item);
			break;
		}
		return true;
	}

	/*
	 * Because Android doesn't let us query a sensor reading whenever we want
	 * we have to keep track of the readings at all times. Here we just update
	 * the class members with the values associated with each reading we're
	 * interested in.
	 */
	@Override
	public void onSensorChanged(SensorEvent event) {
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

	/**
	 * Loads the classifiers for predicting the X position
	 */
	private void loadXClassifierModels() {
		try {
			//classifierXKStar = (KStar) weka.core.SerializationHelper.read(
			//		getAssets().open("5partition/model_x_upperright.model"));
			partitionLowerLeftX = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("5partition/model_x_lowerleft.model"));
			partitionLowerRightX = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("5partition/model_x_lowerright.model"));
			partitionUpperLeftX = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("5partition/model_x_upperleft.model"));
			partitionUpperRightX = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("5partition/model_x_upperright.model"));
			partitionMiddleX = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("5partition/model_x_middle.model"));
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "KStar x classifier did not load", Toast.LENGTH_LONG).show();
		}

		try {
			classifierXRBFRegressor = (RBFRegressor) weka.core.SerializationHelper.read(
					getAssets().open("classifier_x_rbfreg.model"));
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "RBFRegressor x classifier did not load", Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Loads the classifiers to predict Y position
	 */
	private void loadYClassifierModels() {
		try {
			//classifierYKStar = (KStar) weka.core.SerializationHelper.read(
			//		getAssets().open("5partition/model_y_upperright.model"));
			partitionLowerLeftY = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("5partition/model_y_lowerleft.model"));
			partitionLowerRightY = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("5partition/model_y_lowerright.model"));
			partitionUpperLeftY = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("5partition/model_y_upperleft.model"));
			partitionUpperRightY = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("5partition/model_y_upperright.model"));
			partitionMiddleY = (KStar) weka.core.SerializationHelper.read(
					getAssets().open("5partition/model_y_middle.model"));
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "KStar y classifier did not load", Toast.LENGTH_LONG).show();
		}

		try {
			classifierYRBFRegressor = (RBFRegressor) weka.core.SerializationHelper.read(
					getAssets().open("classifier_y_rbfreg.model"));
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "RBFRegressor y classifier did not load", Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Loads the classifier to predict the partition
	 */
	private void loadPartitionClassifierModels() {
		try {
			partitionClassifier = (RandomForest) weka.core.SerializationHelper.read(
					getAssets().open("5partition/model_randomforest.model"));
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "Partition classifier did not load", Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Let the user pick what partitioning scheme they want to use
	 */
	private void showSelectPartitionDialog() {
		DialogFragment dialog = new SelectPartitionDialogFragment();
		dialog.show(getSupportFragmentManager(), "SelectPartitionDialogFragment");
	}

	/**
	 * When the partitioning scheme changes, we should update the classifiers
	 * to reflect this
     *
     * TODO fix this
	 */
	@Override
	public void onPartitionChanged(String partitioning) {
		//TextView buildingText = (TextView) findViewById(R.id.text_building);
		//buildingText.setText("Building: " + building);
		//Toast.makeText(this, "New partitioning: " + partitioning, Toast.LENGTH_SHORT).show();
		/*switch (partitioning) {
			case "Full":
				partitionClassifier = null;
				break;
			case "3Partition":
				try {
					partitionClassifier = (RandomForest) weka.core.SerializationHelper.read(
							getAssets().open("3partition/model_randomforest.model"));
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(this,  "Partition classifier did not load", Toast.LENGTH_LONG).show();
				}
				break;
			case "5Partition":
				try {
					partitionClassifier = (RandomForest) weka.core.SerializationHelper.read(
							getAssets().open("5partition/model_randomforest.model"));
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(this, "Partition classifier did not load", Toast.LENGTH_LONG).show();
				}
				load5PartitionClassifiers();
				break;
			case "7Partition":
				try {
					partitionClassifier = (RandomForest) weka.core.SerializationHelper.read(
							getAssets().open("7partition/model_randomforest.model"));
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(this, "Partition classifier did not load", Toast.LENGTH_LONG).show();
				}
				break;
		}
		 */
	}

	/**
	 * Empty out the wifi readings hashmap. Otherwise if you switch buildings in in the
	 * middle of a session the access points for both buildings will be stored to the
	 * data file and mess up the arff file
	 *
	 * TODO: add other buildings here as well
	 */
	private void resetWifiReadings() {
		wifiReadings.clear();

		// The readings ending in :00, :01, :02, and :03 are in the 2.4 GHz band
		// The readings ending in :0c, :0d, :0e, and :0f are in the 5 GHz band
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
	}
	
	/**
	 * Unnecessary if you're not testing/evaluating
	 * Prints out the sensor values and time at each data point
	 */
	private void printValues() {
		valuesWriter.print(accelerometerX + "," + accelerometerY + "," + accelerometerZ +
				"," + magneticX + "," + magneticY + "," + magneticZ + "," + light +
				"," + rotationX + "," + rotationY + "," + rotationZ + "," +
				orientation[0] + "," + orientation[1] + "," + orientation[2]);

		for (String key : wifiReadings.keySet()) {
			valuesWriter.print("," + wifiReadings.get(key));
			}
		
		if (location != null) {
			valuesWriter.print("," + location.getLatitude() + "," + location.getLongitude() + 
				"," + location.getAccuracy());
		} else {
            try {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch (Exception e) {
                e.printStackTrace(); // TODO fix
            }
			if (location != null) {
				valuesWriter.print("," + location.getLatitude() + "," +
						location.getLongitude() + "," + location.getAccuracy());
			} else {
                try {
                    location = locationManager.getLastKnownLocation(
                            LocationManager.NETWORK_PROVIDER);
                } catch (Exception e) {
                    e.fillInStackTrace(); // TODO fix
                }
				if (location != null) {
					valuesWriter.print("," + location.getLatitude() + "," +
							location.getLongitude() + "," + location.getAccuracy());
				} else {
					valuesWriter.print(",?,?,?");
				}
			}
		}
		
		valuesWriter.print(" %" + (new Timestamp(System.currentTimeMillis())).toString());
		
		valuesWriter.print("\n\n");
		valuesWriter.flush();

	}

	// TODO clean all this up (hide it in another class or something)
	/**
	 * Add the value for each attribute of the data to the instance so that the
	 * classifier can predict a position
	 */
	private void setInstanceValues() {
		xInstances.get(0).setValue(attrAccelX, accelerometerX);
		xInstances.get(0).setValue(attrAccelY, accelerometerY);
		xInstances.get(0).setValue(attrAccelZ, accelerometerZ);
		xInstances.get(0).setValue(attrMagneticX, magneticX);
		xInstances.get(0).setValue(attrMagneticY, magneticY);
		xInstances.get(0).setValue(attrMagneticZ, magneticZ);
		xInstances.get(0).setValue(attrLight, light);
		xInstances.get(0).setValue(attrRotationX, rotationX);
		xInstances.get(0).setValue(attrRotationY, rotationY);
		xInstances.get(0).setValue(attrRotationZ, rotationZ);
		xInstances.get(0).setValue(attrOrientationX, orientation[0]);
		xInstances.get(0).setValue(attrOrientationY, orientation[1]);
		xInstances.get(0).setValue(attrOrientationZ, orientation[2]);

		xInstances.get(0).setValue(attrBSSID1, wifiReadings.get("00:17:0f:8d:c3:e0"));
		xInstances.get(0).setValue(attrBSSID2, wifiReadings.get("00:17:0f:8d:c3:e1"));
		xInstances.get(0).setValue(attrBSSID3, wifiReadings.get("00:17:0f:8d:c3:e2"));
		xInstances.get(0).setValue(attrBSSID4, wifiReadings.get("00:17:0f:8d:c3:e3"));

		xInstances.get(0).setValue(attrBSSID5, wifiReadings.get("00:17:0f:8d:c3:f0"));
		xInstances.get(0).setValue(attrBSSID6, wifiReadings.get("00:17:0f:8d:c3:f1"));
		xInstances.get(0).setValue(attrBSSID7, wifiReadings.get("00:17:0f:8d:c3:f2"));
		xInstances.get(0).setValue(attrBSSID8, wifiReadings.get("00:17:0f:8d:c3:f3"));

		xInstances.get(0).setValue(attrBSSID9, wifiReadings.get("00:18:74:88:df:20"));
		xInstances.get(0).setValue(attrBSSID10, wifiReadings.get("00:18:74:88:df:21"));
		xInstances.get(0).setValue(attrBSSID11, wifiReadings.get("00:18:74:88:df:22"));
		xInstances.get(0).setValue(attrBSSID12, wifiReadings.get("00:18:74:88:df:23"));

		xInstances.get(0).setValue(attrBSSID13, wifiReadings.get("00:18:74:89:58:e0"));
		xInstances.get(0).setValue(attrBSSID14, wifiReadings.get("00:18:74:89:58:e1"));
		xInstances.get(0).setValue(attrBSSID15, wifiReadings.get("00:18:74:89:58:e2"));
		xInstances.get(0).setValue(attrBSSID16, wifiReadings.get("00:18:74:89:58:e3"));
		xInstances.get(0).setValue(attrBSSID17, wifiReadings.get("00:18:74:89:58:ec"));
		xInstances.get(0).setValue(attrBSSID18, wifiReadings.get("00:18:74:89:58:ed"));
		xInstances.get(0).setValue(attrBSSID19, wifiReadings.get("00:18:74:89:58:ee"));
		xInstances.get(0).setValue(attrBSSID20, wifiReadings.get("00:18:74:89:58:ef"));

		xInstances.get(0).setValue(attrBSSID21, wifiReadings.get("00:18:74:89:59:90"));
		xInstances.get(0).setValue(attrBSSID22, wifiReadings.get("00:18:74:89:59:91"));
		xInstances.get(0).setValue(attrBSSID23, wifiReadings.get("00:18:74:89:59:92"));
		xInstances.get(0).setValue(attrBSSID24, wifiReadings.get("00:18:74:89:59:93"));
		xInstances.get(0).setValue(attrBSSID25, wifiReadings.get("00:18:74:89:59:9c"));
		xInstances.get(0).setValue(attrBSSID26, wifiReadings.get("00:18:74:89:59:9d"));
		xInstances.get(0).setValue(attrBSSID27, wifiReadings.get("00:18:74:89:59:9e"));
		xInstances.get(0).setValue(attrBSSID28, wifiReadings.get("00:18:74:89:59:9f"));

		xInstances.get(0).setValue(attrBSSID29, wifiReadings.get("00:18:74:89:a9:20"));
		xInstances.get(0).setValue(attrBSSID30, wifiReadings.get("00:18:74:89:a9:21"));
		xInstances.get(0).setValue(attrBSSID31, wifiReadings.get("00:18:74:89:a9:22"));
		xInstances.get(0).setValue(attrBSSID32, wifiReadings.get("00:18:74:89:a9:23"));
		xInstances.get(0).setValue(attrBSSID33, wifiReadings.get("00:18:74:89:a9:2c"));
		xInstances.get(0).setValue(attrBSSID34, wifiReadings.get("00:18:74:89:a9:2d"));
		xInstances.get(0).setValue(attrBSSID35, wifiReadings.get("00:18:74:89:a9:2e"));
		xInstances.get(0).setValue(attrBSSID36, wifiReadings.get("00:18:74:89:a9:2f"));

		xInstances.get(0).setValue(attrBSSID37, wifiReadings.get("00:18:74:8b:90:f0"));
		xInstances.get(0).setValue(attrBSSID38, wifiReadings.get("00:18:74:8b:90:f1"));
		xInstances.get(0).setValue(attrBSSID39, wifiReadings.get("00:18:74:8b:90:f2"));
		xInstances.get(0).setValue(attrBSSID40, wifiReadings.get("00:18:74:8b:90:f3"));
		xInstances.get(0).setValue(attrBSSID41, wifiReadings.get("00:18:74:8b:90:fc"));
		xInstances.get(0).setValue(attrBSSID42, wifiReadings.get("00:18:74:8b:90:fd"));
		xInstances.get(0).setValue(attrBSSID43, wifiReadings.get("00:18:74:8b:90:fe"));
		xInstances.get(0).setValue(attrBSSID44, wifiReadings.get("00:18:74:8b:90:ff"));

		xInstances.get(0).setValue(attrBSSID45, wifiReadings.get("00:18:74:8b:c8:d0"));
		xInstances.get(0).setValue(attrBSSID46, wifiReadings.get("00:18:74:8b:c8:d1"));
		xInstances.get(0).setValue(attrBSSID47, wifiReadings.get("00:18:74:8b:c8:d2"));
		xInstances.get(0).setValue(attrBSSID48, wifiReadings.get("00:18:74:8b:c8:d3"));
		xInstances.get(0).setValue(attrBSSID49, wifiReadings.get("00:18:74:8b:c8:dc"));
		xInstances.get(0).setValue(attrBSSID50, wifiReadings.get("00:18:74:8b:c8:dd"));
		xInstances.get(0).setValue(attrBSSID51, wifiReadings.get("00:18:74:8b:c8:de"));
		xInstances.get(0).setValue(attrBSSID52, wifiReadings.get("00:18:74:8b:c8:df"));

		xInstances.get(0).setValue(attrBSSID53, wifiReadings.get("00:18:74:8b:cb:b0"));
		xInstances.get(0).setValue(attrBSSID54, wifiReadings.get("00:18:74:8b:cb:b1"));
		xInstances.get(0).setValue(attrBSSID55, wifiReadings.get("00:18:74:8b:cb:b2"));
		xInstances.get(0).setValue(attrBSSID56, wifiReadings.get("00:18:74:8b:cb:b3"));
		xInstances.get(0).setValue(attrBSSID57, wifiReadings.get("00:18:74:8b:cb:bc"));
		xInstances.get(0).setValue(attrBSSID58, wifiReadings.get("00:18:74:8b:cb:bd"));
		xInstances.get(0).setValue(attrBSSID59, wifiReadings.get("00:18:74:8b:cb:be"));
		xInstances.get(0).setValue(attrBSSID60, wifiReadings.get("00:18:74:8b:cb:bf"));

		xInstances.get(0).setValue(attrBSSID61, wifiReadings.get("00:18:74:8b:d0:f0"));
		xInstances.get(0).setValue(attrBSSID62, wifiReadings.get("00:18:74:8b:d0:f1"));
		xInstances.get(0).setValue(attrBSSID63, wifiReadings.get("00:18:74:8b:d0:f2"));
		xInstances.get(0).setValue(attrBSSID64, wifiReadings.get("00:18:74:8b:d0:f3"));
		xInstances.get(0).setValue(attrBSSID65, wifiReadings.get("00:18:74:8b:d0:fc"));
		xInstances.get(0).setValue(attrBSSID66, wifiReadings.get("00:18:74:8b:d0:fd"));
		xInstances.get(0).setValue(attrBSSID67, wifiReadings.get("00:18:74:8b:d0:fe"));
		xInstances.get(0).setValue(attrBSSID68, wifiReadings.get("00:18:74:8b:d0:ff"));

		xInstances.get(0).setValue(attrBSSID69, wifiReadings.get("00:18:74:8b:d5:30"));
		xInstances.get(0).setValue(attrBSSID70, wifiReadings.get("00:18:74:8b:d5:31"));
		xInstances.get(0).setValue(attrBSSID71, wifiReadings.get("00:18:74:8b:d5:32"));
		xInstances.get(0).setValue(attrBSSID72, wifiReadings.get("00:18:74:8b:d5:33"));
		xInstances.get(0).setValue(attrBSSID73, wifiReadings.get("00:18:74:8b:d5:3c"));
		xInstances.get(0).setValue(attrBSSID74, wifiReadings.get("00:18:74:8b:d5:3d"));
		xInstances.get(0).setValue(attrBSSID75, wifiReadings.get("00:18:74:8b:d5:3e"));
		xInstances.get(0).setValue(attrBSSID76, wifiReadings.get("00:18:74:8b:d5:3f"));

		xInstances.get(0).setValue(attrBSSID77, wifiReadings.get("00:1c:0f:83:42:f0"));
		xInstances.get(0).setValue(attrBSSID78, wifiReadings.get("00:1c:0f:83:42:f1"));
		xInstances.get(0).setValue(attrBSSID79, wifiReadings.get("00:1c:0f:83:42:f2"));
		xInstances.get(0).setValue(attrBSSID80, wifiReadings.get("00:1c:0f:83:42:f3"));
		xInstances.get(0).setValue(attrBSSID81, wifiReadings.get("00:1c:0f:83:42:fc"));
		xInstances.get(0).setValue(attrBSSID82, wifiReadings.get("00:1c:0f:83:42:fd"));
		xInstances.get(0).setValue(attrBSSID83, wifiReadings.get("00:1c:0f:83:42:fe"));
		xInstances.get(0).setValue(attrBSSID84, wifiReadings.get("00:1c:0f:83:42:ff"));

		xInstances.get(0).setValue(attrBSSID85, wifiReadings.get("00:1c:57:88:31:f0"));
		xInstances.get(0).setValue(attrBSSID86, wifiReadings.get("00:1c:57:88:31:f1"));
		xInstances.get(0).setValue(attrBSSID87, wifiReadings.get("00:1c:57:88:31:f2"));
		xInstances.get(0).setValue(attrBSSID88, wifiReadings.get("00:1c:57:88:31:f3"));
		xInstances.get(0).setValue(attrBSSID89, wifiReadings.get("00:1c:57:88:31:fc"));
		xInstances.get(0).setValue(attrBSSID90, wifiReadings.get("00:1c:57:88:31:fd"));
		xInstances.get(0).setValue(attrBSSID91, wifiReadings.get("00:1c:57:88:31:fe"));
		xInstances.get(0).setValue(attrBSSID92, wifiReadings.get("00:1c:57:88:31:ff"));

		xInstances.get(0).setValue(attrBSSID93, wifiReadings.get("00:24:c3:32:dc:20"));
		xInstances.get(0).setValue(attrBSSID94, wifiReadings.get("00:24:c3:32:dc:21"));
		xInstances.get(0).setValue(attrBSSID95, wifiReadings.get("00:24:c3:32:dc:22"));
		xInstances.get(0).setValue(attrBSSID96, wifiReadings.get("00:24:c3:32:dc:23"));
		xInstances.get(0).setValue(attrBSSID97, wifiReadings.get("00:24:c3:32:dc:2c"));
		xInstances.get(0).setValue(attrBSSID98, wifiReadings.get("00:24:c3:32:dc:2d"));
		xInstances.get(0).setValue(attrBSSID99, wifiReadings.get("00:24:c3:32:dc:2e"));
		xInstances.get(0).setValue(attrBSSID100, wifiReadings.get("00:24:c3:32:dc:2f"));

		xInstances.get(0).setValue(attrBSSID101, wifiReadings.get("00:27:0d:eb:c2:c0"));
		xInstances.get(0).setValue(attrBSSID102, wifiReadings.get("00:27:0d:eb:c2:c1"));
		xInstances.get(0).setValue(attrBSSID103, wifiReadings.get("00:27:0d:eb:c2:c2"));
		xInstances.get(0).setValue(attrBSSID104, wifiReadings.get("00:27:0d:eb:c2:c3"));
		xInstances.get(0).setValue(attrBSSID105, wifiReadings.get("00:27:0d:eb:c2:cc"));
		xInstances.get(0).setValue(attrBSSID106, wifiReadings.get("00:27:0d:eb:c2:cd"));
		xInstances.get(0).setValue(attrBSSID107, wifiReadings.get("00:27:0d:eb:c2:ce"));
		xInstances.get(0).setValue(attrBSSID108, wifiReadings.get("00:27:0d:eb:c2:cf"));

		xInstances.get(0).setValue(attrBSSID109, wifiReadings.get("08:cc:68:63:70:e0"));
		xInstances.get(0).setValue(attrBSSID110, wifiReadings.get("08:cc:68:63:70:e1"));
		xInstances.get(0).setValue(attrBSSID111, wifiReadings.get("08:cc:68:63:70:e2"));
		xInstances.get(0).setValue(attrBSSID112, wifiReadings.get("08:cc:68:63:70:e3"));
		xInstances.get(0).setValue(attrBSSID113, wifiReadings.get("08:cc:68:63:70:ec"));
		xInstances.get(0).setValue(attrBSSID114, wifiReadings.get("08:cc:68:63:70:ed"));
		xInstances.get(0).setValue(attrBSSID115, wifiReadings.get("08:cc:68:63:70:ee"));
		xInstances.get(0).setValue(attrBSSID116, wifiReadings.get("08:cc:68:63:70:ef"));

		xInstances.get(0).setValue(attrBSSID117, wifiReadings.get("08:cc:68:90:fd:00"));
		xInstances.get(0).setValue(attrBSSID118, wifiReadings.get("08:cc:68:90:fd:01"));
		xInstances.get(0).setValue(attrBSSID119, wifiReadings.get("08:cc:68:90:fd:02"));
		xInstances.get(0).setValue(attrBSSID120, wifiReadings.get("08:cc:68:90:fd:03"));
		xInstances.get(0).setValue(attrBSSID121, wifiReadings.get("08:cc:68:90:fd:0c"));
		xInstances.get(0).setValue(attrBSSID122, wifiReadings.get("08:cc:68:90:fd:0d"));
		xInstances.get(0).setValue(attrBSSID123, wifiReadings.get("08:cc:68:90:fd:0e"));
		xInstances.get(0).setValue(attrBSSID124, wifiReadings.get("08:cc:68:90:fd:0f"));

		xInstances.get(0).setValue(attrBSSID125, wifiReadings.get("08:cc:68:b9:7d:00"));
		xInstances.get(0).setValue(attrBSSID126, wifiReadings.get("08:cc:68:b9:7d:01"));
		xInstances.get(0).setValue(attrBSSID127, wifiReadings.get("08:cc:68:b9:7d:02"));
		xInstances.get(0).setValue(attrBSSID128, wifiReadings.get("08:cc:68:b9:7d:03"));
		xInstances.get(0).setValue(attrBSSID129, wifiReadings.get("08:cc:68:b9:7d:0c"));
		xInstances.get(0).setValue(attrBSSID130, wifiReadings.get("08:cc:68:b9:7d:0d"));
		xInstances.get(0).setValue(attrBSSID131, wifiReadings.get("08:cc:68:b9:7d:0e"));
		xInstances.get(0).setValue(attrBSSID132, wifiReadings.get("08:cc:68:b9:7d:0f"));

		xInstances.get(0).setValue(attrBSSID133, wifiReadings.get("08:cc:68:b9:8c:00"));
		xInstances.get(0).setValue(attrBSSID134, wifiReadings.get("08:cc:68:b9:8c:01"));
		xInstances.get(0).setValue(attrBSSID135, wifiReadings.get("08:cc:68:b9:8c:02"));
		xInstances.get(0).setValue(attrBSSID136, wifiReadings.get("08:cc:68:b9:8c:03"));
		xInstances.get(0).setValue(attrBSSID137, wifiReadings.get("08:cc:68:b9:8c:0c"));
		xInstances.get(0).setValue(attrBSSID138, wifiReadings.get("08:cc:68:b9:8c:0d"));
		xInstances.get(0).setValue(attrBSSID139, wifiReadings.get("08:cc:68:b9:8c:0e"));
		xInstances.get(0).setValue(attrBSSID140, wifiReadings.get("08:cc:68:b9:8c:0f"));

		xInstances.get(0).setValue(attrBSSID141, wifiReadings.get("08:cc:68:da:56:80"));
		xInstances.get(0).setValue(attrBSSID142, wifiReadings.get("08:cc:68:da:56:81"));
		xInstances.get(0).setValue(attrBSSID143, wifiReadings.get("08:cc:68:da:56:82"));
		xInstances.get(0).setValue(attrBSSID144, wifiReadings.get("08:cc:68:da:56:83"));
		xInstances.get(0).setValue(attrBSSID145, wifiReadings.get("08:cc:68:da:56:8c"));
		xInstances.get(0).setValue(attrBSSID146, wifiReadings.get("08:cc:68:da:56:8d"));
		xInstances.get(0).setValue(attrBSSID147, wifiReadings.get("08:cc:68:da:56:8e"));
		xInstances.get(0).setValue(attrBSSID148, wifiReadings.get("08:cc:68:da:56:8f"));

		xInstances.get(0).setValue(attrBSSID149, wifiReadings.get("20:3a:07:38:34:b0"));
		xInstances.get(0).setValue(attrBSSID150, wifiReadings.get("20:3a:07:38:34:b1"));
		xInstances.get(0).setValue(attrBSSID151, wifiReadings.get("20:3a:07:38:34:b2"));
		xInstances.get(0).setValue(attrBSSID152, wifiReadings.get("20:3a:07:38:34:b3"));
		xInstances.get(0).setValue(attrBSSID153, wifiReadings.get("20:3a:07:38:34:bc"));
		xInstances.get(0).setValue(attrBSSID154, wifiReadings.get("20:3a:07:38:34:bd"));
		xInstances.get(0).setValue(attrBSSID155, wifiReadings.get("20:3a:07:38:34:be"));
		xInstances.get(0).setValue(attrBSSID156, wifiReadings.get("20:3a:07:38:34:bf"));

		if (location != null) {
			xInstances.get(0).setValue(attrLatitude, location.getLatitude());
			xInstances.get(0).setValue(attrLongitude, location.getLongitude());
			xInstances.get(0).setValue(attrLocationAccuracy, location.getAccuracy());
		} else {
			Toast.makeText(this, "Location was null", Toast.LENGTH_SHORT).show();
			xInstances.get(0).setMissing(attrLatitude);
			xInstances.get(0).setMissing(attrLongitude);
			xInstances.get(0).setMissing(attrLocationAccuracy);
		}

		yInstances.get(0).setValue(attrAccelX, accelerometerX);
		yInstances.get(0).setValue(attrAccelY, accelerometerY);
		yInstances.get(0).setValue(attrAccelZ, accelerometerZ);
		yInstances.get(0).setValue(attrMagneticX, magneticX);
		yInstances.get(0).setValue(attrMagneticY, magneticY);
		yInstances.get(0).setValue(attrMagneticZ, magneticZ);
		yInstances.get(0).setValue(attrLight, light);
		yInstances.get(0).setValue(attrRotationX, rotationX);
		yInstances.get(0).setValue(attrRotationY, rotationY);
		yInstances.get(0).setValue(attrRotationZ, rotationZ);
		yInstances.get(0).setValue(attrOrientationX, orientation[0]);
		yInstances.get(0).setValue(attrOrientationY, orientation[1]);
		yInstances.get(0).setValue(attrOrientationZ, orientation[2]);
		yInstances.get(0).setValue(attrBSSID1, wifiReadings.get("00:17:0f:8d:c3:e0"));
		yInstances.get(0).setValue(attrBSSID2, wifiReadings.get("00:17:0f:8d:c3:e1"));
		yInstances.get(0).setValue(attrBSSID3, wifiReadings.get("00:17:0f:8d:c3:e2"));
		yInstances.get(0).setValue(attrBSSID4, wifiReadings.get("00:17:0f:8d:c3:e3"));

		yInstances.get(0).setValue(attrBSSID5, wifiReadings.get("00:17:0f:8d:c3:f0"));
		yInstances.get(0).setValue(attrBSSID6, wifiReadings.get("00:17:0f:8d:c3:f1"));
		yInstances.get(0).setValue(attrBSSID7, wifiReadings.get("00:17:0f:8d:c3:f2"));
		yInstances.get(0).setValue(attrBSSID8, wifiReadings.get("00:17:0f:8d:c3:f3"));

		yInstances.get(0).setValue(attrBSSID9, wifiReadings.get("00:18:74:88:df:20"));
		yInstances.get(0).setValue(attrBSSID10, wifiReadings.get("00:18:74:88:df:21"));
		yInstances.get(0).setValue(attrBSSID11, wifiReadings.get("00:18:74:88:df:22"));
		yInstances.get(0).setValue(attrBSSID12, wifiReadings.get("00:18:74:88:df:23"));

		yInstances.get(0).setValue(attrBSSID13, wifiReadings.get("00:18:74:89:58:e0"));
		yInstances.get(0).setValue(attrBSSID14, wifiReadings.get("00:18:74:89:58:e1"));
		yInstances.get(0).setValue(attrBSSID15, wifiReadings.get("00:18:74:89:58:e2"));
		yInstances.get(0).setValue(attrBSSID16, wifiReadings.get("00:18:74:89:58:e3"));
		yInstances.get(0).setValue(attrBSSID17, wifiReadings.get("00:18:74:89:58:ec"));
		yInstances.get(0).setValue(attrBSSID18, wifiReadings.get("00:18:74:89:58:ed"));
		yInstances.get(0).setValue(attrBSSID19, wifiReadings.get("00:18:74:89:58:ee"));
		yInstances.get(0).setValue(attrBSSID20, wifiReadings.get("00:18:74:89:58:ef"));

		yInstances.get(0).setValue(attrBSSID21, wifiReadings.get("00:18:74:89:59:90"));
		yInstances.get(0).setValue(attrBSSID22, wifiReadings.get("00:18:74:89:59:91"));
		yInstances.get(0).setValue(attrBSSID23, wifiReadings.get("00:18:74:89:59:92"));
		yInstances.get(0).setValue(attrBSSID24, wifiReadings.get("00:18:74:89:59:93"));
		yInstances.get(0).setValue(attrBSSID25, wifiReadings.get("00:18:74:89:59:9c"));
		yInstances.get(0).setValue(attrBSSID26, wifiReadings.get("00:18:74:89:59:9d"));
		yInstances.get(0).setValue(attrBSSID27, wifiReadings.get("00:18:74:89:59:9e"));
		yInstances.get(0).setValue(attrBSSID28, wifiReadings.get("00:18:74:89:59:9f"));

		yInstances.get(0).setValue(attrBSSID29, wifiReadings.get("00:18:74:89:a9:20"));
		yInstances.get(0).setValue(attrBSSID30, wifiReadings.get("00:18:74:89:a9:21"));
		yInstances.get(0).setValue(attrBSSID31, wifiReadings.get("00:18:74:89:a9:22"));
		yInstances.get(0).setValue(attrBSSID32, wifiReadings.get("00:18:74:89:a9:23"));
		yInstances.get(0).setValue(attrBSSID33, wifiReadings.get("00:18:74:89:a9:2c"));
		yInstances.get(0).setValue(attrBSSID34, wifiReadings.get("00:18:74:89:a9:2d"));
		yInstances.get(0).setValue(attrBSSID35, wifiReadings.get("00:18:74:89:a9:2e"));
		yInstances.get(0).setValue(attrBSSID36, wifiReadings.get("00:18:74:89:a9:2f"));

		yInstances.get(0).setValue(attrBSSID37, wifiReadings.get("00:18:74:8b:90:f0"));
		yInstances.get(0).setValue(attrBSSID38, wifiReadings.get("00:18:74:8b:90:f1"));
		yInstances.get(0).setValue(attrBSSID39, wifiReadings.get("00:18:74:8b:90:f2"));
		yInstances.get(0).setValue(attrBSSID40, wifiReadings.get("00:18:74:8b:90:f3"));
		yInstances.get(0).setValue(attrBSSID41, wifiReadings.get("00:18:74:8b:90:fc"));
		yInstances.get(0).setValue(attrBSSID42, wifiReadings.get("00:18:74:8b:90:fd"));
		yInstances.get(0).setValue(attrBSSID43, wifiReadings.get("00:18:74:8b:90:fe"));
		yInstances.get(0).setValue(attrBSSID44, wifiReadings.get("00:18:74:8b:90:ff"));

		yInstances.get(0).setValue(attrBSSID45, wifiReadings.get("00:18:74:8b:c8:d0"));
		yInstances.get(0).setValue(attrBSSID46, wifiReadings.get("00:18:74:8b:c8:d1"));
		yInstances.get(0).setValue(attrBSSID47, wifiReadings.get("00:18:74:8b:c8:d2"));
		yInstances.get(0).setValue(attrBSSID48, wifiReadings.get("00:18:74:8b:c8:d3"));
		yInstances.get(0).setValue(attrBSSID49, wifiReadings.get("00:18:74:8b:c8:dc"));
		yInstances.get(0).setValue(attrBSSID50, wifiReadings.get("00:18:74:8b:c8:dd"));
		yInstances.get(0).setValue(attrBSSID51, wifiReadings.get("00:18:74:8b:c8:de"));
		yInstances.get(0).setValue(attrBSSID52, wifiReadings.get("00:18:74:8b:c8:df"));

		yInstances.get(0).setValue(attrBSSID53, wifiReadings.get("00:18:74:8b:cb:b0"));
		yInstances.get(0).setValue(attrBSSID54, wifiReadings.get("00:18:74:8b:cb:b1"));
		yInstances.get(0).setValue(attrBSSID55, wifiReadings.get("00:18:74:8b:cb:b2"));
		yInstances.get(0).setValue(attrBSSID56, wifiReadings.get("00:18:74:8b:cb:b3"));
		yInstances.get(0).setValue(attrBSSID57, wifiReadings.get("00:18:74:8b:cb:bc"));
		yInstances.get(0).setValue(attrBSSID58, wifiReadings.get("00:18:74:8b:cb:bd"));
		yInstances.get(0).setValue(attrBSSID59, wifiReadings.get("00:18:74:8b:cb:be"));
		yInstances.get(0).setValue(attrBSSID60, wifiReadings.get("00:18:74:8b:cb:bf"));

		yInstances.get(0).setValue(attrBSSID61, wifiReadings.get("00:18:74:8b:d0:f0"));
		yInstances.get(0).setValue(attrBSSID62, wifiReadings.get("00:18:74:8b:d0:f1"));
		yInstances.get(0).setValue(attrBSSID63, wifiReadings.get("00:18:74:8b:d0:f2"));
		yInstances.get(0).setValue(attrBSSID64, wifiReadings.get("00:18:74:8b:d0:f3"));
		yInstances.get(0).setValue(attrBSSID65, wifiReadings.get("00:18:74:8b:d0:fc"));
		yInstances.get(0).setValue(attrBSSID66, wifiReadings.get("00:18:74:8b:d0:fd"));
		yInstances.get(0).setValue(attrBSSID67, wifiReadings.get("00:18:74:8b:d0:fe"));
		yInstances.get(0).setValue(attrBSSID68, wifiReadings.get("00:18:74:8b:d0:ff"));

		yInstances.get(0).setValue(attrBSSID69, wifiReadings.get("00:18:74:8b:d5:30"));
		yInstances.get(0).setValue(attrBSSID70, wifiReadings.get("00:18:74:8b:d5:31"));
		yInstances.get(0).setValue(attrBSSID71, wifiReadings.get("00:18:74:8b:d5:32"));
		yInstances.get(0).setValue(attrBSSID72, wifiReadings.get("00:18:74:8b:d5:33"));
		yInstances.get(0).setValue(attrBSSID73, wifiReadings.get("00:18:74:8b:d5:3c"));
		yInstances.get(0).setValue(attrBSSID74, wifiReadings.get("00:18:74:8b:d5:3d"));
		yInstances.get(0).setValue(attrBSSID75, wifiReadings.get("00:18:74:8b:d5:3e"));
		yInstances.get(0).setValue(attrBSSID76, wifiReadings.get("00:18:74:8b:d5:3f"));

		yInstances.get(0).setValue(attrBSSID77, wifiReadings.get("00:1c:0f:83:42:f0"));
		yInstances.get(0).setValue(attrBSSID78, wifiReadings.get("00:1c:0f:83:42:f1"));
		yInstances.get(0).setValue(attrBSSID79, wifiReadings.get("00:1c:0f:83:42:f2"));
		yInstances.get(0).setValue(attrBSSID80, wifiReadings.get("00:1c:0f:83:42:f3"));
		yInstances.get(0).setValue(attrBSSID81, wifiReadings.get("00:1c:0f:83:42:fc"));
		yInstances.get(0).setValue(attrBSSID82, wifiReadings.get("00:1c:0f:83:42:fd"));
		yInstances.get(0).setValue(attrBSSID83, wifiReadings.get("00:1c:0f:83:42:fe"));
		yInstances.get(0).setValue(attrBSSID84, wifiReadings.get("00:1c:0f:83:42:ff"));

		yInstances.get(0).setValue(attrBSSID85, wifiReadings.get("00:1c:57:88:31:f0"));
		yInstances.get(0).setValue(attrBSSID86, wifiReadings.get("00:1c:57:88:31:f1"));
		yInstances.get(0).setValue(attrBSSID87, wifiReadings.get("00:1c:57:88:31:f2"));
		yInstances.get(0).setValue(attrBSSID88, wifiReadings.get("00:1c:57:88:31:f3"));
		yInstances.get(0).setValue(attrBSSID89, wifiReadings.get("00:1c:57:88:31:fc"));
		yInstances.get(0).setValue(attrBSSID90, wifiReadings.get("00:1c:57:88:31:fd"));
		yInstances.get(0).setValue(attrBSSID91, wifiReadings.get("00:1c:57:88:31:fe"));
		yInstances.get(0).setValue(attrBSSID92, wifiReadings.get("00:1c:57:88:31:ff"));

		yInstances.get(0).setValue(attrBSSID93, wifiReadings.get("00:24:c3:32:dc:20"));
		yInstances.get(0).setValue(attrBSSID94, wifiReadings.get("00:24:c3:32:dc:21"));
		yInstances.get(0).setValue(attrBSSID95, wifiReadings.get("00:24:c3:32:dc:22"));
		yInstances.get(0).setValue(attrBSSID96, wifiReadings.get("00:24:c3:32:dc:23"));
		yInstances.get(0).setValue(attrBSSID97, wifiReadings.get("00:24:c3:32:dc:2c"));
		yInstances.get(0).setValue(attrBSSID98, wifiReadings.get("00:24:c3:32:dc:2d"));
		yInstances.get(0).setValue(attrBSSID99, wifiReadings.get("00:24:c3:32:dc:2e"));
		yInstances.get(0).setValue(attrBSSID100, wifiReadings.get("00:24:c3:32:dc:2f"));

		yInstances.get(0).setValue(attrBSSID101, wifiReadings.get("00:27:0d:eb:c2:c0"));
		yInstances.get(0).setValue(attrBSSID102, wifiReadings.get("00:27:0d:eb:c2:c1"));
		yInstances.get(0).setValue(attrBSSID103, wifiReadings.get("00:27:0d:eb:c2:c2"));
		yInstances.get(0).setValue(attrBSSID104, wifiReadings.get("00:27:0d:eb:c2:c3"));
		yInstances.get(0).setValue(attrBSSID105, wifiReadings.get("00:27:0d:eb:c2:cc"));
		yInstances.get(0).setValue(attrBSSID106, wifiReadings.get("00:27:0d:eb:c2:cd"));
		yInstances.get(0).setValue(attrBSSID107, wifiReadings.get("00:27:0d:eb:c2:ce"));
		yInstances.get(0).setValue(attrBSSID108, wifiReadings.get("00:27:0d:eb:c2:cf"));

		yInstances.get(0).setValue(attrBSSID109, wifiReadings.get("08:cc:68:63:70:e0"));
		yInstances.get(0).setValue(attrBSSID110, wifiReadings.get("08:cc:68:63:70:e1"));
		yInstances.get(0).setValue(attrBSSID111, wifiReadings.get("08:cc:68:63:70:e2"));
		yInstances.get(0).setValue(attrBSSID112, wifiReadings.get("08:cc:68:63:70:e3"));
		yInstances.get(0).setValue(attrBSSID113, wifiReadings.get("08:cc:68:63:70:ec"));
		yInstances.get(0).setValue(attrBSSID114, wifiReadings.get("08:cc:68:63:70:ed"));
		yInstances.get(0).setValue(attrBSSID115, wifiReadings.get("08:cc:68:63:70:ee"));
		yInstances.get(0).setValue(attrBSSID116, wifiReadings.get("08:cc:68:63:70:ef"));

		yInstances.get(0).setValue(attrBSSID117, wifiReadings.get("08:cc:68:90:fd:00"));
		yInstances.get(0).setValue(attrBSSID118, wifiReadings.get("08:cc:68:90:fd:01"));
		yInstances.get(0).setValue(attrBSSID119, wifiReadings.get("08:cc:68:90:fd:02"));
		yInstances.get(0).setValue(attrBSSID120, wifiReadings.get("08:cc:68:90:fd:03"));
		yInstances.get(0).setValue(attrBSSID121, wifiReadings.get("08:cc:68:90:fd:0c"));
		yInstances.get(0).setValue(attrBSSID122, wifiReadings.get("08:cc:68:90:fd:0d"));
		yInstances.get(0).setValue(attrBSSID123, wifiReadings.get("08:cc:68:90:fd:0e"));
		yInstances.get(0).setValue(attrBSSID124, wifiReadings.get("08:cc:68:90:fd:0f"));

		yInstances.get(0).setValue(attrBSSID125, wifiReadings.get("08:cc:68:b9:7d:00"));
		yInstances.get(0).setValue(attrBSSID126, wifiReadings.get("08:cc:68:b9:7d:01"));
		yInstances.get(0).setValue(attrBSSID127, wifiReadings.get("08:cc:68:b9:7d:02"));
		yInstances.get(0).setValue(attrBSSID128, wifiReadings.get("08:cc:68:b9:7d:03"));
		yInstances.get(0).setValue(attrBSSID129, wifiReadings.get("08:cc:68:b9:7d:0c"));
		yInstances.get(0).setValue(attrBSSID130, wifiReadings.get("08:cc:68:b9:7d:0d"));
		yInstances.get(0).setValue(attrBSSID131, wifiReadings.get("08:cc:68:b9:7d:0e"));
		yInstances.get(0).setValue(attrBSSID132, wifiReadings.get("08:cc:68:b9:7d:0f"));

		yInstances.get(0).setValue(attrBSSID133, wifiReadings.get("08:cc:68:b9:8c:00"));
		yInstances.get(0).setValue(attrBSSID134, wifiReadings.get("08:cc:68:b9:8c:01"));
		yInstances.get(0).setValue(attrBSSID135, wifiReadings.get("08:cc:68:b9:8c:02"));
		yInstances.get(0).setValue(attrBSSID136, wifiReadings.get("08:cc:68:b9:8c:03"));
		yInstances.get(0).setValue(attrBSSID137, wifiReadings.get("08:cc:68:b9:8c:0c"));
		yInstances.get(0).setValue(attrBSSID138, wifiReadings.get("08:cc:68:b9:8c:0d"));
		yInstances.get(0).setValue(attrBSSID139, wifiReadings.get("08:cc:68:b9:8c:0e"));
		yInstances.get(0).setValue(attrBSSID140, wifiReadings.get("08:cc:68:b9:8c:0f"));

		yInstances.get(0).setValue(attrBSSID141, wifiReadings.get("08:cc:68:da:56:80"));
		yInstances.get(0).setValue(attrBSSID142, wifiReadings.get("08:cc:68:da:56:81"));
		yInstances.get(0).setValue(attrBSSID143, wifiReadings.get("08:cc:68:da:56:82"));
		yInstances.get(0).setValue(attrBSSID144, wifiReadings.get("08:cc:68:da:56:83"));
		yInstances.get(0).setValue(attrBSSID145, wifiReadings.get("08:cc:68:da:56:8c"));
		yInstances.get(0).setValue(attrBSSID146, wifiReadings.get("08:cc:68:da:56:8d"));
		yInstances.get(0).setValue(attrBSSID147, wifiReadings.get("08:cc:68:da:56:8e"));
		yInstances.get(0).setValue(attrBSSID148, wifiReadings.get("08:cc:68:da:56:8f"));

		yInstances.get(0).setValue(attrBSSID149, wifiReadings.get("20:3a:07:38:34:b0"));
		yInstances.get(0).setValue(attrBSSID150, wifiReadings.get("20:3a:07:38:34:b1"));
		yInstances.get(0).setValue(attrBSSID151, wifiReadings.get("20:3a:07:38:34:b2"));
		yInstances.get(0).setValue(attrBSSID152, wifiReadings.get("20:3a:07:38:34:b3"));
		yInstances.get(0).setValue(attrBSSID153, wifiReadings.get("20:3a:07:38:34:bc"));
		yInstances.get(0).setValue(attrBSSID154, wifiReadings.get("20:3a:07:38:34:bd"));
		yInstances.get(0).setValue(attrBSSID155, wifiReadings.get("20:3a:07:38:34:be"));
		yInstances.get(0).setValue(attrBSSID156, wifiReadings.get("20:3a:07:38:34:bf"));

		if (location != null) {
			yInstances.get(0).setValue(attrLatitude, location.getLatitude());
			yInstances.get(0).setValue(attrLongitude, location.getLongitude());
			yInstances.get(0).setValue(attrLocationAccuracy, location.getAccuracy());
		} else {
			yInstances.get(0).setMissing(attrLatitude);
			yInstances.get(0).setMissing(attrLongitude);
			yInstances.get(0).setMissing(attrLocationAccuracy);
		}

		partitionInstances.get(0).setValue(attrAccelX, accelerometerX);
		partitionInstances.get(0).setValue(attrAccelY, accelerometerY);
		partitionInstances.get(0).setValue(attrAccelZ, accelerometerZ);
		partitionInstances.get(0).setValue(attrMagneticX, magneticX);
		partitionInstances.get(0).setValue(attrMagneticY, magneticY);
		partitionInstances.get(0).setValue(attrMagneticZ, magneticZ);
		partitionInstances.get(0).setValue(attrLight, light);
		partitionInstances.get(0).setValue(attrRotationX, rotationX);
		partitionInstances.get(0).setValue(attrRotationY, rotationY);
		partitionInstances.get(0).setValue(attrRotationZ, rotationZ);
		partitionInstances.get(0).setValue(attrOrientationX, orientation[0]);
		partitionInstances.get(0).setValue(attrOrientationY, orientation[1]);
		partitionInstances.get(0).setValue(attrOrientationZ, orientation[2]);
		partitionInstances.get(0).setValue(attrBSSID1, wifiReadings.get("00:17:0f:8d:c3:e0"));
		partitionInstances.get(0).setValue(attrBSSID2, wifiReadings.get("00:17:0f:8d:c3:e1"));
		partitionInstances.get(0).setValue(attrBSSID3, wifiReadings.get("00:17:0f:8d:c3:e2"));
		partitionInstances.get(0).setValue(attrBSSID4, wifiReadings.get("00:17:0f:8d:c3:e3"));

		partitionInstances.get(0).setValue(attrBSSID5, wifiReadings.get("00:17:0f:8d:c3:f0"));
		partitionInstances.get(0).setValue(attrBSSID6, wifiReadings.get("00:17:0f:8d:c3:f1"));
		partitionInstances.get(0).setValue(attrBSSID7, wifiReadings.get("00:17:0f:8d:c3:f2"));
		partitionInstances.get(0).setValue(attrBSSID8, wifiReadings.get("00:17:0f:8d:c3:f3"));

		partitionInstances.get(0).setValue(attrBSSID9, wifiReadings.get("00:18:74:88:df:20"));
		partitionInstances.get(0).setValue(attrBSSID10, wifiReadings.get("00:18:74:88:df:21"));
		partitionInstances.get(0).setValue(attrBSSID11, wifiReadings.get("00:18:74:88:df:22"));
		partitionInstances.get(0).setValue(attrBSSID12, wifiReadings.get("00:18:74:88:df:23"));

		partitionInstances.get(0).setValue(attrBSSID13, wifiReadings.get("00:18:74:89:58:e0"));
		partitionInstances.get(0).setValue(attrBSSID14, wifiReadings.get("00:18:74:89:58:e1"));
		partitionInstances.get(0).setValue(attrBSSID15, wifiReadings.get("00:18:74:89:58:e2"));
		partitionInstances.get(0).setValue(attrBSSID16, wifiReadings.get("00:18:74:89:58:e3"));
		partitionInstances.get(0).setValue(attrBSSID17, wifiReadings.get("00:18:74:89:58:ec"));
		partitionInstances.get(0).setValue(attrBSSID18, wifiReadings.get("00:18:74:89:58:ed"));
		partitionInstances.get(0).setValue(attrBSSID19, wifiReadings.get("00:18:74:89:58:ee"));
		partitionInstances.get(0).setValue(attrBSSID20, wifiReadings.get("00:18:74:89:58:ef"));

		partitionInstances.get(0).setValue(attrBSSID21, wifiReadings.get("00:18:74:89:59:90"));
		partitionInstances.get(0).setValue(attrBSSID22, wifiReadings.get("00:18:74:89:59:91"));
		partitionInstances.get(0).setValue(attrBSSID23, wifiReadings.get("00:18:74:89:59:92"));
		partitionInstances.get(0).setValue(attrBSSID24, wifiReadings.get("00:18:74:89:59:93"));
		partitionInstances.get(0).setValue(attrBSSID25, wifiReadings.get("00:18:74:89:59:9c"));
		partitionInstances.get(0).setValue(attrBSSID26, wifiReadings.get("00:18:74:89:59:9d"));
		partitionInstances.get(0).setValue(attrBSSID27, wifiReadings.get("00:18:74:89:59:9e"));
		partitionInstances.get(0).setValue(attrBSSID28, wifiReadings.get("00:18:74:89:59:9f"));

		partitionInstances.get(0).setValue(attrBSSID29, wifiReadings.get("00:18:74:89:a9:20"));
		partitionInstances.get(0).setValue(attrBSSID30, wifiReadings.get("00:18:74:89:a9:21"));
		partitionInstances.get(0).setValue(attrBSSID31, wifiReadings.get("00:18:74:89:a9:22"));
		partitionInstances.get(0).setValue(attrBSSID32, wifiReadings.get("00:18:74:89:a9:23"));
		partitionInstances.get(0).setValue(attrBSSID33, wifiReadings.get("00:18:74:89:a9:2c"));
		partitionInstances.get(0).setValue(attrBSSID34, wifiReadings.get("00:18:74:89:a9:2d"));
		partitionInstances.get(0).setValue(attrBSSID35, wifiReadings.get("00:18:74:89:a9:2e"));
		partitionInstances.get(0).setValue(attrBSSID36, wifiReadings.get("00:18:74:89:a9:2f"));

		partitionInstances.get(0).setValue(attrBSSID37, wifiReadings.get("00:18:74:8b:90:f0"));
		partitionInstances.get(0).setValue(attrBSSID38, wifiReadings.get("00:18:74:8b:90:f1"));
		partitionInstances.get(0).setValue(attrBSSID39, wifiReadings.get("00:18:74:8b:90:f2"));
		partitionInstances.get(0).setValue(attrBSSID40, wifiReadings.get("00:18:74:8b:90:f3"));
		partitionInstances.get(0).setValue(attrBSSID41, wifiReadings.get("00:18:74:8b:90:fc"));
		partitionInstances.get(0).setValue(attrBSSID42, wifiReadings.get("00:18:74:8b:90:fd"));
		partitionInstances.get(0).setValue(attrBSSID43, wifiReadings.get("00:18:74:8b:90:fe"));
		partitionInstances.get(0).setValue(attrBSSID44, wifiReadings.get("00:18:74:8b:90:ff"));

		partitionInstances.get(0).setValue(attrBSSID45, wifiReadings.get("00:18:74:8b:c8:d0"));
		partitionInstances.get(0).setValue(attrBSSID46, wifiReadings.get("00:18:74:8b:c8:d1"));
		partitionInstances.get(0).setValue(attrBSSID47, wifiReadings.get("00:18:74:8b:c8:d2"));
		partitionInstances.get(0).setValue(attrBSSID48, wifiReadings.get("00:18:74:8b:c8:d3"));
		partitionInstances.get(0).setValue(attrBSSID49, wifiReadings.get("00:18:74:8b:c8:dc"));
		partitionInstances.get(0).setValue(attrBSSID50, wifiReadings.get("00:18:74:8b:c8:dd"));
		partitionInstances.get(0).setValue(attrBSSID51, wifiReadings.get("00:18:74:8b:c8:de"));
		partitionInstances.get(0).setValue(attrBSSID52, wifiReadings.get("00:18:74:8b:c8:df"));

		partitionInstances.get(0).setValue(attrBSSID53, wifiReadings.get("00:18:74:8b:cb:b0"));
		partitionInstances.get(0).setValue(attrBSSID54, wifiReadings.get("00:18:74:8b:cb:b1"));
		partitionInstances.get(0).setValue(attrBSSID55, wifiReadings.get("00:18:74:8b:cb:b2"));
		partitionInstances.get(0).setValue(attrBSSID56, wifiReadings.get("00:18:74:8b:cb:b3"));
		partitionInstances.get(0).setValue(attrBSSID57, wifiReadings.get("00:18:74:8b:cb:bc"));
		partitionInstances.get(0).setValue(attrBSSID58, wifiReadings.get("00:18:74:8b:cb:bd"));
		partitionInstances.get(0).setValue(attrBSSID59, wifiReadings.get("00:18:74:8b:cb:be"));
		partitionInstances.get(0).setValue(attrBSSID60, wifiReadings.get("00:18:74:8b:cb:bf"));

		partitionInstances.get(0).setValue(attrBSSID61, wifiReadings.get("00:18:74:8b:d0:f0"));
		partitionInstances.get(0).setValue(attrBSSID62, wifiReadings.get("00:18:74:8b:d0:f1"));
		partitionInstances.get(0).setValue(attrBSSID63, wifiReadings.get("00:18:74:8b:d0:f2"));
		partitionInstances.get(0).setValue(attrBSSID64, wifiReadings.get("00:18:74:8b:d0:f3"));
		partitionInstances.get(0).setValue(attrBSSID65, wifiReadings.get("00:18:74:8b:d0:fc"));
		partitionInstances.get(0).setValue(attrBSSID66, wifiReadings.get("00:18:74:8b:d0:fd"));
		partitionInstances.get(0).setValue(attrBSSID67, wifiReadings.get("00:18:74:8b:d0:fe"));
		partitionInstances.get(0).setValue(attrBSSID68, wifiReadings.get("00:18:74:8b:d0:ff"));

		partitionInstances.get(0).setValue(attrBSSID69, wifiReadings.get("00:18:74:8b:d5:30"));
		partitionInstances.get(0).setValue(attrBSSID70, wifiReadings.get("00:18:74:8b:d5:31"));
		partitionInstances.get(0).setValue(attrBSSID71, wifiReadings.get("00:18:74:8b:d5:32"));
		partitionInstances.get(0).setValue(attrBSSID72, wifiReadings.get("00:18:74:8b:d5:33"));
		partitionInstances.get(0).setValue(attrBSSID73, wifiReadings.get("00:18:74:8b:d5:3c"));
		partitionInstances.get(0).setValue(attrBSSID74, wifiReadings.get("00:18:74:8b:d5:3d"));
		partitionInstances.get(0).setValue(attrBSSID75, wifiReadings.get("00:18:74:8b:d5:3e"));
		partitionInstances.get(0).setValue(attrBSSID76, wifiReadings.get("00:18:74:8b:d5:3f"));

		partitionInstances.get(0).setValue(attrBSSID77, wifiReadings.get("00:1c:0f:83:42:f0"));
		partitionInstances.get(0).setValue(attrBSSID78, wifiReadings.get("00:1c:0f:83:42:f1"));
		partitionInstances.get(0).setValue(attrBSSID79, wifiReadings.get("00:1c:0f:83:42:f2"));
		partitionInstances.get(0).setValue(attrBSSID80, wifiReadings.get("00:1c:0f:83:42:f3"));
		partitionInstances.get(0).setValue(attrBSSID81, wifiReadings.get("00:1c:0f:83:42:fc"));
		partitionInstances.get(0).setValue(attrBSSID82, wifiReadings.get("00:1c:0f:83:42:fd"));
		partitionInstances.get(0).setValue(attrBSSID83, wifiReadings.get("00:1c:0f:83:42:fe"));
		partitionInstances.get(0).setValue(attrBSSID84, wifiReadings.get("00:1c:0f:83:42:ff"));

		partitionInstances.get(0).setValue(attrBSSID85, wifiReadings.get("00:1c:57:88:31:f0"));
		partitionInstances.get(0).setValue(attrBSSID86, wifiReadings.get("00:1c:57:88:31:f1"));
		partitionInstances.get(0).setValue(attrBSSID87, wifiReadings.get("00:1c:57:88:31:f2"));
		partitionInstances.get(0).setValue(attrBSSID88, wifiReadings.get("00:1c:57:88:31:f3"));
		partitionInstances.get(0).setValue(attrBSSID89, wifiReadings.get("00:1c:57:88:31:fc"));
		partitionInstances.get(0).setValue(attrBSSID90, wifiReadings.get("00:1c:57:88:31:fd"));
		partitionInstances.get(0).setValue(attrBSSID91, wifiReadings.get("00:1c:57:88:31:fe"));
		partitionInstances.get(0).setValue(attrBSSID92, wifiReadings.get("00:1c:57:88:31:ff"));

		partitionInstances.get(0).setValue(attrBSSID93, wifiReadings.get("00:24:c3:32:dc:20"));
		partitionInstances.get(0).setValue(attrBSSID94, wifiReadings.get("00:24:c3:32:dc:21"));
		partitionInstances.get(0).setValue(attrBSSID95, wifiReadings.get("00:24:c3:32:dc:22"));
		partitionInstances.get(0).setValue(attrBSSID96, wifiReadings.get("00:24:c3:32:dc:23"));
		partitionInstances.get(0).setValue(attrBSSID97, wifiReadings.get("00:24:c3:32:dc:2c"));
		partitionInstances.get(0).setValue(attrBSSID98, wifiReadings.get("00:24:c3:32:dc:2d"));
		partitionInstances.get(0).setValue(attrBSSID99, wifiReadings.get("00:24:c3:32:dc:2e"));
		partitionInstances.get(0).setValue(attrBSSID100, wifiReadings.get("00:24:c3:32:dc:2f"));

		partitionInstances.get(0).setValue(attrBSSID101, wifiReadings.get("00:27:0d:eb:c2:c0"));
		partitionInstances.get(0).setValue(attrBSSID102, wifiReadings.get("00:27:0d:eb:c2:c1"));
		partitionInstances.get(0).setValue(attrBSSID103, wifiReadings.get("00:27:0d:eb:c2:c2"));
		partitionInstances.get(0).setValue(attrBSSID104, wifiReadings.get("00:27:0d:eb:c2:c3"));
		partitionInstances.get(0).setValue(attrBSSID105, wifiReadings.get("00:27:0d:eb:c2:cc"));
		partitionInstances.get(0).setValue(attrBSSID106, wifiReadings.get("00:27:0d:eb:c2:cd"));
		partitionInstances.get(0).setValue(attrBSSID107, wifiReadings.get("00:27:0d:eb:c2:ce"));
		partitionInstances.get(0).setValue(attrBSSID108, wifiReadings.get("00:27:0d:eb:c2:cf"));

		partitionInstances.get(0).setValue(attrBSSID109, wifiReadings.get("08:cc:68:63:70:e0"));
		partitionInstances.get(0).setValue(attrBSSID110, wifiReadings.get("08:cc:68:63:70:e1"));
		partitionInstances.get(0).setValue(attrBSSID111, wifiReadings.get("08:cc:68:63:70:e2"));
		partitionInstances.get(0).setValue(attrBSSID112, wifiReadings.get("08:cc:68:63:70:e3"));
		partitionInstances.get(0).setValue(attrBSSID113, wifiReadings.get("08:cc:68:63:70:ec"));
		partitionInstances.get(0).setValue(attrBSSID114, wifiReadings.get("08:cc:68:63:70:ed"));
		partitionInstances.get(0).setValue(attrBSSID115, wifiReadings.get("08:cc:68:63:70:ee"));
		partitionInstances.get(0).setValue(attrBSSID116, wifiReadings.get("08:cc:68:63:70:ef"));

		partitionInstances.get(0).setValue(attrBSSID117, wifiReadings.get("08:cc:68:90:fd:00"));
		partitionInstances.get(0).setValue(attrBSSID118, wifiReadings.get("08:cc:68:90:fd:01"));
		partitionInstances.get(0).setValue(attrBSSID119, wifiReadings.get("08:cc:68:90:fd:02"));
		partitionInstances.get(0).setValue(attrBSSID120, wifiReadings.get("08:cc:68:90:fd:03"));
		partitionInstances.get(0).setValue(attrBSSID121, wifiReadings.get("08:cc:68:90:fd:0c"));
		partitionInstances.get(0).setValue(attrBSSID122, wifiReadings.get("08:cc:68:90:fd:0d"));
		partitionInstances.get(0).setValue(attrBSSID123, wifiReadings.get("08:cc:68:90:fd:0e"));
		partitionInstances.get(0).setValue(attrBSSID124, wifiReadings.get("08:cc:68:90:fd:0f"));

		partitionInstances.get(0).setValue(attrBSSID125, wifiReadings.get("08:cc:68:b9:7d:00"));
		partitionInstances.get(0).setValue(attrBSSID126, wifiReadings.get("08:cc:68:b9:7d:01"));
		partitionInstances.get(0).setValue(attrBSSID127, wifiReadings.get("08:cc:68:b9:7d:02"));
		partitionInstances.get(0).setValue(attrBSSID128, wifiReadings.get("08:cc:68:b9:7d:03"));
		partitionInstances.get(0).setValue(attrBSSID129, wifiReadings.get("08:cc:68:b9:7d:0c"));
		partitionInstances.get(0).setValue(attrBSSID130, wifiReadings.get("08:cc:68:b9:7d:0d"));
		partitionInstances.get(0).setValue(attrBSSID131, wifiReadings.get("08:cc:68:b9:7d:0e"));
		partitionInstances.get(0).setValue(attrBSSID132, wifiReadings.get("08:cc:68:b9:7d:0f"));

		partitionInstances.get(0).setValue(attrBSSID133, wifiReadings.get("08:cc:68:b9:8c:00"));
		partitionInstances.get(0).setValue(attrBSSID134, wifiReadings.get("08:cc:68:b9:8c:01"));
		partitionInstances.get(0).setValue(attrBSSID135, wifiReadings.get("08:cc:68:b9:8c:02"));
		partitionInstances.get(0).setValue(attrBSSID136, wifiReadings.get("08:cc:68:b9:8c:03"));
		partitionInstances.get(0).setValue(attrBSSID137, wifiReadings.get("08:cc:68:b9:8c:0c"));
		partitionInstances.get(0).setValue(attrBSSID138, wifiReadings.get("08:cc:68:b9:8c:0d"));
		partitionInstances.get(0).setValue(attrBSSID139, wifiReadings.get("08:cc:68:b9:8c:0e"));
		partitionInstances.get(0).setValue(attrBSSID140, wifiReadings.get("08:cc:68:b9:8c:0f"));

		partitionInstances.get(0).setValue(attrBSSID141, wifiReadings.get("08:cc:68:da:56:80"));
		partitionInstances.get(0).setValue(attrBSSID142, wifiReadings.get("08:cc:68:da:56:81"));
		partitionInstances.get(0).setValue(attrBSSID143, wifiReadings.get("08:cc:68:da:56:82"));
		partitionInstances.get(0).setValue(attrBSSID144, wifiReadings.get("08:cc:68:da:56:83"));
		partitionInstances.get(0).setValue(attrBSSID145, wifiReadings.get("08:cc:68:da:56:8c"));
		partitionInstances.get(0).setValue(attrBSSID146, wifiReadings.get("08:cc:68:da:56:8d"));
		partitionInstances.get(0).setValue(attrBSSID147, wifiReadings.get("08:cc:68:da:56:8e"));
		partitionInstances.get(0).setValue(attrBSSID148, wifiReadings.get("08:cc:68:da:56:8f"));

		partitionInstances.get(0).setValue(attrBSSID149, wifiReadings.get("20:3a:07:38:34:b0"));
		partitionInstances.get(0).setValue(attrBSSID150, wifiReadings.get("20:3a:07:38:34:b1"));
		partitionInstances.get(0).setValue(attrBSSID151, wifiReadings.get("20:3a:07:38:34:b2"));
		partitionInstances.get(0).setValue(attrBSSID152, wifiReadings.get("20:3a:07:38:34:b3"));
		partitionInstances.get(0).setValue(attrBSSID153, wifiReadings.get("20:3a:07:38:34:bc"));
		partitionInstances.get(0).setValue(attrBSSID154, wifiReadings.get("20:3a:07:38:34:bd"));
		partitionInstances.get(0).setValue(attrBSSID155, wifiReadings.get("20:3a:07:38:34:be"));
		partitionInstances.get(0).setValue(attrBSSID156, wifiReadings.get("20:3a:07:38:34:bf"));

		if (location != null) {
			partitionInstances.get(0).setValue(attrLatitude, location.getLatitude());
			partitionInstances.get(0).setValue(attrLongitude, location.getLongitude());
			partitionInstances.get(0).setValue(attrLocationAccuracy, location.getAccuracy());
		} else {
			partitionInstances.get(0).setMissing(attrLatitude);
			partitionInstances.get(0).setMissing(attrLongitude);
			partitionInstances.get(0).setMissing(attrLocationAccuracy);
		}
	}
}