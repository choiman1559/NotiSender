package com.noti.sender;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.application.isradeleon.notify.Notify;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FirebaseMessageService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {

        if (getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("service", "").equals("reception")
                && getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("serviceToggle", false) &&
                !getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "").equals(""))
            sendNotification(remoteMessage.getData().get("title"), remoteMessage.getData().get("message"), remoteMessage.getData().get("package"));
    }

    private void sendNotification(String title, String content, String Package) {
        Notify.create(FirebaseMessageService.this)
                .setTitle(title)
                .setContent(content)
                .circleLargeIcon()
                .setAction(new Intent(FirebaseMessageService.this, MessageSendClass.class).putExtra("package", Package))
                .setImportance(Notify.NotificationImportance.MAX)
                .enableVibration(true)
                .setAutoCancel(true)
                .show();
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        if (!getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "").equals(""))
            FirebaseMessaging.getInstance().subscribeToTopic(getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", ""));
    }
}