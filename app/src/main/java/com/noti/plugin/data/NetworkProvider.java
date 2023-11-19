package com.noti.plugin.data;

import com.google.firebase.messaging.RemoteMessage;
import com.noti.main.receiver.PushyReceiver;

public class NetworkProvider {

    public static PushyReceiver.onPushyMessageListener onNetworkProviderListener;

    public static void setOnNetworkProviderListener(PushyReceiver.onPushyMessageListener listener) {
        onNetworkProviderListener = listener;
    }

    public static void processReception (NetPacket packet) {
        RemoteMessage remoteMessage = new RemoteMessage.Builder("Implement").setData(packet.build()).build();
        if(onNetworkProviderListener != null) {
            onNetworkProviderListener.onMessageReceived(remoteMessage);
        }
    }
}
