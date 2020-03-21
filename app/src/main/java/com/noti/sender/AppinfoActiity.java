package com.noti.sender;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class AppinfoActiity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appinfo_actiity);

        TextView version = findViewById(R.id.version);
        try {
            version.setText("Version : " + getPackageManager().getPackageInfo("com.noti.sender", 0).versionName + "\nthis app is free-softwear under the GNU LGPL 3.0 license.");
        } catch (Exception ignored) {}

        Button gotogit = findViewById(R.id.gotogit);
        gotogit.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender"))));
    }
}
