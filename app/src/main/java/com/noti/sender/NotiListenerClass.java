package com.noti.sender;

import android.app.Notification;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class NotiListenerClass extends NotificationListenerService {

    @Nullable
    String toString(Boolean boo) { return BuildConfig.DEBUG ? (boo ? "true" : "false") : null ; }

    void Log(String message) {
        if(BuildConfig.DEBUG) Log.d("debug",message);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        if(BuildConfig.DEBUG) {
            Log("                                      ");
            Log("***onNotificationPosted debug info***");
            Log("uid : " + getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", ""));
            Log("package : " + sbn.getPackageName());
            Log("service type : " + getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("service", ""));
            Log("if uid is blank : " + toString(getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "").equals("")));
            Log("if service Enabled : " + toString(getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("serviceToggle", false)));
            Log("if include blacklist : " + toString(getSharedPreferences("Blacklist", MODE_PRIVATE).getBoolean(sbn.getPackageName(), false)));
            Log("**************************************");
            Log("                                       ");
        }

        if (NotiListenerClass.this.getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("service", "").equals("send") &&
                !getSharedPreferences("SettingsActivity",MODE_PRIVATE).getString("UID","").equals("") &&
                getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("serviceToggle", false) &&
        !getSharedPreferences("Blacklist",MODE_PRIVATE).getBoolean(sbn.getPackageName(),false) &&
        !sbn.getPackageName().equals(getPackageName())) {

            Notification notification = sbn.getNotification();
            Bundle extra = notification.extras;

            String TOPIC = "/topics/" + getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "");
            String TITLE = extra.getString(Notification.EXTRA_TITLE);
            String TEXT = Objects.requireNonNull(extra.getCharSequence(Notification.EXTRA_TEXT)).toString();
            String Package = "" + sbn.getPackageName();

            JSONObject notificationHead = new JSONObject();
            JSONObject notifcationBody = new JSONObject();
            try {
                notifcationBody.put("title", TITLE);
                notifcationBody.put("message", TEXT);
                notifcationBody.put("package", Package);
                if(Build.VERSION.SDK_INT > 25)
                    notifcationBody.put("cid", extra.getString(Notification.EXTRA_CHANNEL_ID));

                notificationHead.put("to", TOPIC);
                notificationHead.put("data", notifcationBody);
            } catch (JSONException e) {
                Log.e("Noti", "onCreate: " + e.getMessage());
            }
            Log(notificationHead.toString());
            sendNotification(notificationHead,sbn);
        }
    }

    private void sendNotification(JSONObject notification,StatusBarNotification sbn) {
        final String FCM_API = "https://fcm.googleapis.com/fcm/send";
        final String serverKey = "key=" + getString(R.string.serverKey);
        final String contentType = "application/json";
        final String TAG = "NOTIFICATION TAG";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(FCM_API, notification,
                response -> Log.i(TAG, "onResponse: " + response.toString() + " ,package: " + sbn.getPackageName()),
                error -> {
                    Toast.makeText(NotiListenerClass.this, "Failed to send Notification! Please check internet and try again!", Toast.LENGTH_LONG).show();
                    Log.i(TAG, "onErrorResponse: Didn't work" + " ,package: " + sbn.getPackageName());
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Authorization", serverKey);
                params.put("Content-Type", contentType);
                return params;
            }
        };
        MySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsonObjectRequest);
    }
}
