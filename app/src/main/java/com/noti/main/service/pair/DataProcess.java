package com.noti.main.service.pair;

import static android.content.Context.MODE_PRIVATE;
import static com.noti.main.service.NotiListenerService.getDeviceName;
import static com.noti.main.service.NotiListenerService.getUniqueID;
import static com.noti.main.service.NotiListenerService.sendNotification;

import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.receiver.TaskerPairEventKt;
import com.noti.main.service.NotiListenerService;
import com.noti.main.service.refiler.FileTransferService;
import com.noti.main.utils.PowerUtils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.application.isradeleon.notify.Notify;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

public class DataProcess {
    public static void pushPluginRemoteAction(Context context, String Device_name, String Device_id, String pluginPackage, String actionType, String actionName, String args) {
        String DEVICE_NAME = getDeviceName();
        String DEVICE_ID = getUniqueID();
        JSONObject notificationBody = new JSONObject();

        try {
            notificationBody.put("type", "pair|plugin");
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("send_device_name", Device_name);
            notificationBody.put("send_device_id", Device_id);
            notificationBody.put("date", Application.getDateString());

            notificationBody.put("plugin_action_type", actionType);
            notificationBody.put("plugin_action_name", actionName);
            notificationBody.put("plugin_extra_data", args);
            notificationBody.put("plugin_package", pluginPackage);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
        sendNotification(notificationBody, context.getPackageName(), context, true);
    }

    public static void requestData(Context context, String Device_name, String Device_id, String dataType) {
        String DEVICE_NAME = NotiListenerService.getDeviceName();
        String DEVICE_ID = getUniqueID();
        JSONObject notificationBody = new JSONObject();

        try {
            notificationBody.put("type", "pair|request_data");
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("send_device_name", Device_name);
            notificationBody.put("send_device_id", Device_id);
            notificationBody.put("request_data", dataType);
            notificationBody.put("date", Application.getDateString());
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }

        sendNotification(notificationBody, context.getPackageName(), context, true);
    }

    public static void requestAction(Context context, String Device_name, String Device_id, String dataType, String... args) {
        StringBuilder dataToSend = new StringBuilder();
        if (args.length > 1) {
            for (String str : args) {
                dataToSend.append(str).append("|");
            }
            dataToSend.setCharAt(dataToSend.length() - 1, '\0');
        } else if (args.length == 1) dataToSend.append(args[0]);

        String DEVICE_NAME = NotiListenerService.getDeviceName();
        String DEVICE_ID = getUniqueID();
        JSONObject notificationBody = new JSONObject();

        try {
            notificationBody.put("type", "pair|request_action");
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("send_device_name", Device_name);
            notificationBody.put("send_device_id", Device_id);
            notificationBody.put("request_action", dataType);
            notificationBody.put("date", Application.getDateString());
            if (args.length > 0) notificationBody.put("action_args", dataToSend.toString());
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
        sendNotification(notificationBody, context.getPackageName(), context, true);
    }

    public static void onDataRequested(Map<String, String> map, Context context) {
        String dataType = map.get("request_data");
        String dataToSend = "";
        if (dataType != null) {
            switch (dataType) {
                case "battery_info":
                    IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = context.registerReceiver(null, filter);
                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

                    int batteryPct = (int) (level * 100 / (float) scale);
                    boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                    boolean isBatterySaver = powerManager.isPowerSaveMode();

                    dataToSend = batteryPct + "|" + isCharging + "|" + isBatterySaver;
                    dataToSend += ("|" + context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).getBoolean("serviceToggle", false));
                    break;

                case "":
                    break;
            }
        }

        if (dataToSend.isEmpty()) return;
        String DEVICE_NAME = NotiListenerService.getDeviceName();
        String DEVICE_ID = getUniqueID();
        JSONObject notificationBody = new JSONObject();

        try {
            notificationBody.put("type", "pair|receive_data");
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("send_device_name", map.get("device_name"));
            notificationBody.put("send_device_id", map.get("device_id"));
            notificationBody.put("receive_data", dataToSend);
            notificationBody.put("request_data", dataType);
            notificationBody.put("date", Application.getDateString());
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
        sendNotification(notificationBody, context.getPackageName(), context, true);
    }

    public static void onActionRequested(Map<String, String> map, Context context) {
        PowerUtils.getInstance(context).acquire();
        String Device_id = map.get("device_id");
        String Device_name = map.get("device_name");
        String actionType = map.get("request_action");
        String actionArg = map.get("action_args");
        String[] actionArgs = {};

        if (actionArg != null) {
            actionArgs = actionArg.split("\\|");
        }

        if (actionType != null) {
            switch (actionType) {
                case "Show notification with text" -> Notify.build(context)
                        .setTitle(actionArgs[0])
                        .setContent(actionArgs[1])
                        .setLargeIcon(R.mipmap.ic_launcher)
                        .largeCircularIcon()
                        .setSmallIcon(R.drawable.ic_broken_image)
                        .setChannelName("")
                        .setChannelId("Notification Test")
                        .enableVibration(true)
                        .setAutoCancel(true)
                        .show();

                case "Copy text to clipboard" -> {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Shared from " + Device_name, actionArgs[0]);
                    clipboard.setPrimaryClip(clip);
                }

                case "Open link in Browser" -> {
                    String url = actionArgs[0];
                    if (!url.startsWith("http://") && !url.startsWith("https://"))
                        url = "http://" + url;
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    context.startActivity(browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }

                case "Trigger tasker event" -> {
                    if (Device_name != null && Device_id != null) {
                        TaskerPairEventKt.callTaskerEvent(Device_name, Device_id, context);
                    }
                }

                case "Run application" -> {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> Toast.makeText(context, "Remote run by NotiSender\nfrom " + map.get("device_name"), Toast.LENGTH_SHORT).show(), 0);
                    String Package = actionArgs[0];
                    try {
                        PackageManager pm = context.getPackageManager();
                        pm.getPackageInfo(Package, PackageManager.GET_ACTIVITIES);
                        Intent intent = pm.getLaunchIntentForPackage(Package);
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception e) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Package));
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                }

                case "Run command" -> {
                    final String[] finalActionArgs = actionArgs;
                    new Thread(() -> {
                        try {
                            if (finalActionArgs.length > 0)
                                Runtime.getRuntime().exec(finalActionArgs);
                        } catch (RuntimeException | IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }

                case "Share file" -> new FileTransferService(context, true)
                        .setDownloadProperties(actionArg, false)
                        .execute();

                case "toggle_service" -> {
                    SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putBoolean("serviceToggle", Boolean.parseBoolean(actionArg)).apply();
                }
            }
        }
    }
}
