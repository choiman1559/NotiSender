package com.noti.sender;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
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

        Log("onNotificationPosted");
        Log(getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("service",""));
        Log("uid : " + getSharedPreferences("SettingsActivity",MODE_PRIVATE).getString("UID",""));
        Log("if : " + toString(getSharedPreferences("SettingsActivity",MODE_PRIVATE).getString("UID","").equals("")));

        if (NotiListenerClass.this.getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("service", "").equals("send") &&
                !getSharedPreferences("SettingsActivity",MODE_PRIVATE).getString("UID","").equals("") &&
                getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("serviceToggle", false) ) {

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

                notificationHead.put("to", TOPIC);
                notificationHead.put("data", notifcationBody);
            } catch (JSONException e) {
                Log.e("Noti", "onCreate: " + e.getMessage());
            }
            Log(notificationHead.toString());
            sendNotification(notificationHead);
        }
    }

    private void sendNotification(JSONObject notification) {
        final String FCM_API = "https://fcm.googleapis.com/fcm/send";
        final String serverKey = "key=" + getString(R.string.serverKey);
        final String contentType = "application/json";
        final String TAG = "NOTIFICATION TAG";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(FCM_API, notification,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.i(TAG, "onResponse: " + response.toString());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(NotiListenerClass.this, "알람 전송 실패! 인터넷 환경을 확인해주세요!", Toast.LENGTH_LONG).show();
                        Log.i(TAG, "onErrorResponse: Didn't work");
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("Authorization", serverKey);
                params.put("Content-Type", contentType);
                return params;
            }
        };
        MySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsonObjectRequest);
    }
}
