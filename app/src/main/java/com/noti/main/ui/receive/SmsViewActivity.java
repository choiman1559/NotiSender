package com.noti.main.ui.receive;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.noti.main.service.NotiListenerService;
import com.noti.main.R;

import org.json.JSONException;
import org.json.JSONObject;

public class SmsViewActivity extends Activity {
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smsdetail);
        Intent i = getIntent();

        String Topic = "/topics/" + getSharedPreferences("com.noti.main_preferences",MODE_PRIVATE).getString("UID","");
        String address = i.getStringExtra("address");
        String message = i.getStringExtra("message");
        String Device_name = i.getStringExtra("device_name");
        String Date = i.getStringExtra("date");
        String Package = i.getStringExtra("package");

        MaterialButton Reply = findViewById(R.id.ok);
        MaterialButton Cancel = findViewById(R.id.cancel);
        EditText Content = findViewById(R.id.smsContent);
        TextView ContentView = findViewById(R.id.notiDetail);
        TextView TitleView = findViewById(R.id.titleDetail);

        String content = "";
        content += "<b>From</b> : " + address + "<br>";
        content += "<b>Date</b> : " +  Date + "<br>";
        content += "<b>Sent device</b> : " + Device_name + "<br>";
        content += "<b>Message</b> : " + message;
        ContentView.setText(Html.fromHtml(content));
        TitleView.setText("Sms Overview");

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
                NotiListenerService.sendNotification(notificationHead, Package, this);
                ExitActivity.exitApplication(this);
            }
        });
        Cancel.setOnClickListener(v -> ExitActivity.exitApplication(this));
    }
}
