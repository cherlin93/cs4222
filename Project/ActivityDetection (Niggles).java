package nus.cs4222.activitysim;

import java.io.*;
import java.util.*;
import java.text.*;

import android.hardware.*;
import android.util.*;

import nus.cs4222.activitysim.DataStructure.Fingerprint;
import nus.cs4222.activitysim.DataStructure.RadioMap;

/**
   Class containing the activity detection algorithm.

   <p> You can code your activity detection algorithm in this class.
    (You may add more Java class files or add libraries in the 'libs' 
     folder if you need).
    The different callbacks are invoked as per the sensor log files, 
    in the increasing order of timestamps. In the best case, you will
    simply need to copy paste this class file (and any supporting class
    files and libraries) to the Android app without modification
    (in stage 2 of the project).

   <p> Remember that your detection algorithm executes as the sensor data arrives
    one by one. Once you have detected the user's current activity, output
    it using the {@link ActivitySimulator.outputDetectedActivity(UserActivities)}
    method. If the detected activity changes later on, then you need to output the
    newly detected activity using the same method, and so on.
    The detected activities are logged to the file "DetectedActivities.txt",
    in the same folder as your sensor logs.

   <p> To get the current simulator time, use the method
    {@link ActivitySimulator.currentTimeMillis()}. You can set timers using
    the {@link SimulatorTimer} class if you require. You can log to the 
    console/DDMS using either {@code System.out.println()} or using the
    {@link android.util.Log} class. You can use the {@code SensorManager.getRotationMatrix()}
    method (and any other helpful methods) as you would normally do on Android.

   <p> Note: Since this is a simulator, DO NOT create threads, DO NOT sleep(),
    or do anything that can cause the simulator to stall/pause. You 
    can however use timers if you require, see the documentation of the 
    {@link SimulatorTimer} class. 
    In the simulator, the timers are faked. When you copy the code into an
    actual Android app, the timers are real, but the code of this class
    does not need not be modified.
 */
public class ActivityDetection {

    private ArrayList<Double> accelerations; //Arraylist to store 31 most recent accelerations
    private ArrayList<Double> accelerations_lmeans; //Arraylist to store 31 most recent local means
    private ArrayList<Double> acceleration_buffer;
    private ArrayList<Double> standard_devs; // Arraylist to store window of 16 standard devs
    private ArrayList<String> buffer_provider;
    private ArrayList<Double> speed_buffer;
    private ArrayList<Double> altitude_buffer;
    private double swing_sd = 0.0; // Variable to contain highest SD which signifies end of swing
    private int current_window_index = 0;
    private int current_speed = 0;
    private boolean step_candidate;
    private double plongitude = 0;
    private double platitude = 0;
    private double ptimestamp = 0;
    private final double THRESHOLD_SWING = 3.0;
    private final double THRESHOLD_STANCE = 1.0;
    private String previous_provider = "network";
    private boolean is_walking = false;
    private boolean is_vehicle = false;
    private boolean is_outside = false;
    private boolean is_underground = false;
    private boolean is_com1 = false;

    /** Initialises the detection algorithm. */
    public void initDetection() 
        throws Exception {

        // Add initialisation code here, if any. If you use static variables in this class (avoid
        //  doing this, unless they are constants), please do remember to initialise them HERE. 
        //  Remember that the simulator will be run on multiple traces, and your algorithm's initialisation
        //  should be done here before each trace is simulated.
        accelerations = new ArrayList<Double>();
        buffer_provider = new ArrayList<String>();
        acceleration_buffer = new ArrayList<Double>();
        speed_buffer = new ArrayList<Double>();
        altitude_buffer = new ArrayList<Double>();
        // Assume the user is IDLE_INDOOR, then change state based on your algorithm
        ActivitySimulator.outputDetectedActivity( UserActivities.IDLE_INDOOR );

        // If you are using the Piloc API, then you must load a radio map (in this case, Hande
        //  has provided the radio map data for the pathways marked in the map image in IVLE
        //  workbin, which represents IDLE_COM1 state). You can use your own radio map data, or
        //  code your own localization algorithm in PilocApi. Please see the "onWiFiSensorChanged()"
        //  method.
        pilocApi = new PilocApi();
        if( pilocApi.loadRadioMap( new File( "radiomap.rm" ) ) == null ) {
            throw new IOException( "Unable to open radio map file, did you specify the correct path in ActivityDetection.java?" );
        }
    }

    /** De-initialises the detection algorithm. */
    public void deinitDetection() 
        throws Exception {
        // Add de-initialisation code here, if any
    }

    /** 
       Called when the accelerometer sensor has changed.

       @param   timestamp    Timestamp of this sensor event
       @param   x            Accl x value (m/sec^2)
       @param   y            Accl y value (m/sec^2)
       @param   z            Accl z value (m/sec^2)
       @param   accuracy     Accuracy of the sensor data (you can ignore this)
     */
    public void onAcclSensorChanged( long timestamp , 
                                     float x , 
                                     float y , 
                                     float z , 
                                     int accuracy ) {

        // Process the sensor data as they arrive in each callback, 
        //  with all the processing in the callback itself (don't create threads).
    }

    /** 
       Called when the gravity sensor has changed.

       @param   timestamp    Timestamp of this sensor event
       @param   x            Gravity x value (m/sec^2)
       @param   y            Gravity y value (m/sec^2)
       @param   z            Gravity z value (m/sec^2)
       @param   accuracy     Accuracy of the sensor data (you can ignore this)
     */
    public void onGravitySensorChanged( long timestamp , 
                                        float x , 
                                        float y , 
                                        float z , 
                                        int accuracy ) {
    }

    /** 
       Called when the linear accelerometer sensor has changed.

       @param   timestamp    Timestamp of this sensor event
       @param   x            Linear Accl x value (m/sec^2)
       @param   y            Linear Accl y value (m/sec^2)
       @param   z            Linear Accl z value (m/sec^2)
       @param   accuracy     Accuracy of the sensor data (you can ignore this)
     */
    public void onLinearAcclSensorChanged( long timestamp , 
                                           float x , 
                                           float y , 
                                           float z , 
                                           int accuracy ) {
      //  Calculate current magnitude of acceleration a
      double acceleration = Math.sqrt(x*x + y*y + z*z);
      //  Add current acceleration ai to end of arraylist of accelerations
      accelerations.add(acceleration);

      //  If accelerations length is == 31, calculate variance for ai where i = 16
      if(accelerations.size() == 31) {
        if(is_walking) {
          acceleration_buffer.add(acceleration);
          if(acceleration_buffer.size() > 20) {
            double average = 0.0;
            for(int count = 0; count < acceleration_buffer.size(); count++) {
              average += acceleration_buffer.get(count);
            }
            average = average / 21;
            if(average < 0.08) {
              System.out.println("Average: " + average + " set to false");
              is_walking = false;
            }
            acceleration_buffer.remove(0);
          }
        }
        //  Step 1: Calculate local mean acceleration
        double acceleration_lmean = 0.0;
        for(int i = 0; i < accelerations.size(); i++) {
          acceleration_lmean += accelerations.get(i);
        }
        acceleration_lmean = acceleration_lmean / (31); // 2 * window size + 1 
        //  Step 2: Getting variance
        double variance = 0.0;
        for(int j = 0; j < accelerations.size(); j++) {
          variance = Math.pow((accelerations.get(j) - acceleration_lmean),2);
        }
        variance = variance / (31); // 2 * window size + 1
        //  Getting standard deviation
        double standard_dev = Math.sqrt(variance);
        //  First we locate the swing by looking for the maximum SD thus far  
        if(!step_candidate) {
          //  Add the first standard dev which exceeds the THRESHOLD_SWING
          if(swing_sd == 0.0 && standard_dev > THRESHOLD_SWING) {
            swing_sd = standard_dev;
            System.out.println("Swing sd = " + swing_sd);
          } else if(swing_sd != 0.0 && standard_dev > swing_sd) { // If current standard dev is > current max, replace it
            System.out.println("Replacing swing sd: " + swing_sd + " with:" + standard_dev);
            swing_sd = standard_dev;
          } else if(swing_sd != 0.0 && standard_dev < swing_sd) {
            System.out.println("Found step candidate");
            step_candidate = true;
          }
        }
        else { // After finding swing, we look for stance
          if(current_window_index == 15) {
            System.out.println("Exceeded window for stance, resetting step candidate");
            step_candidate = false;
            swing_sd = 0.0;
            current_window_index = 0;
          } else {
            if(standard_dev < THRESHOLD_STANCE) {
              System.out.println("Possible stance detected with SD: " + standard_dev);
              System.out.println("Found stance within window, walking detected");
              System.out.println("\n\nTIME STAMP: " + timestamp + "\n\n");
              is_walking = true;
              swing_sd = 0.0;
              current_window_index = 0;
              step_candidate = false;
              is_walking = true;
            } else {
              System.out.println("Increasing window index");
              current_window_index++;
            }
          }
        }

        // Remove earliest acceleration
        accelerations.remove(0);
      }
      
      if(is_walking && !is_vehicle) {
          ActivitySimulator.outputDetectedActivity( UserActivities.WALKING);
      }
      else {
        if(is_com1) {
          ActivitySimulator.outputDetectedActivity( UserActivities.IDLE_COM1);
        }
        else if(is_vehicle) {
          ActivitySimulator.outputDetectedActivity( UserActivities.BUS);
        }
        else {
          if(is_outside) {
            ActivitySimulator.outputDetectedActivity( UserActivities.IDLE_OUTDOOR);
          } else {
            ActivitySimulator.outputDetectedActivity( UserActivities.IDLE_INDOOR);  
          }
          
        }
      }
      /*
      if(is_vehicle) {
          ActivitySimulator.outputDetectedActivity( UserActivities.BUS);
      } else {
        if(is_walking) {
          ActivitySimulator.outputDetectedActivity( UserActivities.WALKING);
        }
        else if(is_com1) {
          ActivitySimulator.outputDetectedActivity( UserActivities.IDLE_COM1);
        }
        else {
          if(is_outside) {
            ActivitySimulator.outputDetectedActivity( UserActivities.IDLE_OUTDOOR);
          } else {
            ActivitySimulator.outputDetectedActivity( UserActivities.IDLE_INDOOR);  
          }
          
        }
      }*/
    }

    /** 
       Called when the magnetic sensor has changed.

       @param   timestamp    Timestamp of this sensor event
       @param   x            Magnetic x value (microTesla)
       @param   y            Magnetic y value (microTesla)
       @param   z            Magnetic z value (microTesla)
       @param   accuracy     Accuracy of the sensor data (you can ignore this)
     */
    public void onMagneticSensorChanged( long timestamp , 
                                         float x , 
                                         float y , 
                                         float z , 
                                         int accuracy ) {
    }

    /** 
       Called when the gyroscope sensor has changed.

       @param   timestamp    Timestamp of this sensor event
       @param   x            Gyroscope x value (rad/sec)
       @param   y            Gyroscope y value (rad/sec)
       @param   z            Gyroscope z value (rad/sec)
       @param   accuracy     Accuracy of the sensor data (you can ignore this)
     */
    public void onGyroscopeSensorChanged( long timestamp , 
                                          float x , 
                                          float y , 
                                          float z , 
                                          int accuracy ) {
    }

    /** 
       Called when the rotation vector sensor has changed.

       @param   timestamp    Timestamp of this sensor event
       @param   x            Rotation vector x value (unitless)
       @param   y            Rotation vector y value (unitless)
       @param   z            Rotation vector z value (unitless)
       @param   scalar       Rotation vector scalar value (unitless)
       @param   accuracy     Accuracy of the sensor data (you can ignore this)
     */
    public void onRotationVectorSensorChanged( long timestamp , 
                                               float x , 
                                               float y , 
                                               float z , 
                                               float scalar ,
                                               int accuracy ) {
    }

    /** 
       Called when the barometer sensor has changed.

       @param   timestamp    Timestamp of this sensor event
       @param   pressure     Barometer pressure value (millibar)
       @param   altitude     Barometer altitude value w.r.t. standard sea level reference (meters)
       @param   accuracy     Accuracy of the sensor data (you can ignore this)
     */
    public void onBarometerSensorChanged( long timestamp , 
                                          float pressure , 
                                          float altitude , 
                                          int accuracy ) {
      altitude_buffer.add((double) altitude);
      double average_altitude = 0.0;
      for(int a = 0; a < altitude_buffer.size(); a++) {
        average_altitude += altitude_buffer.get(a);
      }
      average_altitude = average_altitude/altitude_buffer.size();
      if(average_altitude < 60) {
        is_underground = true;
      } else {
        is_underground = false;
      }
      if(altitude_buffer.size() == 3) { //since shortest activity is 5 mins long
        altitude_buffer.remove(0);
      }
    }

    /** 
       Called when the light sensor has changed.

       @param   timestamp    Timestamp of this sensor event
       @param   light        Light value (lux)
       @param   accuracy     Accuracy of the sensor data (you can ignore this)
     */
    public void onLightSensorChanged( long timestamp , 
                                      float light , 
                                      int accuracy ) {
    }

    /** 
       Called when the proximity sensor has changed.

       @param   timestamp    Timestamp of this sensor event
       @param   proximity    Proximity value (cm)
       @param   accuracy     Accuracy of the sensor data (you can ignore this)
     */
    public void onProximitySensorChanged( long timestamp , 
                                          float proximity , 
                                          int accuracy ) {
    }

    /** 
       Called when the location sensor has changed.

       @param   timestamp    Timestamp of this location event
       @param   provider     "gps" or "network"
       @param   latitude     Latitude (deg)
       @param   longitude    Longitude (deg)
       @param   accuracy     Accuracy of the location data (you may use this) (meters)
       @param   altitude     Altitude (meters) (may be -1 if unavailable)
       @param   bearing      Bearing (deg) (may be -1 if unavailable)
       @param   speed        Speed (m/sec) (may be -1 if unavailable)
     */
    public void onLocationSensorChanged( long timestamp , 
                                         String provider , 
                                         double latitude , 
                                         double longitude , 
                                         float accuracy , 
                                         double altitude , 
                                         float bearing , 
                                         float speed ) {
      /*
      buffer_provider.add(provider);
      if(buffer_provider.size() == 5) {
        int gps_count = 0;
        for(int a = 0; a < buffer_provider.size(); a++) {
          if(buffer_provider.get(a).equals("gps")) {
            gps_count++;
          }
        }
        double network_decision = gps_count/5;
        if(network_decision > 0) {
          is_outside = true;
        } else {
          is_outside = false;
        }
        buffer_provider.remove(0);
      }*/
      if(provider.equals("gps") && previous_provider.equals("gps")) {
        is_outside = true;
      } else {
        is_outside = false;
      }
      previous_provider = provider;
      double speed_double = speed;
      if(speed_double == -1.0) {
        speed_double = calculateDistanceLatLon(latitude,longitude,platitude,plongitude)/((timestamp - ptimestamp)/1000);
        //System.out.println("Speed: " + speedd);
      }
      speed_buffer.add(speed_double);
      if(speed_buffer.size() > 4) {
        speed_buffer.remove(0);
      }
      double speed_to_use = 0;
      for(int a = 0; a < speed_buffer.size(); a++) {
        if(speed_to_use < speed_buffer.get(a)) {
          speed_to_use = speed_buffer.get(a);
        }
      }
      speed_to_use = speed_to_use/speed_buffer.size();
      platitude = latitude;
      plongitude = longitude;
      ptimestamp = timestamp;
      if(speed_to_use > 2.0 || (speed_to_use > 1.0 && is_underground)) {
        System.out.println("Speed to use: " + speed_to_use + " & is underground: " + is_underground);
        is_vehicle = true;
      } else {
        is_vehicle = false;
      }
    }

    /**
       Added function to calculate distance from latitude and longditude. Credit to:
       http://stackoverflow.com/questions/27928/calculate-distance-between-two-latitude-longitude-points-haversine-formula 
    */
    private double calculateDistanceLatLon(double first_lat, double first_long, double second_lat, double second_long) {

      // Convert degrees to radians
      first_lat = first_lat * (Math.PI / 180.0);
      first_long = first_long * (Math.PI / 180.0);
      second_lat = second_lat * (Math.PI / 180.0);
      second_long = second_long * (Math.PI / 180.0);

      // Latitude distance
      Double latDistance = second_lat - first_lat;
      // Longtidude distance
      Double lonDistance = second_long - first_long;
  
      int radiusEarth = 6371000; //meters
      Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + 
                   Math.cos(first_lat) * Math.cos(second_lat) * 
                   Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
      Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
      Double distance = radiusEarth * c;
      return distance;
    }

    /** 
       Called when the WiFi sensor has changed (i.e., a WiFi scan has been performed).

       @param   timestamp           Timestamp of this WiFi scan event
       @param   fingerprintVector   Vector of fingerprints from the WiFi scan
     */
    public void onWiFiSensorChanged( long timestamp , 
                                     Vector< Fingerprint > fingerprintVector ) {

        // You can use Piloc APIs here to figure out the indoor location in COM1, or do
        //  anything that will help you figure out the user activity.
        // You can use the method PilocApi.getLocation(fingerprintVector) to get the location
        //  in COM1 from the WiFi scan. You may use your own radio map, or even write your
        //  own localization algorithm in PilocApi.getLocation(). 
        if(pilocApi.getLocation(fingerprintVector) != null) {
          is_com1 = true;
        } else {
          is_com1 = false;
        }
        // NOTE: Please use the "pilocApi" object defined below to use the Piloc API.
    }

    /** Piloc API provided by Hande. */
    private PilocApi pilocApi;

    /** Helper method to convert UNIX millis time into a human-readable string. */
    private static String convertUnixTimeToReadableString( long millisec ) {
        return sdf.format( new Date( millisec ) );
    }

    /** To format the UNIX millis time as a human-readable string. */
    private static final SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd-h-mm-ssa" );

    // Dummy variables used in the dummy timer code example
    private boolean isUserOutside = false;
    private int numberTimers = 0;
    private Runnable task = new Runnable() {
            public void run() {

                // Logging to the DDMS (in the simulator, the DDMS log is to the console)
                System.out.println();
                Log.i( "ActivitySim" , "Timer " + numberTimers + ": Current simulator time: " + 
                       convertUnixTimeToReadableString( ActivitySimulator.currentTimeMillis() ) );
                System.out.println( "Timer " + numberTimers + ": Current simulator time: " + 
                                    convertUnixTimeToReadableString( ActivitySimulator.currentTimeMillis() ) );

                // Dummy example of outputting a detected activity 
                //  to the file "DetectedActivities.txt" in the trace folder.
                //  Here, we just alternate between indoor and walking every 10 min.
                if( ! isUserOutside ) {
                    ActivitySimulator.outputDetectedActivity( UserActivities.IDLE_INDOOR );
                }
                else {
                    ActivitySimulator.outputDetectedActivity( UserActivities.WALKING );
                }
                isUserOutside = !isUserOutside;

                // Set the next timer to execute the same task 10 min later
                ++numberTimers;
                SimulatorTimer timer = new SimulatorTimer();
                timer.schedule( task ,             // Task to be executed
                                10 * 60 * 1000 );  // Delay in millisec (10 min)
            }
        };
}
