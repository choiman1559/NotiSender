package com.noti.main.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.noti.main.R;
import com.noti.main.ui.receive.NotificationViewActivity;
import com.noti.main.ui.receive.SmsViewActivity;
import com.noti.main.utils.CompressStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Map;

import javax.annotation.Nullable;

public class FirebaseMessageService extends FirebaseMessagingService {

    SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Map<String, String> map = remoteMessage.getData();
        String type = map.get("type");

        if(prefs.getBoolean("serviceToggle", false) && !prefs.getString("UID", "").equals("")) {
            if (prefs.getString("service", "").equals("reception")) {
                if (type.equals("send|normal")) {
                    Bitmap icon = null;
                    Bitmap iconw = null;
                    if (!map.get("icon").equals("none")) {
                        icon = CompressStringUtil.StringToBitmap(CompressStringUtil.decompressString(map.get("icon")));
                        iconw = Bitmap.createBitmap(icon.getWidth(), icon.getHeight(), icon.getConfig());
                        Canvas canvas = new Canvas(iconw);
                        canvas.drawColor(Color.WHITE);
                        canvas.drawBitmap(icon, 0, 0, null);
                    }
                    sendNotification(map, icon != null ? iconw : null);
                } else if(type.equals("send|sms")) {
                    sendSmsNotification(map);
                }
            }

            if (prefs.getString("service", "").equals("send") && type.contains("reception")) {
                if (map.get("send_device_name").equals(Build.MANUFACTURER + " " + Build.MODEL) && map.get("send_device_id").equals(NotiListenerService.getMACAddress())) {
                    if(type.equals("reception|normal")) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> Toast.makeText(FirebaseMessageService.this, "Remote run by NotiSender\nfrom " + map.get("device_name"), Toast.LENGTH_SHORT).show(), 0);
                        startNewActivity(map.get("package"));
                    } else if(type.equals("reception|sms")) {
                        SmsManager smgr = SmsManager.getDefault();
                        smgr.sendTextMessage(map.get("address"),null,map.get("message"),null,null);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> Toast.makeText(FirebaseMessageService.this, "Reply message by NotiSender\nfrom " + map.get("device_name"), Toast.LENGTH_SHORT).show(), 0);
                    }
                }
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

    private void sendSmsNotification(Map<String, String> map) {
        String address = map.get("address");
        String message = map.get("message");
        String Device_name = map.get("device_name");
        String Device_id = map.get("device_id");
        String Date = map.get("date");

        Intent notificationIntent = new Intent(FirebaseMessageService.this, SmsViewActivity.class);
        notificationIntent.putExtra("device_id",Device_id);
        notificationIntent.putExtra("message",message);
        notificationIntent.putExtra("address",address);
        notificationIntent.putExtra("device_name",Device_name);
        notificationIntent.putExtra("date",Date);

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notify_channel_id))
                .setContentTitle("New message from " + address)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setGroupSummary(true)
                .setGroup(getPackageName() + ".NOTIFICATION")
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSmallIcon(R.drawable.ic_notification);
            CharSequence channelName = getString(R.string.notify_channel_name);
            String description = getString(R.string.notify_channel_description);
            int importance = getImportance();
            NotificationChannel channel = new NotificationChannel(getString(R.string.notify_channel_id), channelName, importance);
            channel.setDescription(description);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        } else builder.setSmallIcon(R.mipmap.ic_notification);

        assert notificationManager != null;
        notificationManager.notify((int)((new Date().getTime() / 1000L) % Integer.MAX_VALUE), builder.build());
    }

    private void sendNotification(Map<String, String> map, @Nullable Bitmap Icon) {
        String title = map.get("title");
        String content = map.get("message");
        String Package = map.get("package");
        String AppName =  map.get("appname");
        String Device_name = map.get("device_name");
        String Device_id = map.get("device_id");
        String Date = map.get("date");

        try {
            JSONArray array = new JSONArray();
            JSONObject object = new JSONObject();
            String originString = prefs.getString("receivedLogs","");

            if(!originString.equals("")) array = new JSONArray(originString);
            object.put("date",Date);
            object.put("package",Package);
            object.put("title",title);
            object.put("text",content);
            object.put("device",Device_name);
            array.put(object);
            prefs.edit().putString("receivedLogs",array.toString()).apply();

            if(array.length() >= prefs.getInt("HistoryLimit",150)) {
                int a = array.length() - prefs.getInt("HistoryLimit",150);
                for(int i = 0;i < a;i++){
                    array.remove(i);
                }
                prefs.edit().putString("receivedLogs", array.toString()).apply();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(FirebaseMessageService.this, NotificationViewActivity.class);
        notificationIntent.putExtra("package", Package);
        notificationIntent.putExtra("icon",Icon != null ? CompressStringUtil.getStringFromBitmap(Icon) : null);
        notificationIntent.putExtra("device_id",Device_id);
        notificationIntent.putExtra("appname",AppName);
        notificationIntent.putExtra("title",title);
        notificationIntent.putExtra("device_name",Device_name);
        notificationIntent.putExtra("date",Date);

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notify_channel_id))
                .setContentTitle(title + " (" + AppName + ")")
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setGroupSummary(true)
                .setGroup(getPackageName() + ".NOTIFICATION")
                .setAutoCancel(true);

        if(Icon != null) builder.setLargeIcon(Icon);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSmallIcon(R.drawable.ic_notification);
            CharSequence channelName = getString(R.string.notify_channel_name);
            String description = getString(R.string.notify_channel_description);
            int importance = getImportance();
            NotificationChannel channel = new NotificationChannel(getString(R.string.notify_channel_id), channelName, importance);
            channel.setDescription(description);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        } else builder.setSmallIcon(R.mipmap.ic_notification);

        assert notificationManager != null;
        notificationManager.notify((int)((new Date().getTime() / 1000L) % Integer.MAX_VALUE), builder.build());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private int getImportance() {
        String value = prefs.getString("importance","Default");
        switch (value) {
            case "Default":
                return NotificationManager.IMPORTANCE_DEFAULT;
            case "Low":
                return NotificationManager.IMPORTANCE_LOW;
            case "High":
                return NotificationManager.IMPORTANCE_MAX;
            default:
                return NotificationManager.IMPORTANCE_UNSPECIFIED;
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        if (!prefs.getString("UID", "").equals(""))
            FirebaseMessaging.getInstance().subscribeToTopic(prefs.getString("UID", ""));
    }
}