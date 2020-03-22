package com.noti.sender;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


public class MessageSendClass extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String TOPIC = "/topics/" + getSharedPreferences("SettingsActivity",MODE_PRIVATE).getString("UID","") + "_receiver";
        String Package = getIntent().getStringExtra("package");

        JSONObject notificationHead = new JSONObject();
        JSONObject notifcationBody = new JSONObject();
        try {
            notifcationBody.put("Package", Package);

            notificationHead.put("to", TOPIC);
            notificationHead.put("data", notifcationBody);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage() );
        }
        sendNotification(notificationHead);
        finish();
    }

    private void sendNotification(JSONObject notification) {
        final String FCM_API = "https://fcm.googleapis.com/fcm/send";
        final String serverKey = "key=" + getString(R.string.serverKey);
        final String contentType = "application/json";
        final String TAG = "NOTIFICATION TAG";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(FCM_API, notification,
                response -> Log.i(TAG, "onResponse: " + response.toString()),
                error -> {
                    Toast.makeText(MessageSendClass.this, "Request error", Toast.LENGTH_LONG).show();
                    Log.i(TAG, "onErrorResponse: Didn't work");
                }){
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