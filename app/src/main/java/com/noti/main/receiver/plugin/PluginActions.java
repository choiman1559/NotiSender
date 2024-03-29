package com.noti.main.receiver.plugin;

import static android.content.Context.MODE_PRIVATE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.plugin.data.NotificationData;

import java.util.HashSet;
import java.util.Set;

public class PluginActions {
    public static void requestInformation(Context context, String packageName) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_REQUEST_INFO);
        sendBroadcast(context, packageName, extras);
    }

    public static void requestAction(Context context, String device, String packageName, String type, String extra) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_REQUEST_REMOTE_ACTION);
        extras.putString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME, type);
        extras.putString(PluginConst.DATA_KEY_EXTRA_DATA, extra);
        extras.putString(PluginConst.DATA_KEY_REMOTE_TARGET_DEVICE, device);
        sendBroadcast(context, packageName, extras);
    }

    public static void requestData(Context context, String device, String packageName, String type) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_REQUEST_REMOTE_DATA);
        extras.putString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME, type);
        extras.putString(PluginConst.DATA_KEY_REMOTE_TARGET_DEVICE, device);
        sendBroadcast(context, packageName, extras);
    }

    public static void requestHostApiInject(Context context, String device, String packageName, String type, String args) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_REQUEST_HOST_INJECT);
        extras.putString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME, type);
        extras.putString(PluginConst.DATA_KEY_REMOTE_TARGET_DEVICE, device);
        extras.putString(PluginConst.DATA_KEY_EXTRA_DATA, args);
        sendBroadcast(context, packageName, extras);
    }

    public static void responseData(Context context, String packageName, String type, String extra) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_RESPONSE_REMOTE_DATA);
        extras.putString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME, type);
        extras.putString(PluginConst.DATA_KEY_EXTRA_DATA, extra);
        sendBroadcast(context, packageName, extras);
    }

    public static void responseSelfDeviceInfo(Context context, String packageName, String deviceInfo) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_RESPONSE_SELF_DEVICE_INFO);
        extras.putString(PluginConst.DATA_KEY_DEVICE_LIST, deviceInfo);
        sendBroadcast(context, packageName, extras);
    }

    public static void responseDeviceList(Context context, String packageName) {
        Set<String> deviceList = context.getSharedPreferences("com.noti.main_pair", MODE_PRIVATE).getStringSet("paired_list", new HashSet<>());
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_RESPONSE_DEVICE_LIST);
        extras.putStringArray(PluginConst.DATA_KEY_DEVICE_LIST, deviceList.toArray(new String[0]));
        sendBroadcast(context, packageName, extras);
    }

    public static void responsePreferences(Context context, String packageName, String keyToFind) {
        SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);

        if (!prefs.contains(keyToFind)) {
            PluginActions.pushException(context, packageName, new Exception("Preference not found: " + keyToFind));
            return;
        }

        if (keyToFind.startsWith(Application.PREFS_NAME)) {
            PluginActions.pushException(context, packageName, new Exception("Preference not allowed due to data security reason: " + keyToFind));
            return;
        }

        String[] notAllowedPrefs = {"FirebaseIIDPrefix", "GUIDPrefix", "MacIDPrefix", "AndroidIDPrefix", "ApiKey_Billing", "ApiKey_FCM", "ApiKey_Pushy", "UID", "EncryptionPassword"};
        for (String notAllowedPref : notAllowedPrefs) {
            if (notAllowedPref.equalsIgnoreCase(keyToFind)) {
                PluginActions.pushException(context, packageName, new Exception("Preference not allowed due to data security reason: " + keyToFind));
                return;
            }
        }

        String data;
        try {
            data = prefs.getString(keyToFind, "");
        } catch (ClassCastException e) {
            data = String.valueOf(prefs.getBoolean(keyToFind, false));
        }

        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_RESPONSE_PREFS);
        extras.putString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME, keyToFind);
        extras.putString(PluginConst.DATA_KEY_EXTRA_DATA, data);
        sendBroadcast(context, packageName, extras);
    }

    public static void responseServiceStatus(Context context, String packageName) {
        boolean isRunning = false;

        if(context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).getBoolean("SkipAlarmAccessPermission", false)) {
            isRunning = true;
        } else {
            Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(context);
            if (sets.contains(context.getPackageName())) {
                isRunning = true;
            }
        }

        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_RESPONSE_SERVICE_STATUS);
        extras.putString(PluginConst.DATA_KEY_IS_SERVICE_RUNNING, String.valueOf(isRunning));
        sendBroadcast(context, packageName, extras);
    }

    public static void responsePluginToggle(Context context, String packageName, boolean isEnabled) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_RESPONSE_PLUGIN_TOGGLE);
        extras.putString(PluginConst.DATA_KEY_IS_SERVICE_RUNNING, String.valueOf(isEnabled));
        sendBroadcast(context, packageName, extras);
    }

    public static void pushNotification(Context context, String packageName, NotificationData notificationData) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_PUSH_NOTIFICATION);
        sendBroadcast(context, packageName, extras, notificationData);
    }

    public static void pushException(Context context, String packageName, Exception exception) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_PUSH_EXCEPTION);
        extras.putSerializable(PluginConst.DATA_KEY_EXCEPTION, exception);
        sendBroadcast(context, packageName, extras);
    }

    public static void sendBroadcast(Context context, String packageName, Bundle extras) {
        final Intent intent = new Intent();
        intent.setAction(PluginConst.RECEIVER_ACTION_NAME);
        intent.putExtras(extras);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.setComponent(new ComponentName(packageName, PluginConst.RECEIVER_CLASS_NAME));
        context.sendBroadcast(intent);
        if (BuildConfig.DEBUG)
            Log.d("sent", packageName + " " + extras.getString(PluginConst.DATA_KEY_TYPE));
    }

    private static void sendBroadcast(Context context, String packageName, Bundle extras, Parcelable parcelable) {
        final Intent intent = new Intent();
        intent.setAction(PluginConst.RECEIVER_ACTION_NAME);
        intent.putExtra(PluginConst.DATA_KEY_EXTRA_DATA, parcelable);
        intent.putExtras(extras);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.setComponent(new ComponentName(packageName, PluginConst.RECEIVER_CLASS_NAME));
        context.sendBroadcast(intent);
        if (BuildConfig.DEBUG)
            Log.d("sent", packageName + " " + extras.getString(PluginConst.DATA_KEY_TYPE));
    }
}
