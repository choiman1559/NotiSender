package com.noti.main.ui.pair.action;


import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.noti.main.R;
import com.noti.main.service.pair.DataProcess;
import com.noti.main.service.pair.PairDeviceType;

import java.util.Random;

public class PresentationActivity extends AppCompatActivity {
    public static final String ACTION_NAME = "PRESENTATION_KEY_PRESSED";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair_presentation);

        Intent intent = getIntent();
        String Device_name = intent.getStringExtra("device_name");
        String Device_id = intent.getStringExtra("device_id");
        String Device_type = intent.getStringExtra("device_type");

        Button finishButton = findViewById(R.id.finishButton);
        Button startButton = findViewById(R.id.startButton);
        Button previousButton = findViewById(R.id.previousButton);
        Button nextButton = findViewById(R.id.nextButton);

        ImageView deviceIcon = findViewById(R.id.icon);
        TextView deviceName = findViewById(R.id.deviceName);

        finishButton.setOnClickListener(v -> DataProcess.requestAction(PresentationActivity.this, Device_name, Device_id, ACTION_NAME, "escape"));
        startButton.setOnClickListener(v -> DataProcess.requestAction(PresentationActivity.this, Device_name, Device_id, ACTION_NAME, "f5"));
        previousButton.setOnClickListener(v -> DataProcess.requestAction(PresentationActivity.this, Device_name, Device_id, ACTION_NAME, "left"));
        nextButton.setOnClickListener(v -> DataProcess.requestAction(PresentationActivity.this, Device_name, Device_id, ACTION_NAME, "right"));

        if(Device_type != null) deviceIcon.setImageResource(new PairDeviceType(Device_type).getDeviceTypeBitmap());
        String[] colorLow = getResources().getStringArray(R.array.material_color_low);
        String[] colorHigh = getResources().getStringArray(R.array.material_color_high);
        int randomIndex = new Random(Device_name.hashCode()).nextInt(colorHigh.length);

        deviceIcon.setImageTintList(ColorStateList.valueOf(Color.parseColor(colorHigh[randomIndex])));
        deviceIcon.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(colorLow[randomIndex])));
        deviceName.setText(Device_name);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }
}