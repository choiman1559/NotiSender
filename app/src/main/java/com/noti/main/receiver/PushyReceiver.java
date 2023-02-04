package com.noti.main.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PushyReceiver extends BroadcastReceiver {

    public static onPushyMessageListener onPushyMessageListener;
    public interface onPushyMessageListener {
        void onMessageReceived(RemoteMessage message);
    }

    public static void setOnPushyMessageListener(onPushyMessageListener listener) {
        onPushyMessageListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent map) {
        Bundle extras = map.getExtras();
        Map<String, String> hashMap = new HashMap<>();
        Set<String> ks = extras.keySet();
        for (String key : ks) {
            hashMap.put(key, extras.getString(key));
        }

        RemoteMessage remoteMessage = new RemoteMessage.Builder("Implement").setData(hashMap).build();
        if(onPushyMessageListener != null) {
            onPushyMessageListener.onMessageReceived(remoteMessage);
            Log.d("Push", "onPushyMessageListener1");
        }
    }
}