package com.noti.main.service.mirnoti;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Nullable;

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.service.NotiListenerService;
import com.noti.main.service.backend.PacketConst;
import com.noti.main.service.livenoti.LiveNotiProcess;

import org.json.JSONException;
import org.json.JSONObject;

public class NotificationRequest {

    public static final String PREFIX_KEY_NOTIFICATION = "notification_";
    public static final String KEY_NOTIFICATION_API = PREFIX_KEY_NOTIFICATION + "api";
    public static final String KEY_NOTIFICATION_DATA = PREFIX_KEY_NOTIFICATION + "data";
    public static final String KEY_NOTIFICATION_KEY = PREFIX_KEY_NOTIFICATION + "key";
    public static final String KEY_NOTIFICATION_ACTION_INDEX = PREFIX_KEY_NOTIFICATION + "action_index";

    public static void sendMirrorNotification(Context context, boolean isLogging, StatusBarNotification notification) {
        try {
            NotificationsData notificationsData = new NotificationsData(context, notification);
            NotificationActionProcess.registerAction(notification);

            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type", "send|normal");
            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put(KEY_NOTIFICATION_API, "1");
            notificationBody.put(KEY_NOTIFICATION_DATA, notificationsData.toString());

            if (isLogging) Log.d("NOTIFICATION_DATA", notificationBody.toString());
            NotiListenerService.sendNotification(notificationBody, "NOTIFICATION_SEND", context);
        } catch (PackageManager.NameNotFoundException | JSONException e) {
            if(isLogging) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void sendPerformAction(Context context, String key, int index, String deviceName, String deviceId) {
        try {
            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type", "reception|perform_action");
            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", deviceName);
            notificationBody.put("send_device_id", deviceId);
            notificationBody.put(KEY_NOTIFICATION_API, "1");
            notificationBody.put(KEY_NOTIFICATION_KEY, key);
            notificationBody.put(KEY_NOTIFICATION_ACTION_INDEX, index);

            NotiListenerService.sendNotification(notificationBody, "NOTIFICATION_ACTION", context);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void receptionNotification(Context context, String DEVICE_NAME, String DEVICE_ID, String KEY, boolean isNeedToStartRemotely) {
        receptionNotification(context, null, DEVICE_NAME, DEVICE_ID, KEY, isNeedToStartRemotely);
    }

    public static void receptionNotification(Context context, @Nullable String Package, String DEVICE_NAME, String DEVICE_ID, String KEY, boolean isNeedToStartRemotely) {
        receptionNotification(context, Package, DEVICE_NAME, DEVICE_ID, KEY, isNeedToStartRemotely, false);
    }

    public static void receptionNotification(Context context, @Nullable String Package, String DEVICE_NAME, String DEVICE_ID, String KEY, boolean isNeedToStartRemotely, boolean isLiveNotiResponse) {
        SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        boolean isDismiss = prefs.getBoolean("RemoteDismiss", false);
        if(!(isDismiss || isNeedToStartRemotely || isLiveNotiResponse)) return;
        JSONObject notificationBody = new JSONObject();

        try {
            if(Package != null) notificationBody.put("package", Package);
            if(isDismiss || isLiveNotiResponse) notificationBody.put("notification_key", KEY);

            if(isLiveNotiResponse) {
                notificationBody.put("type","pair|live_notification");
                notificationBody.put(PacketConst.KEY_ACTION_TYPE, LiveNotiProcess.REQUEST_NOTIFICATION_ACTION);
            } else {
                notificationBody.put("type","reception|normal");
            }

            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
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
