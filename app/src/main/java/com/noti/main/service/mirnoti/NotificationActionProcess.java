package com.noti.main.service.mirnoti;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;

import com.noti.main.service.FirebaseMessageService;

import java.util.concurrent.ConcurrentHashMap;

public class NotificationActionProcess {

    private static final long expireTime = 1800000L;
    private static ConcurrentHashMap<String, ActionWrapper> actionHashMap;

    private static class ActionWrapper {
        Notification.Action[] actions;
        long registrationTime;

        public ActionWrapper(Notification.Action[] actions) {
            this.actions = actions;
            registrationTime = System.currentTimeMillis();
        }

        public void raiseAction(@Nullable Context context, int index, @Nullable String inputKey, @Nullable String inputValue) {
            try {
                Notification.Action targetAction = actions[index];

                if(inputKey != null && inputValue != null) {
                    Bundle bundle = new Bundle();
                    Intent intent = new Intent();

                    bundle.putString(inputKey, inputValue);
                    intent.putExtra(RemoteInput.EXTRA_RESULTS_DATA, bundle);

                    ClipData.Item item = new ClipData.Item(intent);
                    ClipData clipData = new ClipData(RemoteInput.RESULTS_CLIP_LABEL, new String[]{ClipDescription.MIMETYPE_TEXT_INTENT}, item);

                    Intent actionIntent = new Intent();
                    actionIntent.setClipData(clipData);

                    targetAction.actionIntent.send(context, 0, actionIntent, null, null, null, null);
                } else {
                    targetAction.actionIntent.send();
                }
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

    public static void raiseActionWithInput(Context context, String key, int index, @Nullable String inputKey, @Nullable String inputValue) {
        if (actionHashMap == null || actionHashMap.isEmpty()) {
            return;
        }

        ActionWrapper actionWrapper = actionHashMap.get(key);
        if (actionWrapper != null) {
            actionWrapper.raiseAction(context, index, inputKey, inputValue);
        }
        cleanupExpiredActions();
    }

    public static void raiseAction(String key, int index) {
        raiseActionWithInput(null, key, index, null, null);
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

        public static final String TEST_INPUT_ACTION_KET = "$testInputAction_Key";

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.hasExtra(TEST_INPUT_ACTION_KET)) {
                // For testing purposes only; must do nothing on other packages
                notifyTestInputAction(context, intent);
                return;
            }

            String key = intent.getStringExtra(NotificationRequest.KEY_NOTIFICATION_KEY);
            int index = intent.getIntExtra(NotificationRequest.KEY_NOTIFICATION_ACTION_INDEX, 0);

            if(intent.getBooleanExtra(NotificationRequest.KEY_NOTIFICATION_HAS_INPUT, false)) {
                String inputKey = intent.getStringExtra(NotificationRequest.KEY_NOTIFICATION_KEY_INPUT);
                String inputValue = RemoteInput.getResultsFromIntent(intent).getString(inputKey);
                NotificationRequest.sendPerformActionWithInput(context, key, index, inputKey, inputValue,
                        intent.getStringExtra("device_name"), intent.getStringExtra("device_id"));
            } else {
                NotificationRequest.sendPerformAction(context, key, index,
                        intent.getStringExtra("device_name"), intent.getStringExtra("device_id"));
            }

            final int uniqueCode = intent.getIntExtra(NotificationRequest.KEY_NOTIFICATION_HASHCODE, -1);
            if(uniqueCode != -1) {
                if(FirebaseMessageService.removeListenerById == null) {
                    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.cancel(uniqueCode);
                } else {
                    FirebaseMessageService.removeListenerById.onRequested(key);
                }
            }
        }

        @TestOnly
        protected void notifyTestInputAction(@NonNull Context context, Intent intent) {
            Log.d("ReplyData", "InputType action receiver: " + RemoteInput.getResultsFromIntent(intent).get(intent.getStringExtra(TEST_INPUT_ACTION_KET)));
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(Integer.parseInt(intent.getStringExtra(TEST_INPUT_ACTION_KET)));
        }
    }
}
