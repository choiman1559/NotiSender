package com.noti.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.installations.FirebaseInstallations;
import com.noti.main.ui.SettingsActivity;
import com.noti.main.utils.ui.ToastHelper;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class StartActivity extends AppCompatActivity {

    SharedPreferences prefs;
    ExtendedFloatingActionButton Start_App;
    MaterialButton Permit_Notification;
    MaterialButton Permit_Overlay;
    MaterialButton Permit_Battery;
    MaterialButton Permit_File;
    MaterialButton Permit_Location;
    MaterialButton Permit_Background_Location;
    MaterialButton Permit_Alarm;
    MaterialButton Permit_Privacy;
    MaterialButton Permit_Collect_Data;
    MaterialCheckBox Skip_Alarm;

    ActivityResultLauncher<Intent> startOverlayPermit = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if(Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(this)) {
            setButtonCompleted(this, Permit_Overlay);
            checkPermissionsAndEnableComplete();
        }
    });

    ActivityResultLauncher<Intent> startBatteryOptimizations = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(getPackageName())) {
            setButtonCompleted(this, Permit_Battery);
            checkPermissionsAndEnableComplete();
        }
    });

    ActivityResultLauncher<Intent> startAlarmAccessPermit = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
        if (sets.contains(getPackageName())) {
            setButtonCompleted(this, Permit_Alarm);
            checkPermissionsAndEnableComplete();
        }
    });

    ActivityResultLauncher<String[]> locationPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
            Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION,false);

            if((fineLocationGranted != null && fineLocationGranted) || (coarseLocationGranted != null && coarseLocationGranted)) {
                setButtonCompleted(this, Permit_Location);
                checkPermissionsAndEnableComplete();
            }
        }
    });

    ActivityResultLauncher<String> backgroundLocationPermissionRequest = registerForActivityResult(new ActivityResultContracts.RequestPermission(), fineLocationGranted -> {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if(fineLocationGranted != null && fineLocationGranted) {
                setButtonCompleted(this, Permit_Background_Location);
                checkPermissionsAndEnableComplete();
            }
        }
    });

    @SuppressLint({"BatteryLife", "HardwareIds"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        boolean isTelevisionsEnabled = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;

        prefs = getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        Permit_Notification = findViewById(R.id.Permit_Notification);
        Permit_Overlay = findViewById(R.id.Permit_Overlay);
        Permit_Battery = findViewById(R.id.Permit_Battery);
        Permit_File = findViewById(R.id.Permit_File);
        Permit_Location = findViewById(R.id.Permit_Location);
        Permit_Background_Location = findViewById(R.id.Permit_Background_Location);
        Permit_Alarm = findViewById(R.id.Permit_Alarm);
        Permit_Privacy = findViewById(R.id.Permit_Privacy);
        Permit_Collect_Data = findViewById(R.id.Permit_Collect_Data);
        Skip_Alarm = findViewById(R.id.Skip_Alarm);
        Start_App = findViewById(R.id.Start_App);

        int count = 0;

        if(Build.VERSION.SDK_INT < 31 || ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled()) {
            setButtonCompleted(this, Permit_Notification);
            count++;
        }

        if(Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(this)) {
            setButtonCompleted(this, Permit_Overlay);
            count++;
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT < 23 || isTelevisionsEnabled || pm.isIgnoringBatteryOptimizations(getPackageName())) {
            setButtonCompleted(this, Permit_Battery);
            count++;
        }

        if(Build.VERSION.SDK_INT > 28 || (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            setButtonCompleted(this, Permit_File);
            count++;
        }

        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
        if (isTelevisionsEnabled || sets.contains(getPackageName())) {
            setButtonCompleted(this, Permit_Alarm);
            count++;
        }

        boolean isFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean isCoarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if(isCoarseLocation || isFineLocation) {
            setButtonCompleted(this, Permit_Location);
            count++;
        }

        if(Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            setButtonCompleted(this, Permit_Background_Location);
            count++;
        }

        if(prefs.getBoolean("SkipAlarmAccessPermission", false)) {
            Skip_Alarm.setChecked(true);
            count++;
        }

        if(prefs.getBoolean("AcceptedPrivacyPolicy", false)) {
            setButtonCompleted(this, Permit_Privacy);
            count++;
        }

        if(prefs.getBoolean("AcceptedDataCollection", false)) {
            setButtonCompleted(this, Permit_Collect_Data);
            count++;
        }

        if(prefs.getString("FirebaseIIDPrefix", "").isEmpty()) {
            FirebaseInstallations.getInstance().getId().addOnCompleteListener(task -> {
                if (task.isSuccessful())
                    prefs.edit().putString("FirebaseIIDPrefix", task.getResult()).apply();
            });
        }

        if(prefs.getString("AndroidIDPrefix", "").isEmpty()) {
            prefs.edit().putString("AndroidIDPrefix", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID)).apply();
        }

        if(prefs.getString("GUIDPrefix", "").isEmpty()) {
            prefs.edit().putString("GUIDPrefix", UUID.randomUUID().toString()).apply();
        }

        if(prefs.getString("MacIDPrefix", "").isEmpty()) {
            String interfaceName = "wlan0";
            try {
                List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface intf : interfaces) {
                    if (!intf.getName().equalsIgnoreCase(interfaceName)) continue;
                    byte[] mac = intf.getHardwareAddress();
                    if (mac == null) {
                        prefs.edit().putString("MacIDPrefix","unknown").apply();
                        break;
                    }
                    StringBuilder buf = new StringBuilder();
                    for (byte b : mac) buf.append(String.format("%02X:", b));
                    if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
                    prefs.edit().putString("MacIDPrefix",buf.toString()).apply();
                    break;
                }
            } catch (Exception e) {
                prefs.edit().putString("MacIDPrefix","unknown").apply();
            }
        }

        if(count >= 9) {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        }

        Permit_Notification.setOnClickListener((v) -> ActivityCompat.requestPermissions(this, new String[] { "android.permission.POST_NOTIFICATIONS" }, 100));
        Permit_Overlay.setOnClickListener((v) -> {
            if (Build.VERSION.SDK_INT > 22) startOverlayPermit.launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        Permit_Battery.setOnClickListener((v) -> {
            if (Build.VERSION.SDK_INT > 22) startBatteryOptimizations.launch(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + getPackageName())));
        });
        Permit_Alarm.setOnClickListener((v) -> startAlarmAccessPermit.launch(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        Permit_File.setOnClickListener((v) -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101));
        Permit_Location.setOnClickListener((v) -> locationPermissionRequest.launch(new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}));
        Permit_Background_Location.setOnClickListener((v) -> {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Background Location");
            dialog.setMessage("""
                    Please allow background location permission to remotely locate this device from other devices.

                    On the screen that appears, select “Always Allow” to enable background location permissions.""");
            dialog.setPositiveButton("Permit", (dialog12, which) -> {
                if(Permit_Location.isEnabled()) {
                    ToastHelper.show(this, "You should permit location permission first!", "OK", ToastHelper.LENGTH_SHORT);
                } else if(Build.VERSION.SDK_INT >= 29) {
                    backgroundLocationPermissionRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                }
            });
            dialog.show();
        });
        Permit_Privacy.setOnClickListener((v) -> {
            RelativeLayout layout = (RelativeLayout) LayoutInflater.from(this).inflate(R.layout.dialog_privacy, null,false);
            WebView webView = layout.findViewById(R.id.webView);
            webView.loadUrl("file:///android_asset/privacy_policy.html");
            Button acceptButton = layout.findViewById(R.id.acceptButton);
            Button denyButton = layout.findViewById(R.id.denyButton);

            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Privacy Policy");
            dialog.setMessage(Html.fromHtml("You need to accept <a href=\"https://github.com/choiman1559/NotiSender/blob/master/PrivacyPolicy\">Privacy Policy</a> to use this app."));
            dialog.setView(layout);
            dialog.setIcon(R.drawable.ic_fluent_inprivate_account_24_regular);

            AlertDialog alertDialog = dialog.show();
            ((TextView) Objects.requireNonNull(alertDialog.findViewById(android.R.id.message))).setMovementMethod(LinkMovementMethod.getInstance());

            acceptButton.setOnClickListener((view) -> {
                prefs.edit().putBoolean("AcceptedPrivacyPolicy", true).apply();
                setButtonCompleted(this, Permit_Privacy);
                checkPermissionsAndEnableComplete();
                alertDialog.dismiss();
            });
            denyButton.setOnClickListener((view) -> alertDialog.dismiss());
        });
        Permit_Collect_Data.setOnClickListener((v) -> {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Data Collect Agreement");
            dialog.setMessage("Noti Sender reads the user's SMS information for synchronization between devices, even when the app is closed or not in use.");
            dialog.setIcon(R.drawable.ic_fluent_database_search_24_regular);
            dialog.setPositiveButton("Agree", (dialog1, which) -> {
                prefs.edit().putBoolean("AcceptedDataCollection", true).apply();
                setButtonCompleted(this, Permit_Collect_Data);
                checkPermissionsAndEnableComplete();
            });
            dialog.setNegativeButton("Deny", (dialog1, which) -> { });
            dialog.show();
        });
        Skip_Alarm.setOnCheckedChangeListener((compoundButton, b) -> {
            checkPermissionsAndEnableComplete();
            prefs.edit().putBoolean("SkipAlarmAccessPermission", b).apply();
        });
        Start_App.setOnClickListener((v) -> {
            boolean isBadCondition = Permit_Notification.isEnabled();

            if(Permit_Battery.isEnabled()) {
                isBadCondition = true;
            }

            if(Permit_File.isEnabled()) {
                isBadCondition = true;
            }

            if(Permit_Overlay.isEnabled()) {
                isBadCondition = true;
            }

            if(Permit_Alarm.isEnabled() && !Skip_Alarm.isChecked()) {
                isBadCondition = true;
            }

            if(Permit_Privacy.isEnabled()) {
                isBadCondition = true;
            }

            if(Permit_Location.isEnabled()) {
                isBadCondition = true;
            }

            if(Permit_Background_Location.isEnabled()) {
                isBadCondition = true;
            }

            if(isBadCondition) {
                ToastHelper.show(this, "Please complete all section!", ToastHelper.LENGTH_SHORT);
            } else {
                startActivity(new Intent(this, SettingsActivity.class));
                finish();
            }
        });
    }

    void setButtonCompleted(Context context, MaterialButton button) {
        button.setEnabled(false);
        button.setText(context.getString(R.string.Start_Activity_Completed));
        button.setIcon(AppCompatResources.getDrawable(context, R.drawable.ic_fluent_checkmark_24_regular));
    }

    void checkPermissionsAndEnableComplete() {
        Start_App.setEnabled(!Permit_Notification.isEnabled() && !Permit_Battery.isEnabled() && !Permit_File.isEnabled() && !Permit_Overlay.isEnabled() && (!Permit_Alarm.isEnabled() || Skip_Alarm.isChecked()) && !Permit_Privacy.isEnabled() && !Permit_Collect_Data.isEnabled());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int foo : grantResults) {
            if (foo == PackageManager.PERMISSION_GRANTED) {
                if(requestCode == 100) setButtonCompleted(this, Permit_Notification);
                else if(requestCode == 101) setButtonCompleted(this, Permit_File);
                checkPermissionsAndEnableComplete();
            }
        }
    }
}