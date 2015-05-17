package ronak.com.flip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Created by ronak on 15-05-2015.
 */
public class BroadcastR extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e("Flip Broadcast","Broadcast called"+intent.getAction());
        Intent intent1 = new Intent(context.getApplicationContext(),Background.class);
        if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED))
        {
            SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.SHAREPREFS,Context.MODE_PRIVATE);
            if(sharedPreferences.getBoolean(Constants.AUTO_START_BOOT,false))
            {
                intent1.setAction(Constants.START_SERVICE_FOREGROUND);
                context.startService(intent1);
            }
        }
        else if(intent.getAction().equals(Intent.ACTION_SCREEN_OFF))
            intent1.setAction(Constants.STOP_LISTENING);
        else if(intent.getAction().equals(Intent.ACTION_SCREEN_ON))
            intent1.setAction(Constants.START_LISTENING);
        else
            intent1.setAction(Constants.START_SERVICE_FOREGROUND);
        context.startService(intent1);
    }
}
