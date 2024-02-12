package com.noti.plugin.data;

import static com.noti.main.service.NotiListenerService.getUniqueID;
import static com.noti.main.service.NotiListenerService.sendNotification;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.messaging.RemoteMessage;

import com.noti.main.service.NotiListenerService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class NetworkProvider {

    public static onProviderMessageListener onNetworkProviderListener;
    public static ArrayList<onFCMIgnitionCompleteListener> onFCMIgnitionCompleteListenerList;

    public interface onProviderMessageListener {
        void onMessageReceived(@Nullable RemoteMessage message);
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

    public static void processReception (Context context, @Nullable RemoteMessage remoteMessage) {
        if(onNetworkProviderListener != null) {
            onNetworkProviderListener.onMessageReceived(remoteMessage);
        } else {
            String DEVICE_NAME = NotiListenerService.getDeviceName();
            String DEVICE_ID = getUniqueID();
            JSONObject notificationBody = new JSONObject();

            try {
                notificationBody.put("type", "send|startup");
                notificationBody.put("device_name", DEVICE_NAME);
                notificationBody.put("device_id", DEVICE_ID);
            } catch (JSONException e) {
                Log.e("Noti", "onCreate: " + e.getMessage());
            }

            addOnFCMIgnitionCompleteListener(() -> onNetworkProviderListener.onMessageReceived(remoteMessage));
            sendNotification(notificationBody, context.getPackageName(), context, true);
        }
    }
}
