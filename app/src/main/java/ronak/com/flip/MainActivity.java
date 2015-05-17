package ronak.com.flip;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.zip.CheckedInputStream;

/**
 * Created by ronak on 15-05-2015.
 */
public class MainActivity extends ActionBarActivity {

    SensorManager sensorManager;
    DevicePolicyManager devicePolicyManager;
    ComponentName admin;
    boolean service_running;
    SharedPreferences sharedPreferences;
    ToggleButton button, adminbutton;
    CheckBox proximityBox, acceleromterBox, screenOnBox, autoStartCheck;
    boolean acceBool=true, proxiBool=true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        admin = new ComponentName(getApplicationContext(),DeviceReceiver.class);
        sharedPreferences = getSharedPreferences(Constants.SHAREPREFS,MODE_PRIVATE);
        setupUiComponents();
        setupSensor();
        setupCheckBoxListener();
    }

    private void setupCheckBoxListener() {

        proximityBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked)
                    sharedPreferences.edit().putBoolean(Constants.USE_PROXIMITY,true).apply();
                else
                    sharedPreferences.edit().putBoolean(Constants.USE_PROXIMITY,false).apply();
                refreshService();
                Log.e("Flip main","use_proximity"+sharedPreferences.getBoolean(Constants.USE_PROXIMITY,false));
            }
        });

        screenOnBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked)
                {
                    sharedPreferences.edit().putBoolean(Constants.AUTO_SCREEN_ON,true).apply();
                }
                else
                {
                    sharedPreferences.edit().putBoolean(Constants.AUTO_SCREEN_ON,false).apply();
                }
                refreshService();
            }
        });

        autoStartCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked)
                {
                    sharedPreferences.edit().putBoolean(Constants.AUTO_START_BOOT,true).apply();
                }
                else
                {
                    sharedPreferences.edit().putBoolean(Constants.AUTO_START_BOOT,false).apply();
                }
            }
        });
    }

    private void setupSensor() {

        if(sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)==null)
            proxiBool=false;
        sharedPreferences.edit().putBoolean(Constants.PROXIMITY_AVAILABLE,proxiBool).apply();

        if(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)==null)
            acceBool=false;
        sharedPreferences.edit().putBoolean(Constants.ACCELEROMETER_AVAILABLE,acceBool).apply();

        if(!proxiBool)
        {
            proximityBox.setText("Proximity Sensor not supported");
            proximityBox.setChecked(false);
            proximityBox.setEnabled(false);
        }

        acceleromterBox.setChecked(true);
        acceleromterBox.setEnabled(false);
        sharedPreferences.edit().putBoolean(Constants.USE_ACCELEROMETER,true).apply();

        if(sharedPreferences.getBoolean(Constants.AUTO_SCREEN_ON,false))
            screenOnBox.setChecked(true);
        if(sharedPreferences.getBoolean(Constants.AUTO_START_BOOT,false))
            autoStartCheck.setChecked(true);
        if(sharedPreferences.getBoolean(Constants.USE_PROXIMITY,false))
            proximityBox.setChecked(true);
    }

    private void setupUiComponents()
    {
        button = (ToggleButton) findViewById(R.id.button);
        adminbutton = (ToggleButton) findViewById(R.id.admin);
        proximityBox = (CheckBox) findViewById(R.id.proximityCheckBox);
        acceleromterBox = (CheckBox) findViewById(R.id.accelerometerCheckBox);
        screenOnBox = (CheckBox) findViewById(R.id.screenOnCheckBox);
        autoStartCheck = (CheckBox) findViewById(R.id.autoStartCheckBox);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Service status
        service_running = sharedPreferences.getBoolean(Constants.SERVICE_STATUS,false);
        //Setting text for background service button
        if(service_running)
            button.setChecked(true);
        else
            button.setChecked(false);
        //Setting text for the admin button
        if(devicePolicyManager.isAdminActive(admin))
        {
            adminbutton.setChecked(true);
            button.setEnabled(true);
        }
        else
        {
            adminbutton.setChecked(false);
            button.setEnabled(false);
        }
    }

    //To receive the result code from the device policy manager
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==1)
        {
            Log.e("com.ronak.flip","Result code for the Admin "+resultCode);
        }
    }

    public void start_button(View view)
    {

        //Service disable intent
        if(service_running)
        {
            startService();
            sharedPreferences.edit().putBoolean(Constants.SERVICE_STATUS,false).apply();
            service_running = false;
            button.setChecked(false);
        }
        //Service start intent
        else
        {
            stopService();
            sharedPreferences.edit().putBoolean(Constants.SERVICE_STATUS,true).apply();
            service_running = true;
            button.setChecked(true);
        }
    }

    private void stopService() {
        Intent intent = new Intent(this,Background.class);
        intent.setAction(Constants.START_SERVICE_FOREGROUND);
        startService(intent);
    }

    private void startService()
    {
        Intent intent = new Intent(this,Background.class);
        intent.setAction(Constants.STOP_SERVICE_FORGROUND);
        startService(intent);
    }

    private void refreshService()
    {
        Intent intent = new Intent(this,Background.class);
        intent.setAction(Constants.REFRESH_SERVICE);
        startService(intent);
    }


    public void adminButton(View view)
    {
        //Gaining Administrative rights from device policy manager
        if(!devicePolicyManager.isAdminActive(admin))
        {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,admin);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Admin permission is needed to lock the screen");
            startActivityForResult(intent,1);
        }
        //Giving away administrative rights
        else
        {
            removeAdmin();
        }
    }

    private void removeAdmin() {devicePolicyManager.removeActiveAdmin(admin);
        adminbutton.setText("Grant Admin rights");
        button.setChecked(false);
        button.setEnabled(false);
        if(service_running)
            stopService();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu_main,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        if(item.getItemId()==R.id.deleteButton)
        {
            removeAdmin();
            Uri uri = Uri.parse("package:ronak.com.flip");
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE,uri);
            startActivity(intent);
        }
        return true;
    }
}
