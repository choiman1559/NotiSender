package com.noti.main.receiver.plugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.noti.main.BuildConfig;
import com.noti.main.service.NotiListenerService;
import com.noti.main.service.pair.DataProcess;

import java.util.Calendar;

public class PluginReceiver extends BroadcastReceiver {
    public static onReceivePluginInformation receivePluginInformation;
    public static SharedPreferences pluginPrefs;

    public interface onReceivePluginInformation {
        void onReceive(Bundle data);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(pluginPrefs == null) {
            pluginPrefs = context.getSharedPreferences("com.noti.main_plugin", Context.MODE_PRIVATE);
        }

        if (intent.getAction().equals(PluginConst.SENDER_ACTION_NAME)) {
            Bundle rawData = intent.getExtras();
            String dataType = rawData.getString(PluginConst.DATA_KEY_TYPE);
            String packageName = rawData.getString(PluginConst.DATA_KEY_PLUGIN_PACKAGE_NAME);
            String extra_data = rawData.getString(PluginConst.DATA_KEY_EXTRA_DATA);
            String[] data = extra_data == null ? new String[0] : extra_data.split("\\|");

            if (dataType.equals(PluginConst.ACTION_RESPONSE_INFO)) {
                if (receivePluginInformation != null) {
                    receivePluginInformation.onReceive(rawData);
                }
            } else if(pluginPrefs.getBoolean(packageName, false)) {
                switch (dataType) {
                    case PluginConst.ACTION_REQUEST_DEVICE_LIST:
                        PluginActions.responseDeviceList(context, packageName);
                        break;

                    case PluginConst.ACTION_REQUEST_REMOTE_ACTION:
                        DataProcess.pushPluginRemoteAction(context, data[0], data[1], packageName, PluginConst.ACTION_REQUEST_REMOTE_ACTION, data[2], data[3]);
                        break;

                    case PluginConst.ACTION_REQUEST_REMOTE_DATA:
                        DataProcess.pushPluginRemoteAction(context, data[0], data[1], packageName, PluginConst.ACTION_REQUEST_REMOTE_DATA, data[2], "");
                        break;

                    case PluginConst.ACTION_REQUEST_PREFS:
                        PluginActions.responsePreferences(context, packageName, data[0]);
                        break;

                    case PluginConst.ACTION_REQUEST_SERVICE_STATUS:
                        PluginActions.responseServiceStatus(context, packageName);
                        break;

                    case PluginConst.ACTION_PUSH_CALL_DATA:
                        NotiListenerService.getInstance().sendTelecomNotification(context, BuildConfig.DEBUG, rawData.getString(PluginConst.DATA_KEY_EXTRA_DATA));
                        break;

                    case PluginConst.ACTION_PUSH_MESSAGE_DATA:
                        NotiListenerService.getInstance().sendSmsNotification(BuildConfig.DEBUG, "noti.func", data[0], data[1], Calendar.getInstance().getTime());
                        break;

                    case PluginConst.ACTION_RESPONSE_REMOTE_DATA:
                        DataProcess.pushPluginRemoteAction(context, data[0], data[1], packageName, PluginConst.ACTION_RESPONSE_REMOTE_DATA, data[2], data[3]);
                        break;

                    default:
                        PluginActions.pushException(context, packageName, new IllegalAccessException("Plugin Action type is not supported: " + dataType));
                        break;
                }
            } else PluginActions.pushException(context, packageName, new IllegalAccessException("This plugin is not enabled: " + packageName));
        }
    }
}
