package com.noti.main.updater;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.noti.main.R;
import com.noti.main.SettingsActivity;
import com.noti.main.updater.tasks.GetPlayVersion;
import com.noti.main.updater.tasks.GetGithubVersion;
import com.noti.main.utils.DetectAppSource;

public class UpdaterActivity extends AppCompatActivity {

    private static boolean isActivityRunning = false;
    public static boolean isErrorOccurred = false;
    ActivityResultLauncher<Intent> startDeleteActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> init());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isActivityRunning = true;
        setContentView(R.layout.activity_updater);
        if(isManagerInstalled(this)) {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setTitle(this.getString(R.string.updater_manager_deprecated_title))
                    .setMessage(this.getString(R.string.updater_manager_deprecated_message))
                    .setNegativeButton("No Thanks", (d, w) -> {
                        startMainActivity(this);
                        this.finish();
                    })
                    .setPositiveButton("Delete", (d, w) -> startDeleteActivity.launch(new Intent(Intent.ACTION_DELETE).setData(Uri.parse("package:com.noti.sender"))))
                    .show();
        } else init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityRunning = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityRunning = false;
    }

    private void init() {
        TextView status1 = findViewById(R.id.status1);
        ProgressBar status2 = findViewById(R.id.status2);

        if (isOnline()) {
            status1.setText(R.string.updater_checkupdate);
            status2.setIndeterminate(true);
            SharedPreferences prefs = this.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
            switch (prefs.getString("UpdateChannel","Automatically specified")) {
                case "Github":
                    new GetGithubVersion(this).execute();
                    startDelayCheckThread(this);
                    break;

                case "Play Store":
                    new GetPlayVersion(this).execute();
                    startDelayCheckThread(this);
                    break;

                case "Do not check update":
                    startMainActivity(this);
                    this.finish();
                    break;

                default:
                    int Source = DetectAppSource.detectSource(this);
                    switch (Source) {
                        case 1:
                            Toast.makeText(this,"Run as debug build!", Toast.LENGTH_SHORT).show();
                            startMainActivity(this);
                            this.finish();
                            break;

                        case 2:
                            new GetGithubVersion(this).execute();
                            break;

                        case 3:
                            new GetPlayVersion(this).execute();
                            break;

                        default:
                            Toast.makeText(this,"Unable to check SHA-1 Hash. skipping check update...", Toast.LENGTH_SHORT).show();
                            startMainActivity(this);
                            this.finish();
                            break;
                    }
                    break;
            }
        } else {
            startMainActivity(this);
            this.finish();
        }
    }

    public static void startDelayCheckThread(AppCompatActivity context) {
        final long startTime = System.currentTimeMillis();
        new Thread(() -> {
            while(true) {
                if(System.currentTimeMillis() - startTime > 10000L) {
                    if(isActivityRunning && !isErrorOccurred) {
                        Snackbar.make(context, context.findViewById(R.id.layout), "There is a delay in checking for updates.",Snackbar.LENGTH_INDEFINITE)
                                .setAction("Skip", v -> startMainActivity(context)).show();
                    }
                    break;
                }
            }
        }).start();
    }

    public static void startMainActivity(Context context) {
        Intent intent = new Intent(context, SettingsActivity.class);
        context.startActivity(intent);
    }

    public static boolean isManagerInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean app_installed;
        try {
            pm.getPackageInfo("com.noti.sender", PackageManager.GET_ACTIVITIES);
            app_installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            app_installed = false;
        }
        return app_installed;
    }

    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        assert cm != null;
        if (cm.getActiveNetworkInfo() != null) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }
        return false;
    }
}