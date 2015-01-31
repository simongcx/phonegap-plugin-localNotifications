package jp.wizcorp.phonegap.plugin.localNotification;

import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * This class is triggered upon reboot of the device. It needs to re-register
 * the alarms with the AlarmManager since these alarms are lost in case of
 * reboot.
 * 
 * @author dvtoever
 */
public class AlarmRestoreOnBoot extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        
        // Obtain alarm details form Shared Preferences
        SharedPreferences alarmSettings = context.getSharedPreferences(LocalNotification.TAG, Context.MODE_PRIVATE);
        final AlarmHelper alarm = new AlarmHelper();
        alarm.setContext(context);
        final Map<String, ?> allAlarms = alarmSettings.getAll();

        /*
         * For each alarm, parse its alarm options and register it again with
         * the Alarm Manager
         */
        for (String alarmId : allAlarms.keySet()) {
            try {
                JSONArray args = new JSONArray(alarmSettings.getString(alarmId, "")); // second parameter is default valu
                Log.d(LocalNotification.TAG, "alarmDetails in AlarmRestoreOnBoot.onReceive: " + args.toString());
           
                //long seconds = System.currentTimeMillis() + (args.getJSONObject(1).getLong("seconds") * 1000);
                long seconds = args.getJSONObject(1).getLong("seconds");
                String title, ticker, icon, message;
                int iconResource = android.R.drawable.btn_star_big_on;

                title = ticker = icon = message = "";
                try {
                    title = args.getJSONObject(1).getString("title");
                } catch (Exception e){
                    title = "Notification";
                }
                try {
                    message = args.getJSONObject(1).getString("message");
                } catch (Exception e){
                    message = "Notification message";
                }
                try {
                    ticker = args.getJSONObject(1).getString("ticker");
                } catch (Exception e) {
                    ticker = message;
                }
                try {
                    icon = args.getJSONObject(1).getString("icon");
                } catch (Exception e) {}


                if (icon != "") {
                    try {
                        iconResource = android.R.drawable.btn_star_big_on;
                        //iconResource = cordova.getActivity().getResources().getIdentifier(icon, "drawable", cordova.getActivity().getPackageName());
                    } catch(Exception e) {
                        Log.e(LocalNotification.TAG, "The icon resource couldn't be found. Taking default icon.");
                    }
                }
                        
                alarm.addAlarm(title, message, ticker, alarmId, iconResource, seconds);
                        
                        
            } catch (Exception e) {
                Log.e(LocalNotification.TAG, "AlarmRestoreOnBoot: Error while restoring alarm details after reboot: " + e.toString());
            }

            Log.d(LocalNotification.TAG, "AlarmRestoreOnBoot: Successfully restored alarms id upon reboot: " + alarmId);
            
        }
        Log.d(LocalNotification.TAG, "AlarmRestoreOnBoot: Successfully restored alarms upon reboot");
    }
}
