package com.noti.main.ui.pair;

import static com.noti.main.service.NotiListenerService.getUniqueID;
import static com.noti.main.service.NotiListenerService.sendNotification;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.service.pair.DataProcess;
import com.noti.main.service.pair.PairDeviceType;
import com.noti.main.service.pair.PairListener;
import com.noti.main.utils.ui.ToastHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class PairDetailActivity extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair_detail);
        
        Intent intent = getIntent();
        String Device_name = intent.getStringExtra("device_name");
        String Device_id = intent.getStringExtra("device_id");
        String Device_type = intent.getStringExtra("device_type");
        SharedPreferences prefs = getSharedPreferences("com.noti.main_pair",MODE_PRIVATE);
        SharedPreferences deviceBlacksPrefs = getSharedPreferences("com.noti.main_device.blacklist", MODE_PRIVATE);

        ImageView icon = findViewById(R.id.icon);
        ImageView batteryIcon = findViewById(R.id.batteryIcon);
        TextView deviceName = findViewById(R.id.deviceName);
        TextView deviceIdInfo = findViewById(R.id.deviceIdInfo);
        TextView batteryDetail = findViewById(R.id.batteryDetail);
        Button forgetButton = findViewById(R.id.forgetButton);
        Button findButton = findViewById(R.id.findButton);
        SwitchMaterial blockToggle = findViewById(R.id.switchCompat);

        LinearLayout batterySaveEnabled = findViewById(R.id.batterySaveEnabled);
        LinearLayout batteryLayout = findViewById(R.id.batteryLayout);
        LinearLayout deviceBlackListLayout = findViewById(R.id.deviceBlackListLayout);
        LinearLayout remotePresentation = findViewById(R.id.remotePresentation);

        boolean isBlocked = deviceBlacksPrefs.getBoolean(Device_id, false);
        blockToggle.setChecked(isBlocked);
        blockToggle.setOnCheckedChangeListener((buttonView, isChecked) -> deviceBlacksPrefs.edit().putBoolean(Device_id, isChecked).apply());
        deviceBlackListLayout.setOnClickListener((v) -> blockToggle.setChecked(!blockToggle.isChecked()));

        String[] colorLow = getResources().getStringArray(R.array.material_color_low);
        String[] colorHigh = getResources().getStringArray(R.array.material_color_high);
        int randomIndex = new Random(Device_name.hashCode()).nextInt(colorHigh.length);

        if(Device_type != null) icon.setImageResource(new PairDeviceType(Device_type).getDeviceTypeBitmap());
        icon.setImageTintList(ColorStateList.valueOf(Color.parseColor(colorHigh[randomIndex])));
        icon.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(colorLow[randomIndex])));
        deviceName.setText(Device_name);
        deviceIdInfo.setText("Device's unique address: " + Device_id);

        remotePresentation.setVisibility(Objects.equals(Device_type, PairDeviceType.DEVICE_TYPE_DESKTOP) || Objects.equals(Device_type, PairDeviceType.DEVICE_TYPE_LAPTOP) ? View.VISIBLE : View.GONE);
        remotePresentation.setOnClickListener(v -> {
            Intent presentationIntent = new Intent(PairDetailActivity.this, PresentationActivity.class);
            presentationIntent.putExtra("device_name", Device_name);
            presentationIntent.putExtra("device_id", Device_id);
            presentationIntent.putExtra("device_type", Device_type);
            startActivity(presentationIntent);
        });

        forgetButton.setOnClickListener(v -> {
            String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
            String DEVICE_ID = getUniqueID();
            String TOPIC = "/topics/" + getSharedPreferences(Application.PREFS_NAME,MODE_PRIVATE).getString("UID", "");

            JSONObject notificationHead = new JSONObject();
            JSONObject notificationBody = new JSONObject();
            try {
                notificationBody.put("type", "pair|request_remove");
                notificationBody.put("device_name", DEVICE_NAME);
                notificationBody.put("device_id", DEVICE_ID);
                notificationBody.put("send_device_name", Device_name);
                notificationBody.put("send_device_id", Device_id);

                notificationHead.put("to", TOPIC);
                notificationHead.put("data", notificationBody);
            } catch (JSONException e) {
                Log.e("Noti", "onCreate: " + e.getMessage());
            }

            sendNotification(notificationHead, getPackageName(), this);
            Set<String> list = new HashSet<>(prefs.getStringSet("paired_list", new HashSet<>()));
            if(Device_type == null) list.remove(Device_name + "|" + Device_id);
            else list.remove(Device_name + "|" + Device_id + "|" + Device_type);
            prefs.edit().putStringSet("paired_list", list).apply();

            finish();
        });

        findButton.setOnClickListener(v -> {
            Date date = Calendar.getInstance().getTime();
            String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
            String DEVICE_ID = getUniqueID();
            String TOPIC = "/topics/" + getSharedPreferences(Application.PREFS_NAME,MODE_PRIVATE).getString("UID", "");

            JSONObject notificationHead = new JSONObject();
            JSONObject notificationBody = new JSONObject();
            try {
                notificationBody.put("type", "pair|find");
                notificationBody.put("device_name", DEVICE_NAME);
                notificationBody.put("device_id", DEVICE_ID);
                notificationBody.put("send_device_name", Device_name);
                notificationBody.put("send_device_id", Device_id);
                notificationBody.put("date", date);

                notificationHead.put("to", TOPIC);
                notificationHead.put("data", notificationBody);
            } catch (JSONException e) {
                Log.e("Noti", "onCreate: " + e.getMessage());
            }

            sendNotification(notificationHead, getPackageName(), this);
            ToastHelper.show(this, "Your request is posted!","OK", ToastHelper.LENGTH_SHORT);
        });

        DataProcess.requestData(this, Device_name, Device_id, "battery_info");
        PairListener.addOnDataReceivedListener(map -> {
            if(Objects.equals(map.get("request_data"), "battery_info") &&
                    Objects.equals(map.get("device_name"), Device_name) &&
                    Objects.equals(map.get("device_id"), Device_id)) {
                String[] data = Objects.requireNonNull(map.get("receive_data")).split("\\|");
                int batteryInt = Integer.parseInt(data[0].split("\\.")[0]);
                int resId = R.drawable.ic_fluent_battery_warning_24_regular;

                if(batteryInt < 10) resId = R.drawable.ic_fluent_battery_0_24_regular;
                else if(batteryInt < 20) resId = R.drawable.ic_fluent_battery_1_24_regular;
                else if(batteryInt < 30) resId = R.drawable.ic_fluent_battery_2_24_regular;
                else if(batteryInt < 40) resId = R.drawable.ic_fluent_battery_3_24_regular;
                else if(batteryInt < 50) resId = R.drawable.ic_fluent_battery_4_24_regular;
                else if(batteryInt < 60) resId = R.drawable.ic_fluent_battery_5_24_regular;
                else if(batteryInt < 70) resId = R.drawable.ic_fluent_battery_6_24_regular;
                else if(batteryInt < 80) resId = R.drawable.ic_fluent_battery_7_24_regular;
                else if(batteryInt < 90) resId = R.drawable.ic_fluent_battery_8_24_regular;
                else if(batteryInt < 100) resId = R.drawable.ic_fluent_battery_9_24_regular;
                else if(batteryInt == 100) resId = R.drawable.ic_fluent_battery_10_24_regular;

                int finalResId = resId;
                PairDetailActivity.this.runOnUiThread(() -> {
                    if(data[2].equals("true")) batterySaveEnabled.setVisibility(View.VISIBLE);
                    batteryLayout.setVisibility(View.VISIBLE);
                    batteryDetail.setText(batteryInt + "% remaining" + (data[1].equals("true") ? ", Charging" : ""));
                    batteryIcon.setImageDrawable(AppCompatResources.getDrawable(PairDetailActivity.this, finalResId));
                });
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }
}
