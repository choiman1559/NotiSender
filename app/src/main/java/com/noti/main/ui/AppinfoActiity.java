package com.noti.main.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.noti.main.R;

public class AppinfoActiity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appinfo_actiity);

        TextView version = findViewById(R.id.version);
        try {
            PackageManager pm =  getPackageManager();
            String LV = pm.getPackageInfo("com.noti.main", 0).versionName;
            String MV = pm.getPackageInfo("com.noti.sender", 0).versionName;
            String Value = "Version\n";
            Value += "Manager : " + MV + "\n";
            Value += "Main app : " + LV + "\n\n";
            Value += "this app is free-softwear under the GNU LGPL 3.0 license.";
            version.setText(Value);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Button GoToGit = findViewById(R.id.gotogit);
        GoToGit.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Select Package")
                    .setMessage("Select project that you wants to go")
                    .setPositiveButton("Main App",(d,w) -> startActivity("https://github.com/choiman1559/NotiSender"))
                    .setNegativeButton("Manager",(d,w) -> startActivity("https://github.com/choiman1559/NotiSender-manager"))
                    .setNeutralButton("Close",(d,w) -> { })
                    .show();
        });
    }

    void startActivity(String value) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value)));
    }
}
