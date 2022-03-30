package com.noti.main;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import com.noti.main.ui.options.MainPreference;
import com.noti.main.utils.BillingHelper;

import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    @SuppressLint("StaticFieldLeak")
    public static BillingHelper mBillingHelper;

    @SuppressLint("StaticFieldLeak")
    public static Fragment mFragment;

    ActivityResultLauncher<Intent> startOverlayPermit = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (Build.VERSION.SDK_INT > 28 && !Settings.canDrawOverlays(this)) finish();
    });

    ActivityResultLauncher<Intent> startAlarmAccessPermit = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
        if (!sets.contains(getPackageName())) finish();
    });

    ActivityResultLauncher<Intent> startBatteryOptimizations = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT > 22 && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            finish();
        }
    });

    @SuppressLint("BatteryLife")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);



        Log.d("ver", Build.VERSION.SDK_INT + "");
        if(Build.VERSION.SDK_INT > 31 && !((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled()) {
            requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 100);
        }

        if (Build.VERSION.SDK_INT > 28 && !Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder alert_confirm = new MaterialAlertDialogBuilder(this);
            alert_confirm.setMessage("You need to permit overlay permission to use this app")
                    .setCancelable(false).setPositiveButton("Bring me there",
                    (dialog, which) -> startOverlayPermit.launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))).setNegativeButton("Cancel",
                    (dialog, which) -> finish());
            alert_confirm.create().show();
        }

        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
        if (!sets.contains(getPackageName())) {
            MaterialAlertDialogBuilder alert_confirm = new MaterialAlertDialogBuilder(this);
            alert_confirm.setMessage("You need to permit Alarm access permission to use this app")
                    .setCancelable(false).setPositiveButton("Bring me there", (dialog, which) -> startAlarmAccessPermit.launch(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")))
                    .setNegativeButton("Cancel", (dialog, which) -> finish());
            alert_confirm.create().show();
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT > 22 && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            MaterialAlertDialogBuilder alert_confirm = new MaterialAlertDialogBuilder(this);
            alert_confirm.setMessage("You need to permit ignore battery optimizations permission to use this app")
                    .setCancelable(false).setPositiveButton("Bring me there", (dialog, which) -> startBatteryOptimizations.launch(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("Cancel", (dialog, which) -> finish());
            alert_confirm.create().show();
        }

        if (savedInstanceState == null) {
            attachFragment(this, new MainPreference());
        } else {
            mFragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);
            if(mFragment != null) attachFragment(this, mFragment);
            else attachFragment(this, mFragment = new MainPreference());
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        //Christmas Event!!!
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        int date = calendar.get(Calendar.DATE);
        if(calendar.get(Calendar.MONTH) == Calendar.DECEMBER && date < 26 && date > 17) {
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setNavigationIcon(R.drawable.hat);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBillingHelper.Destroy();
    }

    public static void attachFragment(FragmentActivity activity, Fragment fragment) {
        Bundle bundle = new Bundle(0);
        fragment.setArguments(bundle);

        SettingsActivity.mFragment = fragment;
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, SettingsActivity.mFragment)
                .commit();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 100) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if(Build.VERSION.SDK_INT > 31 && !notificationManager.areNotificationsEnabled()) {
                Toast.makeText(getApplicationContext(), "Notification permission is needed!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}