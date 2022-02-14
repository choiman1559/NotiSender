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
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import com.noti.main.BuildConfig;
import com.noti.main.R;
import com.noti.main.receiver.FindDeviceCancelReceiver;
import com.noti.main.ui.receive.NotificationViewActivity;
import com.noti.main.ui.receive.SmsViewActivity;
import com.noti.main.ui.receive.TelecomViewActivity;
import com.noti.main.utils.AESCrypto;
import com.noti.main.utils.CompressStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import me.pushy.sdk.lib.jackson.databind.ObjectMapper;

import static com.noti.main.service.NotiListenerService.getUniqueID;

public class FirebaseMessageService extends FirebaseMessagingService {

    SharedPreferences prefs;
    SharedPreferences logPrefs;
    public static volatile Ringtone lastPlayedRingtone;
    public static final Thread ringtonePlayedThread = new Thread(() -> {
        while(true) {
            if(lastPlayedRingtone != null && !lastPlayedRingtone.isPlaying()) lastPlayedRingtone.play();
        }
    });

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
        logPrefs = getSharedPreferences("com.noti.main_logs", MODE_PRIVATE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        if(BuildConfig.DEBUG) Log.d(remoteMessage.getMessageId(), remoteMessage.toString());
        Map<String,String> map = remoteMessage.getData();

        String rawPassword = prefs.getString("EncryptionPassword", "");
        if("true".equals(map.get("encrypted"))) {
            if(prefs.getBoolean("UseDataEncryption", false) && !rawPassword.equals("")) {
                try {
                    String uid = FirebaseAuth.getInstance().getUid();
                    if (uid != null) {
                        JSONObject object = new JSONObject(AESCrypto.decrypt(CompressStringUtil.decompressString(map.get("encryptedData")), AESCrypto.parseAESToken(AESCrypto.decrypt(rawPassword, AESCrypto.parseAESToken(uid)))));
                        Map<String, String> newMap = new ObjectMapper().readValue(object.toString(), Map.class);
                        processReception(newMap, this);
                    }
                } catch (GeneralSecurityException e) {
                    Handler mHandler = new Handler(Looper.getMainLooper());
                    mHandler.postDelayed(() -> Toast.makeText(this, "Error occcured while decrypting data!\nPlease check password and try again!", Toast.LENGTH_SHORT).show(), 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else processReception(map, this);
    }

    private void processReception(Map<String,String> map, Context context) {
        String type = map.get("type");
        String mode = prefs.getString("service", "");
        Log.d("ddd","process");

        if(type != null && mode != null) {
            if (prefs.getBoolean("serviceToggle", false) && !prefs.getString("UID", "").equals("")) {

                String Date = map.get("date");
                if(Date != null) {
                    String DeadlineValue = prefs.getString("ReceiveDeadline", "No deadline");
                    if (!DeadlineValue.equals("No deadline")) {
                        if (DeadlineValue.equals("Custom…"))
                            DeadlineValue = prefs.getString("DeadlineCustomValue", "5 min");
                        try {
                            long calculated = parseTimeAndUnitToLong(DeadlineValue);
                            Date ReceivedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(Date);
                            if (ReceivedDate != null && (System.currentTimeMillis() - ReceivedDate.getTime()) > calculated) {
                                return;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                if (mode.equals("reception") || mode.equals("hybrid") && type.contains("send")) {
                    if (mode.equals("hybrid") && isDeviceItself(map, context)) return;
                    switch (type) {
                        case "send|normal":
                            sendNotification(map);
                            break;
                        case "send|sms":
                            sendSmsNotification(map);
                            break;
                        case "send|telecom":
                            sendTelecomNotification(map);
                            break;
                    }
                } else if ((mode.equals("send") || mode.equals("hybrid")) && type.contains("reception")) {
                    if ((Build.MANUFACTURER + " " + Build.MODEL).equals(map.get("send_device_name")) && getUniqueID(context).equals(map.get("send_device_id"))) {
                        if (type.equals("reception|normal")) {
                            startNewRemoteActivity(map);
                        } else if (type.equals("reception|sms")) {
                            startNewRemoteSms(map);
                        }
                    }
                }

                if(type.equals("send|find") && !isDeviceItself(map, context) && !prefs.getBoolean("NotReceiveFindDevice", false)) {
                    sendFindTaskNotification();
                }
            }
        }
    }

    protected boolean isDeviceItself(Map<String, String> map, Context context) {
        String Device_name = map.get("device_name");
        String Device_id = map.get("device_id");

        if(Device_id == null || Device_name == null) {
            Device_id = map.get("send_device_id");
            Device_name = map.get("send_device_name");
        }

        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getUniqueID(context);

        return DEVICE_NAME.equals(Device_name) && DEVICE_ID.equals(Device_id);
    }

    protected void startNewRemoteActivity(Map<String, String> map) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> Toast.makeText(FirebaseMessageService.this, "Remote run by NotiSender\nfrom " + map.get("device_name"), Toast.LENGTH_SHORT).show(), 0);
        String Package = map.get("package");
        try{
            getPackageManager().getPackageInfo(Package, PackageManager.GET_ACTIVITIES);
            Intent intent = getPackageManager().getLaunchIntentForPackage(Package);
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Package));
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    protected void startNewRemoteSms(Map<String, String> map) {
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(map.get("address"),null,map.get("message"),null,null);
        new Handler(Looper.getMainLooper()).postDelayed(() -> Toast.makeText(FirebaseMessageService.this, "Reply message by NotiSender\nfrom " + map.get("device_name"), Toast.LENGTH_SHORT).show(), 0);
    }

    protected void sendFindTaskNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(FirebaseMessageService.this, -2,
                new Intent(FirebaseMessageService.this, FindDeviceCancelReceiver.class),
                Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notify_channel_id))
                .setContentTitle("Finding my devices...")
                .setContentText("User requested playing sound\nto find the device!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(getPackageName() + ".NOTIFICATION")
                .setGroupSummary(false)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(R.drawable.ic_info_outline_black_24dp, "Stop", pendingIntent);

        assert notificationManager != null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSmallIcon(R.drawable.ic_notification);
            CharSequence channelName = getString(R.string.notify_channel_name);
            String description = getString(R.string.notify_channel_description);

            NotificationChannel channel = new NotificationChannel(getString(R.string.notify_channel_id), channelName, getImportance());
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        } else builder.setSmallIcon(R.mipmap.ic_notification);

        final int findDeviceNotificationId = -2;
        notificationManager.notify(findDeviceNotificationId, builder.build());

        AudioManager audioManager = (AudioManager)getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        audioManager.setSpeakerphoneOn(true);

        if(lastPlayedRingtone != null && lastPlayedRingtone.isPlaying()) lastPlayedRingtone.stop();
        lastPlayedRingtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        if(Build.VERSION.SDK_INT >= 28) {
            lastPlayedRingtone.setLooping(true);
            lastPlayedRingtone.play();
        } else ringtonePlayedThread.start();
    }

    protected void sendTelecomNotification(Map<String, String> map) {
        String address = map.get("address");
        String Package = map.get("package");
        String Device_name = map.get("device_name");
        String Device_id = map.get("device_id");
        String Date = map.get("date");

        Intent notificationIntent = new Intent(FirebaseMessageService.this, TelecomViewActivity.class);
        notificationIntent.putExtra("device_id",Device_id);
        notificationIntent.putExtra("address", address);
        notificationIntent.putExtra("device_name",Device_name);
        notificationIntent.putExtra("date",Date);
        notificationIntent.putExtra("package", Package);

        int uniqueCode = 0;
        try {
            if(Date != null) {
                Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(Date);
                uniqueCode = d == null ? 0 : (int) ((d.getTime() / 1000L) % Integer.MAX_VALUE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, uniqueCode, notificationIntent, Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notify_channel_id))
                .setContentTitle("New call inbound from " + address)
                .setContentText("click here to reply or open dialer")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setGroup(getPackageName() + ".NOTIFICATION")
                .setGroupSummary(true)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSmallIcon(R.drawable.ic_notification);
            CharSequence channelName = getString(R.string.notify_channel_name);
            String description = getString(R.string.notify_channel_description);
            NotificationChannel channel = new NotificationChannel(getString(R.string.notify_channel_id), channelName, getImportance());

            channel.setDescription(description);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        } else builder.setSmallIcon(R.mipmap.ic_notification);

        assert notificationManager != null;
        notificationManager.notify((int)((new Date().getTime() / 1000L) % Integer.MAX_VALUE), builder.build());
        playRingtoneAndVibrate();
    }

    protected void sendSmsNotification(Map<String, String> map) {
        String address = map.get("address");
        String message = map.get("message");
        String Package = map.get("package");
        String Device_name = map.get("device_name");
        String Device_id = map.get("device_id");
        String Date = map.get("date");

        Intent notificationIntent = new Intent(FirebaseMessageService.this, SmsViewActivity.class);
        notificationIntent.putExtra("device_id",Device_id);
        notificationIntent.putExtra("message",message);
        notificationIntent.putExtra("address",address);
        notificationIntent.putExtra("device_name",Device_name);
        notificationIntent.putExtra("date",Date);
        notificationIntent.putExtra("package", Package);

        int uniqueCode = 0;
        try {
            if(Date != null) {
                Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(Date);
                uniqueCode = d == null ? 0 : (int) ((d.getTime() / 1000L) % Integer.MAX_VALUE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, uniqueCode, notificationIntent, Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notify_channel_id))
                .setContentTitle("New message from " + address)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setGroup(getPackageName() + ".NOTIFICATION")
                .setGroupSummary(true)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSmallIcon(R.drawable.ic_notification);
            CharSequence channelName = getString(R.string.notify_channel_name);
            String description = getString(R.string.notify_channel_description);
            NotificationChannel channel = new NotificationChannel(getString(R.string.notify_channel_id), channelName, getImportance());

            channel.setDescription(description);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        } else builder.setSmallIcon(R.mipmap.ic_notification);

        assert notificationManager != null;
        notificationManager.notify((int)((new Date().getTime() / 1000L) % Integer.MAX_VALUE), builder.build());
        playRingtoneAndVibrate();
    }

    protected void sendNotification(Map<String, String> map) {
        Log.d("ddd","noti");
        String title = map.get("title");
        String content = map.get("message");
        String Package = map.get("package");
        String AppName =  map.get("appname");
        String Device_name = map.get("device_name");
        String Device_id = map.get("device_id");
        String Date = map.get("date");

        Bitmap Icon_original = null;
        Bitmap Icon = null;
        if (!"none".equals(map.get("icon"))) {
            Icon_original = CompressStringUtil.StringToBitmap(CompressStringUtil.decompressString(map.get("icon")));
            if(Icon_original != null) {
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
                String originString = logPrefs.getString("receivedLogs","");

                if(!originString.equals("")) array = new JSONArray(originString);
                object.put("date",Date);
                object.put("package",Package);
                object.put("title",title);
                object.put("text",content);
                object.put("device",Device_name);
                array.put(object);
                logPrefs.edit().putString("receivedLogs",array.toString()).apply();

                if(array.length() >= prefs.getInt("HistoryLimit",150)) {
                    int a = array.length() - prefs.getInt("HistoryLimit",150);
                    for(int i = 0;i < a;i++){
                        array.remove(i);
                    }
                    logPrefs.edit().putString("receivedLogs", array.toString()).apply();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }).start();

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(FirebaseMessageService.this, NotificationViewActivity.class);
        int uniqueCode = 0;
        try {
            if(Date != null) {
                Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(Date);
                uniqueCode = d == null ? 0 : (int) ((d.getTime() / 1000L) % Integer.MAX_VALUE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        notificationIntent.putExtra("package", Package);
        notificationIntent.putExtra("device_id",Device_id);
        notificationIntent.putExtra("appname",AppName);
        notificationIntent.putExtra("title",title);
        notificationIntent.putExtra("device_name",Device_name);
        notificationIntent.putExtra("date",Date);
        notificationIntent.putExtra("icon",Icon_original != null ? Icon : null);

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, uniqueCode, notificationIntent, Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notify_channel_id))
                .setContentTitle(title + " (" + AppName + ")")
                .setContentText(content)
                .setPriority(Build.VERSION.SDK_INT > 23 ? getPriority() : NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setGroup(getPackageName() + ".NOTIFICATION")
                .setGroupSummary(true)
                .setAutoCancel(true);

        if(Icon != null) builder.setLargeIcon(Icon);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setSmallIcon(R.drawable.ic_notification);
            CharSequence channelName = getString(R.string.notify_channel_name);
            String description = getString(R.string.notify_channel_description);
            NotificationChannel channel = new NotificationChannel(getString(R.string.notify_channel_id), channelName, getImportance());

            channel.setDescription(description);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);
        } else builder.setSmallIcon(R.mipmap.ic_notification);

        notificationManager.notify(uniqueCode, builder.build());
        playRingtoneAndVibrate();
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
            case "Custom…":
                return NotificationManager.IMPORTANCE_NONE;
            default:
                return NotificationManager.IMPORTANCE_UNSPECIFIED;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private int getPriority() {
        String value = prefs.getString("importance","Default");
        switch (value) {
            case "Low":
                return NotificationCompat.PRIORITY_LOW;
            case "High":
                return NotificationCompat.PRIORITY_MAX;
            default:
                return NotificationCompat.PRIORITY_DEFAULT;
        }
    }

    private long parseTimeAndUnitToLong(String data) {
        String[] foo = data.split(" ");
        long numberToMultiply;
        switch(foo[1]) {
            case "sec":
                numberToMultiply = 1000L;
                break;

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
        return Long.parseLong(foo[0]) * numberToMultiply;
    }

    private void playRingtoneAndVibrate() {
        if(prefs.getString("importance","Default").equals("Custom…")) {

            int VibrationRunningTimeValue = prefs.getInt("VibrationRunningTime", 1000);
            if(VibrationRunningTimeValue > 0) {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(VibrationRunningTimeValue);
            }

            if(lastPlayedRingtone != null && lastPlayedRingtone.isPlaying()) {
                if(ringtonePlayedThread.isAlive()) return;
                else lastPlayedRingtone.stop();
            }

            Ringtone r;
            String s = prefs.getString("CustomRingtone", "");
            DocumentFile AudioMedia = DocumentFile.fromSingleUri(this, Uri.parse(s));
            if (!s.isEmpty() && AudioMedia != null && AudioMedia.exists())
                r = RingtoneManager.getRingtone(this, AudioMedia.getUri());
            else
                r = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

            r.play();
            lastPlayedRingtone = r;

            String data = prefs.getString("RingtoneRunningTime", "3 sec");
            new Thread(() -> {
                long runningTime = parseTimeAndUnitToLong(data);
                long startTime = System.currentTimeMillis();
                while (true) {
                    if(!r.isPlaying()) break;
                    if ((System.currentTimeMillis() - startTime) > runningTime) {
                        if(r.isPlaying()) r.stop();
                        break;
                    }
                }
            }).start();
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        if (!prefs.getString("UID", "").equals(""))
            FirebaseMessaging.getInstance().subscribeToTopic(prefs.getString("UID", ""));
    }
}