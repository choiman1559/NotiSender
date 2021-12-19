package com.noti.main.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.noti.main.BuildConfig;
import com.noti.main.utils.AESCrypto;
import com.noti.main.utils.JsonRequest;
import com.noti.main.utils.CompressStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NotiListenerService extends NotificationListenerService {

    private static class Query {
        private String Package;
        private long Timestamp;

        public String getPackage() {
            return Package;
        }

        public void setPackage(String aPackage) {
            Package = aPackage;
        }

        public long getTimestamp() {
            return Timestamp;
        }

        public void setTimestamp(long timestamp) {
            Timestamp = timestamp;
        }
    }

    private static class SmsQuery {
        private String Number;
        private String Content;
        private long TimeStamp;

        public String getNumber() {
            return Number;
        }

        public String getContent() {
            return Content;
        }

        public long getTimeStamp() {
            return TimeStamp;
        }

        public void setNumber(String number) {
            Number = number;
        }

        public void setContent(String content) {
            Content = content;
        }

        public void setTimeStamp(long timeStamp) {
            TimeStamp = timeStamp;
        }
    }

    private static SharedPreferences prefs;
    private volatile long intervalTimestamp = 0;
    private volatile StatusBarNotification pastNotification = null;
    private final ArrayList<Query> intervalQuery = new ArrayList<>();
    private final ArrayList<SmsQuery> smsIntervalQuery = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = this.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
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

    private Bitmap getBitmapFromDrawable(Drawable drawable) {
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

    public static String getMACAddress() {
        String interfaceName = "wlan0";
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (!intf.getName().equalsIgnoreCase(interfaceName)) continue;
                byte[] mac = intf.getHardwareAddress();
                if (mac == null) return "";
                StringBuilder buf = new StringBuilder();
                for (byte b : mac) buf.append(String.format("%02X:", b));
                if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
                return buf.toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        if (sbn.equals(pastNotification)) {
            pastNotification = sbn;
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
                String TITLE = extra.getString(Notification.EXTRA_TITLE);
                String TEXT = extra.getString(Notification.EXTRA_TEXT);
                String PackageName = sbn.getPackageName();

                if (PackageName.equals(getPackageName()) && (TITLE != null && (!TITLE.toLowerCase().contains("test") || TITLE.contains("main"))))
                    return;
                else if (prefs.getBoolean("UseReplySms", false) && Telephony.Sms.getDefaultSmsPackage(this).equals(PackageName)) {
                    sendSmsNotification(isLogging, PackageName, time);
                } else if (isWhitelist(PackageName)) {
                    if (prefs.getBoolean("StrictStringNull", false) && (TITLE == null || TEXT == null))
                        return;
                    if (isBannedWords(TEXT, TITLE) || isIntervalNotGaped(isLogging, PackageName, time))
                        return;
                    new Thread(() -> {
                        try {
                            JSONArray array = new JSONArray();
                            JSONObject object = new JSONObject();
                            String originString = prefs.getString("sendLogs", "");

                            if (!originString.equals("")) array = new JSONArray(originString);
                            object.put("date", DATE);
                            object.put("package", PackageName);
                            object.put("title", extra.getString(Notification.EXTRA_TITLE));
                            object.put("text", extra.getString(Notification.EXTRA_TEXT));
                            array.put(object);
                            prefs.edit().putString("sendLogs", array.toString()).apply();

                            if (array.length() >= prefs.getInt("HistoryLimit", 150)) {
                                int a = array.length() - prefs.getInt("HistoryLimit", 150);
                                for (int i = 0; i < a; i++) {
                                    array.remove(i);
                                }
                                prefs.edit().putString("sendLogs", array.toString()).apply();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }).start();
                    sendNormalNotification(notification, PackageName, isLogging, DATE, TITLE, TEXT);
                }
            }
        }
        pastNotification = sbn;
    }

    private void sendSmsNotification(Boolean isLogging, String PackageName, Date time) {
        Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
        cursor.moveToFirst();
        String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
        String message = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(Long.parseLong(cursor.getString(cursor.getColumnIndexOrThrow("date")))));
        cursor.close();

        if(isSmsIntervalGaped(address, message, time)) {
            String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
            String DEVICE_ID = getMACAddress();
            String TOPIC = "/topics/" + prefs.getString("UID", "");

            JSONObject notificationHead = new JSONObject();
            JSONObject notifcationBody = new JSONObject();
            try {
                notifcationBody.put("type", "send|sms");
                notifcationBody.put("message", message);
                notifcationBody.put("address", address);
                notifcationBody.put("package", PackageName);
                notifcationBody.put("device_name", DEVICE_NAME);
                notifcationBody.put("device_id", DEVICE_ID);
                notifcationBody.put("date", date);

                int dataLimit = prefs.getInt("DataLimit", 4096);
                notificationHead.put("to", TOPIC);
                notificationHead.put("data", notifcationBody.toString().length() < dataLimit ? notifcationBody : notifcationBody.put("icon", "none"));
            } catch (JSONException e) {
                if (isLogging) Log.e("Noti", "onCreate: " + e.getMessage());
            }
            if (isLogging) Log.d("data", notificationHead.toString());
            sendNotification(notificationHead, PackageName, this);
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void sendNormalNotification(Notification notification, String PackageName, boolean isLogging, String DATE, String TITLE, String TEXT) {
        Bitmap ICON = null;
        try {
            if (prefs.getBoolean("IconUseNotification", false)) {
                Context packageContext = createPackageContext(PackageName, CONTEXT_IGNORE_SECURITY);

                if(Build.VERSION.SDK_INT > 22) {
                    Icon LargeIcon = notification.getLargeIcon();
                    Icon SmallIcon = notification.getSmallIcon();

                    if (LargeIcon != null)
                        ICON = getBitmapFromDrawable(LargeIcon.loadDrawable(packageContext));
                    else if (SmallIcon != null)
                        ICON = getBitmapFromDrawable(SmallIcon.loadDrawable(packageContext));
                    else
                        ICON = getBitmapFromDrawable(this.getPackageManager().getApplicationIcon(PackageName));
                } else {
                    Bitmap LargeIcon = notification.largeIcon;
                    int SmallIcon = notification.icon;

                    if(LargeIcon != null) ICON = LargeIcon;
                    else if(SmallIcon != 0) ICON = getBitmapFromDrawable(packageContext.getDrawable(SmallIcon));
                }
            } else {
                ICON = getBitmapFromDrawable(this.getPackageManager().getApplicationIcon(PackageName));
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
            ICONS = res == 0 ? "none" : CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(getResizedBitmap(ICON, res, res)));
        } else ICONS = "none";

        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getMACAddress();
        String TOPIC = "/topics/" + prefs.getString("UID", "");
        String APPNAME = null;
        try {
            APPNAME = "" + NotiListenerService.this.getPackageManager().
                    getApplicationLabel(NotiListenerService.this.getPackageManager().getApplicationInfo(PackageName, PackageManager.GET_META_DATA));
        } catch (PackageManager.NameNotFoundException e) {
            if (isLogging) Log.d("Error", "Package not found : " + PackageName);
        }

        if (isLogging) Log.d("length", String.valueOf(ICONS.length()));
        JSONObject notificationHead = new JSONObject();
        JSONObject notifcationBody = new JSONObject();
        try {
            notifcationBody.put("type", "send|normal");
            notifcationBody.put("title", TITLE != null ? TITLE : prefs.getString("DefaultTitle", "New notification"));
            notifcationBody.put("message", TEXT != null ? TEXT : prefs.getString("DefaultMessage", "notification arrived."));
            notifcationBody.put("package", PackageName);
            notifcationBody.put("appname", APPNAME);
            notifcationBody.put("device_name", DEVICE_NAME);
            notifcationBody.put("device_id", DEVICE_ID);
            notifcationBody.put("date", DATE);
            notifcationBody.put("icon", ICONS);

            int dataLimit = prefs.getInt("DataLimit", 4096);
            notificationHead.put("to", TOPIC);
            notificationHead.put("android", new JSONObject().put("priority", "high"));
            notificationHead.put("priority", 10);
            notificationHead.put("data", notifcationBody.toString().length() < dataLimit - 20 ? notifcationBody : notifcationBody.put("icon", "none"));
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

    private boolean isSmsIntervalGaped(String Number, String Content, Date time) {
        if (prefs.getBoolean("UseInterval", false)) {
            int timeInterval = prefs.getInt("IntervalTime", 150);
            SmsQuery query = new SmsQuery();
            synchronized (smsIntervalQuery) {
                int index = -1;
                SmsQuery object = null;

                for(int i = 0;i < smsIntervalQuery.size(); i++) {
                    object = smsIntervalQuery.get(i);
                    if(object.getNumber().equals(Number) && object.getContent().equals(Content)) {
                        index = i;
                        break;
                    }
                }

                query.setNumber(Number);
                query.setContent(Content);
                query.setTimeStamp(time.getTime());

                if(index > -1) {
                    smsIntervalQuery.set(index, query);
                    if(time.getTime() - object.getTimeStamp() <= timeInterval) return false;
                } else {
                    smsIntervalQuery.add(query);
                }
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
        if(prefs == null) prefs = context.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
        if (prefs.getString("server", "Firebase Cloud Message").equals("Pushy")) {
            if(!prefs.getString("AuthKey_Pushy", "").equals("")) sendPushyNotification(notification, PackageName, context);
        } else sendFCMNotification(notification, PackageName, context);
        System.gc();
    }

    private static void sendFCMNotification(JSONObject notification, String PackageName, Context context) {
        final String FCM_API = "https://fcm.googleapis.com/fcm/send";
        final String serverKey = "key=" + prefs.getString("ApiKey_FCM", "");
        final String contentType = "application/json";
        final String TAG = "NOTIFICATION TAG";

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
        } catch(Exception e) {
            if(BuildConfig.DEBUG) e.printStackTrace();
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST,FCM_API, notification,
                response -> Log.i(TAG, "onResponse: " + response.toString() + " ,package: " + PackageName),
                error -> {
                    Toast.makeText(context, "Failed to send Notification! Please check internet and try again!", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "onErrorResponse: Didn't work" + " ,package: " + PackageName);
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
        final String URI = "https://api.pushy.me/push?api_key=" + prefs.getString("AuthKey_Pushy", "");
        final String contentType = "application/json";
        final String TAG = "NOTIFICATION TAG";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URI, notification, response -> Log.i(TAG, "onResponse: " + response.toString() + " ,package: " + PackageName), error -> {
            Toast.makeText(context, "Failed to send Notification! Please check internet and try again!", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "onErrorResponse: Didn't work: " + new String(error.networkResponse.data, StandardCharsets.UTF_8) + ", package: " + PackageName);
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
