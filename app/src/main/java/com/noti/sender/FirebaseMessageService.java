package com.noti.sender;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import javax.annotation.Nullable;

public class FirebaseMessageService extends FirebaseMessagingService {

    private static Bitmap StringToBitmap(String encodedString) {
        try {
            byte[] encodeByte = Base64.decode(encodedString, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);
        } catch (Exception e) {
            e.getMessage();
            return null;
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {

        if (getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("service", "").equals("reception")
                && getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("serviceToggle", false) &&
                !getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "").equals("") && remoteMessage.getData().get("type").equals("send")) {

            Map<String, String> map = remoteMessage.getData();
            Bitmap icon = StringToBitmap(CompressStringUtil.decompressString(map.get("icon")));
            Bitmap iconw = Bitmap.createBitmap(icon.getWidth(),icon.getHeight(),icon.getConfig());
            Canvas canvas = new Canvas(iconw);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(icon, 0, 0, null);

            sendNotification(map.get("title"), map.get("message"), map.get("package"), map.get("appname"), Build.VERSION.SDK_INT > 22 && !"none".equals(map.get("icon")) ? iconw : null);
        }

        if (getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("service", "").equals("send")
                && getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("serviceToggle", false) &&
                !getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "").equals("") && remoteMessage.getData().get("type").equals("reception")) {
            startNewActivity(remoteMessage.getData().get("package"));
        }
    }

    private void startNewActivity(String Package){
        try{
            getPackageManager().getPackageInfo(Package, PackageManager.GET_ACTIVITIES);
            Intent intent = getPackageManager().getLaunchIntentForPackage(Package);
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Package));
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    public void sendNotification(String title, String content, String Package, String AppName, @Nullable Bitmap icon) {
        Log.d("dd","dd");
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(FirebaseMessageService.this, MessageSendClass.class).putExtra("package", Package);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notify_channel_id))
                .setContentTitle(title + " (" + AppName + ")")
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if(icon != null) builder.setLargeIcon(icon);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            builder.setSmallIcon(R.drawable.ic_notification);
            CharSequence channelName = getString(R.string.notify_channel_name);
            String description = getString(R.string.notify_channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(getString(R.string.notify_channel_id), channelName, importance);
            channel.setDescription(description);

            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);

        }

        assert notificationManager != null;
        notificationManager.notify(1234, builder.build());
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        if (!getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "").equals(""))
            FirebaseMessaging.getInstance().subscribeToTopic(getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", ""));
    }
}