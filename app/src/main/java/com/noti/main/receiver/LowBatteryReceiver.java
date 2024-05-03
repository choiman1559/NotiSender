package com.noti.main.receiver;

import static com.noti.main.service.NotiListenerService.getUniqueID;
import static com.noti.main.service.NotiListenerService.sendNotification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.util.Log;

import com.noti.main.Application;
import com.noti.main.service.NotiListenerService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

public class LowBatteryReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(Objects.equals(intent.getAction(), Intent.ACTION_BATTERY_LOW)) {
            SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
            if(prefs.getString("UID", "").isEmpty() || !prefs.getBoolean("sendBatteryLowWarning", true)) {
                return;
            }

            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, filter);
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);

            String DEVICE_NAME = NotiListenerService.getDeviceName();
            String DEVICE_ID = getUniqueID();
            JSONObject notificationBody = new JSONObject();

            try {
                notificationBody.put("type", "pair|battery_warning");
                notificationBody.put("device_name", DEVICE_NAME);
                notificationBody.put("device_id", DEVICE_ID);
                notificationBody.put("battery_level", level);
            } catch (JSONException e) {
                Log.e("Noti", "onCreate: " + e.getMessage());
            }

            sendNotification(notificationBody, context.getPackageName(), context);
        }
    }
}
