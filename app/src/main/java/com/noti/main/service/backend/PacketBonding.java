package com.noti.main.service.backend;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.noti.main.BuildConfig;
import com.noti.main.service.NotiListenerService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import me.pushy.sdk.lib.jackson.databind.ObjectMapper;

public class PacketBonding {

    public static final long DEFAULT_DELAY = 300;
    protected static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    protected static final CopyOnWriteArrayList<JSONObject> onWriteArrayList = new CopyOnWriteArrayList<>();
    protected static volatile ScheduledFuture<?> scheduledFuture;

    public interface OnPacketBondingCallback {
        void onBond(Map<String, String> map);
    }

    public interface OnPacketSingleBondingCallback {
        void onBond(JSONObject notification) throws JSONException, NoSuchAlgorithmException;
    }

    public static void runBondingSchedule(Context context, JSONObject notification, OnPacketSingleBondingCallback onPacketSingleBondingCallback) throws Exception {
        if(scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }

        SharedPreferences prefs = NotiListenerService.getPrefs();
        final long selectedDelay = prefs.getLong("ProxyPacketBondingDelay", DEFAULT_DELAY);

        if(selectedDelay <= 0) {
            onPacketSingleBondingCallback.onBond(notification);
            return;
        }

        onWriteArrayList.add(notification);
        scheduledFuture = scheduler.schedule(() -> {
            try {
                if(onWriteArrayList.size() < 2) {
                    onPacketSingleBondingCallback.onBond(notification);
                } else {
                    JSONArray array = new JSONArray();
                    for(JSONObject o : onWriteArrayList) {
                        array.put(o);
                    }

                    JSONObject finalNotification = new JSONObject();
                    finalNotification.put("type", PacketConst.SERVICE_TYPE_PACKET_BONDING);
                    finalNotification.put(PacketConst.KEY_PACKET_BONDING_ARRAY, array.toString());
                    finalNotification.put(PacketConst.KEY_DEVICE_ID, NotiListenerService.getUniqueID());
                    finalNotification.put(PacketConst.KEY_DEVICE_NAME, NotiListenerService.getDeviceName());

                    if(BuildConfig.DEBUG) Log.d("PacketBonding", String.format("Sending %d bonded packets together...", array.length()));
                    NotiListenerService.proxyToBackend(finalNotification, "PacketBonding", context, false);
                }
            } catch (JSONException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            } finally {
                onWriteArrayList.clear();
            }
        }, selectedDelay, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("unchecked")
    public static void processPacketBonding(Map<String, String> map, OnPacketBondingCallback callback) {
        String rawData = map.get(PacketConst.KEY_PACKET_BONDING_ARRAY);
        try {
            JSONArray jsonArray = new JSONArray(rawData);
            if (BuildConfig.DEBUG) Log.d("PacketBonding", String.format("Encountered %d packets with bonded", jsonArray.length()));
            for (int i = 0; i < jsonArray.length(); i++) {
                Map<String, String> newMap = new ObjectMapper().readValue(jsonArray.getString(i), Map.class);
                callback.onBond(newMap);
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }
}
