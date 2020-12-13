package com.noti.main;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class AppinfoActiity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appinfo_actiity);

        TextView version = findViewById(R.id.version);
        try {
            version.setText(String.format("Version : %s\nthis app is free-softwear under the GNU LGPL 3.0 license.", getPackageManager().getPackageInfo("com.noti.main", 0).versionName));
        } catch (Exception ignored) {}

        Button GoToGit = findViewById(R.id.gotogit);
        GoToGit.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender"))));
    }
}
