package com.noti.main.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.noti.main.R;
import com.noti.main.utils.DetectAppSource;

public class AppInfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(Build.VERSION.SDK_INT > 25 ? R.layout.activity_appinfo : R.layout.activity_appinfo_v25);

        if(Build.VERSION.SDK_INT <= 25) {
            TextView OSS = findViewById(R.id.oss);
            OSS.setText(R.string.ossl);
        }

        TextView version = findViewById(R.id.version);
        try {
            PackageManager pm =  getPackageManager();
            String LV = pm.getPackageInfo(getPackageName(), 0).versionName;
            int AppSource = DetectAppSource.detectSource(this);
            String Source;

            switch (AppSource) {
                case 1:
                    Source = "Debug build";
                    break;

                case 2:
                    Source = "Github";
                    break;

                case 3:
                    Source = "Play Store";
                    break;

                default:
                    Source = "Other";
                    break;
            }

            String Value = "Version : " + LV + "\n";
            Value += "Downloaded from : " + Source + "\n\n";
            Value += "this app is free-software under the GNU LGPL 3.0 license.";
            version.setText(Value);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Button GoToGit = findViewById(R.id.gotogit);
        GoToGit.setOnClickListener(v -> startActivity());

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }

    void startActivity() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender")));
    }
}
