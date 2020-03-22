package com.noti.sender;

import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.application.isradeleon.notify.Notify;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FirebaseMessageService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        SharedPreferences prefs = getSharedPreferences("SettingActivity",MODE_PRIVATE);

        if (prefs.getBoolean("reception", true) && !prefs.getString("UID","").equals(""))
            sendNotification(remoteMessage.getData().get("title"), remoteMessage.getData().get("message"),remoteMessage.getData().get("package"));
    }

    private void sendNotification(String title, String content,String Package) {
        Notify.create(FirebaseMessageService.this)
                .setTitle(title)
                .setContent(content)
                .circleLargeIcon()
                .setAction(new Intent(FirebaseMessageService.this,MessageSendClass.class).putExtra("package",Package))
                .setImportance(Notify.NotificationImportance.MAX)
                .enableVibration(true)
                .setAutoCancel(true)
                .show();
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        if(!getSharedPreferences("SettingActivity", MODE_PRIVATE).getString("UID", "").equals(""))
            FirebaseMessaging.getInstance().subscribeToTopic(getSharedPreferences("SettingActivity", MODE_PRIVATE).getString("UID", ""));
    }
}