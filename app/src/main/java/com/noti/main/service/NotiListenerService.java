package com.noti.main.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telecom.TelecomManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.auth.FirebaseAuth;

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.receiver.plugin.PluginActions;
import com.noti.main.receiver.plugin.PluginConst;
import com.noti.main.service.backend.PacketConst;
import com.noti.main.service.backend.PacketRequester;
import com.noti.main.service.backend.ResultPacket;
import com.noti.main.service.livenoti.LiveNotiProcess;
import com.noti.main.service.media.MediaReceiver;
import com.noti.main.service.mirnoti.NotificationRequest;
import com.noti.main.utils.network.AESCrypto;
import com.noti.main.utils.network.HMACCrypto;
import com.noti.main.utils.network.JsonRequest;
import com.noti.main.utils.network.CompressStringUtil;
import com.noti.main.service.IntervalQueries.*;
import com.noti.main.utils.PowerUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NotiListenerService extends NotificationListenerService {

    private static NotiListenerService instance;
    private SharedPreferences prefs;
    private SharedPreferences logPrefs;
    private PowerUtils manager;
    public MediaReceiver mediaReceiver;

    private static final Object pastNotificationLock = new Object();
    private volatile StatusBarNotification pastNotification = null;
    private final FirebaseMessageService.OnNotificationRemoveRequest onRemoveRequestListener = this::cancelNotification;

    private static int queryAccessCount = 0;
    private static volatile long intervalTimestamp = 0;
    private final ArrayList<Query> intervalQuery = new ArrayList<>();
    private final ArrayList<SmsQuery> smsIntervalQuery = new ArrayList<>();
    private final ArrayList<TelecomQuery> telecomQuery = new ArrayList<>();

    public static NotiListenerService getInstance() {
        if (instance == null) {
            instance = new NotiListenerService();
        }
        return instance;
    }

    public static SharedPreferences getPrefs() {
        return getInstance().prefs;
    }

    public NotiListenerService() {
        initService(Application.getApplicationInstance().getApplicationContext());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (instance == null) initService(this);
        FirebaseMessageService.removeListener = onRemoveRequestListener;
    }

    void initService(Context context) {
        prefs = context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        logPrefs = context.getSharedPreferences("com.noti.main_logs", MODE_PRIVATE);
        manager = PowerUtils.getInstance(context);
        mediaReceiver = new MediaReceiver(context);

        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaReceiver.onDestroy();
    }

    public static Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        return getResizedBitmap(bm, newWidth, newHeight, null);
    }

    public static Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight, @Nullable Integer backgroundColor) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);

        if(backgroundColor != null) {
            Paint paint = new Paint();
            ColorFilter filter = new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.SRC_IN);
            paint.setColorFilter(filter);

            Canvas canvas = new Canvas(resizedBitmap);
            canvas.drawBitmap(resizedBitmap, 0, 0, paint);
        }

        bm.recycle();
        return resizedBitmap;
    }

    public static Bitmap getBitmapFromDrawable(Drawable drawable) {
        try {
            Bitmap bitmap;
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getDeviceName() {
        return String.format("%s %s", Build.MANUFACTURER, Build.MODEL);
    }

    @SuppressLint("HardwareIds")
    public static String getUniqueID() {
        SharedPreferences prefs = getPrefs();
        String str = "";

        if (prefs != null) {
            switch (prefs.getString("uniqueIdMethod", "Globally-Unique ID")) {
                case "Globally-Unique ID" -> str = prefs.getString("GUIDPrefix", "");
                case "Android ID" -> str = prefs.getString("AndroidIDPrefix", "");
                case "Firebase IID" -> str = prefs.getString("FirebaseIIDPrefix", "");
                case "Device MAC ID" -> str = prefs.getString("MacIDPrefix", "");
            }
            return str;
        }
        return "";
    }

    public static String getNonNullString(@Nullable Object value) {
        if(value == null) {
            return "";
        } else if(value instanceof CharSequence) {
            return value.toString();
        }

        return "";
    }

    private void QueryGC() {
        if (prefs.getInt("IntervalQueryGCTrigger", 50) < 1) return;
        long currentTimeMillis = Calendar.getInstance().getTime().getTime();
        int timeInterval = prefs.getInt("IntervalTime", 150);
        int[] cleanCount = {0, 0, 0};

        Log.d("Interval Query GC", "GC started to clean unused Query! Reference interval value: " + timeInterval);
        Log.d("Interval Query GC", "Current Query size: " + intervalQuery.size() + " normal Query, " + smsIntervalQuery.size() + " SMS Query, " + telecomQuery.size() + " Telecom Query, " + (intervalQuery.size() + smsIntervalQuery.size() + telecomQuery.size()) + " total count.");

        synchronized (intervalQuery) {
            Iterator<Query> iterator = intervalQuery.iterator();
            while (iterator.hasNext()) {
                Query query = iterator.next();
                if (currentTimeMillis - query.getTimestamp() > timeInterval + 100) {
                    iterator.remove();
                    cleanCount[0]++;
                }
            }
        }

        synchronized (smsIntervalQuery) {
            Iterator<SmsQuery> iterator = smsIntervalQuery.iterator();
            while (iterator.hasNext()) {
                SmsQuery query = iterator.next();
                if (currentTimeMillis - query.getTimeStamp() > timeInterval + 100) {
                    iterator.remove();
                    cleanCount[1]++;
                }
            }
        }

        synchronized (telecomQuery) {
            Iterator<TelecomQuery> iterator = telecomQuery.iterator();
            while (iterator.hasNext()) {
                TelecomQuery query = iterator.next();
                if (currentTimeMillis - query.getTimeStamp() > timeInterval + 100) {
                    iterator.remove();
                    cleanCount[2]++;
                }
            }
        }

        //Run actual android runtime GC here?
        //System.gc();
        Log.d("Interval Query GC", "GC Cleaned: " + cleanCount[0] + " normal Query, " + cleanCount[1] + " SMS Query, " + cleanCount[2] + " Telecom Query, " + (cleanCount[0] + cleanCount[1] + cleanCount[2]) + " total count.");
    }

    private String getSystemDialerApp(Context context) {
        return ((TelecomManager) context.getSystemService(Context.TELECOM_SERVICE)).getDefaultDialerPackage();
    }

    private boolean isTelephonyApp(Context context, String packageName) {
        try {
            String defaultSms = Telephony.Sms.getDefaultSmsPackage(context);
            String defaultTelephony = getSystemDialerApp(context);
            return (defaultSms != null && defaultSms.equals(packageName)) || (defaultTelephony != null && defaultTelephony.equals(packageName));
        } catch (NullPointerException e) {
            return false;
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        LiveNotiProcess.mOnNotificationListListener = NotiListenerService.this::getActiveNotifications;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        if (BuildConfig.DEBUG) Log.d("ddd", sbn.getPackageName());
        if (manager == null) manager = PowerUtils.getInstance(this);
        manager.acquire();
        synchronized (pastNotificationLock) {
            if (sbn.equals(pastNotification)) {
                pastNotification = sbn;
                manager.release();
                return;
            }

            Notification notification = sbn.getNotification();
            Bundle extra = notification.extras;
            Date time = Calendar.getInstance().getTime();
            String DATE = Application.getDateString();
            String KEY = sbn.getKey();

            boolean isLogging = BuildConfig.DEBUG;

            if (!prefs.getString("UID", "").isEmpty() && prefs.getBoolean("serviceToggle", false)) {
                String mode = prefs.getString("service", "reception");
                if (mode.equals("send") || mode.equals("hybrid")) {
                    String TITLE = getNonNullString(extra.getCharSequence(Notification.EXTRA_TITLE));
                    String TEXT = getNonNullString(extra.getCharSequence(Notification.EXTRA_TEXT));
                    String TEXT_LINES = getNonNullString(extra.getCharSequence(Notification.EXTRA_TEXT_LINES));
                    if (!TEXT_LINES.isEmpty() && TEXT.isEmpty()) TEXT = TEXT_LINES;
                    String PackageName = sbn.getPackageName();

                    if (PackageName.equals(getPackageName()) && (!TITLE.toLowerCase().contains("test") || TITLE.contains("main"))) {
                        manager.release();
                        return;
                    } else if (isTelephonyApp(this, PackageName)) {
                        manager.release();
                        return;
                    } else if (isWhitelist(PackageName)) {
                        if (prefs.getBoolean("IgnoreOngoing", false) &&
                                (notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
                                (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0 ||
                                (notification.flags & Notification.FLAG_LOCAL_ONLY) != 0) {
                            manager.release();
                            return;
                        }

                        if (prefs.getBoolean("StrictStringNull", false) && ((TITLE.isEmpty() || TITLE.equals("null")) || (TEXT.isEmpty() || TEXT.equals("null")))) {
                            manager.release();
                            return;
                        }
                        if (isBannedWords(TEXT, TITLE) || isIntervalNotGaped(isLogging, PackageName, time)) {
                            manager.release();
                            return;
                        }

                        new Thread(() -> {
                            try {
                                JSONArray array = new JSONArray();
                                JSONObject object = new JSONObject();
                                String originString = logPrefs.getString("sendLogs", "");

                                if (!originString.isEmpty()) array = new JSONArray(originString);
                                object.put("date", DATE);
                                object.put("package", PackageName);
                                object.put("title", Objects.requireNonNullElse(extra.getCharSequence(Notification.EXTRA_TITLE), prefs.getString("DefaultTitle", "New notification")).toString());
                                object.put("text", Objects.requireNonNullElse(extra.getCharSequence(Notification.EXTRA_TEXT), prefs.getString("DefaultMessage", "notification arrived.")).toString());
                                array.put(object);
                                logPrefs.edit().putString("sendLogs", array.toString()).apply();

                                if (array.length() >= prefs.getInt("HistoryLimit", 150)) {
                                    int a = array.length() - prefs.getInt("HistoryLimit", 150);
                                    for (int i = 0; i < a; i++) {
                                        array.remove(i);
                                    }
                                    logPrefs.edit().putString("sendLogs", array.toString()).apply();
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }).start();

                        if (prefs.getBoolean("useLegacyAPI", false)) {
                            sendNormalNotificationOld(notification, PackageName, isLogging, DATE, TITLE, TEXT, KEY);
                        } else {
                            NotificationRequest.sendMirrorNotification(this, isLogging, sbn);
                        }
                    }

                    if (queryAccessCount > prefs.getInt("IntervalQueryGCTrigger", 50)) {
                        QueryGC();
                        queryAccessCount = 0;
                    }
                }
            }

            pastNotification = sbn;
        }
    }

    public void sendTelecomNotification(Context context, Boolean isLogging, String address, String nickname) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        }

        Date time = Calendar.getInstance().getTime();
        String date = Application.getDateString();
        String PackageName = getSystemDialerApp(context);

        if (isTelecomIntervalGaped(address, time)) {
            String DEVICE_NAME = NotiListenerService.getDeviceName();
            String DEVICE_ID = getUniqueID();

            JSONObject notificationBody = new JSONObject();
            try {
                notificationBody.put("type", "send|telecom");
                notificationBody.put("address", address);
                notificationBody.put("nickname", nickname);
                notificationBody.put("package", PackageName);
                notificationBody.put("device_name", DEVICE_NAME);
                notificationBody.put("device_id", DEVICE_ID);
                notificationBody.put("date", date);
            } catch (JSONException e) {
                if (isLogging) Log.e("Noti", "onCreate: " + e.getMessage());
            }
            if (isLogging) Log.d("data", notificationBody.toString());
            sendNotification(notificationBody, PackageName, context);
        }
    }

    public void sendSmsNotification(Context context, Boolean isLogging, String PackageName, String address, String nickname, String message, Date time) {
        if (isSmsIntervalGaped(context, address, message, time)) {
            String DEVICE_NAME = NotiListenerService.getDeviceName();
            String DEVICE_ID = getUniqueID();

            JSONObject notificationBody = new JSONObject();
            try {
                notificationBody.put("type", "send|sms");
                notificationBody.put("message", message);
                notificationBody.put("address", address);
                notificationBody.put("nickname", nickname);
                notificationBody.put("device_name", DEVICE_NAME);
                notificationBody.put("device_id", DEVICE_ID);
                notificationBody.put("date", Application.getDateString());
            } catch (JSONException e) {
                if (isLogging) Log.e("Noti", "onCreate: " + e.getMessage());
            }
            if (isLogging) Log.d("data", notificationBody.toString());
            sendNotification(notificationBody, PackageName, context);
        }
    }

    private void sendNormalNotificationOld(Notification notification, String PackageName, boolean isLogging, String DATE, String TITLE, String TEXT, String KEY) {
        PackageManager pm = this.getPackageManager();
        Bitmap ICON = null;
        Integer iconTintColor = null;

        try {
            if (prefs.getBoolean("IconUseNotification", false)) {
                Context packageContext = createPackageContext(PackageName, CONTEXT_IGNORE_SECURITY);
                Icon LargeIcon = notification.getLargeIcon();
                Icon SmallIcon = notification.getSmallIcon();

                if (LargeIcon != null)
                    ICON = getBitmapFromDrawable(LargeIcon.loadDrawable(packageContext));
                else if (SmallIcon != null) {
                    Drawable iconDrawable = SmallIcon.loadDrawable(packageContext);
                    if(iconDrawable != null) {
                        iconTintColor = Color.BLACK;
                        iconDrawable.setTint(iconTintColor);
                        ICON = NotiListenerService.getBitmapFromDrawable(iconDrawable);
                    }
                }
                else
                    ICON = getBitmapFromDrawable(pm.getApplicationIcon(PackageName));
            } else {
                ICON = getBitmapFromDrawable(pm.getApplicationIcon(PackageName));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String ICONS;
        if (ICON != null && prefs.getBoolean("SendIcon", false)) {
            ICON.setHasAlpha(true);
            int res = switch (prefs.getString("IconRes", "")) {
                case "68 x 68 (Not Recommend)" -> 68;
                case "52 x 52 (Default)" -> 52;
                case "36 x 36" -> 36;
                default -> 0;
            };

            Bitmap finalBitmap = (iconTintColor == null ? getResizedBitmap(ICON, res, res) : getResizedBitmap(ICON, res, res, iconTintColor));
            ICONS = (res == 0 ? "none" : CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(finalBitmap)));
        } else ICONS = "none";

        String DEVICE_NAME = NotiListenerService.getDeviceName();
        String DEVICE_ID = getUniqueID();
        String APPNAME = null;

        try {
            APPNAME = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(PackageName, PackageManager.GET_META_DATA)));
        } catch (PackageManager.NameNotFoundException e) {
            if (isLogging) Log.d("Error", "Package not found : " + PackageName);
        }

        if (isLogging) Log.d("length", String.valueOf(ICONS.length()));
        JSONObject notificationBody = new JSONObject();

        try {
            notificationBody.put("type", "send|normal");
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);

            notificationBody.put("title", TITLE == null || TITLE.equals("null") ? prefs.getString("DefaultTitle", "New notification") : TITLE);
            notificationBody.put("message", TEXT == null || TEXT.equals("null") ? prefs.getString("DefaultMessage", "notification arrived.") : TEXT);
            notificationBody.put("package", PackageName);
            notificationBody.put("appname", APPNAME);
            notificationBody.put("date", DATE);
            notificationBody.put("icon", ICONS);
            notificationBody.put("notification_key", KEY);

            int dataLimit = prefs.getInt("DataLimit", 4096);
            if (notificationBody.toString().length() >= dataLimit - 20 &&
                    !(prefs.getBoolean("UseSplitData", false) || prefs.getBoolean("UseBackendProxy", true))) {
                notificationBody.put("icon", "none");
            }
        } catch (JSONException e) {
            if (isLogging) Log.e("Noti", "onCreate: " + e.getMessage());
        }
        if (isLogging) Log.d("data", notificationBody.toString());
        sendNotification(notificationBody, PackageName, this);
    }

    private boolean isBannedWords(String TEXT, String TITLE) {
        if (prefs.getBoolean("UseBannedOption", false)) {
            String word = prefs.getString("BannedWords", "");
            if (!word.isEmpty()) {
                String[] words = word.split("/");
                for (String s : words) {
                    if ((TEXT != null && TEXT.contains(s)) || (TITLE != null && TITLE.contains(s)))
                        return true;
                }
            }
        }
        return false;
    }

    private boolean isWhitelist(String PackageName) {
        boolean isWhitelist = prefs.getBoolean("UseWhite", false);
        boolean isContain = getSharedPreferences(isWhitelist ? "Whitelist" : "Blacklist", MODE_PRIVATE).getBoolean(PackageName, false);
        return isWhitelist == isContain;
    }

    private boolean isTelecomIntervalGaped(String Number, Date time) {
        if (prefs.getBoolean("UseInterval", false)) {
            int timeInterval = prefs.getInt("IntervalTime", 150);
            TelecomQuery query = new TelecomQuery();
            synchronized (telecomQuery) {
                int index = -1;
                TelecomQuery object = null;

                for (int i = 0; i < telecomQuery.size(); i++) {
                    object = telecomQuery.get(i);
                    if (object.getNumber().equals(Number)) {
                        index = i;
                        break;
                    }
                }

                query.setNumber(Number);
                query.setTimeStamp(time.getTime());

                if (index > -1) {
                    telecomQuery.set(index, query);
                    if (time.getTime() - object.getTimeStamp() <= timeInterval) return false;
                } else {
                    telecomQuery.add(query);
                }
                queryAccessCount++;
            }
        }
        return true;
    }

    private boolean isSmsIntervalGaped(Context context, String Number, String Content, Date time) {
        if (prefs == null)
            prefs = context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean("UseInterval", false)) {
            int timeInterval = prefs.getInt("IntervalTime", 150);
            SmsQuery query = new SmsQuery();
            synchronized (smsIntervalQuery) {
                int index = -1;
                SmsQuery object = null;

                for (int i = 0; i < smsIntervalQuery.size(); i++) {
                    object = smsIntervalQuery.get(i);
                    if (object.getNumber().equals(Number) && object.getContent().equals(Content)) {
                        index = i;
                        break;
                    }
                }

                query.setNumber(Number);
                query.setContent(Content);
                query.setTimeStamp(time.getTime());

                if (index > -1) {
                    smsIntervalQuery.set(index, query);
                    if (time.getTime() - object.getTimeStamp() <= timeInterval) return false;
                } else {
                    smsIntervalQuery.add(query);
                }
                queryAccessCount++;
            }
        }
        return true;
    }

    private boolean isIntervalNotGaped(Boolean isLogging, String PackageName, Date time) {
        if (prefs.getBoolean("UseInterval", false)) {
            String Type = prefs.getString("IntervalType", "Entire app");
            int timeInterval = prefs.getInt("IntervalTime", 150);
            if (Type.equals("Entire app")) {
                if (isLogging)
                    Log.d("IntervalCalculate", "Package" + PackageName + "/Calculated(ms):" + (time.getTime() - intervalTimestamp));
                if (intervalTimestamp != 0 && time.getTime() - intervalTimestamp <= timeInterval) {
                    intervalTimestamp = time.getTime();
                    return true;
                } else intervalTimestamp = time.getTime();
            } else if (Type.equals("Per app")) {
                int index = findIndex(PackageName);
                Query newQuery = new Query();
                synchronized (intervalQuery) {
                    if (index > -1) {
                        Query query = intervalQuery.get(index);
                        newQuery.setTimestamp(time.getTime());
                        newQuery.setPackage(PackageName);
                        intervalQuery.set(index, newQuery);
                        if (time.getTime() - query.getTimestamp() <= timeInterval)
                            return true;
                    } else {
                        newQuery.setPackage(PackageName);
                        newQuery.setTimestamp(time.getTime());
                        intervalQuery.add(newQuery);
                    }
                    queryAccessCount++;
                }
            }
        }
        return false;
    }

    private synchronized int findIndex(String value) {
        for (int i = 0; i < intervalQuery.size(); i++) {
            if (intervalQuery.get(i).getPackage().equals(value)) return i;
        }
        return -1;
    }

    public static void sendNotification(JSONObject notification, String PackageName, Context context, boolean useFCMOnly) {
        SharedPreferences prefs = getPrefs();
        PowerUtils manager = getInstance().manager;

        if (prefs == null)
            prefs = context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        if (manager == null) manager = PowerUtils.getInstance(context);
        manager.acquire();

        try {
            JSONObject serializationJson = new JSONObject();
            for (Iterator<String> it = notification.keys(); it.hasNext(); ) {
                String key = it.next();
                serializationJson.put(key, notification.get(key).toString());
            }
            notification = serializationJson;

            boolean useSplit = prefs.getBoolean("UseSplitData", false) && notification.length() > 3072;
            boolean useEncryption = prefs.getBoolean("UseDataEncryption", false);
            boolean splitAfterEncryption = prefs.getBoolean("SplitAfterEncryption", false);
            boolean useBackendProxy = prefs.getBoolean("UseBackendProxy", true);
            boolean enforceBackendProxy = prefs.getBoolean("EnforceBackendProxy", false);
            int splitInterval = prefs.getInt("SplitInterval", 500);

            if (useSplit && !useFCMOnly && !useBackendProxy) {
                if(useEncryption && splitAfterEncryption) notification = encryptData(notification);
                for (JSONObject object : splitData(notification)) {
                    if(useEncryption && !splitAfterEncryption) object = encryptData(object);
                    finalProcessData(object, PackageName, context, false);
                    if (splitInterval > 0) {
                        Thread.sleep(splitInterval);
                    }
                }
                return;
            } else if (useEncryption) {
                notification = encryptData(notification);
            }

            if(useBackendProxy && (enforceBackendProxy || notification.toString().length() > 2048)) {
                proxyToBackend(notification, PackageName, context, useFCMOnly);
            } else {
                finalProcessData(notification, PackageName, context, useFCMOnly);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendNotification(JSONObject notification, String PackageName, Context context) {
       sendNotification(notification, PackageName, context, false);
    }

    protected static JSONObject[] splitData(JSONObject rawData) throws JSONException {
        int size = 1024;
        List<String> arr = new ArrayList<>((rawData.length() + size - 1) / size);
        for (int start = 0; start < rawData.length(); start += size) {
            arr.add(rawData.toString().substring(start, Math.min(rawData.length(), start + size)));
        }

        JSONObject[] data = new JSONObject[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            String str = arr.get(i);
            JSONObject obj = new JSONObject();
            obj.put("type", "split_data");
            obj.put("split_index", i + "/" + arr.size());
            obj.put("split_unique", Integer.toString(rawData.hashCode()));
            obj.put("split_data", str);
            obj.put("device_name", NotiListenerService.getDeviceName());
            obj.put("device_id", getUniqueID());
            Log.d("unique_id", "id: " + rawData.hashCode());
            data[i] = obj;
        }

        return data;
    }

    protected static void proxyToBackend(JSONObject notification, String PackageName, Context context, boolean useFCMOnly) throws JSONException, NoSuchAlgorithmException {
        String finalData = notification.toString();
        String deviceId = getUniqueID();
        String deviceName = getDeviceName();
        String dataHashKey = AESCrypto.shaAndHex(deviceId + finalData.hashCode());

        JSONObject serverBody = new JSONObject();
        serverBody.put(PacketConst.KEY_ACTION_TYPE, PacketConst.REQUEST_POST_SHORT_TERM_DATA);
        serverBody.put(PacketConst.KEY_DATA_KEY, dataHashKey);
        serverBody.put(PacketConst.KEY_EXTRA_DATA, finalData);

        PacketRequester.addToRequestQueue(context, PacketConst.SERVICE_TYPE_PACKET_PROXY, serverBody, response -> {
            try {
                ResultPacket resultPacket = ResultPacket.parseFrom(response.toString());
                if(resultPacket.isResultOk()) {
                    JSONObject responseObject = new JSONObject();
                    responseObject.put(PacketConst.KEY_DEVICE_ID, deviceId);
                    responseObject.put(PacketConst.KEY_DEVICE_NAME, deviceName);
                    responseObject.put(PacketConst.KEY_DATA_KEY, Integer.toString(finalData.hashCode()));
                    responseObject.put("type", PacketConst.SERVICE_TYPE_PACKET_PROXY);

                    finalProcessData(responseObject, PackageName, context, useFCMOnly);
                }
            } catch (JSONException | IOException e) {
                e.printStackTrace();
            }
        }, Throwable::printStackTrace);
    }

    protected static JSONObject encryptData(JSONObject data) throws Exception {
        SharedPreferences prefs = getPrefs();
        String rawPassword = prefs.getString("EncryptionPassword", "");
        boolean useEncryption = prefs.getBoolean("UseDataEncryption", false);
        boolean isAlwaysEncryptData = prefs.getBoolean("AlwaysEncryptData", true);

        boolean useHmacAuth = prefs.getBoolean("AllowOnlyPaired", false) && prefs.getBoolean("UseHMacAuth", false) && switch (data.getString("type")) {
            case "pair|request_device_list", "pair|request_pair", "pair|response_device_list", "pair|accept_pair" ->
                    false;
            default -> true;
        };

        String DEVICE_NAME = NotiListenerService.getDeviceName();
        String DEVICE_ID = getUniqueID();
        String HmacToken = HMACCrypto.generateTokenIdentifier(DEVICE_NAME, DEVICE_ID);

        if ((useEncryption && !rawPassword.isEmpty()) || isAlwaysEncryptData) {
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid != null) {
                String finalPassword = AESCrypto.parseAESToken(useEncryption ? AESCrypto.decrypt(rawPassword, AESCrypto.parseAESToken(uid)) : Base64.encodeToString(prefs.getString("Email", "").getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                String encryptedData = useHmacAuth ? HMACCrypto.encrypt(data.toString(), DEVICE_ID, finalPassword) : AESCrypto.encrypt(data.toString(), finalPassword);

                JSONObject newData = new JSONObject();
                newData.put("encrypted", "true");
                newData.put("encryptedData", CompressStringUtil.compressString(encryptedData));
                newData.put("HmacID", useHmacAuth ? HmacToken : "none");
                data = newData;
            }
        } else {
            if (useHmacAuth) {
                String encryptedData = HMACCrypto.encrypt(data.toString(), DEVICE_ID, null);
                JSONObject newData = new JSONObject();
                newData.put("encrypted", "false");
                newData.put("encryptedData", CompressStringUtil.compressString(encryptedData));
                newData.put("HmacID", HmacToken);
                data = newData;
            } else {
                data.put("encrypted", "false");
                data.put("HmacID", "none");
            }
        }

        return data;
    }

    protected static void finalProcessData(JSONObject notification, String PackageName, Context context, boolean useFCMOnly) throws JSONException {
        SharedPreferences prefs = getPrefs();
        notification.put("topic", prefs.getString("UID", ""));

        if(notification.has("encryptedData")) {
            int uniqueId = notification.getString("encryptedData").hashCode();
            FirebaseMessageService.selfReceiveDetectorList.add(uniqueId);
        }

        JSONObject finalObject = new JSONObject();
        finalObject.put("android", new JSONObject().put("priority", "high"));
        finalObject.put("data", notification);
        finalObject.put("topic", prefs.getString("UID", ""));

        String networkProvider = useFCMOnly ? "Firebase Cloud Message" : prefs.getString("server", "Firebase Cloud Message");
        switch (networkProvider) {
            case "Firebase Cloud Message" ->
                    sendFCMNotificationWrapper(finalObject, PackageName, context);
            case "Pushy" -> {
                if (!prefs.getString("ApiKey_Pushy", "").isEmpty())
                    sendPushyNotification(finalObject, PackageName, context);
            }
            default -> {
                boolean isAppInstalled;
                PackageManager packageManager = context.getPackageManager();

                try {
                    packageManager.getApplicationInfo(networkProvider, PackageManager.GET_META_DATA);
                    isAppInstalled = true;
                } catch (PackageManager.NameNotFoundException e) {
                    isAppInstalled = false;
                }

                if(isAppInstalled) {
                    Bundle extras = new Bundle();
                    extras.putString(PluginConst.DATA_KEY_TYPE, PluginConst.NET_PROVIDER_POST);
                    extras.putSerializable(PluginConst.NET_PROVIDER_DATA, finalObject.toString());
                    PluginActions.sendBroadcast(context, networkProvider, extras);
                } else {
                    sendFCMNotificationWrapper(finalObject, PackageName, context);
                }
            }
        }

        System.gc();
    }

    private static void sendFCMNotificationWrapper(JSONObject notification, String PackageName, Context context) {
        new Thread(() -> {
            try {
                sendFCMNotification(notification, PackageName, context);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    private static void sendFCMNotification(JSONObject notification, String PackageName, Context context) throws JSONException {
        PowerUtils manager = getInstance().manager;
        JSONObject objToSend = new JSONObject();
        objToSend.put("message", notification);

        final String FCM_API = "https://fcm.googleapis.com/v1/projects/notisender-41c1b/messages:send";
        final String serverKey = "Bearer " + getGoogleOAuthAccessToken(context);
        final String contentType =  "application/json; UTF-8";
        final String TAG = "NOTIFICATION TAG";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, FCM_API, objToSend,
                response -> {
                    Log.i(TAG, "onResponse: " + response.toString() + " ,package: " + PackageName);
                    manager.release();
                },
                error -> {
                    Toast.makeText(context, "Failed to send Notification! Please check internet and try again!", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "onErrorResponse: Didn't work, message: " + error.toString() + " ,package: " + PackageName);
                    manager.release();
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Authorization", serverKey);
                params.put("Content-Type", contentType);
                return params;
            }
        };

        if(BuildConfig.DEBUG) Log.d("DATA_TO_POST", String.valueOf(objToSend));
        JsonRequest.getInstance(context).addToRequestQueue(jsonObjectRequest);
    }

    private static String getGoogleOAuthAccessToken(Context context) {
        try {
            GoogleCredentials googleCredentials = GoogleCredentials
                    .fromStream(context.getAssets().open("service-account.json"))
                    .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
            googleCredentials.refreshIfExpired();
            return googleCredentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private static void sendPushyNotification(JSONObject notification, String PackageName, Context context) {
        SharedPreferences prefs = getPrefs();
        PowerUtils manager = getInstance().manager;

        final String URI = "https://api.pushy.me/push?api_key=" + prefs.getString("ApiKey_Pushy", "");
        final String contentType = "application/json";
        final String TAG = "NOTIFICATION TAG";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URI, notification, response -> {
            Log.i(TAG, "onResponse: " + response.toString() + " ,package: " + PackageName);
            manager.release();
        }, error -> {
            Toast.makeText(context, "Failed to send Notification! Please check internet and try again!", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "onErrorResponse: Didn't work: " + new String(error.networkResponse.data, StandardCharsets.UTF_8) + ", package: " + PackageName);
            manager.release();
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Content-Type", contentType);
                return params;
            }
        };
        JsonRequest.getInstance(context).addToRequestQueue(jsonObjectRequest);
    }
}
