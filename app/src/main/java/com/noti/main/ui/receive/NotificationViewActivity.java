package com.noti.main.ui.receive;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.noti.main.service.BitmapIPCManager;
import com.noti.main.R;
import com.noti.main.service.mirnoti.NotificationsData;
import com.noti.main.service.mirnoti.NotificationRequest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class NotificationViewActivity extends Activity {

    Bitmap icon;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notidetail);
        setFinishOnTouchOutside(false);
        Intent intent = getIntent();

        MaterialButton OK = findViewById(R.id.ok);
        MaterialButton NO = findViewById(R.id.cancel);
        ImageView ICON = findViewById(R.id.iconView);
        TextView NAME = findViewById(R.id.titleDetail);
        TextView DETAIL = findViewById(R.id.contentDetail);

        String DEVICE_NAME = intent.getStringExtra("device_name");
        String DEVICE_ID = intent.getStringExtra("device_id");
        String KEY, Package;

        if(intent.hasExtra(NotificationRequest.KEY_NOTIFICATION_KEY)) {
            NotificationsData notificationsData = (NotificationsData) BitmapIPCManager.getInstance().getSerialize(intent.getIntExtra(NotificationRequest.KEY_NOTIFICATION_KEY, 0));
            KEY = Objects.requireNonNull(notificationsData).key;
            Package = notificationsData.appPackage;

            String detail = "";
            detail += "Noti Title  : " + notificationsData.title + "\n";
            detail += "Device      : " + DEVICE_NAME + "\n";
            detail += "Posted Time : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(notificationsData.postTime)) + "\n";

            icon = notificationsData.getBigIcon();
            if(icon != null) ICON.setImageBitmap(icon);
            else ICON.setVisibility(View.GONE);
            NAME.setText(notificationsData.appName);
            DETAIL.setText(detail);
        } else {
            String APP_NAME = intent.getStringExtra("appname");
            String TITLE = intent.getStringExtra("title");
            String DATE = intent.getStringExtra("date");

            KEY = intent.getStringExtra("notification_key");
            Package = intent.getStringExtra("package");

            String detail = "";
            detail += "Noti Title  : " + TITLE + "\n";
            detail += "Device      : " + DEVICE_NAME + "\n";
            detail += "Posted Time : " + DATE + "\n";

            icon = BitmapIPCManager.getInstance().getBitmap(intent.getIntExtra("bitmapId", -1));
            if(icon != null) ICON.setImageBitmap(icon);
            else ICON.setVisibility(View.GONE);
            NAME.setText(APP_NAME);
            DETAIL.setText(detail);
        }

        OK.setOnClickListener(v -> {
            NotificationRequest.receptionNotification(this, Package, DEVICE_NAME, DEVICE_ID, KEY, true);
            ExitActivity.exitApplication(this);
        });

        NO.setOnClickListener(v -> {
            NotificationRequest.receptionNotification(this, DEVICE_NAME, DEVICE_ID, KEY, false);
            ExitActivity.exitApplication(this);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (icon != null) icon.recycle();
    }
}