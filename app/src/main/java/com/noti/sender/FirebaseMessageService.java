package com.noti.sender;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import javax.annotation.Nullable;

public class FirebaseMessageService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {

        Map<String, String> map = remoteMessage.getData();
        if (getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("service", "").equals("reception")
                && getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("serviceToggle", false) &&
                !getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "").equals("") && map.get("type").equals("send")) {

            Bitmap icon = null;
            Bitmap iconw = null;
            if(!map.get("icon").equals("none")) {
                icon = CompressStringUtil.StringToBitmap(CompressStringUtil.decompressString(map.get("icon")));
                iconw = Bitmap.createBitmap(icon.getWidth(),icon.getHeight(),icon.getConfig());
                Canvas canvas = new Canvas(iconw);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(icon, 0, 0, null);
            }
            sendNotification(map.get("title"), map.get("message"), map.get("package"), map.get("appname"),map.get("device_name"),map.get("device_id"),map.get("date"),Build.VERSION.SDK_INT > 22 && icon != null ? iconw : null);
        }

        if (getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("service", "").equals("send")
                && getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("serviceToggle", false) &&
                !getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "").equals("") && map.get("type").equals("reception")) {
            if(map.get("send_device_name").equals(Build.MANUFACTURER  + " " + Build.MODEL) && map.get("send_device_id").equals(NotiListenerClass.getMACAddress())) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> Toast.makeText(FirebaseMessageService.this, "Remote run by NotiSender\nfrom " + map.get("device_name"), Toast.LENGTH_SHORT).show(), 0);
                startNewActivity(map.get("package"));
            }
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

    public void sendNotification(String title, String content, String Package, String AppName,String Device_name,String Device_id,String Date, @Nullable Bitmap icon) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(FirebaseMessageService.this, MessageSendClass.class)
                .putExtra("package", Package).putExtra("icon",icon != null ? CompressStringUtil.getStringFromBitmap(icon) : null).putExtra("device_id",Device_id)
                .putExtra("appname",AppName).putExtra("title",title).putExtra("device_name",Device_name).putExtra("date",Date);

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
        } else builder.setSmallIcon(R.mipmap.ic_notification);

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