package com.noti.main.service.mirnoti;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;

import java.util.concurrent.ConcurrentHashMap;

public class NotificationActionProcess {

    private static final long expireTime = 240000L;
    private static ConcurrentHashMap<String, ActionWrapper> actionHashMap;

    private static class ActionWrapper {
        Notification.Action[] actions;
        long registrationTime;

        public ActionWrapper(Notification.Action[] actions) {
            this.actions = actions;
            registrationTime = System.currentTimeMillis();
        }

        public void raiseAction(int index) {
            try {
                Notification.Action targetAction = actions[index];
                targetAction.actionIntent.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - registrationTime > expireTime;
        }
    }

    public static void registerAction(StatusBarNotification notification) {
        if (actionHashMap == null) {
            actionHashMap = new ConcurrentHashMap<>();
        }
        actionHashMap.put(notification.getKey(), new ActionWrapper(notification.getNotification().actions));
        cleanupExpiredActions();
    }

    public static void raiseAction(String key, int index) {
        if (actionHashMap == null || actionHashMap.isEmpty()) {
            return;
        }

        ActionWrapper actionWrapper = actionHashMap.get(key);
        if (actionWrapper != null) {
            actionWrapper.raiseAction(index);
        }
        cleanupExpiredActions();
    }

    public static void removeAction(String key) {
        if (actionHashMap != null && !actionHashMap.isEmpty()) {
            actionHashMap.remove(key);
        }
    }

    public static void cleanupExpiredActions() {
        if (actionHashMap == null || actionHashMap.isEmpty()) {
            return;
        }

        for (String key : actionHashMap.keySet()) {
            ActionWrapper actionWrapper = actionHashMap.get(key);
            if(actionWrapper != null && actionWrapper.isExpired()) {
                removeAction(key);
            }
        }
    }

    public static class NotificationActionRaiseBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String key = intent.getStringExtra(NotificationRequest.KEY_NOTIFICATION_KEY);
            int index = intent.getIntExtra(NotificationRequest.KEY_NOTIFICATION_ACTION_INDEX, 0);
            NotificationRequest.sendPerformAction(context, key, index, intent.getStringExtra("device_name"), intent.getStringExtra("device_id"));
        }
    }
}
