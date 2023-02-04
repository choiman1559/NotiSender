package com.noti.main.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.firebase.auth.FirebaseAuth;

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.service.media.MediaReceiver;
import com.noti.main.utils.AESCrypto;
import com.noti.main.utils.JsonRequest;
import com.noti.main.utils.CompressStringUtil;
import com.noti.main.service.IntervalQueries.*;
import com.noti.main.utils.PowerUtils;

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
    private static SharedPreferences prefs;
    private static SharedPreferences logPrefs;
    private static PowerUtils manager;

    @SuppressLint("StaticFieldLeak")
    public static MediaReceiver mediaReceiver;

    private static final Object pastNotificationLock = new Object();
    private volatile StatusBarNotification pastNotification = null;

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

    public static String getTopic() {
        return prefs == null ? "" : "/topics/" + prefs.getString("UID", "");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = this.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        logPrefs = this.getSharedPreferences("com.noti.main_logs", MODE_PRIVATE);
        manager = PowerUtils.getInstance(this);
        mediaReceiver = new MediaReceiver(this);
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
        String str = "";
        if (prefs != null) {
            switch (prefs.getString("uniqueIdMethod", "Globally-Unique ID")) {
                case "Globally-Unique ID":
                    str = prefs.getString("GUIDPrefix", "");
                    break;

                case "Android ID":
                    str = prefs.getString("AndroidIDPrefix", "");
                    break;

                case "Firebase IID":
                    str = prefs.getString("FirebaseIIDPrefix", "");
                    break;

                case "Device MAC ID":
                    str = prefs.getString("MacIDPrefix", "");
                    break;
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
            @SuppressWarnings("deprecation") List<ResolveInfo> mResolveInfoList = context.getPackageManager().queryIntentActivities(dialerIntent, 0);
            return mResolveInfoList.get(0).activityInfo.packageName;
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        Log.d("ddd", sbn.getPackageName());
        if(manager == null) manager = PowerUtils.getInstance(this);
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

            boolean isLogging = BuildConfig.DEBUG;

            if (!prefs.getString("UID", "").equals("") && prefs.getBoolean("serviceToggle", false)) {
                String mode = prefs.getString("service", "");
                if (mode.equals("send") || mode.equals("hybrid")) {
                    String TITLE = extra.getString(Notification.EXTRA_TITLE) + "";
                    String TEXT = extra.getString(Notification.EXTRA_TEXT) + "";
                    String TEXT_LINES = extra.getString(Notification.EXTRA_TEXT_LINES) + "";
                    if(!TEXT_LINES.isEmpty() && TEXT.isEmpty()) TEXT = TEXT_LINES;
                    String PackageName = sbn.getPackageName();

                    if (PackageName.equals(getPackageName()) && (!TITLE.toLowerCase().contains("test") || TITLE.contains("main"))) {
                        manager.release();
                        return;
                    } else if (prefs.getBoolean("UseReplySms", false) && Telephony.Sms.getDefaultSmsPackage(this).equals(PackageName)) {
                        sendSmsNotification(isLogging, PackageName, time);
                    } else if (prefs.getBoolean("UseReplyTelecom", false) && getSystemDialerApp(this).equals(PackageName)) {
                        if (prefs.getBoolean("UseCallLog", false))
                            sendTelecomNotification(isLogging, PackageName, time);
                        else {
                            manager.release();
                            return;
                        }
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
                        sendNormalNotification(notification, PackageName, isLogging, DATE, TITLE, TEXT);
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

    public static void sendFindTaskNotification(Context context) {
        boolean isLogging = BuildConfig.DEBUG;
        Date date = Calendar.getInstance().getTime();
        if (prefs == null)
            prefs = context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        int timeInterval = prefs.getInt("IntervalTime", 150);

        if (isLogging)
            Log.d("IntervalCalculate", "Package " + context.getPackageName() + "/Calculated(ms):" + (date.getTime() - intervalTimestamp));
        if (intervalTimestamp != 0 && date.getTime() - intervalTimestamp <= timeInterval) {
            intervalTimestamp = date.getTime();
            return;
        }
        intervalTimestamp = date.getTime();

        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getUniqueID();
        String TOPIC = "/topics/" + prefs.getString("UID", "");

        JSONObject notificationHead = new JSONObject();
        JSONObject notificationBody = new JSONObject();
        try {
            notificationBody.put("type", "send|find");
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("date", date);

            notificationHead.put("to", TOPIC);
            notificationHead.put("data", notificationBody);
        } catch (JSONException e) {
            if (isLogging) Log.e("Noti", "onCreate: " + e.getMessage());
        }
        if (isLogging) Log.d("data", notificationHead.toString());
        sendNotification(notificationHead, context.getPackageName(), context);
    }

    private void sendTelecomNotification(Boolean isLogging, String PackageName, Date time) {
        Cursor cursor = getContentResolver().query(android.provider.CallLog.Calls.CONTENT_URI, null, android.provider.CallLog.Calls.TYPE + "=" + CallLog.Calls.INCOMING_TYPE, null, null);
        cursor.moveToFirst();
        String address = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(Long.parseLong(cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)))));
        cursor.close();

        if (isTelecomIntervalGaped(address, time)) {
            String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
            String DEVICE_ID = getUniqueID();
            String TOPIC = "/topics/" + prefs.getString("UID", "");

            JSONObject notificationHead = new JSONObject();
            JSONObject notificationBody = new JSONObject();
            try {
                notificationBody.put("type", "send|telecom");
                notificationBody.put("address", address);
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
            sendNotification(notificationHead, PackageName, this);
        }
    }

    public void sendTelecomNotification(Context context, Boolean isLogging, String address) {
        if (prefs == null)
            prefs = context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean("UseReplyTelecom", false) && !prefs.getBoolean("UseCallLog", false)) {
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
    }

    private void sendSmsNotification(Boolean isLogging, String PackageName, Date time) {
        Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
        cursor.moveToFirst();
        String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
        String message = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(Long.parseLong(cursor.getString(cursor.getColumnIndexOrThrow("date")))));
        cursor.close();

        if (isSmsIntervalGaped(address, message, time)) {
            String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
            String DEVICE_ID = getUniqueID();
            String TOPIC = "/topics/" + prefs.getString("UID", "");

            JSONObject notificationHead = new JSONObject();
            JSONObject notificationBody = new JSONObject();
            try {
                notificationBody.put("type", "send|sms");
                notificationBody.put("message", message);
                notificationBody.put("address", address);
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
            sendNotification(notificationHead, PackageName, this);
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void sendNormalNotification(Notification notification, String PackageName, boolean isLogging, String DATE, String TITLE, String TEXT) {
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
            int res;
            switch (prefs.getString("IconRes", "")) {
                case "68 x 68 (Not Recommend)":
                    res = 68;
                    break;

                case "52 x 52 (Default)":
                    res = 52;
                    break;

                case "36 x 36":
                    res = 36;
                    break;

                default:
                    res = 0;
                    break;
            }
            ICONS = (res == 0 || prefs.getBoolean("UseDataEncryption", false) ? "none" : CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(getResizedBitmap(ICON, res, res))));
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

            int dataLimit = prefs.getInt("DataLimit", 4096);
            boolean isLimit = notificationBody.toString().length() < dataLimit - 20 || prefs.getBoolean("UseSplitData", false);

            notificationHead.put("to", TOPIC);
            notificationHead.put("android", new JSONObject().put("priority", "high"));
            notificationHead.put("priority", 10);
            notificationHead.put("data", isLimit ? notificationBody : notificationBody.put("icon", "none"));
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

    private boolean isSmsIntervalGaped(String Number, String Content, Date time) {
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
        if (prefs == null)
            prefs = context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        if (prefs.getString("server", "Firebase Cloud Message").equals("Pushy")) {
            if (!prefs.getString("ApiKey_Pushy", "").equals(""))
                sendPushyNotification(notification, PackageName, context);
        } else sendFCMNotification(notification, PackageName, context);
        System.gc();
    }

    private static void sendFCMNotification(JSONObject notification, String PackageName, Context context) {
        final String FCM_API = "https://fcm.googleapis.com/fcm/send";
        final String serverKey = "key=" + prefs.getString("ApiKey_FCM", "");
        final String contentType = "application/json";
        final String TAG = "NOTIFICATION TAG";

        if(manager == null) manager = PowerUtils.getInstance(context);
        manager.acquire();

        if (prefs.getBoolean("UseSplitData", false)) {
            try {
                String rawData = notification.getString("data");
                if (rawData.length() > 3072) {
                    String[] arr = rawData.split("(?<=\\G.{1024})");
                    for(int i = 0;i < arr.length;i++) {
                        String str = arr[i];
                        JSONObject obj = new JSONObject();
                        obj.put("type", "split_data");
                        obj.put("split_index", i + "/" + arr.length);
                        obj.put("split_unique", Integer.toString(rawData.hashCode()));
                        obj.put("split_data", str);
                        obj.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
                        obj.put("device_id", getUniqueID());
                        Log.d("unique_id", "id: " + rawData.hashCode());
                        sendFCMNotification(notification.put("data", obj), PackageName, context);
                    }
                    return;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            String rawPassword = prefs.getString("EncryptionPassword", "");
            JSONObject data = notification.getJSONObject("data");
            if (prefs.getBoolean("UseDataEncryption", false) && !rawPassword.equals("")) {
                String uid = FirebaseAuth.getInstance().getUid();
                if (uid != null) {
                    String encryptedData = AESCrypto.encrypt(notification.getJSONObject("data").toString(), AESCrypto.parseAESToken(AESCrypto.decrypt(rawPassword, AESCrypto.parseAESToken(uid))));

                    JSONObject newData = new JSONObject();
                    newData.put("encrypted", "true");
                    newData.put("encryptedData", CompressStringUtil.compressString(encryptedData));
                    notification.put("data", newData);
                }
            } else {
                data.put("encrypted", "false");
                notification.put("data", data);
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) e.printStackTrace();
        }

        try {
            JSONObject data = notification.getJSONObject("data");
            data.put("topic", prefs.getString("UID", ""));
        } catch (JSONException e) {
            e.printStackTrace();
        }

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
