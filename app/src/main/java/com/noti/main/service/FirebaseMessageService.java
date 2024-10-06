package com.noti.main.service;

import android.app.Notification;
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
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;

import com.application.isradeleon.notify.Notify;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.R;
import com.noti.main.receiver.FindDeviceCancelReceiver;
import com.noti.main.receiver.media.MediaSession;
import com.noti.main.receiver.plugin.PluginHostInject;
import com.noti.main.service.backend.PacketConst;
import com.noti.main.service.backend.PacketRequester;
import com.noti.main.service.backend.ResultPacket;
import com.noti.main.service.livenoti.LiveNotiProcess;
import com.noti.main.service.mirnoti.NotificationAction;
import com.noti.main.service.mirnoti.NotificationActionProcess;
import com.noti.main.service.mirnoti.NotificationRequest;
import com.noti.main.service.mirnoti.NotificationsData;
import com.noti.main.service.refiler.RemoteFileProcess;
import com.noti.main.ui.pair.DeviceFindActivity;
import com.noti.main.utils.network.HMACCrypto;
import com.noti.plugin.data.NetworkProvider;
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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import me.pushy.sdk.lib.jackson.databind.ObjectMapper;

import static com.noti.main.Application.pairingProcessList;
import static com.noti.main.service.NotiListenerService.getUniqueID;

public class FirebaseMessageService extends FirebaseMessagingService {

    SharedPreferences prefs;
    SharedPreferences logPrefs;
    SharedPreferences pairPrefs;
    SharedPreferences regexPrefs;
    SharedPreferences deviceDetailPrefs;

    private static PowerUtils manager;
    public static volatile Ringtone lastPlayedRingtone;
    public static final ConcurrentHashMap<String, MediaSession> playingSessionMap = new ConcurrentHashMap<>();
    public static final ArrayList<SplitDataObject> splitDataList = new ArrayList<>();
    public static final ArrayList<Integer> selfReceiveDetectorList = new ArrayList<>();

    private final NetworkProvider.onProviderMessageListener onProviderMessageListener = (message) -> {
        if (message != null) {
            this.onMessageReceived(message);
        }
    };
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
    public static OnNotificationRemoveRequest removeListenerById;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        logPrefs = getSharedPreferences("com.noti.main_logs", MODE_PRIVATE);
        pairPrefs = getSharedPreferences("com.noti.main_pair", MODE_PRIVATE);
        regexPrefs = getSharedPreferences("com.noti.main_regex", MODE_PRIVATE);
        deviceDetailPrefs = getSharedPreferences("com.noti.main_device.detail", MODE_PRIVATE);
        manager = PowerUtils.getInstance(this);
        manager.acquire();
        NetworkProvider.setOnNetworkProviderListener(this.onProviderMessageListener);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        NetworkProvider.setOnNetworkProviderListener(this.onProviderMessageListener);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        manager.acquire();

        if (BuildConfig.DEBUG) Log.d(remoteMessage.getMessageId(), remoteMessage.toString());
        Map<String, String> map = remoteMessage.getData();
        preProcessReception(map, this);
    }

    @SuppressWarnings("unchecked")
    public void preProcessReception(Map<String, String> map, Context context) {
        String rawPassword = prefs.getString("EncryptionPassword", "");
        boolean useEncryption = prefs.getBoolean("UseDataEncryption", false);
        boolean useHmacAuth = prefs.getBoolean("AllowOnlyPaired", false) && prefs.getBoolean("UseHMacAuth", false);

        if ("true".equals(map.get("encrypted"))) {
            int dataHash = Objects.requireNonNull(map.get("encryptedData")).hashCode();
            if (selfReceiveDetectorList.contains(dataHash)) {
                selfReceiveDetectorList.remove((Integer) dataHash);
                return;
            }

            boolean isAlwaysEncryptData = prefs.getBoolean("AlwaysEncryptData", true);
            if ((useEncryption && !rawPassword.isEmpty()) || isAlwaysEncryptData) {
                try {
                    String uid = prefs.getString("UID", "");
                    if (!uid.isEmpty()) {
                        String finalPassword = AESCrypto.parseAESToken(useEncryption ? AESCrypto.decrypt(rawPassword, AESCrypto.parseAESToken(uid)) : Base64.encodeToString(prefs.getString("Email", "").getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                        if (useHmacAuth) {
                            preProcessReceptionWithHmac(map, finalPassword, context);
                        } else {
                            JSONObject object = new JSONObject(AESCrypto.decrypt(CompressStringUtil.decompressString(map.get("encryptedData")), finalPassword));
                            Map<String, String> newMap = new ObjectMapper().readValue(object.toString(), Map.class);
                            processReception(newMap, context);
                        }
                    }
                } catch (GeneralSecurityException e) {
                    Handler mHandler = new Handler(Looper.getMainLooper());
                    mHandler.postDelayed(() -> Toast.makeText(context, "Error occurred while decrypting data!\nPlease check password and try again!", Toast.LENGTH_SHORT).show(), 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            if (useHmacAuth) {
                preProcessReceptionWithHmac(map, null, context);
            } else {
                processReception(map, context);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void preProcessReceptionWithHmac(Map<String, String> map, @Nullable String token, Context context) {
        String HmacID = map.get("HmacID");
        if (HmacID != null && !HmacID.isEmpty()) {
            try {
                String HmacToken = "";
                for (String str : pairPrefs.getStringSet("paired_list", new HashSet<>())) {
                    String[] savedData = str.split("\\|");
                    if (HMACCrypto.generateTokenIdentifier(savedData[0], savedData[1]).equals(HmacID)) {
                        HmacToken = savedData[1];
                        break;
                    }
                }

                if (HmacToken.isEmpty()) {
                    throw new GeneralSecurityException("Invalid token: Paired Device not found!");
                }

                JSONObject object = new JSONObject(HMACCrypto.decrypt(CompressStringUtil.decompressString(map.get("encryptedData")), HmacToken, token));
                Map<String, String> newMap = new ObjectMapper().readValue(object.toString(), Map.class);
                processReception(newMap, context);
            } catch (IOException | GeneralSecurityException | JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void processReception(Map<String, String> map, Context context) {
        String type = map.get("type");
        String mode = prefs.getString("service", "reception");

        if (type != null && !prefs.getString("UID", "").isEmpty()) {
            if ("send|startup".equals(type) && isDeviceItself(map)) {
                NetworkProvider.fcmIgnitionComplete();
                return;
            }

            if (prefs.getBoolean("serviceToggle", false)) {
                if (PacketConst.SERVICE_TYPE_PACKET_PROXY.equals(type) && !isDeviceItself(map)) {
                    processProxyData(map, context);
                    return;
                }

                if ("split_data".equals(type) && !isDeviceItself(map)) {
                    processSplitData(map, context);
                    return;
                }

                if (prefs.getBoolean("AllowOnlyPaired", false) && !isPairedDevice(map)) {
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

                String[] deviceDetails = deviceDetailPrefs.getString(map.get("device_id"), "").split("\\|");
                if (deviceDetails.length > 0 && "true".equals(deviceDetails[0])) {
                    return;
                }

                if (mode.equals("reception") || mode.equals("hybrid") && type.contains("send")) {
                    if (mode.equals("hybrid") && isDeviceItself(map)) return;
                    switch (type) {
                        case "send|sms" -> sendSmsNotification(map);
                        case "send|telecom" -> sendTelecomNotification(map);
                        case "send|dismiss" -> dismissMirroredNotification(map);
                        case "send|normal" -> {
                            if(map.containsKey(NotificationRequest.KEY_NOTIFICATION_API)) {
                                sendNotificationNew(map);
                            } else {
                                sendNotificationOld(map);
                            }
                        }
                    }
                } else if ((mode.equals("send") || mode.equals("hybrid")) && type.contains("reception")) {
                    if ((NotiListenerService.getDeviceName()).equals(map.get("send_device_name")) && getUniqueID().equals(map.get("send_device_id"))) {
                        switch (type) {
                            case "reception|perform_action" -> sendNotificationAction(map);
                            case "reception|normal" -> startNewRemoteActivity(this, map);
                            case "reception|sms" -> startNewRemoteSms(map);
                        }
                    }
                }

                if (type.startsWith("media") && prefs.getBoolean("UseMediaSync", false)) {
                    manager.acquire();
                    try {
                        String raw = map.get("media_data");
                        if (raw != null && !raw.isEmpty()) {
                            JSONObject object = new JSONObject(raw);

                            switch (type) {
                                case "media|meta_data" -> {
                                    if (!isDeviceItself(map)) {
                                        synchronized (playingSessionMap) {
                                            MediaSession current;
                                            if (!playingSessionMap.containsKey(map.get("device_id"))) {
                                                current = new MediaSession(this, map.get("device_name"), map.get("device_id"));
                                                playingSessionMap.put(map.get("device_id"), current);
                                            } else {
                                                current = playingSessionMap.get(map.get("device_id"));
                                            }

                                            assert current != null;
                                            current.update(object);
                                        }
                                    }
                                }
                                case "media|action" -> {
                                    if (isTargetDevice(map)) {
                                        NotiListenerService.getInstance().mediaReceiver.onDataReceived(object);
                                    }
                                }
                            }
                        }
                    } catch (JSONException | NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (prefs.getBoolean("pairToggle", false)) {
                if (type.startsWith("pair") && !isDeviceItself(map)) {
                    switch (type) {
                        case "pair|request_device_list" -> {
                            //Target Device action
                            //Have to Send this device info Data Now
                            if (!isPairedDevice(map) || prefs.getBoolean("showAlreadyConnected", false)) {
                                PairDeviceInfo info = new PairDeviceInfo(map.get("device_name"), map.get("device_id"), PairDeviceStatus.Device_Process_Pairing);
                                info.setDeviceType(new PairDeviceType(map.get("device_type")));
                                pairingProcessList.add(info);
                                Application.isListeningToPair = true;
                                PairingUtils.responseDeviceInfoToFinder(map, context);
                            }
                        }
                        case "pair|response_device_list" -> {
                            //Request Device Action
                            //Show device list here; give choice to user which device to pair
                            if (Application.isFindingDeviceToPair && (!isPairedDevice(map) || prefs.getBoolean("showAlreadyConnected", false))) {
                                PairDeviceInfo info = new PairDeviceInfo(map.get("device_name"), map.get("device_id"), PairDeviceStatus.Device_Process_Pairing);
                                info.setDeviceType(new PairDeviceType(map.get("device_type")));
                                pairingProcessList.add(info);
                                PairingUtils.onReceiveDeviceInfo(map);
                            }
                        }
                        case "pair|request_pair" -> {
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
                        }
                        case "pair|accept_pair" -> {
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
                        }
                        case "pair|request_remove" -> {
                            if (isTargetDevice(map) && isPairedDevice(map) && prefs.getBoolean("allowRemovePairRemotely", true)) {
                                removePairedDevice(map);
                            }
                        }
                        case "pair|request_remove_all" -> {
                            if (isPairedDevice(map) && prefs.getBoolean("allowRemovePairRemotely", true)) {
                                removePairedDevice(map);
                            }
                        }
                        case "pair|request_data" -> {
                            //process request normal data here sent by paired device(s).
                            if (isTargetDevice(map) && isPairedDevice(map)) {
                                DataProcess.onDataRequested(map, context);
                            }
                        }
                        case "pair|receive_data" -> {
                            //process received normal data here sent by paired device(s).
                            if (isTargetDevice(map) && isPairedDevice(map)) {
                                PairListener.callOnDataReceived(map);
                            }
                        }
                        case "pair|request_action" -> {
                            //process received action data here sent by paired device(s).
                            if (isTargetDevice(map) && isPairedDevice(map)) {
                                DataProcess.onActionRequested(map, context);
                            }
                        }
                        case "pair|find" -> {
                            if (isTargetDevice(map) && isPairedDevice(map)) {
                                if (map.containsKey("findType"))
                                    switch (Objects.requireNonNull(map.get("findType"))) {
                                        case "findRequest" -> {
                                            if ("true".equals(map.get("playSound")) && !prefs.getBoolean("NotReceiveFindDevice", false)) {
                                                sendFindTaskNotification();
                                            }
                                            if ("true".equals(map.get("locationRequest"))) {
                                                DeviceFindActivity.responseLocation(context, map.get("device_id"), map.get("device_name"));
                                            }
                                        }
                                        case "locationResponse" -> {
                                            if (DeviceFindActivity.mOnLocationResponseListener != null) {
                                                if ("true".equals(map.get("isSuccess"))) {
                                                    DeviceFindActivity.mOnLocationResponseListener.onLocationResponse(true,
                                                            Double.parseDouble(Objects.requireNonNull(map.get("latitude"))),
                                                            Double.parseDouble(Objects.requireNonNull(map.get("longitude"))));
                                                } else
                                                    DeviceFindActivity.mOnLocationResponseListener.onLocationResponse(false, null, null);
                                            }
                                        }
                                    }
                                else if (!prefs.getBoolean("NotReceiveFindDevice", false)) {
                                    sendFindTaskNotification();
                                }
                            }
                        }
                        case "pair|remote_file" -> {
                            if (isTargetDevice(map) && isPairedDevice(map)) {
                                RemoteFileProcess.onReceive(map, context);
                            }
                        }
                        case "pair|live_notification" -> {
                            if (isTargetDevice(map) && isPairedDevice(map)) {
                                LiveNotiProcess.onProcessReceive(map, context);
                            }
                        }
                        case "pair|battery_warning" -> {
                            String[] deviceDetailPrefsList = deviceDetailPrefs.getString(map.get("device_id"), "").split("\\|");
                            if(deviceDetailPrefsList.length >= 2 && Boolean.parseBoolean(deviceDetailPrefsList[1])) {
                                Notify.build(context)
                                        .setTitle(String.format("Warning: %s's battery is low", map.get("device_name")))
                                        .setContent(String.format(Locale.getDefault(), "Battery level is %d%%.\nCharging device is recommended.",
                                                Integer.parseInt(Objects.requireNonNull(map.get("battery_level")))))
                                        .setLargeIcon(R.mipmap.ic_launcher)
                                        .largeCircularIcon()
                                        .setSmallIcon(R.drawable.ic_fluent_battery_warning_24_regular)
                                        .setChannelName("Remote Battery Warning")
                                        .setChannelId("battery_warning")
                                        .enableVibration(true)
                                        .setAutoCancel(true)
                                        .show();
                            }
                        }
                        case "pair|plugin" -> {
                            if (isTargetDevice(map) && isPairedDevice(map) && !prefs.getBoolean("NotReceivePlugin", false)) {
                                String actionType = map.get("plugin_action_type");
                                String actionName = map.get("plugin_action_name");
                                String pluginPackage = map.get("plugin_package");
                                String extraData = map.get("plugin_extra_data");
                                String targetDevice = map.get("device_name") + "|" + map.get("device_id");

                                if (actionType != null) switch (actionType) {
                                    case PluginConst.ACTION_REQUEST_REMOTE_ACTION ->
                                            PluginActions.requestAction(context, targetDevice, pluginPackage, actionName, extraData);
                                    case PluginConst.ACTION_REQUEST_REMOTE_DATA ->
                                            PluginActions.requestData(context, targetDevice, pluginPackage, actionName);
                                    case PluginConst.ACTION_RESPONSE_REMOTE_DATA ->
                                            PluginActions.responseData(context, pluginPackage, actionName, extraData);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void processProxyData(Map<String, String> map, Context context) {
        try {
            if (BuildConfig.DEBUG) Log.d("proxy_data", "Encountered proxy needs: " + map.get(PacketConst.KEY_DATA_KEY));
            String sendDeviceId = map.get(PacketConst.KEY_DEVICE_ID);
            String dataHashKey = AESCrypto.shaAndHex(sendDeviceId + map.get(PacketConst.KEY_DATA_KEY));

            JSONObject serverBody = new JSONObject();
            serverBody.put(PacketConst.KEY_SEND_DEVICE_NAME, map.get(PacketConst.KEY_DEVICE_NAME));
            serverBody.put(PacketConst.KEY_SEND_DEVICE_ID, sendDeviceId);
            serverBody.put(PacketConst.KEY_ACTION_TYPE, PacketConst.REQUEST_GET_SHORT_TERM_DATA);
            serverBody.put(PacketConst.KEY_DATA_KEY, dataHashKey);

            PacketRequester.addToRequestQueue(context, PacketConst.SERVICE_TYPE_PACKET_PROXY, serverBody, response -> {
                try {
                    ResultPacket resultPacket = ResultPacket.parseFrom(response.toString());
                    if (resultPacket.isResultOk()) {
                        Map<String, String> newMap = new ObjectMapper().readValue(resultPacket.getExtraData(), Map.class);
                        preProcessReception(newMap, context);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, Throwable::printStackTrace);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    protected void processSplitData(Map<String, String> map, Context context) {
        synchronized (splitDataList) {
            if (BuildConfig.DEBUG) Log.d("split_data", "current size : " + splitDataList.size());

            for (int i = 0; i < splitDataList.size(); i++) {
                SplitDataObject object = splitDataList.get(i);
                if (object.unique_id.equals(map.get("split_unique"))) {
                    object = object.addData(map);
                    splitDataList.set(i, object);

                    if (object.length == object.getSize()) {
                        try {
                            Map<String, String> newMap = new ObjectMapper().readValue(object.getFullData(), Map.class);
                            preProcessReception(newMap, context);
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

        String DEVICE_NAME = NotiListenerService.getDeviceName();
        String DEVICE_ID = getUniqueID();

        return DEVICE_NAME.equals(Device_name) && DEVICE_ID.equals(Device_id);
    }

    protected boolean isTargetDevice(Map<String, String> map) {
        String Device_name = map.get("send_device_name");
        String Device_id = map.get("send_device_id");

        String DEVICE_NAME = NotiListenerService.getDeviceName();
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

    protected void removePairedDevice(Map<String, String> map) {
        String dataToFind = map.get("device_name") + "|" + map.get("device_id") + (map.containsKey("device_type") ? "|" + map.get("device_type") : "");
        String dataToRemove = null;

        Set<String> list = new HashSet<>(pairPrefs.getStringSet("paired_list", new HashSet<>()));
        for (String str : list) {
            if (str.contains(dataToFind)) {
                dataToRemove = str;
                break;
            }
        }

        if (dataToRemove != null) list.remove(dataToRemove);
        pairPrefs.edit().putStringSet("paired_list", list).apply();
    }

    public static void startNewRemoteActivity(Context context, Map<String, String> map) {
        if (map.containsKey("notification_key") && removeListener != null) {
            removeListener.onRequested(map.get("notification_key"));
        }

        if (!map.containsKey("start_remote_activity") || "true".equals(map.get("start_remote_activity"))) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> Toast.makeText(context, "Remote run by NotiSender\nfrom " + map.get("device_name"), Toast.LENGTH_SHORT).show(), 0);
            String Package = map.get("package");
            if(Package != null) try {
                PackageManager pm = context.getPackageManager();
                pm.getPackageInfo(Package, PackageManager.GET_ACTIVITIES);
                Intent intent = pm.getLaunchIntentForPackage(Package);
                context.startActivity(Objects.requireNonNull(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Package));
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
    }

    protected void startNewRemoteSms(Map<String, String> map) {
        if (map.get("address") != null && map.get("message") != null) {
            String deviceInfo = map.get("device_name") + "|" + map.get("device_id");
            String args = map.get("address") + "|" + map.get("message");
            PluginActions.requestHostApiInject(this, deviceInfo, PluginHostInject.HostInjectAPIName.PLUGIN_TELEPHONY_PACKAGE, PluginHostInject.HostInjectAPIName.ACTION_REQUEST_SEND_SMS, args);
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
        if (lastPlayedRingtone != null) {
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

        if (Build.VERSION.SDK_INT < 33) {
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

        if (Build.VERSION.SDK_INT < 33) {
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

    protected void dismissMirroredNotification(Map<String, String> map) {
        if(!map.containsKey(NotificationRequest.KEY_NOTIFICATION_API)) {
            return;
        }

        String key = map.get(NotificationRequest.KEY_NOTIFICATION_KEY);
        if(key != null && !key.isEmpty() && removeListenerById != null) {
            removeListenerById.onRequested(key);
        }
    }

    protected void sendNotificationAction(Map<String, String> map) {
        String key = map.get(NotificationRequest.KEY_NOTIFICATION_KEY);
        int actionIndex = Integer.parseInt(map.get(NotificationRequest.KEY_NOTIFICATION_ACTION_INDEX));

        if(Objects.equals(map.get(NotificationRequest.KEY_NOTIFICATION_HAS_INPUT), "true")) {
            NotificationActionProcess.raiseActionWithInput(this, key, actionIndex,
                    map.get(NotificationRequest.KEY_NOTIFICATION_KEY_INPUT),
                    map.get(NotificationRequest.KEY_NOTIFICATION_DATA_INPUT));
        } else {
            NotificationActionProcess.raiseAction(key, actionIndex);
        }
    }

    protected void sendNotificationNew(Map<String, String> map) {
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            String notificationStr = map.get(NotificationRequest.KEY_NOTIFICATION_DATA);

            if(notificationStr != null) {
                final String deviceId = map.get("device_id");
                final String deviceName = map.get("device_name");
                final NotificationsData notificationsData = NotificationsData.parseFrom(notificationStr);
                final int uniqueCode = notificationsData.key.hashCode();

                Notification.Builder builder = notificationsData.getBuilder(FirebaseMessageService.this);
                builder.setPriority(Build.VERSION.SDK_INT > 23 ? getPriority() : Notification.PRIORITY_DEFAULT);

                Intent notificationIntent = new Intent(FirebaseMessageService.this, NotificationViewActivity.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                BitmapIPCManager.getInstance().addSerialize(uniqueCode, notificationsData);

                notificationIntent.putExtra(NotificationRequest.KEY_NOTIFICATION_KEY, uniqueCode);
                notificationIntent.putExtra("device_name", deviceName);
                notificationIntent.putExtra("device_id", deviceId);

                Intent onDismissIntent = new Intent(this, BitmapIPCManager.BitmapDismissBroadcastListener.class);
                onDismissIntent.putExtra("bitmapId", uniqueCode);
                onDismissIntent.putExtra("device_id", deviceId);
                onDismissIntent.putExtra("device_name", deviceName);
                onDismissIntent.putExtra("notification_key", notificationsData.key);

                PendingIntent pendingIntent = PendingIntent.getActivity(this, uniqueCode, notificationIntent, Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
                PendingIntent onDismissPendingIntent = PendingIntent.getBroadcast(this.getApplicationContext(), uniqueCode, onDismissIntent, Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT);
                builder.setContentIntent(pendingIntent);
                builder.setDeleteIntent(onDismissPendingIntent);

                for(int i = 0; i < notificationsData.actions.length; i++) {
                    Intent onActionIntent = new Intent(this, NotificationActionProcess.NotificationActionRaiseBroadcastReceiver.class);
                    onActionIntent.putExtra(NotificationRequest.KEY_NOTIFICATION_KEY, notificationsData.key);
                    onActionIntent.putExtra(NotificationRequest.KEY_NOTIFICATION_HASHCODE, uniqueCode);
                    onActionIntent.putExtra(NotificationRequest.KEY_NOTIFICATION_ACTION_INDEX, i);

                    NotificationAction action = notificationsData.actions[i];
                    int flags;

                    if(action.isInputAction) {
                        flags = PendingIntent.FLAG_MUTABLE;
                        onActionIntent.putExtra(NotificationRequest.KEY_NOTIFICATION_HAS_INPUT, true);
                        onActionIntent.putExtra(NotificationRequest.KEY_NOTIFICATION_KEY_INPUT, action.inputResultKey);
                    } else {
                        flags = Build.VERSION.SDK_INT > 30 ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
                        onActionIntent.putExtra(NotificationRequest.KEY_NOTIFICATION_HAS_INPUT, false);
                    }

                    onActionIntent.putExtra("device_name", deviceName);
                    onActionIntent.putExtra("device_id", deviceId);
                    PendingIntent onActionPendingIntent = PendingIntent.getBroadcast(this.getApplicationContext(), uniqueCode, onActionIntent, flags);
                    builder.addAction(action.getAction(onActionPendingIntent));
                }

                NotificationData data = new NotificationData(notificationsData, deviceName);
                postPluginNotification(data);
                saveNotificationHistory(data);

                String regexData = regexPrefs.getString("RegexData", "");
                if (regexData.isEmpty()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        CharSequence channelName = getString(R.string.notify_channel_name);
                        String description = getString(R.string.notify_channel_description);
                        NotificationChannel channel = new NotificationChannel(getString(R.string.notify_channel_id), channelName, getImportance());

                        channel.setDescription(description);
                        assert notificationManager != null;
                        notificationManager.createNotificationChannel(channel);
                    }

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

                        AtomicReference<Bitmap> finalIcon = new AtomicReference<>();
                        RegexInterpreter.evalRegexWithArray(this, regexArray, dataArray, (obj) -> {
                            String bitmapUri = "";
                            String ringtoneUri = "";

                            if (obj == null || obj.equals("null")) return;
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
                                CharSequence channelName = getString(R.string.notify_channel_name);
                                String description = getString(R.string.notify_channel_description);
                                NotificationChannel channel = new NotificationChannel(getString(R.string.notify_channel_id), channelName, getImportance());

                                channel.setDescription(description);
                                assert notificationManager != null;
                                notificationManager.createNotificationChannel(channel);
                            } else builder.setSmallIcon(R.mipmap.ic_notification);

                            notificationManager.notify(uniqueCode, builder.build());
                            playRingtoneAndVibrate(ringtoneUri);
                        });
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void sendNotificationOld(Map<String, String> map) {
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
        String rawIconData = map.get("icon");

        if (!(rawIconData == null || rawIconData.isEmpty() || "none".equals(rawIconData))
                && !prefs.getBoolean("OverrideReceivedIcon", false)) {
            Icon_original = CompressStringUtil.getBitmapFromString(CompressStringUtil.decompressString(rawIconData));
            if (Icon_original != null) {
                Icon = Bitmap.createBitmap(Icon_original.getWidth(), Icon_original.getHeight(), Icon_original.getConfig());
                Canvas canvas = new Canvas(Icon);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(Icon_original, 0, 0, null);
            }
        } else if (prefs.getBoolean("UseAlternativeIcon", false)) {
            try {
                Icon = NotiListenerService.getBitmapFromDrawable(this.getPackageManager().getApplicationIcon(Objects.requireNonNull(Package)));
            } catch (PackageManager.NameNotFoundException e) {
                //Ignore this case
            }
        }

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

        if (Build.VERSION.SDK_INT < 33) {
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

        postPluginNotification(data);
        saveNotificationHistory(data);

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

                    if (obj == null || obj.equals("null")) return;
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
    }

    private void saveNotificationHistory(NotificationData data) {
        new Thread(() -> {
            try {
                JSONArray array = new JSONArray();
                JSONObject object = new JSONObject();
                String originString = logPrefs.getString("receivedLogs", "");

                if (!originString.isEmpty()) array = new JSONArray(originString);
                object.put("date", data.DATE);
                object.put("package", data.PACKAGE_NAME);
                object.put("title", data.TITLE);
                object.put("text", data.CONTENT);
                object.put("device", data.DEVICE_NAME);
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
    }

    private void postPluginNotification(NotificationData data) {
        SharedPreferences pluginPrefs = getSharedPreferences("com.noti.main_plugin", Context.MODE_PRIVATE);
        Map<String, ?> pluginMap = pluginPrefs.getAll();

        for (Map.Entry<String, ?> entry : pluginMap.entrySet()) {
            PluginPrefs pluginPref = new PluginPrefs(this, entry.getKey());
            if (pluginPref.isPluginEnabled() && pluginPref.isRequireSensitiveAPI() && pluginPref.isAllowSensitiveAPI()) {
                PluginActions.pushNotification(this, entry.getKey(), data);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private int getImportance() {
        String value = prefs.getString("importance", "Default");
        return switch (value) {
            case "Default" -> NotificationManager.IMPORTANCE_DEFAULT;
            case "Low" -> NotificationManager.IMPORTANCE_LOW;
            case "High" -> NotificationManager.IMPORTANCE_MAX;
            case "Custom…" -> NotificationManager.IMPORTANCE_NONE;
            default -> NotificationManager.IMPORTANCE_UNSPECIFIED;
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private int getPriority() {
        String value = prefs.getString("importance", "Default");
        return switch (value) {
            case "Low" -> NotificationCompat.PRIORITY_LOW;
            case "High" -> NotificationCompat.PRIORITY_MAX;
            default -> NotificationCompat.PRIORITY_DEFAULT;
        };
    }

    private long parseTimeAndUnitToLong(String data) {
        String[] foo = data.split(" ");
        long numberToMultiply = switch (foo[1]) {
            case "sec" -> 1000L;
            case "min" -> 60000L;
            case "hour" -> 3600000L;
            case "day" -> 86400000L;
            case "week" -> 604800000L;
            case "month" -> 2419200000L;
            case "year" -> 29030400000L;
            default -> 0L;
        };
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
        if (!prefs.getString("UID", "").isEmpty())
            FirebaseMessaging.getInstance().subscribeToTopic(prefs.getString("UID", ""));
    }
}