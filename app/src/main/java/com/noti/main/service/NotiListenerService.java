package com.noti.main.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
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

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.firebase.auth.FirebaseAuth;

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.receiver.plugin.PluginActions;
import com.noti.main.receiver.plugin.PluginConst;
import com.noti.main.service.media.MediaReceiver;
import com.noti.main.utils.network.AESCrypto;
import com.noti.main.utils.network.HMACCrypto;
import com.noti.main.utils.network.JsonRequest;
import com.noti.main.utils.network.CompressStringUtil;
import com.noti.main.service.IntervalQueries.*;
import com.noti.main.utils.PowerUtils;
import com.noti.plugin.data.NetPacket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public static String getTopic() {
        SharedPreferences prefs = getPrefs();
        return prefs == null ? "" : "/topics/" + prefs.getString("UID", "");
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
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
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
        if (Build.VERSION.SDK_INT > 22) {
            return ((TelecomManager) context.getSystemService(Context.TELECOM_SERVICE)).getDefaultDialerPackage();
        } else {
            Intent dialerIntent = new Intent(Intent.ACTION_DIAL).addCategory(Intent.CATEGORY_DEFAULT);
            List<ResolveInfo> mResolveInfoList = context.getPackageManager().queryIntentActivities(dialerIntent, 0);
            return mResolveInfoList.get(0).activityInfo.packageName;
        }
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
            String DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(time);
            String KEY = sbn.getKey();

            boolean isLogging = BuildConfig.DEBUG;

            if (!prefs.getString("UID", "").equals("") && prefs.getBoolean("serviceToggle", false)) {
                String mode = prefs.getString("service", "reception");
                if (mode.equals("send") || mode.equals("hybrid")) {
                    String TITLE = extra.getString(Notification.EXTRA_TITLE) + "";
                    String TEXT = extra.getString(Notification.EXTRA_TEXT) + "";
                    String TEXT_LINES = extra.getString(Notification.EXTRA_TEXT_LINES) + "";
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

                                if (!originString.equals("")) array = new JSONArray(originString);
                                object.put("date", DATE);
                                object.put("package", PackageName);
                                object.put("title", extra.getString(Notification.EXTRA_TITLE));
                                object.put("text", extra.getString(Notification.EXTRA_TEXT));
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
                        sendNormalNotification(notification, PackageName, isLogging, DATE, TITLE, TEXT, KEY);
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
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(time);
        String PackageName = getSystemDialerApp(context);

        if (isTelecomIntervalGaped(address, time)) {
            String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
            String DEVICE_ID = getUniqueID();
            String TOPIC = "/topics/" + prefs.getString("UID", "");

            JSONObject notificationHead = new JSONObject();
            JSONObject notificationBody = new JSONObject();
            try {
                notificationBody.put("type", "send|telecom");
                notificationBody.put("address", address);
                notificationBody.put("nickname", nickname);
                notificationBody.put("package", PackageName);
                notificationBody.put("device_name", DEVICE_NAME);
                notificationBody.put("device_id", DEVICE_ID);
                notificationBody.put("date", date);

                notificationHead.put("to", TOPIC);
                notificationHead.put("data", notificationBody);
            } catch (JSONException e) {
                if (isLogging) Log.e("Noti", "onCreate: " + e.getMessage());
            }
            if (isLogging) Log.d("data", notificationHead.toString());
            sendNotification(notificationHead, PackageName, context);
        }
    }

    public void sendSmsNotification(Context context, Boolean isLogging, String PackageName, String address, String nickname, String message, Date time) {
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(time);

        if (isSmsIntervalGaped(context, address, message, time)) {
            String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
            String DEVICE_ID = getUniqueID();
            String TOPIC = "/topics/" + prefs.getString("UID", "");

            JSONObject notificationHead = new JSONObject();
            JSONObject notificationBody = new JSONObject();
            try {
                notificationBody.put("type", "send|sms");
                notificationBody.put("message", message);
                notificationBody.put("address", address);
                notificationBody.put("nickname", nickname);
                notificationBody.put("device_name", DEVICE_NAME);
                notificationBody.put("device_id", DEVICE_ID);
                notificationBody.put("date", date);

                notificationHead.put("to", TOPIC);
                notificationHead.put("data", notificationBody);
            } catch (JSONException e) {
                if (isLogging) Log.e("Noti", "onCreate: " + e.getMessage());
            }
            if (isLogging) Log.d("data", notificationHead.toString());
            sendNotification(notificationHead, PackageName, context);
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void sendNormalNotification(Notification notification, String PackageName, boolean isLogging, String DATE, String TITLE, String TEXT, String KEY) {
        PackageManager pm = this.getPackageManager();
        Bitmap ICON = null;
        try {
            if (prefs.getBoolean("IconUseNotification", false)) {
                Context packageContext = createPackageContext(PackageName, CONTEXT_IGNORE_SECURITY);

                if (Build.VERSION.SDK_INT > 22) {
                    Icon LargeIcon = notification.getLargeIcon();
                    Icon SmallIcon = notification.getSmallIcon();

                    if (LargeIcon != null)
                        ICON = getBitmapFromDrawable(LargeIcon.loadDrawable(packageContext));
                    else if (SmallIcon != null)
                        ICON = getBitmapFromDrawable(SmallIcon.loadDrawable(packageContext));
                    else
                        ICON = getBitmapFromDrawable(pm.getApplicationIcon(PackageName));
                } else {
                    Bitmap LargeIcon = notification.largeIcon;
                    int SmallIcon = notification.icon;

                    if (LargeIcon != null) ICON = LargeIcon;
                    else if (SmallIcon != 0)
                        ICON = getBitmapFromDrawable(packageContext.getDrawable(SmallIcon));
                }
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
            ICONS = (res == 0 ? "none" : CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(getResizedBitmap(ICON, res, res))));
        } else ICONS = "none";

        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getUniqueID();
        String TOPIC = "/topics/" + prefs.getString("UID", "");
        String APPNAME = null;
        try {
            APPNAME = "" + pm.getApplicationLabel(pm.getApplicationInfo(PackageName, PackageManager.GET_META_DATA));
        } catch (PackageManager.NameNotFoundException e) {
            if (isLogging) Log.d("Error", "Package not found : " + PackageName);
        }

        if (isLogging) Log.d("length", String.valueOf(ICONS.length()));

        JSONObject notificationHead = new JSONObject();
        JSONObject notificationBody = new JSONObject();
        try {
            notificationBody.put("type", "send|normal");
            notificationBody.put("title", TITLE == null || TITLE.equals("null") ? prefs.getString("DefaultTitle", "New notification") : TITLE);
            notificationBody.put("message", TEXT == null || TEXT.equals("null") ? prefs.getString("DefaultMessage", "notification arrived.") : TEXT);
            notificationBody.put("package", PackageName);
            notificationBody.put("appname", APPNAME);
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("date", DATE);
            notificationBody.put("icon", ICONS);
            notificationBody.put("notification_key", KEY);

            int dataLimit = prefs.getInt("DataLimit", 4096);
            if (notificationBody.toString().length() >= dataLimit - 20 && !prefs.getBoolean("UseSplitData", false)) {
                notificationBody.put("icon", "none");
            }

            notificationHead.put("to", TOPIC);
            notificationHead.put("android", new JSONObject().put("priority", "high"));
            notificationHead.put("priority", 10);
            notificationHead.put("data", notificationBody);
        } catch (JSONException e) {
            if (isLogging) Log.e("Noti", "onCreate: " + e.getMessage());
        }
        if (isLogging) Log.d("data", notificationHead.toString());
        sendNotification(notificationHead, PackageName, this);
    }

    private boolean isBannedWords(String TEXT, String TITLE) {
        if (prefs.getBoolean("UseBannedOption", false)) {
            String word = prefs.getString("BannedWords", "");
            if (!word.equals("")) {
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

    public static void sendNotification(JSONObject notification, String PackageName, Context context) {
        SharedPreferences prefs = getPrefs();
        PowerUtils manager = getInstance().manager;

        if (prefs == null)
            prefs = context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        if (manager == null) manager = PowerUtils.getInstance(context);
        manager.acquire();

        try {
            boolean useSplit = prefs.getBoolean("UseSplitData", false) && notification.getString("data").length() > 3072;
            boolean useEncryption = prefs.getBoolean("UseDataEncryption", false);
            boolean splitAfterEncryption = prefs.getBoolean("SplitAfterEncryption", false);
            int splitInterval = prefs.getInt("SplitInterval", 500);

            if (useSplit) {
                if(useEncryption && splitAfterEncryption) encryptData(notification);
                for (JSONObject object : splitData(notification)) {
                    if(useEncryption && !splitAfterEncryption) encryptData(object);
                    finalProcessData(object, PackageName, context);
                    if (splitInterval > 0) {
                        Thread.sleep(splitInterval);
                    }
                }
                return;
            } else if (useEncryption) {
                encryptData(notification);
            }

            finalProcessData(notification, PackageName, context);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected static JSONObject[] splitData(JSONObject notification) throws JSONException {
        String rawData = notification.getString("data");

        int size = 1024;
        List<String> arr = new ArrayList<>((rawData.length() + size - 1) / size);
        for (int start = 0; start < rawData.length(); start += size) {
            arr.add(rawData.substring(start, Math.min(rawData.length(), start + size)));
        }

        JSONObject[] data = new JSONObject[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            String str = arr.get(i);
            JSONObject obj = new JSONObject();
            obj.put("type", "split_data");
            obj.put("split_index", i + "/" + arr.size());
            obj.put("split_unique", Integer.toString(rawData.hashCode()));
            obj.put("split_data", str);
            obj.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
            obj.put("device_id", getUniqueID());
            Log.d("unique_id", "id: " + rawData.hashCode());
            data[i] = new JSONObject(notification.put("data", obj).toString());
        }
        return data;
    }

    protected static void encryptData(JSONObject notification) throws Exception {
        SharedPreferences prefs = getPrefs();
        JSONObject data = notification.getJSONObject("data");

        String rawPassword = prefs.getString("EncryptionPassword", "");
        boolean useEncryption = prefs.getBoolean("UseDataEncryption", false);
        boolean isAlwaysEncryptData = prefs.getBoolean("AlwaysEncryptData", true);

        boolean useHmacAuth = prefs.getBoolean("AllowOnlyPaired", false) && prefs.getBoolean("UseHMacAuth", false) && switch (data.getString("type")) {
            case "pair|request_device_list", "pair|request_pair", "pair|response_device_list", "pair|accept_pair" ->
                    false;
            default -> true;
        };

        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getUniqueID();
        String HmacToken = HMACCrypto.generateTokenIdentifier(DEVICE_NAME, DEVICE_ID);

        if ((useEncryption && !rawPassword.equals("")) || isAlwaysEncryptData) {
            String uid = FirebaseAuth.getInstance().getUid();
            if (uid != null) {
                String finalPassword = AESCrypto.parseAESToken(useEncryption ? AESCrypto.decrypt(rawPassword, AESCrypto.parseAESToken(uid)) : Base64.encodeToString(prefs.getString("Email", "").getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                String encryptedData = useHmacAuth ? HMACCrypto.encrypt(data.toString(), DEVICE_ID, finalPassword) : AESCrypto.encrypt(data.toString(), finalPassword);

                JSONObject newData = new JSONObject();
                newData.put("encrypted", "true");
                newData.put("encryptedData", CompressStringUtil.compressString(encryptedData));
                newData.put("HmacID", useHmacAuth ? HmacToken : "none");
                notification.put("data", newData);
            }
        } else {
            if (useHmacAuth) {
                String encryptedData = HMACCrypto.encrypt(data.toString(), DEVICE_ID, null);
                JSONObject newData = new JSONObject();
                newData.put("encrypted", "false");
                newData.put("encryptedData", CompressStringUtil.compressString(encryptedData));
                newData.put("HmacID", HmacToken);
                notification.put("data", newData);
            } else {
                data.put("encrypted", "false");
                data.put("HmacID", "none");
                notification.put("data", data);
            }
        }
    }

    protected static void finalProcessData(JSONObject notification, String PackageName, Context context) throws JSONException {
        SharedPreferences prefs = getPrefs();
        JSONObject data = notification.getJSONObject("data");
        data.put("topic", prefs.getString("UID", ""));
        notification.put("data", data);

        if(data.has("encryptedData")) {
            int uniqueId = data.getString("encryptedData").hashCode();
            FirebaseMessageService.selfReceiveDetectorList.add(uniqueId);
        }

        String networkProvider = prefs.getString("server", "Firebase Cloud Message");
        switch (networkProvider) {
            case "Firebase Cloud Message" ->
                    sendFCMNotification(notification, PackageName, context);
            case "Pushy" -> {
                if (!prefs.getString("ApiKey_Pushy", "").equals(""))
                    sendPushyNotification(notification, PackageName, context);
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
                    extras.putSerializable(PluginConst.NET_PROVIDER_DATA, notification.toString());
                    PluginActions.sendBroadcast(context, networkProvider, extras);
                } else {
                    sendFCMNotification(notification, PackageName, context);
                }
            }
        }

        System.gc();
    }

    private static void sendFCMNotification(JSONObject notification, String PackageName, Context context) {
        SharedPreferences prefs = getPrefs();
        PowerUtils manager = getInstance().manager;

        final String FCM_API = "https://fcm.googleapis.com/fcm/send";
        final String serverKey = "key=" + prefs.getString("ApiKey_FCM", "");
        final String contentType = "application/json";
        final String TAG = "NOTIFICATION TAG";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, FCM_API, notification,
                response -> {
                    Log.i(TAG, "onResponse: " + response.toString() + " ,package: " + PackageName);
                    manager.release();
                },
                error -> {
                    Toast.makeText(context, "Failed to send Notification! Please check internet and try again!", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "onErrorResponse: Didn't work" + " ,package: " + PackageName);
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
        JsonRequest.getInstance(context).addToRequestQueue(jsonObjectRequest);
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
