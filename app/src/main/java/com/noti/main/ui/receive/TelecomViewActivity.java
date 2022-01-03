package com.noti.main.ui.receive;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.noti.main.service.NotiListenerService;
import com.noti.main.R;

import org.json.JSONException;
import org.json.JSONObject;

public class TelecomViewActivity extends Activity {
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smsdetail);
        Intent i = getIntent();

        String Topic = "/topics/" + getSharedPreferences("com.noti.main_preferences",MODE_PRIVATE).getString("UID","");
        String address = i.getStringExtra("address");
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
        ContentView.setText(Html.fromHtml(content));

        TitleView.setText("Call Overview");
        Reply.setText("Open in Dialer");

        Content.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(s.length() > 0) {
                    Reply.setText("Reply as SMS");
                } else {
                    Reply.setText("Open in Dialer");
                }
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        Reply.setOnClickListener(v -> {
            String msg = Content.getText().toString();
            if(msg.equals("")) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + address));
                startActivity(intent);
                finish();
            } else {
                JSONObject notificationHead = new JSONObject();
                JSONObject notificationBody = new JSONObject();
                try {
                    notificationBody.put("type","reception|sms");
                    notificationBody.put("message",msg);
                    notificationBody.put("address",address);
                    notificationBody.put("device_name", Build.MANUFACTURER  + " " + Build.MODEL);
                    notificationBody.put("send_device_name",Device_name);
                    notificationBody.put("send_device_id",i.getStringExtra("device_id"));
                    notificationHead.put("to",Topic);
                    notificationHead.put("data", notificationBody);
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
