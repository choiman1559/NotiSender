package com.noti.main.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.messaging.RemoteMessage;

import com.noti.main.Application;
import com.noti.plugin.data.NetworkProvider;

import java.util.Objects;

public class BootCompleteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
            SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
            if(!prefs.getString("UID", "").isEmpty()) {
                NetworkProvider.processReception(context, (RemoteMessage) null);
                Log.i("BootCompleteReceiver", "Attempting to ignite the NotiSender service... If failed, run NotiSender manually");
            }
        }
    }
}
