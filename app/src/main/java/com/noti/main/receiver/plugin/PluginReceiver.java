package com.noti.main.receiver.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.noti.main.BuildConfig;
import com.noti.main.service.NotiListenerService;
import com.noti.main.service.pair.DataProcess;

import java.util.Calendar;

public class PluginReceiver extends BroadcastReceiver {
    public static onReceivePluginInformation receivePluginInformation;

    public interface onReceivePluginInformation {
        void onReceive(Bundle data);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(PluginConst.SENDER_ACTION_NAME)) {
            Bundle rawData = intent.getExtras();
            String dataType = rawData.getString(PluginConst.DATA_KEY_TYPE);
            String packageName = rawData.getString(PluginConst.PLUGIN_PACKAGE_NAME);
            String extra_data = rawData.getString(PluginConst.DATA_KEY_EXTRA_DATA);
            String[] data = extra_data == null ? new String[0] : extra_data.split("\\|");
            PluginPrefs pluginPrefs = new PluginPrefs(context, packageName);

            if (dataType.equals(PluginConst.ACTION_RESPONSE_INFO)) {
                if (receivePluginInformation != null) {
                    receivePluginInformation.onReceive(rawData);
                }
            } else if(pluginPrefs.isPluginEnabled()) {
                switch (dataType) {
                    case PluginConst.ACTION_REQUEST_DEVICE_LIST:
                        PluginActions.responseDeviceList(context, packageName);
                        break;

                    case PluginConst.ACTION_REQUEST_REMOTE_ACTION:
                        if(BuildConfig.DEBUG && rawData.getString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME).equals("request_purchase")) {
                            @VisibleForTesting
                            Intent iap = new Intent(context, IAPTestActivity.class);
                            iap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(iap);
                            break;
                        }

                        DataProcess.pushPluginRemoteAction(context, data[0], data[1], packageName, PluginConst.ACTION_REQUEST_REMOTE_ACTION, rawData.getString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME), data[2]);
                        break;

                    case PluginConst.ACTION_REQUEST_REMOTE_DATA:
                        DataProcess.pushPluginRemoteAction(context, data[0], data[1], packageName, PluginConst.ACTION_REQUEST_REMOTE_DATA, rawData.getString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME), "");
                        break;

                    case PluginConst.ACTION_REQUEST_PREFS:
                        if(pluginPrefs.isRequireSensitiveAPI()) {
                            PluginActions.responsePreferences(context, packageName, rawData.getString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME));
                        } else PluginActions.pushException(context, packageName, new IllegalAccessException("ACTION_REQUEST_PREFS requires sensitiveAPI=true" + packageName));
                        break;

                    case PluginConst.ACTION_REQUEST_SERVICE_STATUS:
                        PluginActions.responseServiceStatus(context, packageName);
                        break;

                    case PluginConst.ACTION_PUSH_CALL_DATA:
                        NotiListenerService.getInstance().sendTelecomNotification(context, BuildConfig.DEBUG, rawData.getString(PluginConst.DATA_KEY_EXTRA_DATA));
                        break;

                    case PluginConst.ACTION_PUSH_MESSAGE_DATA:
                        NotiListenerService.getInstance().sendSmsNotification(context, BuildConfig.DEBUG, "noti.func", data[0], data[1], Calendar.getInstance().getTime());
                        break;

                    case PluginConst.ACTION_RESPONSE_REMOTE_DATA:
                        DataProcess.pushPluginRemoteAction(context, data[0], data[1], packageName, PluginConst.ACTION_RESPONSE_REMOTE_DATA, rawData.getString(PluginConst.DATA_KEY_REMOTE_ACTION_NAME), data[2]);
                        break;

                    default:
                        PluginActions.pushException(context, packageName, new IllegalAccessException("Plugin Action type is not supported: " + dataType));
                        break;
                }
            } else PluginActions.pushException(context, packageName, new IllegalAccessException("This plugin is not enabled: " + packageName));
        }
    }
}
