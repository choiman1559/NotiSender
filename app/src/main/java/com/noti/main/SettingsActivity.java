package com.noti.main;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import com.noti.main.ui.options.MainPreference;
import com.noti.main.utils.BillingHelper;

import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    @SuppressLint("StaticFieldLeak")
    public static BillingHelper mBillingHelper;

    @SuppressLint("StaticFieldLeak")
    public static Fragment mFragment;

    @SuppressLint("BatteryLife")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);
        if (Build.VERSION.SDK_INT > 28 && !Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder alert_confirm = new MaterialAlertDialogBuilder(this);
            alert_confirm.setMessage("You need to permit overlay permission to use this app")
                    .setCancelable(false).setPositiveButton("Bring me there",
                    (dialog, which) -> startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 101)).setNegativeButton("Cancel",
                    (dialog, which) -> finish());
            alert_confirm.create().show();
        }

        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
        if (!sets.contains(getPackageName())) {
            MaterialAlertDialogBuilder alert_confirm = new MaterialAlertDialogBuilder(this);
            alert_confirm.setMessage("You need to permit Alarm access permission to use this app")
                    .setCancelable(false).setPositiveButton("Bring me there",
                    (dialog, which) -> startActivityForResult(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            , 102)).setNegativeButton("Cancel",
                    (dialog, which) -> finish());
            alert_confirm.create().show();
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT > 22 && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            MaterialAlertDialogBuilder alert_confirm = new MaterialAlertDialogBuilder(this);
            alert_confirm.setMessage("You need to permit ignore battery optimizations permission to use this app")
                    .setCancelable(false).setPositiveButton("Bring me there",
                    (dialog, which) -> startActivityForResult(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + getPackageName()))
                            , 103)).setNegativeButton("Cancel",
                    (dialog, which) -> finish());
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBillingHelper.Destroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case 101:
                if (Build.VERSION.SDK_INT > 28 && !Settings.canDrawOverlays(this)) finish();
                break;

            case 102:
                Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
                if (!sets.contains(getPackageName())) finish();
                break;

            case 103:
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (Build.VERSION.SDK_INT > 22 && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    finish();
                }
                break;
        }
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
}