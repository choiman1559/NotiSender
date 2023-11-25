package com.noti.plugin.data;

import static android.content.Context.MODE_PRIVATE;
import static com.noti.main.service.NotiListenerService.getUniqueID;
import static com.noti.main.service.NotiListenerService.sendNotification;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.firebase.messaging.RemoteMessage;
import com.noti.main.Application;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class NetworkProvider {

    public static onProviderMessageListener onNetworkProviderListener;
    public static ArrayList<onFCMIgnitionCompleteListener> onFCMIgnitionCompleteListenerList;

    public interface onProviderMessageListener {
        void onMessageReceived(RemoteMessage message);
    }

    public interface onFCMIgnitionCompleteListener {
        void onStartUp();
    }

    public static void setOnNetworkProviderListener(onProviderMessageListener listener) {
        onNetworkProviderListener = listener;
    }

    public static void addOnFCMIgnitionCompleteListener(onFCMIgnitionCompleteListener listener) {
        if(onFCMIgnitionCompleteListenerList == null) {
            onFCMIgnitionCompleteListenerList = new ArrayList<>();
        }

        onFCMIgnitionCompleteListenerList.add(listener);
    }

    public static void fcmIgnitionComplete() {
        if(onFCMIgnitionCompleteListenerList != null) {
            for(onFCMIgnitionCompleteListener listener : onFCMIgnitionCompleteListenerList) {
                listener.onStartUp();
            }
            onFCMIgnitionCompleteListenerList.clear();
        }
    }

    public static void processReception (Context context, NetPacket packet) {
        RemoteMessage remoteMessage = new RemoteMessage.Builder("Implement").setData(packet.build()).build();
        processReception(context, remoteMessage);
    }

    public static void processReception (Context context, RemoteMessage remoteMessage) {
        if(onNetworkProviderListener != null) {
            onNetworkProviderListener.onMessageReceived(remoteMessage);
        } else {
            String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
            String DEVICE_ID = getUniqueID();
            String TOPIC = "/topics/" + context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).getString("UID", "");

            JSONObject notificationHead = new JSONObject();
            JSONObject notificationBody = new JSONObject();
            try {
                notificationBody.put("type", "send|startup");
                notificationBody.put("device_name", DEVICE_NAME);
                notificationBody.put("device_id", DEVICE_ID);

                notificationHead.put("to", TOPIC);
                notificationHead.put("priority", "high");
                notificationHead.put("data", notificationBody);
            } catch (JSONException e) {
                Log.e("Noti", "onCreate: " + e.getMessage());
            }

            addOnFCMIgnitionCompleteListener(() -> onNetworkProviderListener.onMessageReceived(remoteMessage));
            sendNotification(notificationHead, context.getPackageName(), context, true);
        }
    }
}
