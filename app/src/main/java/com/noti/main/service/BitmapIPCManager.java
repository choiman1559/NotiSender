package com.noti.main.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.Nullable;

import com.noti.main.service.mirnoti.NotificationRequest;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class BitmapIPCManager {
    private static BitmapIPCManager instance;
    private final Map<Integer, Bitmap> bitmapMap;
    private final Map<Integer, Serializable> serializableMap;

    private BitmapIPCManager() {
        bitmapMap = new HashMap<>();
        serializableMap = new HashMap<>();
    }

    public static BitmapIPCManager getInstance() {
        if(instance == null) instance = new BitmapIPCManager();
        return instance;
    }

    public void addBitmap(Integer id, @Nullable Bitmap bitmap) {
        if(bitmap == null) return;
        bitmapMap.put(id, bitmap);
    }

    public void addSerialize(Integer id, @Nullable Serializable serializable) {
        if(serializable == null) return;
        serializableMap.put(id, serializable);
    }

    @Nullable
    public Bitmap getBitmap(Integer id) {
        if(id == -1 || bitmapMap.isEmpty()) return null;

        Bitmap bitmap = bitmapMap.get(id);
        bitmapMap.remove(id);
        return bitmap;
    }

    @Nullable
    public Serializable getSerialize(Integer id) {
        if(serializableMap.isEmpty()) return null;

        Serializable serializable = serializableMap.get(id);
        serializableMap.remove(id);
        return serializable;
    }

    private void dismissBitmap(Integer id) {
        if(bitmapMap.containsKey(id)) {
            Bitmap bitmap = bitmapMap.get(id);
            bitmapMap.remove(id);
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    public static class BitmapDismissBroadcastListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.hasExtra(NotificationRequest.KEY_NOTIFICATION_KEY)) {
                int id = intent.getIntExtra(NotificationRequest.KEY_NOTIFICATION_KEY, -1);
                if(id!= -1) {
                    Log.d("BitmapIPCManager", "Recycling unused serialize Id: " + id);
                    BitmapIPCManager.getInstance().serializableMap.remove(id);
                }
            } else {
                int id = intent.getIntExtra("bitmapId", -1);
                if(id != -1) {
                    Log.d("BitmapIPCManager", "Recycling unused bitmap Id: " + id);
                    BitmapIPCManager.getInstance().dismissBitmap(id);
                }
            }

            String DEVICE_NAME = intent.getStringExtra("device_name");
            String DEVICE_ID = intent.getStringExtra("device_id");
            String KEY = intent.getStringExtra("notification_key");

            NotificationRequest.receptionNotification(context, DEVICE_NAME, DEVICE_ID, KEY, false);
        }
    }
}
