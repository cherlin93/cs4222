package org.nus.cirlab.mapactivity;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.IndoorBuilding;
import com.google.android.gms.maps.model.IndoorLevel;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.nus.cirlab.mapactivity.DataStructure.Fingerprint;
import org.nus.cirlab.mapactivity.DataStructure.RadioMap;
import org.nus.cirlab.mapactivity.DataStructure.StepInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import static org.nus.cirlab.mapactivity.R.id.map;

public class MapsActivity extends AppCompatActivity implements OnMarkerDragListener, OnMapLongClickListener, OnMapReadyCallback {

    private GoogleMap mMap;
    private UiSettings mUiSettings;
    private final String mServerIP = "piloc.d1.comp.nus.edu.sg";//
    private String floorPlanID = "";
    private String floorLevel = "1";
    private RadioMap mRadioMap = null;

    // These are simply the string resource IDs for each of the style names. We use them
    // as identifiers when choosing which style to apply.
    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final String SELECTED_OPERATION = "selected_operation";
    // Stores the ID of the currently selected style, so that we can re-apply it when
    // the activity restores state, for example when the device changes orientation.
    private int mSelectedOperationId = R.string.operation_label_default;
    private ArrayList<DraggablePoint> mCircles = new ArrayList<>();
    private ArrayList<LatLng> mConfig = new ArrayList<>();
//    private CheckBox mWiFiScanCheckbox;
    private RadioMapCollectionService mPilocService = null;
    private TextView mAccuracyText ;


    private int mOperationIds[] = {
            R.string.operation_label_config,
            R.string.operation_label_cancel,
            R.string.operation_label_save,
            R.string.operation_label_upload,
            R.string.operation_label_upload_local_file,
            R.string.operation_label_load_local_file,
            R.string.operation_label_show,
            R.string.operation_label_localization,
            R.string.operation_label_default
    };
    int pointSize = 1;

    private static final LatLng nus_com1 = new LatLng(1.294867, 103.773938);
    private static final int MY_LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int LOCATION_LAYER_PERMISSION_REQUEST_CODE = 2;

    /**
     * Flag indicating whether a requested permission has been denied after returning in
     * {@link #onRequestPermissionsResult(int, String[], int[])}.
     */
    private boolean mLocationPermissionDenied = false;
    private boolean isUpload = true;
    private boolean isUploadRadioMap = true;
    private boolean isSave = false;
    //    private boolean isDownload = false;
    private boolean isShowRadioMap = false;
//    private boolean isWiFiScanOn =false;
    private static final int REQUEST_PLACE_PICKER_UPLOAD = 1;
    private static final int REQUEST_PLACE_PICKER_DOWNLOAD = 2;
    private static final int REQUEST_PLACE_PICKER_SAVE = 3;
    private static final int FILE_SELECT_CODE = 4;



    private ServiceConnection conn = new ServiceConnection() {
        public void onServiceDisconnected(ComponentName name) {
            mPilocService.onDestroy();
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            RadioMapCollectionService.MyBinder binder = (RadioMapCollectionService.MyBinder) service;
            mPilocService = binder.getService();
            // Start collecting only WiFi fingerprints

            mPilocService.startCollectingFingerprints();
            mPilocService.startCollection();
        }
    };

    private class DraggablePoint {
        private Circle circle;
//        private Marker centerMarker;

        public DraggablePoint(LatLng center, double radius, boolean clickable, int color) {

//            centerMarker = mMap.addMarker(new MarkerOptions()
//                    .position(center)
//                    .draggable(true)
//                    .title(center.toString()));
            circle = mMap.addCircle(new CircleOptions()
                    .center(center)
                    .radius(radius)
                    .strokeColor(color)
                    .fillColor(color)
                    .clickable(clickable));
        }

        public LatLng getCenterLatLng(){
            return circle.getCenter();
        }



    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(map);
        mapFragment.getMapAsync(this);
//        mWiFiScanCheckbox = (CheckBox)findViewById(R.id.data_collection);
        mAccuracyText = (TextView)findViewById(R.id.textView);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            // Create and bind the PiLoc service, make some change here
            Intent intent = new Intent(MapsActivity.this, RadioMapCollectionService.class);
            this.getApplicationContext().bindService(intent, conn, Context.BIND_AUTO_CREATE);
        }
    }
        /**
         * Shows a dialog listing the styles to choose from, and applies the selected
         * style when chosen.
         */
    private void showOperationDialog() {
        // mStyleIds stores each style's resource ID, and we extract the names here, rather
        // than using an XML array resource which AlertDialog.Builder.setItems() can also
        // accept. We do this since using an array resource would mean we would not have
        // constant values we can switch/case on, when choosing which style to apply.
        List<String> operationNames = new ArrayList<>();
        for (int operation : mOperationIds) {
            operationNames.add(getString(operation));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.operation_choose));
        builder.setItems(operationNames.toArray(new CharSequence[operationNames.size()]),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSelectedOperationId = mOperationIds[which];
                        String msg = getString(R.string.operation_set_to, getString(mSelectedOperationId));
//                        Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, msg);
                        runSelectedOperation();
                    }
                });
        builder.show();
    }

    /**
     * Creates a {@link MapStyleOptions} object via loadRawResourceStyle() (or via the
     * constructor with a JSON String), then sets it on the {@link GoogleMap} instance,
     * via the setMapStyle() method.
     */
    private void runSelectedOperation() {
        switch (mSelectedOperationId) {
            case R.string.operation_label_config:
                isUpload = true;
                isSave = false;
                isUploadRadioMap = false;
//                isDownloadRadioMap = false;
                new GetMapIDTask().execute(null, null, null);
//                UploadRadioMap();
                break;
            case R.string.operation_label_cancel:
                CancelMapping();
                break;
            case R.string.operation_label_save:
                isSave = true;
                isUpload = false;
                SaveSelectedTrace();
                break;
            case R.string.operation_label_upload:
                isUpload =true;
                isSave = false;
                isUploadRadioMap = true;
//                isDownloadRadioMap = false;
//                UploadRadioMap();
                new GetMapIDTask().execute(null, null, null);
                break;
            case R.string.operation_label_upload_local_file:
                isUpload =true;
                showFileChooser();
                break;

            case R.string.operation_label_load_local_file:
                Log.wtf("hande","load local trace");
                isUpload =false;
                showFileChooser();
                break;

            case R.string.operation_label_show:
                if (mIsLocating)
                    mIsLocating = false;
//                isDownloadRadioMap = true;
                isUpload = false;
                isSave = false;
                isShowRadioMap = true;
                new GetMapIDTask().execute(null, null, null);

                break;
            case R.string.operation_label_localization:
//                isDownloadRadioMap = true;
                isUpload = false;
                isSave = false;
                isShowRadioMap = false;
                if (mIsLocating) {
                    mIsLocating = false;
                    if(locationMarker!=null){
                        locationMarker.remove();
                    }
                }
                else {
                    mIsLocating = true;
                    new GetMapIDTask().execute(null, null, null);
//                    startLocalization();

                }
                break;
            case R.string.operation_label_default:
                this.finish();
                break;
            default:
                return;
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mUiSettings = mMap.getUiSettings();

        // Keep the UI Settings state in sync.
        mUiSettings.setZoomControlsEnabled(true);
        mUiSettings.setCompassEnabled(true);
        mUiSettings.setMyLocationButtonEnabled(true);
        mUiSettings.setScrollGesturesEnabled(true);
        mUiSettings.setZoomGesturesEnabled(true);
        mUiSettings.setTiltGesturesEnabled(false);
        mUiSettings.setRotateGesturesEnabled(true);
        mMap.setOnMarkerDragListener(this);
        mMap.setOnMapLongClickListener(this);
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mUiSettings.setMyLocationButtonEnabled(true);
        } else {
            requestLocationPermission(MY_LOCATION_PERMISSION_REQUEST_CODE);
        }
        Location location = mMap.getMyLocation();
        if (location != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),
                    location.getLongitude()), 19));
        }else
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nus_com1, 19));

        // Set up the click listener for the circle.
        googleMap.setOnCircleClickListener(new GoogleMap.OnCircleClickListener() {
            @Override
            public void onCircleClick(Circle circle) {
                // Flip the r, g and b components of the circle's stroke color.
                circle.setStrokeColor(Color.BLUE);
            }
        });

        // Create and bind the PiLoc service, make some change here
//        Intent intent = new Intent(MapsActivity.this, RadioMapCollectionService.class);
//        this.getApplicationContext().bindService(intent, conn, Context.BIND_AUTO_CREATE);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.operation_map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_operation_choose) {
            showOperationDialog();
        }
        return true;
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
        onMarkerMoved(marker);
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        onMarkerMoved(marker);
    }

    @Override
    public void onMarkerDrag(Marker marker) {
        onMarkerMoved(marker);
    }

    private void onMarkerMoved(Marker marker) {
        for (DraggablePoint draggablePoint : mCircles) {
//            if (draggablePoint.onMarkerMoved(marker)) {
//                break;
//            }
        }
    }

    private boolean isStartCollecting = false;
    private LatLng mStartLoc = null;
    private LatLng mEndLoc = null;
    private Boolean mIsRedoMapping = false;
    private Vector<StepInfo> mCurrentMappedSteps = null;


    @Override
    public void onMapLongClick(LatLng point) {
        DraggablePoint circle =
                new DraggablePoint(point, pointSize, true, Color.GREEN);
        mCircles.add(circle);
//        if(isWiFiScanOn){
            // No starting location yet, set current point as the starting location
            if (mStartLoc == null) {
                mPilocService.setStartCountingStep(true);
                isStartCollecting = true;
                mStartLoc = point;
            } else {
                // If it is not re-mapping, set previous ending point as the
                // starting location
                if (!mIsRedoMapping && mEndLoc != null)
                    mStartLoc = mEndLoc;

                // Set current point as the ending location
                mEndLoc = point;
            }


            if (mStartLoc != null && mEndLoc != null) {
                // If it is not re-mapping, confirm the previous mapping
                if (!mIsRedoMapping)
                    mPilocService.confirmCurrentMapping();
                else
                    mIsRedoMapping = false;

                // Get mapping for the newly collected annotated walking trajectory
                mCurrentMappedSteps = mPilocService.mapCurrentTrajectory(mStartLoc, mEndLoc);
                if (mCurrentMappedSteps != null) {
                    // Set newly mapped points to green color on the bitmap
                    for (StepInfo s : mCurrentMappedSteps) {
                        DraggablePoint tempDP = new DraggablePoint(new LatLng(s.mPosX,s.mPosY), pointSize, true, Color.GREEN);
                        mCircles.add(tempDP);
                    }
                }
            }
//        }
    }


    /**
     * Checks if the map is ready (which depends on whether the Google Play services APK is
     * available. This should be called prior to calling any methods on GoogleMap.
     */
    private boolean checkReady() {
        if (mMap == null) {
            Toast.makeText(this, R.string.map_not_ready, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * Requests the fine location permission. If a rationale with an additional explanation should
     * be shown to the user, displays a dialog that triggers the request.
     */
    public void requestLocationPermission(int requestCode) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Display a dialog with rationale.
            PermissionUtils.RationaleDialog
                    .newInstance(requestCode, false).show(
                    getSupportFragmentManager(), "dialog");
        } else {
            // Location permission has not been granted yet, request it.
            PermissionUtils.requestPermission(this, requestCode,
                    Manifest.permission.ACCESS_FINE_LOCATION, false);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == MY_LOCATION_PERMISSION_REQUEST_CODE) {
            // Enable the My Location button if the permission has been granted.
            if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                mUiSettings.setMyLocationButtonEnabled(true);
            } else {
                mLocationPermissionDenied = true;
            }

        } else if (requestCode == LOCATION_LAYER_PERMISSION_REQUEST_CODE) {
            // Enable the My Location layer if the permission has been granted.
            if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                try {
                    mMap.setMyLocationEnabled(true);
                } catch (SecurityException e) {
                    Toast.makeText(this, "Not enough permission to run this application!", Toast.LENGTH_SHORT).show();
                }
            } else {
                mLocationPermissionDenied = true;
            }
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mLocationPermissionDenied) {
            PermissionUtils.PermissionDeniedDialog
                    .newInstance(false).show(getSupportFragmentManager(), "dialog");
            mLocationPermissionDenied = false;
        }
    }


    String chosenFileName = "";
    private void showFileChooser() {

        SimpleFileDialog FileOpenDialog =  new SimpleFileDialog(MapsActivity.this,
                new SimpleFileDialog.SimpleFileDialogListener()
                {
                    @Override
                    public void onChosenDir(String chosenDir)
                    {
                        // The code in this function will be executed when the dialog OK button is pushed
                        chosenFileName = chosenDir;
                        String[] pathSegment = chosenFileName.split("/");
                        floorPlanID = pathSegment[pathSegment.length-3];
                        floorLevel = pathSegment[pathSegment.length-2];
                        fileName = pathSegment[pathSegment.length-1];

//                        Toast.makeText(MapsActivity.this, floorPlanID+" "+floorLevel+" "+fileName, Toast.LENGTH_LONG).show();
                        Log.d(TAG, "File Uri: " + chosenFileName);

                        if(isUpload)
                            new UploadTraceTask().execute(null, null, null);
                        else
                            new LoadTraceFromLocalTask().execute(null, null, null);


                    }
                });

        FileOpenDialog.default_file_name = "";
        FileOpenDialog.chooseFile_or_Dir();
    }


    List<String> locationNames = null;
    private void showLocationDialog( Vector<String> locationList) {
        // mStyleIds stores each style's resource ID, and we extract the names here, rather
        // than using an XML array resource which AlertDialog.Builder.setItems() can also
        // accept. We do this since using an array resource would mean we would not have
        // constant values we can switch/case on, when choosing which style to apply.
        locationNames = new ArrayList<>();
        for (String place : locationList) {
            locationNames.add(place);
        }
        locationNames.add(getString(R.string.location_pick));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.location_choose));
        builder.setItems(locationNames.toArray(new CharSequence[locationNames.size()]),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String msg = locationNames.get(which);
                        Log.d(TAG, msg);
                        if(isUpload)
                            UploadSelectedRadioMap(locationNames.get(which));
                        else
                            DownloadSelectedRadioMap(locationNames.get(which));
                    }
                });
        builder.show();
    }


    private void DownloadSelectedRadioMap(String locationString) {

        if (locationString.contains("Level")) {
            String[] ls = locationString.split(", ");
            floorPlanID = ls[0];
            floorLevel = ls[1].substring(6);

            Log.d(TAG, floorPlanID + " " + floorLevel);
            if (floorPlanID.length() > 0) {
                File filename = new File(this.getExternalCacheDir() + "/PiLoc/" + floorPlanID + "/" + floorLevel + "/radiomap.rm");
                if (filename.exists() && !isShowRadioMap)
                    new LoadRadioMapFromLocalTask().execute(null, null, null);
                else
                    new GetRadioMapTask().execute(null, null, null);
                while (mCircles.size() > 0) {
                    DraggablePoint freePoint = mCircles.remove(mCircles.size() - 1);
                    freePoint.circle.remove();
                }
            }

        } else {
            try {
                PlacePicker.IntentBuilder intentBuilder =
                        new PlacePicker.IntentBuilder();
                Intent intent = intentBuilder.build(this);
                // Start the intent by requesting a result,
                // identified by a request code.

                startActivityForResult(intent, REQUEST_PLACE_PICKER_DOWNLOAD);

            } catch (GooglePlayServicesRepairableException e) {
                e.printStackTrace();
            } catch (GooglePlayServicesNotAvailableException e) {
                e.printStackTrace();
            }
        }


    }

    private void SaveSelectedTrace() {
            try {
                PlacePicker.IntentBuilder intentBuilder =
                        new PlacePicker.IntentBuilder();
                Intent intent = intentBuilder.build(this);
                startActivityForResult(intent, REQUEST_PLACE_PICKER_SAVE);

            } catch (GooglePlayServicesRepairableException e) {
                e.printStackTrace();
            } catch (GooglePlayServicesNotAvailableException e) {
                e.printStackTrace();
            }
    }


    private void UploadSelectedRadioMap(String locationString) {

        if(locationString.contains("Level")){
            String[] ls =  locationString.split(", ");
            floorPlanID = ls[0];
            floorLevel = ls[1].substring(6);

            Log.d(TAG, floorPlanID+" "+floorLevel);
            if(floorPlanID.length()>0) {
                if(isUploadRadioMap){
                    new UploadRadioMapTask().execute(null, null, null);
                }else{
                    mConfig.clear();
                    for(DraggablePoint dp: mCircles){
                        mConfig.add(dp.getCenterLatLng());
                    }

                    new UploadRadioMapConfigTask().execute(null, null, null);
                }
                while (mCircles.size()>0) {
                    DraggablePoint freePoint = mCircles.remove(mCircles.size() - 1);
                    freePoint.circle.remove();
                }
            }

        }else{
            try {
                PlacePicker.IntentBuilder intentBuilder =
                        new PlacePicker.IntentBuilder();
                Intent intent = intentBuilder.build(this);
                // Start the intent by requesting a result,
                // identified by a request code.

                startActivityForResult(intent, REQUEST_PLACE_PICKER_UPLOAD);

            } catch (GooglePlayServicesRepairableException e) {
                e.printStackTrace();
            } catch (GooglePlayServicesNotAvailableException e) {
                e.printStackTrace();
            }
        }


    }

    private Boolean mIsRadioMapReady = false;
    private String fileName = null;
    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode, Intent data) {

        if (requestCode == REQUEST_PLACE_PICKER_UPLOAD
                && resultCode == Activity.RESULT_OK) {
            // The user has selected a place. Extract the name and address.
            final Place place = PlacePicker.getPlace(data, this);

            floorPlanID = place.getName().toString();

            IndoorBuilding building = mMap.getFocusedBuilding();
            if (building != null) {
                IndoorLevel level =
                        building.getLevels().get(building.getActiveLevelIndex());

                if (level != null) {
                    floorLevel = level.getShortName();
                } else {
                    floorLevel = "1";
                }
            }

            Log.d(TAG, floorPlanID+" "+floorLevel);
            if(floorPlanID.length()>0) {
                if(isUploadRadioMap){
                    new UploadRadioMapTask().execute(null, null, null);
                }else{
                    mConfig.clear();
                    for(DraggablePoint dp: mCircles){
                        mConfig.add(dp.getCenterLatLng());
                    }

                    new UploadRadioMapConfigTask().execute(null, null, null);
                }
                while (mCircles.size()>0) {
                    DraggablePoint freePoint = mCircles.remove(mCircles.size() - 1);
                    freePoint.circle.remove();
                }
            }
        }else if (requestCode == REQUEST_PLACE_PICKER_DOWNLOAD
                && resultCode == Activity.RESULT_OK) {

            // The user has selected a place. Extract the name and address.
            final Place place = PlacePicker.getPlace(data, this);

            floorPlanID = place.getName().toString();

            IndoorBuilding building = mMap.getFocusedBuilding();
            if (building != null) {
                IndoorLevel level =
                        building.getLevels().get(building.getActiveLevelIndex());

                if (level != null) {
                    floorLevel = level.getShortName();
                } else {
                    floorLevel = "1";
                }
            }

            Log.d(TAG, floorPlanID+" "+floorLevel);
            if(floorPlanID.length()>0) {
                File filename = new File(this.getExternalCacheDir()  + "/PiLoc/"+floorPlanID+"/"+floorLevel+"/radiomap.rm");
                if(filename.exists() && !isShowRadioMap)
                    new LoadRadioMapFromLocalTask().execute(null, null, null);
                else
                    new GetRadioMapTask().execute(null, null, null);
                while (mCircles.size()>0) {
                    DraggablePoint freePoint = mCircles.remove(mCircles.size() - 1);
                    freePoint.circle.remove();
                }
            }
        }else if (requestCode == REQUEST_PLACE_PICKER_SAVE
                && resultCode == Activity.RESULT_OK) {

            // The user has selected a place. Extract the name and address.
            final Place place = PlacePicker.getPlace(data, this);

            floorPlanID = place.getName().toString();

            IndoorBuilding building = mMap.getFocusedBuilding();
            if (building != null) {
                IndoorLevel level =
                        building.getLevels().get(building.getActiveLevelIndex());

                if (level != null) {
                    floorLevel = level.getShortName();
                } else {
                    floorLevel = "1";
                }
            }

            Log.d(TAG, floorPlanID+" "+floorLevel);
            if(floorPlanID.length()>0) {
                new SaveTraceTask().execute(null, null, null);
                while (mCircles.size()>0) {
                    DraggablePoint freePoint = mCircles.remove(mCircles.size() - 1);
                    freePoint.circle.remove();
                }
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    private class UploadRadioMapTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... f) {
            try {
                if(mPilocService!=null){
                    // Append the newly mapped fingerprints to current radiomap
                    mPilocService.appendRadioMapFromMapping();
                    // Upload the current radiomap to the server
                    mPilocService.uploadRadioMap(mServerIP, floorPlanID,  floorLevel);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) { }
    }

    private  boolean isUploadSuccess = false;
    private class UploadTraceTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... f) {
            try {
                if(mPilocService!=null) {
                    isUploadSuccess = mPilocService.uploadTraceFile(mServerIP, floorPlanID, floorLevel, fileName);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            if (isUploadSuccess) {
                Toast.makeText(getBaseContext(), "Upload successfully", Toast.LENGTH_SHORT).show();
            } else
                Toast.makeText(getBaseContext(), "Upload failed", Toast.LENGTH_SHORT).show();
        }
    }

    private class UploadRadioMapConfigTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... f) {
            try {
                    // Upload the current radio map to the server
                mPilocService.uploadRadioMapConfig(mServerIP, floorPlanID,  floorLevel, mConfig);

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) { }
    }

    private class GetRadioMapTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... s) {
            try {
                // Get radio map using the floor ID from server
                mRadioMap = mPilocService.getRadioMap(mServerIP, floorPlanID, floorLevel);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            if (mRadioMap != null) {
                Toast.makeText(getBaseContext(), "Get radioMap successfully", Toast.LENGTH_SHORT).show();
                if(isShowRadioMap)
                    ShowRadioMap();
                else if (mIsLocating)
                    startLocalization();
            } else
                Toast.makeText(getBaseContext(), "Get radioMap failed", Toast.LENGTH_SHORT).show();

        }
    }


    private ArrayList<LatLng> NodeList = new ArrayList<>();
    private class LoadTraceFromLocalTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... s) {
            try {
                // Get radio map using the floor ID from server
                NodeList = mPilocService.loadTrace(floorPlanID, floorLevel, fileName);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            if (NodeList!=null && NodeList.size()>0) {
                ShowTrace();
            } else
                Toast.makeText(getBaseContext(), "Load trace failed", Toast.LENGTH_SHORT).show();

        }
    }

    private class LoadRadioMapFromLocalTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... s) {
            try {
                // Get radio map using the floor ID from server
                mRadioMap = mPilocService.loadRadioMap(floorPlanID, floorLevel);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            if (mRadioMap != null) {
                if (mIsLocating)
                    startLocalization();
                Toast.makeText(getBaseContext(), "Load radioMap successfully", Toast.LENGTH_SHORT).show();
            } else
                Toast.makeText(getBaseContext(), "Load radioMap failed", Toast.LENGTH_SHORT).show();

        }
    }

    private class SaveTraceTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... s) {
            try {
                // Get radio map using the floor ID from server
                mPilocService.saveNewlyCollectedTrace(floorPlanID, floorLevel);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private Vector<String> locationList= null;
    private class GetMapIDTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... s) {
            try {
                locationList = mPilocService.getCurrentFloorIDList(mServerIP, mPilocService.getFingerprint());

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            if (locationList != null) {
//                Toast.makeText(getBaseContext(), "Get map id successfully", Toast.LENGTH_SHORT).show();
                showLocationDialog(locationList);
            } else
                Toast.makeText(getBaseContext(), "Get map id failed", Toast.LENGTH_SHORT).show();

        }
    }

    private Boolean mIsLocating = false;
    private LatLng mCurrentLocation ;
    private Marker locationMarker = null;

    public void startLocalization() {
        Log.d(TAG, "start localization");
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (mIsLocating) {
                        // Get current fingerprints
                        Vector<Fingerprint> fp = mPilocService.getFingerprint();
                        if(fp!=null && fp.size()>0){
                            //Log.d(TAG, "finger print size: "+fp.size()); DISABLED TEMPORARILY FOR TASK 2 ANALYSIS
                            // TASK 1:
                            //mCurrentLocation = getLocation(mRadioMap, fp);
                            // TASK 2:
                            mCurrentLocation = getLocation(mRadioMap);

                            if (mCurrentLocation == null) {
                                Thread.sleep(500);
                                continue;
                            } else {
                                Log.d(TAG, mCurrentLocation.toString());

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if(locationMarker!=null){
                                            locationMarker.remove();
                                        }
                                        locationMarker = mMap.addMarker(new MarkerOptions()
                                                .position(mCurrentLocation)
                                                .title("Your Indoor Location"));
                                    }
                                });

                                Thread.sleep(3000);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // TASK TWO:    For this task, we have come up with several algorithms to calculate the best possible "candidate"
    //              for the user based on the the radiomap
    //              The conditions that the algorithms use to decide the best candidate are as follows:
    //              Algorithm 1: Highest number of MAC matches and lowest summation of RSSI differences
    //              Algorithm 2: Ranked list of candidates based on MAC matches and difference in RSSI values
    public LatLng getLocation(RadioMap mRadioMap) {

        // HARDCODED FINGERPRINTS FOR TASK 2
        Vector<Fingerprint> fp = new Vector<Fingerprint>();
        fp.add(new Fingerprint("84:b8:02:00:3b:bb", 83, 50));
        fp.add(new Fingerprint("88:f0:31:8d:21:cf", 82, 50));
        fp.add(new Fingerprint("84:b8:02:00:3b:bf", 80, 50));
        fp.add(new Fingerprint("88:f0:31:8d:21:cb", 85, 50));
        fp.add(new Fingerprint("a8:9d:21:74:0c:09", 75, 50));
        fp.add(new Fingerprint("74:a2:e6:ec:55:c5", 71, 50));
        fp.add(new Fingerprint("74:a2:e6:ec:55:c9", 64, 50));
        fp.add(new Fingerprint("a8:9d:21:74:0d:9f", 69, 50));
        fp.add(new Fingerprint("a8:9d:21:44:05:aa", 85, 50));
        fp.add(new Fingerprint("a8:9d:21:0f:7e:89", 58, 50));
        fp.add(new Fingerprint("a8:9d:21:0f:7e:87", 51, 50));
        fp.add(new Fingerprint("a8:9d:21:74:0d:99", 66, 50));
        fp.add(new Fingerprint("a8:9d:21:0f:7e:8f", 45, 50));

        /**
        // ALGORITHM ONE: HIGHEST MAC MATCHES AND LOWEST ABSOLUTE RSSI DIFFERENCE

        // ALGORITHM STARTS HERE
        LatLng Key = null;
        double current_sum = 0;
        double best_sum = Double.MAX_VALUE;
        int current_number_matches = 0;
        int best_number_matches = 0;
        Vector<Fingerprint> best_candidate = new Vector<Fingerprint>();
        Vector<Fingerprint> current_candidate = new Vector<Fingerprint>();

        //  Looping through the various points on the radiomap
        for (LatLng k : mRadioMap.mLocFingerPrints.keySet()) {
            //  Resetting current variables
            current_sum = 0;
            current_number_matches= 0;
            current_candidate.clear();
            //  Looping through comparisons of fingerprint pairs
            for (Fingerprint f1 : fp) {
                for(Fingerprint f2 :mRadioMap.mLocFingerPrints.get(k)){
                    if(f1.mMac.equalsIgnoreCase(f2.mMac)){
                        current_number_matches++;
                        current_sum += Math.abs(f1.mRSSI-f2.mRSSI);
                        current_candidate.add(f2);
                        break;
                    }
                }
            }
            if (current_number_matches >= best_number_matches && current_sum <= best_sum) {
                best_sum = current_sum;
                best_candidate.clear();
                for(Fingerprint f: current_candidate) {
                    best_candidate.add(f);
                }
                best_number_matches = current_number_matches;
                Key = new LatLng(k.latitude, k.longitude);
            }
        }

        Log.d(TAG, "Best match has: " + best_candidate.size() + " fingerprints");
        for(Fingerprint f: best_candidate) {
            Log.d(TAG, "MAC Address: " + f.mMac + " ; RSSI: " + f.mRSSI);
        }
        Log.d(TAG, "Best sum is : " + best_sum);
        Log.d(TAG, "---------------------------------------------");
         **/


        //  ALGORITHM TWO: ARBITUARY SCORING SYSTEM BASED ON RSSI DIFFERENCES
        //  Description of Algorithm:
        //  This algorithm is based on a scoring system based on the RSSI differences between the candidates fingerprints and the hardcoded fingerprints.
        //  The idea is that we will give a higher priority/weight by assigning arbituary scores to candidates depending on how close the RSSI values of the MAC addresses that match are to those of the hardcoded MAC addresses RSSI values.
        //  Therefore, the more similar the RSSI values of the candidate's matched MAC addresses are to the hardcoded ones, the higher the points awarded to it.
        //  At the end of the algorithm, we will choose the candidate with the highest score based on our scoring system to be the location that the localization will return.
        LatLng Key = null;
        Vector<Pair<LatLng, Integer>> ranking = new Vector<Pair<LatLng, Integer>>();
        // SCORING SYSTEM
        // Note to team: Feel free to edit and refine the system! :)
        int SCORE_WITHIN_FIVE = 10;
        int SCORE_WITHIN_TEN = 6;
        int SCORE_WITHIN_TWENTY = 3;
        int SCORE_WITHIN_THIRTY = 1;
        //int SCORE_WITHIN_FORTY = 2;
        //int SCORE_WITHIN_FIFTY = 1;
        int best_score = 0;
        int current_score = 0;
        int difference_in_RSSI = 0;
        for (LatLng k : mRadioMap.mLocFingerPrints.keySet()) {
            current_score = 0;
            for (Fingerprint f1 : fp) {
                for (Fingerprint f2 : mRadioMap.mLocFingerPrints.get(k)) {
                    if (f1.mMac.equalsIgnoreCase(f2.mMac)) {
                        difference_in_RSSI = Math.abs(f1.mRSSI - f2.mRSSI);
                        // Giving the candidate a score based on the current matched MAC address and the difference between the RSSI values
                        if(difference_in_RSSI <= 5) { current_score += SCORE_WITHIN_FIVE; }
                        else if(difference_in_RSSI > 5 && difference_in_RSSI <= 10) { current_score += SCORE_WITHIN_TEN; }
                        else if(difference_in_RSSI > 10 && difference_in_RSSI <= 20) { current_score += SCORE_WITHIN_TWENTY; }
                        else if(difference_in_RSSI > 20 && difference_in_RSSI <= 30) { current_score += SCORE_WITHIN_THIRTY; }
                        //else if(difference_in_RSSI > 30 && difference_in_RSSI <= 40) { current_score += SCORE_WITHIN_FORTY; }
                        //else if(difference_in_RSSI > 40 && difference_in_RSSI <= 50) { current_score += SCORE_WITHIN_FIFTY; }
                        break;
                    }
                }
            }
            ranking.add(new Pair<LatLng, Integer>(k,current_score));
        }
        //Extraction of best candidate
        for(Pair<LatLng, Integer> candidate: ranking) {
            current_score = candidate.second;
            if(current_score > best_score) {
                best_score = current_score;
                Key = candidate.first;
            }
        }
        // Analysis purposes:
        Log.d(TAG, "BEST CANDIDATE has: " + mRadioMap.mLocFingerPrints.get(Key).size() + " fingerprints");
        Log.d(TAG, "SCORE = " + best_score);
        Log.d(TAG, "LIST OF APS -");
        for(Fingerprint f: mRadioMap.mLocFingerPrints.get(Key)) {
            Log.d(TAG, "MAC Address: " + f.mMac + " ; RSSI:" + f.mRSSI);
        }
        Log.d(TAG, "END OF LIST OF APS");
        int count_matches = 0;
        Log.d(TAG, "LIST OF MATCHED DIFFERENCES: ");
        for(Fingerprint f2: fp) {
            for(Fingerprint f: mRadioMap.mLocFingerPrints.get(Key)) {
                if (f.mMac.equalsIgnoreCase(f2.mMac)) {
                    count_matches++;
                    Log.d(TAG, "Matched MAC: " + f.mMac + " ; Difference: " + Math.abs(f.mRSSI - f2.mRSSI));
                    break;
                }
            }
        }
        Log.d(TAG, "Total matches: " + count_matches);
        return Key;
    }

    //Original
    public LatLng getLocation(RadioMap mRadioMap, Vector<Fingerprint> fp) {
        // try your own localization algorithm here
        LatLng Key = null;
        double sum;
        double minScore = Double.MAX_VALUE;

        for (LatLng k : mRadioMap.mLocFingerPrints.keySet()) {
            int number = 0;
            sum = 0;
            for (Fingerprint f1 : fp) {
                for(Fingerprint f2 :mRadioMap.mLocFingerPrints.get(k)){
                    if(f1.mMac.equalsIgnoreCase(f2.mMac)){
                        number++;
                        sum += f1.mRSSI-f2.mRSSI;
                        break;
                    }
                }
            }

            if ( number > fp.size() / 3 && sum < minScore) {
                minScore = sum;
                Key = new LatLng(k.latitude, k.longitude);
            }
        }
        return Key;
    }

    public void ShowRadioMap() {
        Log.d(TAG, "show radio map on UI");
        while (mCircles.size()>0) {
            DraggablePoint freePoint = mCircles.remove(mCircles.size() - 1);
            freePoint.circle.remove();
        }

        if(locationMarker!=null){
            locationMarker.remove();
        }

        if (mRadioMap != null) {
            Log.d(TAG, mRadioMap.mLocFingerPrints.size()+" ");
            // Set newly mapped points to green color on the bitmap
            for (LatLng latLng : mRadioMap.mLocFingerPrints.keySet()) {
                DraggablePoint tempDP = new DraggablePoint(latLng, pointSize, true, Color.GRAY);
                mCircles.add(tempDP);
            }
//            mAccuracyText.setText("Error: "+mPilocService.getLocalizationError()+" m");
        }
    }

    public void ShowTrace() {
        Log.d(TAG, "show trace on UI");
        while (mCircles.size()>0) {
            DraggablePoint freePoint = mCircles.remove(mCircles.size() - 1);
            freePoint.circle.remove();
        }

        if(locationMarker!=null){
            locationMarker.remove();
        }

        if (NodeList != null) {
            Log.d(TAG, NodeList.size()+" ");
            // Set newly mapped points to green color on the bitmap
            for (LatLng latLng :NodeList) {
                DraggablePoint tempDP = new DraggablePoint(latLng, pointSize, true, Color.GREEN);
                mCircles.add(tempDP);
            }
        }
    }

    public void CancelMapping( ) {
        // If it is already in the re-mapping state, remove the current mapping
        if (mIsRedoMapping) {
            mPilocService.removeCurrentMapping();
            mIsRedoMapping = false;
            Toast.makeText(getBaseContext(), "Previous mapping removed", Toast.LENGTH_SHORT).show();
        } else {
            // No mapped steps, return immediately
            if (mCurrentMappedSteps == null)
                return;

            // Set the remap flag
            mIsRedoMapping = true;

            for (int i=0;i<= mCurrentMappedSteps.size();i++) {
                DraggablePoint freePoint = mCircles.remove(mCircles.size()-1);
//                freePoint.centerMarker.remove();
                freePoint.circle.remove();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPilocService != null) {
            // Stop collecting annotated walking trajectories
            mPilocService.stopCollection();
        }

        // Unbind the service
        while (mCircles.size()>0) {
            DraggablePoint freePoint = mCircles.remove(mCircles.size() - 1);
            freePoint.circle.remove();
        }
        mCircles.clear();
        if(conn != null)
            getApplicationContext().unbindService(conn);
        isStartCollecting = false;
    }


}
