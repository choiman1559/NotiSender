package com.noti.main.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;

import com.noti.main.BuildConfig;
import com.noti.main.service.NotiListenerService;

public class TelecomReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent.getAction().equals("android.intent.action.PHONE_STATE")) {
            String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

            if (phoneNumber != null && TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                NotiListenerService.getInstance().sendTelecomNotification(context, BuildConfig.DEBUG, phoneNumber);
            }
        }
    }
}