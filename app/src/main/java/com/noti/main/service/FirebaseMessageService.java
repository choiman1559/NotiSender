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
import android.graphics.ImageDecoder;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.MediaStore;
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

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.R;
import com.noti.main.receiver.FindDeviceCancelReceiver;
import com.noti.main.receiver.PushyReceiver;
import com.noti.main.receiver.media.MediaSession;
import com.noti.plugin.data.NotificationData;
import com.noti.main.receiver.plugin.PluginActions;
import com.noti.main.receiver.plugin.PluginConst;
import com.noti.main.receiver.plugin.PluginPrefs;
import com.noti.main.service.pair.DataProcess;
import com.noti.main.service.pair.PairDeviceInfo;
import com.noti.main.service.pair.PairDeviceStatus;
import com.noti.main.service.pair.PairDeviceType;
import com.noti.main.service.pair.PairListener;
import com.noti.main.service.pair.PairingUtils;
import com.noti.main.ui.prefs.custom.RegexInterpreter;
import com.noti.main.ui.receive.NotificationViewActivity;
import com.noti.main.ui.receive.SmsViewActivity;
import com.noti.main.ui.receive.TelecomViewActivity;
import com.noti.main.utils.network.AESCrypto;
import com.noti.main.utils.network.CompressStringUtil;
import com.noti.main.utils.PowerUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import me.pushy.sdk.lib.jackson.databind.ObjectMapper;

import static com.noti.main.Application.pairingProcessList;
import static com.noti.main.service.NotiListenerService.getUniqueID;

public class FirebaseMessageService extends FirebaseMessagingService {

    SharedPreferences prefs;
    SharedPreferences logPrefs;
    SharedPreferences pairPrefs;
    SharedPreferences regexPrefs;
    SharedPreferences deviceBlacksPrefs;

    private static PowerUtils manager;
    public static volatile Ringtone lastPlayedRingtone;
    public static HashMap<String, MediaSession> playingSessionMap;
    public static final ArrayList<SplitDataObject> splitDataList = new ArrayList<>();
    private final PushyReceiver.onPushyMessageListener onPushyMessageListener = message -> preProcessReception(message.getData(), FirebaseMessageService.this);
    public static final Thread ringtonePlayedThread = new Thread(() -> {
        while (true) {
            if (lastPlayedRingtone != null && !lastPlayedRingtone.isPlaying())
                lastPlayedRingtone.play();
        }
    });

    public interface OnNotificationRemoveRequest {
        void onRequested(String key);
    }

    public static OnNotificationRemoveRequest removeListener;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        logPrefs = getSharedPreferences("com.noti.main_logs", MODE_PRIVATE);
        pairPrefs = getSharedPreferences("com.noti.main_pair", MODE_PRIVATE);
        regexPrefs = getSharedPreferences("com.noti.main_regex", MODE_PRIVATE);
        deviceBlacksPrefs = getSharedPreferences("com.noti.main_device.blacklist", MODE_PRIVATE);
        playingSessionMap = new HashMap<>();
        manager = PowerUtils.getInstance(this);
        manager.acquire();
        PushyReceiver.setOnPushyMessageListener(this.onPushyMessageListener);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        manager.acquire();

        if (BuildConfig.DEBUG) Log.d(remoteMessage.getMessageId(), remoteMessage.toString());
        Map<String, String> map = remoteMessage.getData();
        preProcessReception(map, this);
    }

    @SuppressWarnings("unchecked")
    public void preProcessReception(Map <String, String> map, Context context) {
        String rawPassword = prefs.getString("EncryptionPassword", "");
        if ("true".equals(map.get("encrypted"))) {
            if (prefs.getBoolean("UseDataEncryption", false) && !rawPassword.equals("")) {
                try {
                    String uid = FirebaseAuth.getInstance().getUid();
                    if (uid != null) {
                        JSONObject object = new JSONObject(AESCrypto.decrypt(CompressStringUtil.decompressString(map.get("encryptedData")), AESCrypto.parseAESToken(AESCrypto.decrypt(rawPassword, AESCrypto.parseAESToken(uid)))));
                        Map<String, String> newMap = new ObjectMapper().readValue(object.toString(), Map.class);
                        processReception(newMap, context);
                    }
                } catch (GeneralSecurityException e) {
                    Handler mHandler = new Handler(Looper.getMainLooper());
                    mHandler.postDelayed(() -> Toast.makeText(context, "Error occurred while decrypting data!\nPlease check password and try again!", Toast.LENGTH_SHORT).show(), 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else processReception(map, context);
    }

    public void processReception(Map<String, String> map, Context context) {
        String type = map.get("type");
        String mode = prefs.getString("service", "reception");

        if (type != null && !prefs.getString("UID", "").equals("")) {
            if (prefs.getBoolean("serviceToggle", false)) {
                if ("split_data".equals(type) && !isDeviceItself(map)) {
                    processSplitData(map, context);
                    return;
                }

                String Date = map.get("date");
                if (Date != null) {
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

                if (deviceBlacksPrefs.getBoolean(map.get("device_id"), false)) {
                    return;
                }

                if (mode.equals("reception") || mode.equals("hybrid") && type.contains("send")) {
                    if (mode.equals("hybrid") && isDeviceItself(map)) return;
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
                    if ((Build.MANUFACTURER + " " + Build.MODEL).equals(map.get("send_device_name")) && getUniqueID().equals(map.get("send_device_id"))) {
                        if (type.equals("reception|normal")) {
                            startNewRemoteActivity(map);
                        } else if (type.equals("reception|sms")) {
                            startNewRemoteSms(map);
                        }
                    }
                }

                if (type.equals("send|find") && !isDeviceItself(map) && !prefs.getBoolean("NotReceiveFindDevice", false)) {
                    sendFindTaskNotification();
                }

                if (type.startsWith("media") && prefs.getBoolean("UseMediaSync", false)) {
                    manager.acquire();
                    try {
                        String raw = map.get("media_data");
                        if (raw != null && !raw.isEmpty()) {
                            JSONObject object = new JSONObject(raw);

                            switch (type) {
                                case "media|meta_data":
                                    if (!isDeviceItself(map)) {
                                        MediaSession current;
                                        if (!playingSessionMap.containsKey(map.get("device_id"))) {
                                            current = new MediaSession(this, map.get("device_name"), map.get("device_id"), prefs.getString("UID", ""));
                                            playingSessionMap.put(map.get("device_id"), current);
                                        } else {
                                            current = playingSessionMap.get(map.get("device_id"));
                                        }

                                        assert current != null;
                                        current.update(object);
                                    }
                                    break;

                                case "media|action":
                                    if (isTargetDevice(map)) {
                                        NotiListenerService.getInstance().mediaReceiver.onDataReceived(object);
                                    }
                                    break;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            if(prefs.getBoolean("pairToggle", false)) {
                if (type.startsWith("pair") && !isDeviceItself(map)) {
                    switch (type) {
                        case "pair|request_device_list":
                            //Target Device action
                            //Have to Send this device info Data Now
                            if (!isPairedDevice(map) || prefs.getBoolean("showAlreadyConnected", false)) {
                                PairDeviceInfo info = new PairDeviceInfo(map.get("device_name"), map.get("device_id"), PairDeviceStatus.Device_Process_Pairing);
                                info.setDeviceType(new PairDeviceType(map.get("device_type")));
                                pairingProcessList.add(info);
                                Application.isListeningToPair = true;
                                PairingUtils.responseDeviceInfoToFinder(map, context);
                            }
                            break;

                        case "pair|response_device_list":
                            //Request Device Action
                            //Show device list here; give choice to user which device to pair
                            if (Application.isFindingDeviceToPair && (!isPairedDevice(map) || prefs.getBoolean("showAlreadyConnected", false))) {
                                PairDeviceInfo info = new PairDeviceInfo(map.get("device_name"), map.get("device_id"), PairDeviceStatus.Device_Process_Pairing);
                                info.setDeviceType(new PairDeviceType(map.get("device_type")));
                                pairingProcessList.add(info);
                                PairingUtils.onReceiveDeviceInfo(map);
                            }
                            break;

                        case "pair|request_pair":
                            //Target Device action
                            //Show choice notification (or activity) to user whether user wants to pair this device with another one or not
                            if (Application.isListeningToPair && isTargetDevice(map)) {
                                for (PairDeviceInfo info : pairingProcessList) {
                                    if (info.getDevice_name().equals(map.get("device_name")) && info.getDevice_id().equals(map.get("device_id"))) {
                                        PairingUtils.showPairChoiceAction(map, context);
                                        break;
                                    }
                                }
                            }
                            break;

                        case "pair|accept_pair":
                            //Request Device Action
                            //Check if target accepted to pair and process result here
                            if (Application.isFindingDeviceToPair && isTargetDevice(map)) {
                                for (PairDeviceInfo info : pairingProcessList) {
                                    if (info.getDevice_name().equals(map.get("device_name")) && info.getDevice_id().equals(map.get("device_id"))) {
                                        PairingUtils.checkPairResultAndRegister(map, info, context);
                                        break;
                                    }
                                }
                            }
                            break;

                        case "pair|request_remove":
                            if (isTargetDevice(map) && isPairedDevice(map) && prefs.getBoolean("allowRemovePairRemotely", true)) {
                                String dataToFind = map.get("device_name") + "|" + map.get("device_id") + (map.containsKey("device_type") ? "|" + map.get("device_type") : "");
                                String dataToRemove = null;

                                Set<String> list = new HashSet<>(pairPrefs.getStringSet("paired_list", new HashSet<>()));
                                for(String str : list) {
                                    if(str.contains(dataToFind)) {
                                        dataToRemove = str;
                                        break;
                                    }
                                }

                                if(dataToRemove != null) list.remove(dataToRemove);
                                pairPrefs.edit().putStringSet("paired_list", list).apply();
                            }
                            break;

                        case "pair|request_data":
                            //process request normal data here sent by paired device(s).
                            if (isTargetDevice(map) && isPairedDevice(map)) {
                                DataProcess.onDataRequested(map, context);
                            }
                            break;

                        case "pair|receive_data":
                            //process received normal data here sent by paired device(s).
                            if (isTargetDevice(map) && isPairedDevice(map)) {
                                PairListener.callOnDataReceived(map);
                            }
                            break;

                        case "pair|request_action":
                            //process received action data here sent by paired device(s).
                            if (isTargetDevice(map) && isPairedDevice(map)) {
                                DataProcess.onActionRequested(map, context);
                            }
                            break;

                        case "pair|find":
                            if (isTargetDevice(map) && isPairedDevice(map) && !prefs.getBoolean("NotReceiveFindDevice", false)) {
                                sendFindTaskNotification();
                            }
                            break;

                        case "pair|plugin":
                            if (isTargetDevice(map) && isPairedDevice(map) && !prefs.getBoolean("NotReceivePlugin", false)) {
                                String actionType = map.get("plugin_action_type");
                                String actionName = map.get("plugin_action_name");
                                String pluginPackage = map.get("plugin_package");
                                String extraData = map.get("plugin_extra_data");
                                String targetDevice = map.get("device_name") + "|" + map.get("device_id");

                               if(actionType != null) switch (actionType) {
                                   case PluginConst.ACTION_REQUEST_REMOTE_ACTION:
                                       PluginActions.requestAction(context, targetDevice, pluginPackage, actionName, extraData);
                                       break;

                                   case PluginConst.ACTION_REQUEST_REMOTE_DATA:
                                       PluginActions.requestData(context, targetDevice, pluginPackage, actionName);
                                       break;

                                   case PluginConst.ACTION_RESPONSE_REMOTE_DATA:
                                       PluginActions.responseData(context, pluginPackage, actionName, extraData);
                                       break;
                               }
                            }
                            break;
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void processSplitData(Map<String, String> map, Context context) {
        synchronized (splitDataList) {
            Log.d("split_data", "current size : " + splitDataList.size());

            for (int i = 0; i < splitDataList.size(); i++) {
                SplitDataObject object = splitDataList.get(i);
                if (object.unique_id.equals(map.get("split_unique"))) {
                    object = object.addData(map);
                    splitDataList.set(i, object);

                    if (object.length == object.getSize()) {
                        try {
                            Map<String, String> newMap = new ObjectMapper().readValue(object.getFullData(), Map.class);
                            processReception(newMap, context);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            splitDataList.remove(object);
                        }
                    }
                    return;
                }
            }
            splitDataList.add(new SplitDataObject(map));
        }
    }

    protected boolean isDeviceItself(Map<String, String> map) {
        String Device_name = map.get("device_name");
        String Device_id = map.get("device_id");

        if (Device_id == null || Device_name == null) {
            Device_id = map.get("send_device_id");
            Device_name = map.get("send_device_name");
        }

        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getUniqueID();

        return DEVICE_NAME.equals(Device_name) && DEVICE_ID.equals(Device_id);
    }

    protected boolean isTargetDevice(Map<String, String> map) {
        String Device_name = map.get("send_device_name");
        String Device_id = map.get("send_device_id");

        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getUniqueID();

        return DEVICE_NAME.equals(Device_name) && DEVICE_ID.equals(Device_id);
    }

    protected boolean isPairedDevice(Map<String, String> map) {
        String dataToFind = map.get("device_name") + "|" + map.get("device_id");
        for (String str : pairPrefs.getStringSet("paired_list", new HashSet<>())) {
            String[] savedData = str.split("\\|");
            if ((savedData[0] + "|" + savedData[1]).equals(dataToFind)) return true;
        }
        return false;
    }

    protected void startNewRemoteActivity(Map<String, String> map) {
        if(map.containsKey("notification_key") && removeListener != null) {
            removeListener.onRequested(map.get("notification_key"));
        }

        if(!map.containsKey("start_remote_activity") || "true".equals(map.get("start_remote_activity"))) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> Toast.makeText(FirebaseMessageService.this, "Remote run by NotiSender\nfrom " + map.get("device_name"), Toast.LENGTH_SHORT).show(), 0);
            String Package = map.get("package");
            try {
                getPackageManager().getPackageInfo(Package, PackageManager.GET_ACTIVITIES);
                Intent intent = getPackageManager().getLaunchIntentForPackage(Package);
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Package));
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
    }

    protected void startNewRemoteSms(Map<String, String> map) {
        if (map.get("address") != null && map.get("message") != null) {
            PluginActions.requestAction(this, map.get("device_name") + "|" + map.get("device_id"),"com.noti.plugin.telephony", "send_sms",  map.get("address") + "|" + map.get("message") + "|" + map.get("device_name"));
        }
    }

    protected void sendFindTaskNotification() {
        manager.acquire();

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

        AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        audioManager.setSpeakerphoneOn(true);

        if (lastPlayedRingtone != null && lastPlayedRingtone.isPlaying()) lastPlayedRingtone.stop();
        lastPlayedRingtone = RingtoneManager.getRingtone(this, RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE));
        if(lastPlayedRingtone != null) {
            if (Build.VERSION.SDK_INT >= 28) {
                AudioAttributes.Builder audioAttributes = new AudioAttributes.Builder();
                audioAttributes.setUsage(AudioAttributes.USAGE_ALARM);
                audioAttributes.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);

                lastPlayedRingtone.setLooping(true);
                lastPlayedRingtone.setAudioAttributes(audioAttributes.build());
                lastPlayedRingtone.play();
            } else ringtonePlayedThread.start();
        }
    }

    protected void sendTelecomNotification(Map<String, String> map) {
        String address = map.get("address");
        String Package = map.get("package");
        String nickname = map.get("nickname");
        String Device_name = map.get("device_name");
        String Device_id = map.get("device_id");
        String Date = map.get("date");

        Intent notificationIntent = new Intent(FirebaseMessageService.this, TelecomViewActivity.class);
        notificationIntent.putExtra("device_id", Device_id);
        notificationIntent.putExtra("address", address);
        notificationIntent.putExtra("nickname", nickname);
        notificationIntent.putExtra("device_name", Device_name);
        notificationIntent.putExtra("date", Date);
        notificationIntent.putExtra("package", Package);

        int uniqueCode = 0;
        try {
            if (Date != null) {
                Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(Date);
                uniqueCode = d == null ? 0 : (int) ((d.getTime() / 1000L) % Integer.MAX_VALUE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, uniqueCode, notificationIntent, Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notify_channel_id))
                .setContentTitle("New call inbound from " + address + (nickname == null || nickname.isEmpty() ? "" : " (" + nickname + ")"))
                .setContentText("click here to reply or open dialer")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if(Build.VERSION.SDK_INT < 33) {
            builder
                    .setGroup(getPackageName() + ".NOTIFICATION")
                    .setGroupSummary(true);
        }

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
        notificationManager.notify(uniqueCode, builder.build());
        playRingtoneAndVibrate();
    }

    protected void sendSmsNotification(Map<String, String> map) {
        String address = map.get("address");
        String nickname = map.get("nickname");
        String message = map.get("message");
        String Package = map.get("package");
        String Device_name = map.get("device_name");
        String Device_id = map.get("device_id");
        String Date = map.get("date");

        Intent notificationIntent = new Intent(FirebaseMessageService.this, SmsViewActivity.class);
        notificationIntent.putExtra("device_id", Device_id);
        notificationIntent.putExtra("message", message);
        notificationIntent.putExtra("address", address);
        notificationIntent.putExtra("nickname", nickname);
        notificationIntent.putExtra("device_name", Device_name);
        notificationIntent.putExtra("date", Date);
        notificationIntent.putExtra("package", Package);

        int uniqueCode = 0;
        try {
            if (Date != null) {
                Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(Date);
                uniqueCode = d == null ? 0 : (int) ((d.getTime() / 1000L) % Integer.MAX_VALUE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, uniqueCode, notificationIntent, Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notify_channel_id))
                .setContentTitle("New message from " + address + (nickname == null || nickname.isEmpty() ? "" : " (" + nickname + ")"))
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if(Build.VERSION.SDK_INT < 33) {
            builder
                    .setGroup(getPackageName() + ".NOTIFICATION")
                    .setGroupSummary(true);
        }

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
        notificationManager.notify(uniqueCode, builder.build());
        playRingtoneAndVibrate();
    }

    protected void sendNotification(Map<String, String> map) {
        String title = map.get("title");
        String content = map.get("message");
        String Package = map.get("package");
        String AppName = map.get("appname");
        String Device_name = map.get("device_name");
        String Device_id = map.get("device_id");
        String Date = map.get("date");
        String Key = map.get("notification_key");

        Bitmap Icon_original;
        Bitmap Icon = null;
        if (!"none".equals(map.get("icon")) && !prefs.getBoolean("OverrideReceivedIcon", false)) {
            Icon_original = CompressStringUtil.StringToBitmap(CompressStringUtil.decompressString(map.get("icon")));
            if (Icon_original != null) {
                Icon = Bitmap.createBitmap(Icon_original.getWidth(), Icon_original.getHeight(), Icon_original.getConfig());
                Canvas canvas = new Canvas(Icon);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(Icon_original, 0, 0, null);
            }
        } else if (prefs.getBoolean("UseAlternativeIcon", false)) {
            try {
                Icon = NotiListenerService.getBitmapFromDrawable(this.getPackageManager().getApplicationIcon(Package));
            } catch (PackageManager.NameNotFoundException e) {
                //Ignore this case
            }
        }

        new Thread(() -> {
            try {
                JSONArray array = new JSONArray();
                JSONObject object = new JSONObject();
                String originString = logPrefs.getString("receivedLogs", "");

                if (!originString.equals("")) array = new JSONArray(originString);
                object.put("date", Date);
                object.put("package", Package);
                object.put("title", title);
                object.put("text", content);
                object.put("device", Device_name);
                array.put(object);
                logPrefs.edit().putString("receivedLogs", array.toString()).apply();

                if (array.length() >= prefs.getInt("HistoryLimit", 150)) {
                    int a = array.length() - prefs.getInt("HistoryLimit", 150);
                    for (int i = 0; i < a; i++) {
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
            if (Date != null) {
                Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(Date);
                uniqueCode = d == null ? 0 : (int) ((d.getTime() / 1000L) % Integer.MAX_VALUE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        BitmapIPCManager.getInstance().addBitmap(uniqueCode, Icon);
        notificationIntent.putExtra("bitmapId", uniqueCode);

        notificationIntent.putExtra("package", Package);
        notificationIntent.putExtra("device_id", Device_id);
        notificationIntent.putExtra("appname", AppName);
        notificationIntent.putExtra("title", title);
        notificationIntent.putExtra("device_name", Device_name);
        notificationIntent.putExtra("date", Date);
        notificationIntent.putExtra("notification_key", Key);

        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, uniqueCode, notificationIntent, Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

        Intent onDismissIntent = new Intent(this, BitmapIPCManager.BitmapDismissBroadcastListener.class);
        onDismissIntent.putExtra("bitmapId", uniqueCode);
        onDismissIntent.putExtra("device_id", Device_id);
        onDismissIntent.putExtra("device_name", Device_name);
        onDismissIntent.putExtra("notification_key", Key);

        PendingIntent onDismissPendingIntent = PendingIntent.getBroadcast(this.getApplicationContext(), uniqueCode, onDismissIntent, Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notify_channel_id))
                .setContentTitle(title + " (" + AppName + ")")
                .setContentText(content)
                .setPriority(Build.VERSION.SDK_INT > 23 ? getPriority() : NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(onDismissPendingIntent)
                .setAutoCancel(true);

        if(Build.VERSION.SDK_INT < 33) {
            builder
                    .setGroup(getPackageName() + ".NOTIFICATION")
                    .setGroupSummary(true);
        }

        NotificationData data = new NotificationData();
        data.TITLE = title;
        data.CONTENT = content;
        data.DEVICE_NAME = Device_name;
        data.PACKAGE_NAME = Package;
        data.APP_NAME = AppName;
        data.DATE = Date;

        String regexData = regexPrefs.getString("RegexData", "");
        if (regexData.isEmpty()) {
            if (Icon != null) builder.setLargeIcon(Icon);
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
        } else {
            try {
                JSONArray array = new JSONArray(regexData);
                String[] regexArray = new String[array.length()];
                NotificationData[] dataArray = new NotificationData[array.length()];

                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.getJSONObject(i);
                    boolean isEnabled = object.optBoolean("enabled");

                    String regex = isEnabled ? object.getString("regex") : "false";
                    regexArray[i] = regex;
                    dataArray[i] = data;
                }

                AtomicReference<Bitmap> finalIcon = new AtomicReference<>(Icon);
                int finalUniqueCode = uniqueCode;
                RegexInterpreter.evalRegexWithArray(this, regexArray, dataArray, (obj) -> {
                    String bitmapUri = "";
                    String ringtoneUri = "";

                    if(obj == null || obj.equals("null")) return;
                    int targetIndex = Integer.parseInt((String) obj);
                    if (targetIndex > -1) {
                        try {
                            JSONObject object = array.getJSONObject(targetIndex);
                            if (object.has("bitmap")) bitmapUri = object.getString("bitmap");
                            if (object.has("ringtone")) ringtoneUri = object.getString("ringtone");

                            if (!bitmapUri.isEmpty()) {
                                Uri uri = Uri.parse(bitmapUri);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    finalIcon.set(ImageDecoder.decodeBitmap(ImageDecoder.createSource(FirebaseMessageService.this.getContentResolver(), uri)));
                                } else {
                                    finalIcon.set(MediaStore.Images.Media.getBitmap(FirebaseMessageService.this.getContentResolver(), uri));
                                }
                            }
                        } catch (JSONException | IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if (finalIcon.get() != null) builder.setLargeIcon(finalIcon.get());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        builder.setSmallIcon(R.drawable.ic_notification);
                        CharSequence channelName = getString(R.string.notify_channel_name);
                        String description = getString(R.string.notify_channel_description);
                        NotificationChannel channel = new NotificationChannel(getString(R.string.notify_channel_id), channelName, getImportance());

                        channel.setDescription(description);
                        assert notificationManager != null;
                        notificationManager.createNotificationChannel(channel);
                    } else builder.setSmallIcon(R.mipmap.ic_notification);

                    notificationManager.notify(finalUniqueCode, builder.build());
                    playRingtoneAndVibrate(ringtoneUri);
                });
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        SharedPreferences pluginPrefs = getSharedPreferences("com.noti.main_plugin", Context.MODE_PRIVATE);
        Map<String, ?> pluginMap = pluginPrefs.getAll();

        for (Map.Entry<String, ?> entry : pluginMap.entrySet()) {
            PluginPrefs pluginPref = new PluginPrefs(this, entry.getKey());
            if(pluginPref.isPluginEnabled() && pluginPref.isRequireSensitiveAPI() && pluginPref.isAllowSensitiveAPI()) {
                PluginActions.pushNotification(this, entry.getKey(), data);
            }
        }
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
            case "Custom…":
                return NotificationManager.IMPORTANCE_NONE;
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

    private long parseTimeAndUnitToLong(String data) {
        String[] foo = data.split(" ");
        long numberToMultiply;
        switch (foo[1]) {
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
        playRingtoneAndVibrate("");
    }

    private void playRingtoneAndVibrate(String mediaUri) {
        if (!mediaUri.isEmpty() || prefs.getString("importance", "Default").equals("Custom…")) {

            int VibrationRunningTimeValue = prefs.getInt("VibrationRunningTime", 1000);
            if (VibrationRunningTimeValue > 0) {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(VibrationRunningTimeValue);
            }

            if (lastPlayedRingtone != null && lastPlayedRingtone.isPlaying()) {
                if (ringtonePlayedThread.isAlive()) return;
                else lastPlayedRingtone.stop();
            }

            Ringtone r;
            String s = mediaUri.isEmpty() ? prefs.getString("CustomRingtone", "") : mediaUri;
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
                    if (!r.isPlaying()) break;
                    if ((System.currentTimeMillis() - startTime) > runningTime) {
                        if (r.isPlaying()) r.stop();
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