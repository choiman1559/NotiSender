package com.noti.main.receiver.plugin;

import static android.content.Context.MODE_PRIVATE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.noti.main.Application;

import java.util.HashSet;
import java.util.Set;

public class PluginActions {
    public static void requestInformation(Context context, String packageName) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_REQUEST_INFO);
        sendBroadcast(context, packageName, extras);
    }

    public static void requestAction(Context context, String packageName, String type, String extra) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_REQUEST_REMOTE_ACTION);
        extras.putString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME, type);
        extras.putString(PluginConst.DATA_KEY_EXTRA_DATA, extra);
        sendBroadcast(context, packageName, extras);
    }

    public static void requestData(Context context, String packageName, String type) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_REQUEST_REMOTE_DATA);
        extras.putString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME, type);
        sendBroadcast(context, packageName, extras);
    }

    public static void responseData(Context context, String packageName, String type, String extra) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_RESPONSE_REMOTE_DATA);
        extras.putString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME, type);
        extras.putString(PluginConst.DATA_KEY_EXTRA_DATA, extra);
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
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_RESPONSE_PREFS);
        extras.putString(PluginConst.DATA_KEY_EXTRA_DATA, context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).getString(keyToFind, ""));
        sendBroadcast(context, packageName, extras);
    }

    public static void responseServiceStatus(Context context, String packageName) {
        boolean isRunning = false;
        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(context);
        if (sets.contains(context.getPackageName())) {
            isRunning = true;
        }

        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_RESPONSE_SERVICE_STATUS);
        extras.putBoolean(PluginConst.DATA_KEY_IS_SERVICE_RUNNING, isRunning);
        sendBroadcast(context, packageName, extras);
    }

    public static void pushException(Context context, String packageName, Exception exception) {
        Bundle extras = new Bundle();
        extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.ACTION_PUSH_EXCEPTION);
        extras.putSerializable(PluginConst.DATA_KEY_EXTRA_DATA, exception);
        sendBroadcast(context, packageName, extras);
    }

    private static void sendBroadcast(Context context, String packageName, Bundle extras) {
        final Intent intent = new Intent();
        intent.setAction(PluginConst.RECEIVER_ACTION_NAME);
        intent.putExtras(extras);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.setComponent(new ComponentName(packageName,"com.noti.plugin.DataReceiver"));
        context.sendBroadcast(intent);
        Log.d("sent", packageName + extras.getString(PluginConst.DATA_KEY_TYPE));
    }
}
