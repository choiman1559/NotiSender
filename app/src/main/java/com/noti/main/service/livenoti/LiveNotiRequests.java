package com.noti.main.service.livenoti;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.common.util.Hex;

import com.noti.main.Application;
import com.noti.main.service.NotiListenerService;
import com.noti.main.service.backend.PacketConst;
import com.noti.main.service.backend.ResultPacket;
import com.noti.main.utils.network.AESCrypto;
import com.noti.main.utils.network.JsonRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.pushy.sdk.lib.jackson.databind.ObjectMapper;

public class LiveNotiRequests {
    public static final String TAG = "LiveNotiRequests";
    public static ConcurrentHashMap<String, String> keyHashMap = new ConcurrentHashMap<>();

    public static void requestLiveNotificationData(Context context, String deviceId, String deviceName) throws Exception {
        JSONObject notificationBody = new JSONObject();
        SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
        String uniqueId = shaAndHex(AESCrypto.encrypt(deviceId + System.currentTimeMillis(), AESCrypto.parseAESToken(prefs.getString("UID", ""))));

        notificationBody.put("type", "pair|live_notification");
        notificationBody.put("date", Application.getDateString());

        notificationBody.put(PacketConst.KEY_ACTION_TYPE, LiveNotiProcess.REQUEST_LIVE_NOTIFICATION);
        notificationBody.put(PacketConst.KEY_DATA_KEY, uniqueId);
        notificationBody.put(PacketConst.KEY_DEVICE_NAME, NotiListenerService.getDeviceName());
        notificationBody.put(PacketConst.KEY_DEVICE_ID, NotiListenerService.getUniqueID());
        notificationBody.put(PacketConst.KEY_SEND_DEVICE_NAME, deviceName);
        notificationBody.put(PacketConst.KEY_SEND_DEVICE_ID, deviceId);

        NotiListenerService.sendNotification(notificationBody, TAG, context);
        if(keyHashMap == null) {
            keyHashMap = new ConcurrentHashMap<>();
        }
        keyHashMap.put(deviceId, uniqueId);
    }

    public static void postLiveNotificationData(Context context, Map<String, String> map) throws Exception {
        SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
        String userId = prefs.getString("UID", "");
        String notificationData = LiveNotiProcess.toStringLiveNotificationList(context);

        String deviceId = NotiListenerService.getUniqueID();
        String deviceName = NotiListenerService.getDeviceName();
        String sendDeviceId = map.get(PacketConst.KEY_DEVICE_ID);
        String sendDeviceName = map.get(PacketConst.KEY_DEVICE_NAME);

        String holdUniqueId = map.get(PacketConst.KEY_DATA_KEY);
        String receivedUniqueId = shaAndHex(AESCrypto.encrypt(sendDeviceId + System.currentTimeMillis(), AESCrypto.parseAESToken(userId)));
        String finalUniqueId = shaAndHex(holdUniqueId + receivedUniqueId);

        JSONObject notificationBody = new JSONObject();
        notificationBody.put("type", "pair|live_notification");
        notificationBody.put("date", Application.getDateString());

        notificationBody.put(PacketConst.KEY_ACTION_TYPE, LiveNotiProcess.RESPONSE_LIVE_NOTIFICATION);
        notificationBody.put(PacketConst.KEY_DATA_KEY, receivedUniqueId);
        notificationBody.put(PacketConst.KEY_DEVICE_NAME, deviceName);
        notificationBody.put(PacketConst.KEY_DEVICE_ID, deviceId);
        notificationBody.put(PacketConst.KEY_SEND_DEVICE_NAME, sendDeviceName);
        notificationBody.put(PacketConst.KEY_SEND_DEVICE_ID, sendDeviceId);

        JSONObject serverBody = new JSONObject();
        serverBody.put(PacketConst.KEY_ACTION_TYPE, PacketConst.REQUEST_POST_SHORT_TERM_DATA);
        serverBody.put(PacketConst.KEY_DATA_KEY, finalUniqueId);
        serverBody.put(PacketConst.KEY_UID, userId);
        serverBody.put(PacketConst.KEY_EXTRA_DATA, notificationData);

        serverBody.put(PacketConst.KEY_SEND_DEVICE_ID, sendDeviceId);
        serverBody.put(PacketConst.KEY_SEND_DEVICE_NAME, sendDeviceName);
        serverBody.put(PacketConst.KEY_DEVICE_ID, deviceId);
        serverBody.put(PacketConst.KEY_DEVICE_NAME, deviceName);

        final String URI = PacketConst.getApiAddress(PacketConst.SERVICE_TYPE_LIVE_NOTIFICATION);
        final String contentType = "application/json";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URI, serverBody, response -> {
            try {
                notificationBody.put(PacketConst.KEY_IS_SUCCESS, "true");
                NotiListenerService.sendNotification(notificationBody, TAG, context);
            } catch (JSONException e) {
                //ignore exception
            }
        }, error -> {
            try {
                notificationBody.put(PacketConst.KEY_IS_SUCCESS, "false");
                NotiListenerService.sendNotification(notificationBody, TAG, context);
            } catch (JSONException e) {
                //ignore exception
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Content-Type", contentType);
                return params;
            }
        };
        JsonRequest.getInstance(context).addToRequestQueue(jsonObjectRequest);
    }

    public static void getLiveNotificationData(Context context, Map<String, String> map) throws NoSuchAlgorithmException, JSONException {
        SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);

        String deviceId = NotiListenerService.getUniqueID();
        String deviceName = NotiListenerService.getDeviceName();
        String sendDeviceId = map.get(PacketConst.KEY_DEVICE_ID);
        String sendDeviceName = map.get(PacketConst.KEY_DEVICE_NAME);

        if(keyHashMap == null || !keyHashMap.containsKey(map.get(PacketConst.KEY_DEVICE_ID))) {
            LiveNotiProcess.callLiveNotificationUploadCompleteListener(false, null);
            return;
        }

        String holdUniqueId = keyHashMap.get(map.get(PacketConst.KEY_DEVICE_ID));
        String receivedUniqueId = map.get(PacketConst.KEY_DATA_KEY);
        String finalUniqueId = shaAndHex(holdUniqueId + receivedUniqueId);

        JSONObject serverBody = new JSONObject();
        serverBody.put(PacketConst.KEY_ACTION_TYPE, PacketConst.REQUEST_GET_SHORT_TERM_DATA);
        serverBody.put(PacketConst.KEY_DATA_KEY, finalUniqueId);
        serverBody.put(PacketConst.KEY_UID, prefs.getString("UID", ""));

        serverBody.put(PacketConst.KEY_SEND_DEVICE_ID, sendDeviceId);
        serverBody.put(PacketConst.KEY_SEND_DEVICE_NAME, sendDeviceName);
        serverBody.put(PacketConst.KEY_DEVICE_ID, deviceId);
        serverBody.put(PacketConst.KEY_DEVICE_NAME, deviceName);

        final String URI = PacketConst.getApiAddress(PacketConst.SERVICE_TYPE_LIVE_NOTIFICATION);
        final String contentType = "application/json";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URI, serverBody, response -> {
            try {
                ResultPacket resultPacket = ResultPacket.parseFrom(response.toString());
                if(resultPacket.isResultOk()) {
                    String[] rawArray = new ObjectMapper().readValue(resultPacket.getExtraData(), String[].class);
                    LiveNotificationData[] liveNotiArray = new LiveNotificationData[rawArray.length];
                    for (int i = 0; i < rawArray.length; i++) {
                        liveNotiArray[i] = LiveNotificationData.parseFrom(rawArray[i]);
                    }
                    LiveNotiProcess.callLiveNotificationUploadCompleteListener(true, liveNotiArray);
                }  else {
                    throw new IOException(resultPacket.getErrorCause());
                }
            } catch (IOException e) {
                LiveNotiProcess.callLiveNotificationUploadCompleteListener(false, null);
                e.printStackTrace();
            }
        }, error -> LiveNotiProcess.callLiveNotificationUploadCompleteListener(false, null)) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Content-Type", contentType);
                return params;
            }
        };

        if("true".equals(map.get(PacketConst.KEY_IS_SUCCESS))) {
            JsonRequest.getInstance(context).addToRequestQueue(jsonObjectRequest);
            if(LiveNotiProcess.mOnLiveNotificationUploadCompleteListener != null) {
                LiveNotiProcess.mOnLiveNotificationUploadCompleteListener.onReceive(true, null);
            }
        } else {
            LiveNotiProcess.callLiveNotificationUploadCompleteListener(false, null);
        }
    }

    public static String shaAndHex(String plainText) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(plainText.getBytes(StandardCharsets.UTF_8));
        return Hex.bytesToStringLowercase(md.digest());
    }
}
