package ronak.com.flip;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Created by ronak on 15-05-2015.
 */
public class Background extends Service {

    SharedPreferences sharedPreferences;
    DevicePolicyManager devicePolicyManager;
    Sensor accelerometer,proximity;
    SensorManager sensorManager;
    IntentFilter intentFilter;
    SensorEventListener accelerometerListener, proximityListener, screenOnListener;
    float[] pastSensorValues, presentSensorValues;
    boolean x_axis,y_axis,z_axis;
    final int samplingRate = 5;
    int samplingCount;
    Intent intent;
    PendingIntent pendingIntent;
    AlarmManager alarmManager;
    BroadcastReceiver broadcastReceiver;
    boolean useProximity;
    boolean stopRequest=false;
    boolean screenOffByApp;
    boolean autoScreenOn;
    float xMaxDrift, yMaxDrift, zMaxDrift;
    PowerManager.WakeLock wakeLock;
    PowerManager powerManager;
    long savedTimeStamp;
    long freqScreeOn;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e("Flip Service", "Service running successfully");
        //Replaced Alarm manager with broadcast
        setupIntent();
        //alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        sharedPreferences = getSharedPreferences(Constants.SHAREPREFS,MODE_PRIVATE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP|PowerManager.FULL_WAKE_LOCK|PowerManager.ON_AFTER_RELEASE,"Mywakelock");
        initializeVars();
        setupListener();
        openServiceStatus();
        updateSettings();
        registerBroadCast();
    }

    private void updateSettings() {
        sharedPreferences = getSharedPreferences(Constants.SHAREPREFS,MODE_PRIVATE);
        useProximity = sharedPreferences.getBoolean(Constants.USE_PROXIMITY,false);
        if(useProximity)
        {
            Log.e("Flip service","Updating settings to MAX");
            xMaxDrift = Constants.X_MAX_DRIFT;
            yMaxDrift = Constants.Y_MAX_DRIFT;
            zMaxDrift = Constants.Z_MAX_DRIFT;
        }
        else
        {
            Log.e("Flip service","Updating settings to Min");
            xMaxDrift = Constants.X_MIN_DRIFT;
            yMaxDrift = Constants.Y_MIN_DRIFT;
            zMaxDrift = Constants.Z_MIN_DRIFT;
        }
        autoScreenOn = sharedPreferences.getBoolean(Constants.AUTO_SCREEN_ON,false);
    }

    private void openServiceStatus() {
        sharedPreferences.edit().putBoolean(Constants.SERVICE_STATUS,true).apply();
    }

    private void closeServiceStatus()
    {
        sharedPreferences.edit().putBoolean(Constants.SERVICE_STATUS,false).apply();
    }


    private void initializeVars() {
        freqScreeOn = Constants.FREQUENCY_SCREEN_ON;
        savedTimeStamp = 0;
        presentSensorValues = new float[]{0,0,0};
        pastSensorValues = new float[]{0,0,0};
        samplingCount = 0;
    }

    private void registerBroadCast() {
        broadcastReceiver = new BroadcastR();
        registerReceiver(broadcastReceiver,intentFilter);
    }

    private void unregisterBroadcast()
    {
        try{
            unregisterReceiver(broadcastReceiver);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    private void setupIntent() {
        intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
    }


    private void setupListener() {
        accelerometerListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if(event.values[2]<0)
                {
                    presentSensorValues = event.values.clone();
                    //X-axis should be less than 1 if it is placed on a flat surface and difference is minimum
                    x_axis = (Math.abs(presentSensorValues[0]-pastSensorValues[0])<.5)&&(Math.abs(presentSensorValues[0])<xMaxDrift);
                    y_axis = (Math.abs(presentSensorValues[1]-pastSensorValues[1])<.5)&&(Math.abs(presentSensorValues[1])<yMaxDrift);
                    z_axis = presentSensorValues[2]<0;
                    if(x_axis&&y_axis&&z_axis)
                    {
                        samplingCount++;
                        if(samplingCount >= samplingRate)
                        {
                            samplingCount = 0;
                            if(useProximity)
                                startProximity();
                            else
                            {
                                devicePolicyManager.lockNow();
                                screenOffByApp = true;
                                Log.e("Flip service","accelerometer closed the app");
                                stopListening();
                            }
                        }
                    }
                    pastSensorValues = presentSensorValues;
                }

            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
        proximityListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if(event.values[0]<event.sensor.getMaximumRange()) {
                    devicePolicyManager.lockNow();
                    Log.e("Flip Service", "Proximity screen off");
                    screenOffByApp = true;
                    stopListening();
                }
                stopProximity();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        screenOnListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if(event.timestamp-savedTimeStamp > freqScreeOn)
                {
                    savedTimeStamp = event.timestamp;
                    if(event.values[2]>0)
                    {
                        wakeLock.acquire();
                        wakeLock.release();
                        //Log.e("Flip service","Woke up");
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
    }

    private void startProximity()
    {
        sensorManager.registerListener(proximityListener, proximity, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopProximity()
    {
        sensorManager.unregisterListener(proximityListener);
    }

    private void startAccelerometer()
    {
        sensorManager.registerListener(accelerometerListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void startScreenOn()
    {
        savedTimeStamp = 0;
        sensorManager.registerListener(screenOnListener,accelerometer,SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void stopScreenOn()
    {
        sensorManager.unregisterListener(screenOnListener);
    }

    private void stopAccelerometer()
    {
        sensorManager.unregisterListener(accelerometerListener);
    }

    private void stopListening() {
        stopProximity();
        stopAccelerometer();
        stopScreenOn();
    }

    private void startListening() {
        startAccelerometer();
        //startForeground(Constants.FOREGROUND_CODE,new Notification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if(intent.getAction().equals(Constants.STOP_SERVICE_FORGROUND)){
            stopRequest = true;
            stopSelf();
        }
        else if(intent.getAction().equals(Constants.STOP_LISTENING))
        {
            stopListening();
            if(screenOffByApp&&autoScreenOn)
                startScreenOn();
        }
        else if(intent.getAction().equals(Constants.START_LISTENING))
        {
            stopScreenOn();
            startListening();
            screenOffByApp = false;
        }
        else if(intent.getAction().equals(Constants.REFRESH_SERVICE))
        {
            updateSettings();
        }
        else
        {
            //alarmManager.cancel(pendingIntent);
            stopRequest=false;
            Log.e("Flip service","service started");
            openServiceStatus();
            startListening();
        }
        return START_STICKY;
    }

    protected void finsih()
    {
        stopListening();
        closeServiceStatus();
        if(!stopRequest) {
            Intent intent1 = new Intent(this,BroadcastR.class);
            intent1.setAction(Constants.START_SERVICE_FOREGROUND);
            sendBroadcast(intent1);
        }
        else
        {
            unregisterBroadcast();
        }
            //alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+1000,5000,pendingIntent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        finsih();
        Log.e("Flip service","OnTaskRemoved()");
    }

    @Override
    public void onDestroy() {
        finsih();
        Log.e("Flip Service", "OnDestory() service");
        //stopForeground(true);
    }
}
