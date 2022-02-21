package com.noti.main.service.pair;

import static android.content.Context.MODE_PRIVATE;

import static com.noti.main.service.pair.PairListener.m_onDeviceFoundListener;
import static com.noti.main.service.pair.PairListener.m_onDevicePairResultListener;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.service.FirebaseMessageService;
import com.noti.main.service.NotiListenerService;
import com.noti.main.ui.pair.PairAcceptActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PairingUtils {
    private PairingUtils() {

    }

    public static void requestDeviceListWidely(Context context) {
        Application.isFindingDeviceToPair = true;
        String Topic = "/topics/" + context.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE).getString("UID","");
        JSONObject notificationHead = new JSONObject();
        JSONObject notificationBody = new JSONObject();
        try {
            notificationBody.put("type","pair|request_device_list");
            notificationBody.put("device_name", Build.MANUFACTURER  + " " + Build.MODEL);
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationHead.put("to",Topic);
            notificationHead.put("data", notificationBody);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage() );
        }
        NotiListenerService.sendNotification(notificationHead, "pair.func", context);
        if(isShowDebugLog(context)) Log.d("sync sent","request list: " + notificationBody);
    }

    public static void responseDeviceInfoToFinder(Map<String, String> map, Context context) {
        String Topic = "/topics/" + context.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE).getString("UID","");
        JSONObject notificationHead = new JSONObject();
        JSONObject notificationBody = new JSONObject();
        try {
            notificationBody.put("type","pair|response_device_list");
            notificationBody.put("device_name", Build.MANUFACTURER  + " " + Build.MODEL);
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", map.get("device_name"));
            notificationBody.put("send_device_id", map.get("device_id"));
            notificationHead.put("to",Topic);
            notificationHead.put("data", notificationBody);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage() );
        }
        NotiListenerService.sendNotification(notificationHead, "pair.func", context);
        if(isShowDebugLog(context)) Log.d("sync sent","response list: " + notificationBody);
    }

    public static void onReceiveDeviceInfo(Map<String, String> map) {
        if(m_onDeviceFoundListener != null) m_onDeviceFoundListener.onReceive(map);
    }

    public static void requestPair(String Device_name, String Device_id, Context context) {
        String Topic = "/topics/" + context.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE).getString("UID","");
        JSONObject notificationHead = new JSONObject();
        JSONObject notificationBody = new JSONObject();
        try {
            notificationBody.put("type","pair|request_pair");
            notificationBody.put("device_name", Build.MANUFACTURER  + " " + Build.MODEL);
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", Device_name);
            notificationBody.put("send_device_id", Device_id);
            notificationHead.put("to",Topic);
            notificationHead.put("data", notificationBody);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage() );
        }
        NotiListenerService.sendNotification(notificationHead, "pair.func", context);
        if(isShowDebugLog(context)) Log.d("sync sent","request pair: " + notificationBody);
    }

    public static void showPairChoiceAction(Map<String, String> map, Context context) {
        if(context.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE).getBoolean("allowAcceptPairAutomatically", false)) {
            PairAcceptActivity.sendAcceptedMessage(map.get("device_name"), map.get("device_id"), true, context);
            SharedPreferences prefs = context.getSharedPreferences("com.noti.main_pair", MODE_PRIVATE);
            boolean isNotRegistered = true;
            String dataToSave = map.get("device_name") + "|" + map.get("device_id");

            Set<String> list = new HashSet<>(prefs.getStringSet("paired_list", new HashSet<>()));
            for(String str : list) {
                if(str.equals(dataToSave)) {
                    isNotRegistered = false;
                    break;
                }
            }

            if(isNotRegistered) {
                list.add(dataToSave);
                prefs.edit().putStringSet("paired_list", list).apply();
            }
            return;
        }

        int uniqueCode = (int) (Calendar.getInstance().getTime().getTime() / 1000L % Integer.MAX_VALUE);

        Intent notificationIntent = new Intent(context, PairAcceptActivity.class);
        notificationIntent.putExtra("device_name", map.get("device_name"));
        notificationIntent.putExtra("device_id", map.get("device_id"));

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, uniqueCode, notificationIntent, Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, context.getString(R.string.notify_channel_id))
                .setContentTitle("New pair request incoming!")
                .setContentText("click here to pair device")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_fluent_arrow_sync_checkmark_24_regular))
                .setGroup(context.getPackageName() + ".NOTIFICATION")
                .setGroupSummary(true)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSmallIcon(R.drawable.ic_notification);
            CharSequence channelName = context.getString(R.string.notify_channel_name);
            String description = context.getString(R.string.notify_channel_description);
            NotificationChannel channel = new NotificationChannel(context.getString(R.string.notify_channel_id), channelName, NotificationManager.IMPORTANCE_HIGH);

            channel.setDescription(description);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        } else builder.setSmallIcon(R.mipmap.ic_notification);

        assert notificationManager != null;
        notificationManager.notify((int)((new Date().getTime() / 1000L) % Integer.MAX_VALUE), builder.build());
    }

    public static void checkPairResultAndRegister(Map<String, String> map,PairDeviceInfo info, Context context) {
        if(isShowDebugLog(context)) Log.i("pair result", "device name: " + map.get("device_name") + " /device id: " + map.get("device_id") + " /result: " + map.get("pair_accept"));
        if(m_onDevicePairResultListener != null) m_onDevicePairResultListener.onReceive(map);
        if("true".equals(map.get("pair_accept"))) {
            SharedPreferences prefs = context.getSharedPreferences("com.noti.main_pair", MODE_PRIVATE);
            boolean isNotRegistered = true;
            String dataToSave = map.get("device_name") + "|" + map.get("device_id");

            Set<String> list = new HashSet<>(prefs.getStringSet("paired_list", new HashSet<>()));
            for(String str : list) {
                if(str.equals(dataToSave)) {
                    isNotRegistered = false;
                    break;
                }
            }

            if(isNotRegistered) {
                list.add(dataToSave);
                prefs.edit().putStringSet("paired_list", list).apply();
            }

            Application.isFindingDeviceToPair = false;
            FirebaseMessageService.pairingProcessList.remove(info);
        }
    }

    public static boolean isShowDebugLog(Context context) {
        return context.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE).getBoolean("printDebugLog", false);
    }
}
