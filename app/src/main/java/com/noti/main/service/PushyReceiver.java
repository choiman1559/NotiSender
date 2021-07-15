package com.noti.main.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
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

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.noti.main.R;
import com.noti.main.ui.receive.NotificationViewActivity;
import com.noti.main.ui.receive.SmsViewActivity;
import com.noti.main.utils.CompressStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.content.Context.MODE_PRIVATE;
import static com.noti.main.service.NotiListenerService.getMACAddress;

public class PushyReceiver extends BroadcastReceiver {

    SharedPreferences prefs;

    @Override
    public void onReceive(Context context, Intent map) {
        if (prefs == null)
            prefs = context.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);

        String type = map.getStringExtra("type");
        String mode = prefs.getString("service", "");

        if (prefs.getBoolean("serviceToggle", false) && !prefs.getString("UID", "").equals("")) {
            if (mode.equals("reception") || mode.equals("hybrid") && type.contains("send")) {
                if (mode.equals("hybrid") && isDeviceItself(map)) return;
                if (type.equals("send|normal")) {
                    sendNotification(map, context);
                } else if (type.equals("send|sms")) {
                    sendSmsNotification(map, context);
                }
            } else if ((mode.equals("send") || mode.equals("hybrid")) && type.contains("reception")) {
                if (map.getStringExtra("send_device_name").equals(Build.MANUFACTURER + " " + Build.MODEL) && map.getStringExtra("send_device_id").equals(getMACAddress())) {
                    if (type.equals("reception|normal")) {
                        startNewRemoteActivity(map, context);
                    } else if (type.equals("reception|sms")) {
                        startNewRemoteSms(map, context);
                    }
                }
            }
        }
    }

    protected boolean isDeviceItself(Intent map) {
        String Device_name = map.getStringExtra("device_name");
        String Device_id = map.getStringExtra("device_id");

        if (Device_id == null || Device_name == null) {
            Device_id = map.getStringExtra("send_device_id");
            Device_name = map.getStringExtra("send_device_name");
        }

        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getMACAddress();

        return Device_name.equals(DEVICE_NAME) && Device_id.equals(DEVICE_ID);
    }

    protected void startNewRemoteActivity(Intent map, Context context) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> Toast.makeText(context, "Remote run by NotiSender\nfrom " + map.getStringExtra("device_name"), Toast.LENGTH_SHORT).show(), 0);
        String Package = map.getStringExtra("package");
        try {
            context.getPackageManager().getPackageInfo(Package, PackageManager.GET_ACTIVITIES);
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(Package);
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Package));
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    protected void startNewRemoteSms(Intent map, Context context) {
        SmsManager smgr = SmsManager.getDefault();
        smgr.sendTextMessage(map.getStringExtra("address"), null, map.getStringExtra("message"), null, null);
        new Handler(Looper.getMainLooper()).postDelayed(() -> Toast.makeText(context, "Reply message by NotiSender\nfrom " + map.getStringExtra("device_name"), Toast.LENGTH_SHORT).show(), 0);
    }

    protected void sendSmsNotification(Intent map, Context context) {
        String address = map.getStringExtra("address");
        String message = map.getStringExtra("message");
        String Device_name = map.getStringExtra("device_name");
        String Device_id = map.getStringExtra("device_id");
        String Date = map.getStringExtra("date");
        String Package = map.getStringExtra("package");

        String DeadlineValue = prefs.getString("ReceiveDeadline", "No deadline");
        if(!DeadlineValue.equals("No deadline")) {
            String[] foo = DeadlineValue.split(" ");
            long numberToMultiply;
            switch(foo[1]) {
                case "min":
                    numberToMultiply = 60000L;
                    break;

                case "hour":
                    numberToMultiply = 3600000L;
                    break;

                case "day":
                    numberToMultiply = 86400000L;
                    break;

                case "week":
                    numberToMultiply = 604800000L;
                    break;

                case "month":
                    numberToMultiply = 2419200000L;
                    break;

                case "year":
                    numberToMultiply = 29030400000L;
                    break;

                default:
                    numberToMultiply = 0L;
                    break;
            }

            try {
                if(Date != null) {
                    long calculated = Long.parseLong(foo[0]) * numberToMultiply;
                    Date ReceivedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(Date);
                    if ((System.currentTimeMillis() - ReceivedDate.getTime()) > calculated) {
                        return;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Intent notificationIntent = new Intent(context, SmsViewActivity.class);
        notificationIntent.putExtra("device_id", Device_id);
        notificationIntent.putExtra("message", message);
        notificationIntent.putExtra("address", address);
        notificationIntent.putExtra("device_name", Device_name);
        notificationIntent.putExtra("date", Date);
        notificationIntent.putExtra("package", Package);

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, context.getString(R.string.notify_channel_id))
                .setContentTitle("New message from " + address)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setGroup(context.getPackageName() + ".NOTIFICATION")
                .setGroupSummary(true)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSmallIcon(R.drawable.ic_notification);
            CharSequence channelName = context.getString(R.string.notify_channel_name);
            String description = context.getString(R.string.notify_channel_description);
            int importance = getImportance();
            NotificationChannel channel = new NotificationChannel(context.getString(R.string.notify_channel_id), channelName, importance);
            channel.setDescription(description);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        } else builder.setSmallIcon(R.mipmap.ic_notification);

        assert notificationManager != null;
        notificationManager.notify((int) ((new Date().getTime() / 1000L) % Integer.MAX_VALUE), builder.build());
    }

    protected void sendNotification(Intent map, Context context) {
        String title = map.getStringExtra("title");
        String content = map.getStringExtra("message");
        String Package = map.getStringExtra("package");
        String AppName = map.getStringExtra("appname");
        String Device_name = map.getStringExtra("device_name");
        String Device_id = map.getStringExtra("device_id");
        String Date = map.getStringExtra("date");

        String DeadlineValue = prefs.getString("ReceiveDeadline", "No deadline");
        if(!DeadlineValue.equals("No deadline")) {
            String[] foo = DeadlineValue.split(" ");
            long numberToMultiply;
            switch(foo[1]) {
                case "min":
                    numberToMultiply = 60000L;
                    break;

                case "hour":
                    numberToMultiply = 3600000L;
                    break;

                case "day":
                    numberToMultiply = 86400000L;
                    break;

                case "week":
                    numberToMultiply = 604800000L;
                    break;

                case "month":
                    numberToMultiply = 2419200000L;
                    break;

                case "year":
                    numberToMultiply = 29030400000L;
                    break;

                default:
                    numberToMultiply = 0L;
                    break;
            }

            try {
                if(Date != null) {
                    long calculated = Long.parseLong(foo[0]) * numberToMultiply;
                    Date ReceivedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(Date);
                    if ((System.currentTimeMillis() - ReceivedDate.getTime()) > calculated) {
                        return;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Bitmap Icon_original = null;
        Bitmap Icon = null;
        if (!map.getStringExtra("icon").equals("none")) {
            Icon_original = CompressStringUtil.StringToBitmap(CompressStringUtil.decompressString(map.getStringExtra("icon")));
            if (Icon_original != null) {
                Icon = Bitmap.createBitmap(Icon_original.getWidth(), Icon_original.getHeight(), Icon_original.getConfig());
                Canvas canvas = new Canvas(Icon);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(Icon_original, 0, 0, null);
            }
        }

        new Thread(() -> {
            try {
                JSONArray array = new JSONArray();
                JSONObject object = new JSONObject();
                String originString = prefs.getString("receivedLogs", "");

                if (!originString.equals("")) array = new JSONArray(originString);
                object.put("date", Date);
                object.put("package", Package);
                object.put("title", title);
                object.put("text", content);
                object.put("device", Device_name);
                array.put(object);
                prefs.edit().putString("receivedLogs", array.toString()).apply();

                if (array.length() >= prefs.getInt("HistoryLimit", 150)) {
                    int a = array.length() - prefs.getInt("HistoryLimit", 150);
                    for (int i = 0; i < a; i++) {
                        array.remove(i);
                    }
                    prefs.edit().putString("receivedLogs", array.toString()).apply();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }).start();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(context, NotificationViewActivity.class);
        int uniqueCode = 0;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(Date);
            uniqueCode = d == null ? 0 : (int) ((d.getTime() / 1000L) % Integer.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
        }

        notificationIntent.putExtra("package", Package);
        notificationIntent.putExtra("device_id", Device_id);
        notificationIntent.putExtra("appname", AppName);
        notificationIntent.putExtra("title", title);
        notificationIntent.putExtra("device_name", Device_name);
        notificationIntent.putExtra("date", Date);
        notificationIntent.putExtra("icon", Icon_original != null ? Icon : null);

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, uniqueCode, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, context.getString(R.string.notify_channel_id))
                .setContentTitle(title + " (" + AppName + ")")
                .setContentText(content)
                .setPriority(Build.VERSION.SDK_INT > 23 ? getPriority() : NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setGroup(context.getPackageName() + ".NOTIFICATION")
                .setGroupSummary(true)
                .setAutoCancel(true);

        if (Icon != null) builder.setLargeIcon(Icon);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSmallIcon(R.drawable.ic_notification);
            CharSequence channelName = context.getString(R.string.notify_channel_name);
            String description = context.getString(R.string.notify_channel_description);
            NotificationChannel channel = new NotificationChannel(context.getString(R.string.notify_channel_id), channelName, getImportance());
            channel.setDescription(description);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        } else builder.setSmallIcon(R.mipmap.ic_notification);
        notificationManager.notify(uniqueCode, builder.build());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private int getImportance() {
        String value = prefs.getString("importance", "Default");
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

    @RequiresApi(api = Build.VERSION_CODES.N)
    private int getPriority() {
        String value = prefs.getString("importance", "Default");
        switch (value) {
            case "Low":
                return NotificationCompat.PRIORITY_LOW;
            case "High":
                return NotificationCompat.PRIORITY_MAX;
            default:
                return NotificationCompat.PRIORITY_DEFAULT;
        }
    }
}