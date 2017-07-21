package org.gpsalarm;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.model.LatLng;

import java.text.Format;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

public class StartActivity extends AppCompatActivity {

    final String TAG = "StartActivity";
    private boolean checkedWiFi = false;

    private PendingIntent pendingIntent;
    private boolean currentlyTracking;
    private int intervalSec = 61;
    private AlarmManager alarmManager;

    LocationData selectedLocationData;
    ArrayList<LocationData> locationDataList = new ArrayList<>();
    InternalStorage internalStorage;

    LocationManager locationManager;
    WifiManager wifiManager;

    private TextView tCurrentLocation;
    private Button trackingButton;

    public String addressName;
    public LatLng addressGeo;
    private double currentLat;
    private double currentLng;
    private double destinationLat;
    private double destinationLng;
    private Intent trackGPS;
    private boolean destPassed;



    // This class is used to provide alphabetic sorting for LocationData list
    class CustomAdapter extends ArrayAdapter<LocationData> {
        public CustomAdapter(Context context, ArrayList<LocationData> locationDataArrayList) {
            super(context, R.layout.row_layout, locationDataArrayList);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater myInflater = LayoutInflater.from(getContext());
            // Last two arguments are significant if we inflate this into a parent.
            View theView = myInflater.inflate(R.layout.row_layout, parent, false);
            String cline = getItem(position).getName();
            TextView myTextView = (TextView) theView.findViewById(R.id.customTextView);
            myTextView.setText(cline);
            return theView;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate(StartActivity) started");
        super.onCreate(savedInstanceState);
        internalStorage = new InternalStorage();
        internalStorage.setContext(this);

        if(!googleServicesAvailable()){
            System.exit(22);
        }
        checkGPS();
        checkAndConnect();

        locationDataList = internalStorage.readLocationDataList();

        Log.i(TAG, "onCreate, locationDataList" + locationDataList);

        setContentView(R.layout.activity_start);
        tCurrentLocation = (TextView)findViewById(R.id.textView3);
        trackingButton = (Button)findViewById(R.id.tracker);

        final ArrayAdapter myAdapter = new CustomAdapter(this, locationDataList);
        ListView listView = (ListView) findViewById(R.id.listView);

            Collections.sort(locationDataList, new Comparator<LocationData>() {

                /* This comparator sorts LocationData objects alphabetically. */
                @Override
                public int compare(LocationData a1, LocationData a2) {
                    // String implements Comparable
                    return (a1.getName()).compareTo(a2.getName());
                }
            });

            listView.setAdapter(myAdapter);

            final ListView myListView2 = listView;
            myListView2.setOnItemClickListener(new AdapterView.OnItemClickListener() {        // Dobavlenij kod 21.02.2017
                @Override //NOTE: open map, which will show saved point with drawn radius
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    selectedLocationData = (LocationData) myAdapter.getItem(i);
                    Toast.makeText(StartActivity.this, "Alarm '" + selectedLocationData.getName() + "' is set", Toast.LENGTH_LONG).show();
                    Intent myIntent = new Intent(StartActivity.this, MapActivity.class);
                    myIntent.putExtra(InternalStorage.SEL_LOC_DATA_KEY, selectedLocationData);
                    Log.i("StartActivity", selectedLocationData.getName() + " is selected");
                    startActivity(myIntent);
                }
            });

            myListView2.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override //NOTE: delete saved point, if selection is long-pressed
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    AlertDialog.Builder alert = new AlertDialog.Builder(StartActivity.this); //NOTE: build confirmation AlertDialog
                    alert.setMessage("Are you sure you want to delete this?");
                    alert.setCancelable(false);
                    alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            locationDataList.remove(i);
                            myAdapter.notifyDataSetChanged();
                            internalStorage.writeLocationDataList(locationDataList);
                        }
                    });
                    alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
                    alert.show();
                    return true;
                }
            });


        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_fragment);
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                addressGeo = place.getLatLng();
                addressName = place.getName().toString();
                Log.i("V", "longitude: " + place.getLatLng().longitude);

                if (addressName != null) {
                    double destLat = addressGeo.latitude;
                    double destLng = addressGeo.longitude;
                    setDestinationLat(destLat);
                    setDestinationLng(destLng);
                    destPassed = true;
                    currentlyTracking = true;
                    triggerAlarmManager();
                    setButtonState();
                    Intent serviceIntent = new Intent(StartActivity.this, CoordService.class);
                    serviceIntent.putExtra("DestLat", destLat);
                    serviceIntent.putExtra("DestLng", destLng);
                    startService(serviceIntent);
                } else {
                    Toast.makeText(getApplicationContext(), "No such location found. \nTry a different keyword.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(Status status) {

            }
        });
        trackingButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view){
                trackGPS(view);
            }
        });
        registerReceiver(updateLocation, new IntentFilter("LOCATION_UPDATE"));
    }

    private BroadcastReceiver updateLocation = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            tCurrentLocation.setText("Current location: Lat: "+intent.getExtras().getDouble("latitude")+
                    " Lng: "+intent.getExtras().getDouble("longitude"));
        }
    };


    protected void trackGPS(View view) {
        if (!googleServicesAvailable()) {
            return;
        }
        if (destPassed) {
            if (currentlyTracking) {
                stopAlarmManager();
                currentlyTracking = false;
            } else {
                triggerAlarmManager();
                currentlyTracking = true;
            }
            setButtonState();
        } else Toast.makeText(this, "No destination set", Toast.LENGTH_LONG).show();
    }

    private void setButtonState(){
        if(!currentlyTracking){
            trackingButton.setText("Tracking STOPPED");
        } else{
            trackingButton.setText("Tracking STARTED");
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        Log.i(TAG, "onResume(StartActivity) called");
        setButtonState();
    }

    @Override
    protected void onPause(){
        super.onPause();
        Log.i(TAG, "onPause(StartActivity) called");
    }

    @Override
    protected void onStop(){
        super.onStop();
        Log.i(TAG, "onStop(StartActivity) called");

    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(updateLocation);
        stopAlarmManager();
        super.onDestroy();
        Log.i(TAG, "onDestroy(StartActivity) called");
    }

    public boolean googleServicesAvailable() {
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        int isAvailable = api.isGooglePlayServicesAvailable(this);      // Can return 3 different values

        if (isAvailable == ConnectionResult.SUCCESS) {
            return true;
        } else if (api.isUserResolvableError(isAvailable)) {
            Dialog dialog = api.getErrorDialog(this, isAvailable, 0);
            dialog.show();
        } else if (api.isUserResolvableError(isAvailable)) {
            Dialog dialog = api.getErrorDialog(this, isAvailable, 0);
            dialog.show();
        } else {
            Toast.makeText(this, "Can't connect to play services! Exiting...", Toast.LENGTH_LONG).show();
        }

        return false;
    }

    public void checkGPS() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            buildAlertMessageNoGps();
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?\n" + "\"If no, program will be closed.\"")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                        System.exit(0);
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    public void enableWiFi() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);
        startActivity(new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK));
        Toast.makeText(getApplicationContext(), "Wi-fi connecting..", Toast.LENGTH_LONG).show();
    }

    private void buildAlertMessageNoWifi() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your Wi-Fi seems to be disabled, do you want to enable it?\n" + "\"If wi-fi not available, please connect via mobile data\"")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        enableWiFi();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    public void checkAndConnect() {
        if (!checkedWiFi) {
            ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
            // test for connection
            if (cm != null) {
                if (!(cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isAvailable() && cm.getActiveNetworkInfo().isConnected())) {
                    buildAlertMessageNoWifi();
                }
            }
            checkedWiFi = true;
        }
    }

    private void triggerAlarmManager(){
        Log.d("StartActivity", "triggerAlarmManager");

        Context context = getBaseContext();
        alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        trackGPS = new Intent(context, AlarmReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(context, 0, trackGPS, 0);

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, SystemClock.elapsedRealtime(), intervalSec * 1000, pendingIntent);
    }

    private void stopAlarmManager(){
        Log.d("StartActivity", "stopAlarmManager");
        Context context = getBaseContext();
        Intent trackGPS = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, trackGPS, 0);
        AlarmManager alarmManager = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
    }

    double haversine(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }

    public void setCurrentLat(double latit){
        this.currentLat=latit;
    }
    public void setCurrentLng(double longi){
        this.currentLng=longi;
    }
    public double getCurrentLat(){
        return currentLat;
    }
    public double getCurrentLng(){
        return currentLng;
    }
    public void setDestinationLat(double latit){
        this.destinationLat=latit;
    }
    public void setDestinationLng(double longi){
        this.destinationLng=longi;
    }
    public double getDestinationLat(){
        return destinationLat;
    }
    public double getDestinationLng(){
        return destinationLng;
    }

}
