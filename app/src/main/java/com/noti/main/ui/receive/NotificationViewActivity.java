package com.noti.main.ui.receive;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.service.BitmapIPCManager;
import com.noti.main.service.NotiListenerService;
import com.noti.main.R;

import org.json.JSONException;
import org.json.JSONObject;

public class NotificationViewActivity extends Activity {

    Bitmap icon;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notidetail);
        setFinishOnTouchOutside(false);

        Intent intent = getIntent();
        String Package = intent.getStringExtra("package");
        icon = BitmapIPCManager.getInstance().getBitmap(intent.getIntExtra("bitmapId", -1));

        MaterialButton OK = findViewById(R.id.ok);
        MaterialButton NO = findViewById(R.id.cancel);
        ImageView ICON = findViewById(R.id.iconView);
        TextView NAME = findViewById(R.id.titleDetail);
        TextView DETAIL = findViewById(R.id.contentDetail);

        String APP_NAME = intent.getStringExtra("appname");
        String TITLE = intent.getStringExtra("title");
        String DEVICE_NAME = intent.getStringExtra("device_name");
        String DEVICE_ID = intent.getStringExtra("device_id");
        String DATE = intent.getStringExtra("date");
        String KEY = intent.getStringExtra("notification_key");

        String detail = "";
        detail += "Noti Title  : " + TITLE + "\n";
        detail += "Device      : " + DEVICE_NAME + "\n";
        detail += "Posted Time : " + DATE + "\n";

        if(icon != null) ICON.setImageBitmap(icon);
        else ICON.setVisibility(View.GONE);
        NAME.setText(APP_NAME);
        DETAIL.setText(detail);

        OK.setOnClickListener(v -> {
            receptionNotification(this, Package, DEVICE_NAME, DEVICE_ID, KEY, true);
            ExitActivity.exitApplication(this);
        });

        NO.setOnClickListener(v -> {
            receptionNotification(this, DEVICE_NAME, DEVICE_ID, KEY, false);
            ExitActivity.exitApplication(this);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (icon != null) icon.recycle();
    }

    public static void receptionNotification(Context context, String DEVICE_NAME, String DEVICE_ID, String KEY, boolean isNeedToStartRemotely) {
        receptionNotification(context, null, DEVICE_NAME, DEVICE_ID, KEY, isNeedToStartRemotely);
    }

    public static void receptionNotification(Context context, @Nullable String Package, String DEVICE_NAME, String DEVICE_ID, String KEY, boolean isNeedToStartRemotely) {
        SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME,MODE_PRIVATE);
        boolean isDismiss = prefs.getBoolean("RemoteDismiss", false);
        if(!(isDismiss || isNeedToStartRemotely)) return;
        JSONObject notificationBody = new JSONObject();

        try {
            if(Package != null) notificationBody.put("package", Package);
            if(isDismiss) notificationBody.put("notification_key", KEY);

            notificationBody.put("type","reception|normal");
            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("send_device_name", DEVICE_NAME);
            notificationBody.put("send_device_id", DEVICE_ID);
            notificationBody.put("start_remote_activity", isNeedToStartRemotely ? "true" : "false");
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage() );
        }

        if(BuildConfig.DEBUG) Log.d("data-receive", notificationBody.toString());
        NotiListenerService.sendNotification(notificationBody, Package, context);
    }
}