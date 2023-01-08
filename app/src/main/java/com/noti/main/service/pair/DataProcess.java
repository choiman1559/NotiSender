package com.noti.main.service.pair;

import static android.content.Context.MODE_PRIVATE;
import static com.noti.main.service.NotiListenerService.getUniqueID;
import static com.noti.main.service.NotiListenerService.sendNotification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.application.isradeleon.notify.Notify;

import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.receiver.TaskerPairEventKt;
import com.noti.main.utils.AsyncTask;
import com.noti.main.utils.PowerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

public class DataProcess {
    public static void requestData(Context context, String Device_name, String Device_id, String dataType) {
        Date date = Calendar.getInstance().getTime();
        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getUniqueID();
        String TOPIC = "/topics/" + context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).getString("UID", "");

        JSONObject notificationHead = new JSONObject();
        JSONObject notificationBody = new JSONObject();
        try {
            notificationBody.put("type", "pair|request_data");
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("send_device_name", Device_name);
            notificationBody.put("send_device_id", Device_id);
            notificationBody.put("request_data", dataType);
            notificationBody.put("date", date);

            notificationHead.put("to", TOPIC);
            notificationHead.put("priority", "high");
            notificationHead.put("data", notificationBody);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
        sendNotification(notificationHead, context.getPackageName(), context);
    }

    public static void requestAction(Context context, String Device_name, String Device_id, String dataType, String... args) {
        StringBuilder dataToSend = new StringBuilder();
        if (args.length > 1) {
            for (String str : args) {
                dataToSend.append(str).append("|");
            }
            dataToSend.setCharAt(dataToSend.length() - 1, '\0');
        } else if (args.length == 1) dataToSend.append(args[0]);

        Date date = Calendar.getInstance().getTime();
        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getUniqueID();
        String TOPIC = "/topics/" + context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).getString("UID", "");

        JSONObject notificationHead = new JSONObject();
        JSONObject notificationBody = new JSONObject();
        try {
            notificationBody.put("type", "pair|request_action");
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("send_device_name", Device_name);
            notificationBody.put("send_device_id", Device_id);
            notificationBody.put("request_action", dataType);
            notificationBody.put("date", date);
            if (args.length > 0) notificationBody.put("action_args", dataToSend.toString());

            notificationHead.put("to", TOPIC);
            notificationHead.put("priority", "high");
            notificationHead.put("data", notificationBody);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
        sendNotification(notificationHead, context.getPackageName(), context);
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
                    break;

                case "":
                    break;
            }
        }

        if (dataToSend.isEmpty()) return;
        Date date = Calendar.getInstance().getTime();
        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getUniqueID();
        String TOPIC = "/topics/" + context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).getString("UID", "");

        JSONObject notificationHead = new JSONObject();
        JSONObject notificationBody = new JSONObject();
        try {
            notificationBody.put("type", "pair|receive_data");
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("send_device_name", map.get("device_name"));
            notificationBody.put("send_device_id", map.get("device_id"));
            notificationBody.put("receive_data", dataToSend);
            notificationBody.put("request_data", dataType);
            notificationBody.put("date", date);

            notificationHead.put("to", TOPIC);
            notificationHead.put("priority", "high");
            notificationHead.put("data", notificationBody);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
        sendNotification(notificationHead, context.getPackageName(), context);
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
                case "Show notification with text":
                    Notify.build(context)
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
                    break;

                case "Copy text to clipboard":
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Shared from " + Device_name, actionArgs[0]);
                    clipboard.setPrimaryClip(clip);
                    break;

                case "Open link in Browser":
                    String url = actionArgs[0];
                    if (!url.startsWith("http://") && !url.startsWith("https://"))
                        url = "http://" + url;
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    context.startActivity(browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    break;

                case "Trigger tasker event":
                    if (Device_name != null && Device_id != null) {
                        TaskerPairEventKt.callTaskerEvent(Device_name, Device_id, context);
                    }
                    break;

                case "Run application":
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
                    break;

                case "Run command":
                    final String[] finalActionArgs = actionArgs;
                    new Thread(() -> {
                        try {
                            if (finalActionArgs.length > 0)
                                Runtime.getRuntime().exec(finalActionArgs);
                        } catch (RuntimeException | IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                    break;

                case "Share file":
                    int notificationId = (int) ((new Date().getTime() / 1000L) % Integer.MAX_VALUE);
                    String notificationChannel = "DownloadFile";
                    NotificationManager mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationChannel channel = new NotificationChannel(notificationChannel, "Download File Notification", NotificationManager.IMPORTANCE_DEFAULT);
                        mNotifyManager.createNotificationChannel(channel);
                    }

                    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, notificationChannel)
                            .setContentTitle("File Download")
                            .setContentText("File name: " + actionArg)
                            .setSmallIcon(R.drawable.ic_fluent_arrow_download_24_regular)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setOnlyAlertOnce(true)
                            .setGroupSummary(false)
                            .setOngoing(true)
                            .setAutoCancel(false);
                    mBuilder.setProgress(0, 0, true);
                    mNotifyManager.notify(notificationId, mBuilder.build());

                    new Thread(() -> {
                        FirebaseStorage storage = FirebaseStorage.getInstance();
                        StorageReference storageRef = storage.getReferenceFromUrl("gs://notisender-41c1b.appspot.com");
                        StorageReference fileRef = storageRef.child(context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).getString("UID", "") + "/" + actionArg);

                        try {
                            if (Build.VERSION.SDK_INT < 29) {
                                File targetFile = new File(Environment.getExternalStorageDirectory(), "Download/NotiSender/" + actionArg);
                                targetFile.mkdirs();
                                if (targetFile.exists()) targetFile.delete();
                                targetFile.createNewFile();
                                FileDownloadTask task = fileRef.getFile(targetFile);

                                task.addOnSuccessListener(taskSnapshot -> {
                                    mBuilder.setContentText(actionArg + " download completed.\nCheck download folder!")
                                            .setProgress(0, 0, false)
                                            .setOngoing(false);
                                    mNotifyManager.notify(notificationId, mBuilder.build());
                                });

                                task.addOnFailureListener(exception -> {
                                    mBuilder.setContentText(actionArg + " download failed")
                                            .setProgress(0, 0, false)
                                            .setOngoing(false);
                                    mNotifyManager.notify(notificationId, mBuilder.build());
                                });

                                task.addOnProgressListener(snapshot -> {
                                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                                    mBuilder.setProgress(100, (int) progress, false);
                                    mNotifyManager.notify(notificationId, mBuilder.build());
                                });
                            } else {
                                fileRef.getMetadata().addOnSuccessListener(storageMetadata -> {
                                    ContentResolver resolver = context.getContentResolver();
                                    ContentValues contentValues = new ContentValues();
                                    contentValues.put(MediaStore.Downloads.DISPLAY_NAME, actionArg);
                                    contentValues.put(MediaStore.Downloads.RELATIVE_PATH, "Download/" + "NotiSender");
                                    contentValues.put(MediaStore.Downloads.MIME_TYPE, storageMetadata.getContentType());
                                    contentValues.put(MediaStore.Downloads.IS_PENDING, true);
                                    Uri uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                                    Uri itemUri = resolver.insert(uri, contentValues);

                                    fileRef.getStream().addOnSuccessListener(stream -> {
                                        if (itemUri != null) {
                                            AsyncTask<Void, Void, Void> downloadTask = new AsyncTask<>() {
                                                @Override
                                                protected Void doInBackground(Void... voids) {
                                                    try {
                                                        InputStream inputStream = stream.getStream();
                                                        OutputStream outputStream = resolver.openOutputStream(itemUri);
                                                        byte[] buffer = new byte[102400];
                                                        int len;
                                                        while ((len = inputStream.read(buffer)) > 0) {
                                                            outputStream.write(buffer, 0, len);
                                                        }
                                                        outputStream.close();

                                                        contentValues.put(MediaStore.Images.Media.IS_PENDING, false);
                                                        resolver.update(itemUri, contentValues, null, null);
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                    return null;
                                                }

                                                @Override
                                                protected void onPostExecute(Void unused) {
                                                    super.onPostExecute(unused);
                                                    mBuilder.setContentText(actionArg + " download completed.\nCheck download folder!")
                                                            .setProgress(0, 0, false)
                                                            .setOngoing(false);
                                                    mNotifyManager.notify(notificationId, mBuilder.build());
                                                }
                                            };
                                            downloadTask.execute();
                                        }
                                    }).addOnFailureListener(e -> {
                                        e.printStackTrace();
                                        mBuilder.setContentText(actionArg + " download failed")
                                                .setProgress(0, 0, false)
                                                .setOngoing(false);
                                        mNotifyManager.notify(notificationId, mBuilder.build());
                                    });
                                }).addOnFailureListener(e -> {
                                    e.printStackTrace();
                                    mBuilder.setContentText(actionArg + " download failed")
                                            .setProgress(0, 0, false)
                                            .setOngoing(false);
                                    mNotifyManager.notify(notificationId, mBuilder.build());
                                });
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            mNotifyManager.cancel(notificationId);
                        }
                    }).start();
                    break;
            }
        }
    }
}
