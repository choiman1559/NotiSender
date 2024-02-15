package com.noti.main.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.messaging.RemoteMessage;

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.utils.PowerUtils;
import com.noti.plugin.data.NetworkProvider;

import java.util.Objects;

public class BootCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
            SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
            if(!prefs.getString("UID", "").isEmpty()) {
                PowerUtils.getInstance(context).acquire();
                NetworkProvider.processReception(context, (RemoteMessage) null);
                if(BuildConfig.DEBUG) {
                    Log.i("BootCompleteReceiver", "Attempting to ignite the NotiSender service... If failed, run NotiSender manually");
                }
            }
        }
    }
}
