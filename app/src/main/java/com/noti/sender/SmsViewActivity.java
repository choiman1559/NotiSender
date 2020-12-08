package com.noti.sender;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SmsViewActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smsdetail);
        Intent i = getIntent();

        String Topic = "/topics/" + getSharedPreferences("com.noti.sender_preferences",MODE_PRIVATE).getString("UID","");
        String address = i.getStringExtra("address");
        String message = i.getStringExtra("message");
        String Device_name = i.getStringExtra("device_name");
        String Date = i.getStringExtra("date");

        Button Reply = findViewById(R.id.ok);
        Button Cancel = findViewById(R.id.cancel);
        EditText Content = findViewById(R.id.smsContent);
        TextView ContentView = findViewById(R.id.notiDetail);

        String content = "";
        content += "Sms Overview\n";
        content += "from : " + address + "\n";
        content += "date : " +  Date + "\n";
        content += "sent device : " + Device_name + "\n";
        content += "message : " + message + "\n";
        ContentView.setText(content);

        Reply.setOnClickListener(v -> {
            String msg = Content.getText().toString();
            if(msg.equals("")) Content.setError("Please type message");
            else {
                JSONObject notificationHead = new JSONObject();
                JSONObject notifcationBody = new JSONObject();
                try {
                    notifcationBody.put("type","reception|sms");
                    notifcationBody.put("message",msg);
                    notifcationBody.put("address",address);
                    notifcationBody.put("device_name", Build.MANUFACTURER  + " " + Build.MODEL);
                    notifcationBody.put("send_device_name",Device_name);
                    notifcationBody.put("send_device_id",i.getStringExtra("device_id"));
                    notificationHead.put("to",Topic);
                    notificationHead.put("data", notifcationBody);
                } catch (JSONException e) {
                    Log.e("Noti", "onCreate: " + e.getMessage() );
                }
                sendNotification(notificationHead);
                this.finish();
            }
        });
        Cancel.setOnClickListener(v -> finish());
    }

    private void sendNotification(JSONObject notification) {
        final String FCM_API = "https://fcm.googleapis.com/fcm/send";
        final String serverKey = "key=" + getString(R.string.serverKey);
        final String contentType = "application/json";
        final String TAG = "NOTIFICATION TAG";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(FCM_API, notification,
                response -> Log.i(TAG, "onResponse: " + response.toString()),
                error -> Toast.makeText(this, "Failed to send Notification! Please check internet and try again!", Toast.LENGTH_SHORT).show()){
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
