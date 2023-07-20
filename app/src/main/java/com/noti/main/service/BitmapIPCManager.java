package com.noti.main.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

public class BitmapIPCManager {
    private static BitmapIPCManager instance;
    private final Map<Integer, Bitmap> bitmapMap;

    private BitmapIPCManager() {
        bitmapMap = new HashMap<>();
    }

    public static BitmapIPCManager getInstance() {
        if(instance == null) instance = new BitmapIPCManager();
        return instance;
    }

    public void addBitmap(Integer id, @Nullable Bitmap bitmap) {
        if(bitmap == null) return;
        bitmapMap.put(id, bitmap);
    }

    @Nullable
    public Bitmap getBitmap(Integer id) {
        if(id < 0) return null;
        Bitmap bitmap = bitmapMap.get(id);
        bitmapMap.remove(id);
        return bitmap;
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
            int id = intent.getIntExtra("bitmapId", -1);
            if(id != -1) {
                Log.d("BitmapIPCManager", "Recycling unused bitmap Id: " + id);
                BitmapIPCManager.getInstance().dismissBitmap(id);
            }
        }
    }
}
