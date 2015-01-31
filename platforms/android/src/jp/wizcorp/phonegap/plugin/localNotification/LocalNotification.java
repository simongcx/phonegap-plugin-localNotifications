package jp.wizcorp.phonegap.plugin.localNotification;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.util.Log;
import java.util.Set;

import java.lang.Exception;

/**
 * This plugin utilizes the Android AlarmManager in combination with StatusBar
 * notifications. When a local notification is scheduled the alarm manager takes
 * care of firing the event. When the event is processed, a notification is put
 * in the Android status bar.
 * 
 * @author Daniel van 't Oever
 * @author Updated By Ally Ogilvie
 */
public class LocalNotification extends CordovaPlugin {

    public static final String TAG = "LocalNotification";
    public static CordovaWebView _webview;
    private AlarmHelper alarm = null;

    @Override
    public void initialize(org.apache.cordova.CordovaInterface cordova, org.apache.cordova.CordovaWebView webView) {
        // Keep a pointer to the WebView so we can emit JS Event when getting a notification
        _webview = webView;
        super.initialize(cordova, webView);
        // If we received notification when the app was cold send them to JS now

        String notificationTapped = cordova.getActivity().getApplicationContext()
                                     .getSharedPreferences(LocalNotification.TAG, Context.MODE_PRIVATE)
                                     .getString("notificationTapped", null);
        if (notificationTapped != null) {
            LocalNotification.getCordovaWebView().sendJavascript("cordova.fireDocumentEvent('receivedLocalNotification', { active : false, notificationId : " + notificationTapped + " })");
            cordova.getActivity().getApplicationContext()
              .getSharedPreferences(LocalNotification.TAG, Context.MODE_PRIVATE)
              .edit()
              .clear()
              .commit();
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        String alarmId;
        this.alarm = new AlarmHelper();
        this.alarm.setContext(cordova.getActivity().getApplicationContext());

        if (action.equalsIgnoreCase("addNotification")) {

            try {
                alarmId = args.getString(0); //getString coerces if necessary, throws JSONexception if not
            } catch (Exception e) {
                Log.e(TAG, "No ID provided.");
                callbackContext.error("No ID provided.");
                return true;
            }


            try {               
                long seconds = System.currentTimeMillis() + (args.getJSONObject(1).getLong("seconds") * 1000);
                String title, ticker, icon;
                int iconResource = 0;

                title = ticker = icon = "";
                try {
                    title = args.getJSONObject(1).getString("title");
                } catch (Exception e){}
                try {
                    ticker = args.getJSONObject(1).getString("ticker");
                } catch (Exception e){}
                try {
                    icon = args.getJSONObject(1).getString("icon");
                } catch (Exception e) {}


                if (icon != "") {
                    try {
                        iconResource = cordova.getActivity().getResources().getIdentifier(icon, "drawable", cordova.getActivity().getPackageName());
                    } catch(Exception e) {
                        Log.e(TAG, "The icon resource couldn't be found. Taking default icon.");
                    }
                }
                
                args.getJSONObject(1).put("seconds", seconds);
                
                persistAlarm(alarmId, args);
                return this.add(callbackContext, title == "" ? "Notification" : title,
                        args.getJSONObject(1).getString("message"), ticker == "" ? args.getJSONObject(1).getString("message") : ticker,
                        alarmId.toString(), iconResource == 0 ? android.R.drawable.btn_star_big_on : iconResource, seconds);
            } catch (Exception e) {
                Log.e(TAG, "Exception: " + e);
            }
            
        } else if (action.equalsIgnoreCase("cancelNotification")) {
            
            try {
                alarmId = args.getString(0); //getString coerces if necessary, throws JSONexception if not
            } catch (Exception e) {
                Log.e(TAG, "No ID provided.");
                callbackContext.error("No ID provided.");
                return true;
            }
            
            boolean result = cancelNotification(callbackContext, alarmId);
            
            if (result) {
                callbackContext.success();
                unpersistAlarm(alarmId);
                return true;
            } else {
                callbackContext.error("Cancel notification failed, ID: " + alarmId);
                return false;
            }

            
        } else if (action.equalsIgnoreCase("cancelAllNotifications")) {
            Log.d(TAG, "cancelAllNotifications called");
            return this.cancelAllNotifications(callbackContext);
        }
        
        Log.e(TAG, "Didn't match any action, returning false");
        return false; // Returning false results in a "MethodNotFound" error.
    }

    /**
     * Set an alarm
     * 
     * @param callbackContext
     *            Callback context of the request from Cordova
     * @param alarmTitle
     *            The title of the alarm as shown in the Android notification
     *            panel
     * @param alarmSubTitle
     *            The subtitle of the alarm
     * @param alarmId
     *            The unique ID of the notification
     * @param seconds
     *            A calendar object that represents the time at which the alarm
     *            should first be started
     */
    public Boolean add(CallbackContext callbackContext, String alarmTitle, String alarmSubTitle, String alarmTicker,
        String alarmId, int icon, long seconds) {

        boolean result = alarm.addAlarm(alarmTitle, alarmSubTitle, alarmTicker, alarmId, icon, seconds);
        
        if (result) {
            callbackContext.success();
            return true;
        } else {
            callbackContext.error("Add notification failed.");
            return false;
        }
    }

    /**
     * Cancel a specific notification that was previously registered.
     * 
     * @param callbackContext
     *            Callback context of the request from Cordova
     * @param notificationId
     *            The original ID of the notification that was used when it was
     *            registered using addNotification()
     */
    public Boolean cancelNotification(CallbackContext callbackContext, String notificationId) {
        Log.d(TAG, "cancelNotification: Canceling event with id: " + notificationId);

        return alarm.cancelAlarm(notificationId);

    }

    /**
     * @param callbackContext
     *            Callback context of the request from Cordova
     * Cancel all notifications that were created by this plugin.
     */
    public Boolean cancelAllNotifications(CallbackContext callbackContext) {
        Log.d(TAG, "cancelAllNotifications: cancelling all events for this application");
        /*
         * Android can only unregister a specific alarm. There is no such thing
         * as cancelAll. Therefore we rely on the Shared Preferences which holds
         * all our alarms to loop through these alarms and unregister them one
         * by one.
         */
        
        Set<String> alarmIds = cordova.getActivity().getApplicationContext().getSharedPreferences(TAG, Context.MODE_PRIVATE).getAll().keySet();
        
        Log.d(LocalNotification.TAG, "Number of alarmIds: " + alarmIds.size());

        boolean outcome = true;

        for (String alarmId : alarmIds) {
            Log.d(LocalNotification.TAG, "Canceling notification with id: " + alarmId);
            boolean result = this.cancelNotification(callbackContext, alarmId);
            if (result) {
                unpersistAlarm(alarmId);
            }
            else {
                Log.e(LocalNotification.TAG, "Failed to cancel notification with id: " + alarmId);
                callbackContext.error("Failed to cancel all notifications. Failed to cancel notification with id: " + alarmId);
                return false;
            }
        }
        callbackContext.success();
        return true;
        
        /*
        boolean result = alarm.cancelAll(cordova.getActivity()
                                                .getApplicationContext()
                                                .getSharedPreferences(TAG, Context.MODE_PRIVATE));
    
        if (result) {
            callbackContext.success();
            return true;
        } else {
            callbackContext.error("Cancel all notifications failed.");
            return false;
        }
        */
    }

    public static CordovaWebView getCordovaWebView() {
        return _webview;
    }

    /**
     * Persist the information of this alarm to the Android Shared Preferences.
     * This will allow the application to restore the alarm upon device reboot.
     * Also this is used by the cancelAllNotifications method.
     * 
     * @see #cancelAllNotifications()
     * 
     * @param optionsArr
     *            The assumption is that parseOptions has been called already.
     * 
     * @return true when successful, otherwise false
     */
    private boolean persistAlarm(String alarmId, JSONArray optionsArr) {
    
        return  cordova.getActivity().getApplicationContext()
                                     .getSharedPreferences(TAG, Context.MODE_PRIVATE)
                                     .edit()
                                     .putString(alarmId.toString(), optionsArr.toString())
                                     .commit();
    }

    /**
     * Remove a specific alarm from the Android shared Preferences
     * 
     * @param alarmId
     *            The Id of the notification that must be removed.
     * 
     * @return true when successful, otherwise false
     */
    private boolean unpersistAlarm(String alarmId) {
    
        return cordova.getActivity().getApplicationContext()
                                    .getSharedPreferences(TAG, Context.MODE_PRIVATE)
                                    .edit()
                                    .remove(alarmId)
                                    .commit();
    }

    /**
     * Clear all alarms from the Android shared Preferences
     * 
     * @return true when successful, otherwise false
     */
    private boolean unpersistAlarmAll() {
    
        return cordova.getActivity().getApplicationContext()
                                    .getSharedPreferences(TAG, Context.MODE_PRIVATE)
                                    .edit()
                                    .clear()
                                    .commit();
    }
}
