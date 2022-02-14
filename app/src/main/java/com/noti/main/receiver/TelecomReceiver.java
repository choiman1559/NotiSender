package com.noti.main.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresApi;

import com.noti.main.BuildConfig;
import com.noti.main.service.NotiListenerService;

public class TelecomReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        if(intent.getAction().equals("android.intent.action.PHONE_STATE")) {
            TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            telephony.listen(new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String incomingNumber) {
                    super.onCallStateChanged(state, incomingNumber);
                    if (state == TelephonyManager.CALL_STATE_RINGING) {
                        NotiListenerService.getInstance().sendTelecomNotification(context, BuildConfig.DEBUG, incomingNumber);
                        telephony.listen(this, PhoneStateListener.LISTEN_NONE);
                    }
                }
            }, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    static class CustomTelephonyCallback extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        private final CallBack mCallBack;

        public CustomTelephonyCallback(CallBack callBack) {
            mCallBack = callBack;
        }

        @Override
        public void onCallStateChanged(int state) {
            mCallBack.callStateChanged(state);
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    public void registerCustomTelephonyCallback(Context context) {
        TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        telephony.registerTelephonyCallback(context.getMainExecutor(), new CustomTelephonyCallback(state -> {
            //TODO : how to get 'incoming Number' ???
        }));
    }

    interface CallBack {
        void callStateChanged(int state);
    }
}