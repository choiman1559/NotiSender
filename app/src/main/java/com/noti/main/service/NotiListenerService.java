package com.noti.main.service;

import android.app.Notification;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.toolbox.JsonObjectRequest;
import com.noti.main.BuildConfig;
import com.noti.main.utils.MySingleton;
import com.noti.main.R;
import com.noti.main.utils.CompressStringUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
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
import java.util.Objects;

public class NotiListenerService extends NotificationListenerService {

    String toString(Boolean boo) {
        return boo ? "true" : "false";
    }

    private static class Query {
        private String Package;
        private long Timestamp;

        public String getPackage() {
            return Package;
        }

        public void setPackage(String aPackage) {
            Package = aPackage;
        }

        public void setTimestamp(long timestamp) {
            Timestamp = timestamp;
        }

        public long getTimestamp() {
            return Timestamp;
        }
    }

    private volatile ArrayList<Query> intervalQuery = new ArrayList<>();
    private volatile long intervalTimestamp = 0;
    volatile StatusBarNotification pastNotification = null;

    void Log(String message, String time) {
        if (getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE).getBoolean("debugInfo", false)) {
            Log.d("debug", message);
            File txt = new File(Environment.getExternalStorageDirectory() + "/NotiSender_Logs", time + ".txt");
            try {
                if (!txt.getParentFile().exists()) txt.getParentFile().mkdirs();
                if (!txt.exists()) txt.createNewFile();
                RandomAccessFile raf = new RandomAccessFile(txt, "rw");
                raf.seek(raf.length());
                raf.writeBytes(new String(message.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8) + "\n");
                raf.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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

    static String getMACAddress() {
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
        if (sbn.equals(pastNotification)) return;

        Notification notification = sbn.getNotification();
        Bundle extra = notification.extras;
        SharedPreferences prefs = getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
        Date time = Calendar.getInstance().getTime();
        String DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(time);
        boolean isLogging = BuildConfig.DEBUG || prefs.getBoolean("debugInfo", false);

        new Thread(() -> {
            if (isLogging) {
                String str = "";
                str += "\n";
                str += "***onNotificationPosted debug info***\n";
                str += "date : " + DATE + "\n";
                str += "uid : " + prefs.getString("UID", "") + "\n";
                str += "package : " + sbn.getPackageName() + "\n";
                str += "service type : " + prefs.getString("service", "") + "\n";
                str += "if uid is blank : " + toString(Objects.equals(prefs.getString("UID", ""), "")) + "\n";
                str += "if service Enabled : " + toString(prefs.getBoolean("serviceToggle", false)) + "\n";
                str += "if include blacklist : " + toString(getSharedPreferences("Blacklist", MODE_PRIVATE).getBoolean(sbn.getPackageName(), false)) + "\n";
                str += "EXTRA_TITLE : " + extra.getString(Notification.EXTRA_TITLE) + "\n";
                str += "EXTRA_TEXT : " + extra.getString(Notification.EXTRA_TEXT) + "\n";
                str += "**************************************\n";
                str += "\n";
                Log(str, DATE);
            }
        }).start();

        if (prefs.getString("service", "").equals("send") && !prefs.getString("UID", "").equals("") && prefs.getBoolean("serviceToggle", false)) {
            if (prefs.getBoolean("UseReplySms", false) && Telephony.Sms.getDefaultSmsPackage(this).equals(sbn.getPackageName())) {
                Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
                cursor.moveToFirst();
                String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                String message = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(Long.parseLong(cursor.getString(cursor.getColumnIndexOrThrow("date")))));
                cursor.close();

                String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
                String DEVICE_ID = getMACAddress();
                String TOPIC = "/topics/" + prefs.getString("UID", "");

                JSONObject notificationHead = new JSONObject();
                JSONObject notifcationBody = new JSONObject();
                try {
                    notifcationBody.put("type", "send|sms");
                    notifcationBody.put("message", message);
                    notifcationBody.put("address", address);
                    notifcationBody.put("device_name", DEVICE_NAME);
                    notifcationBody.put("device_id", DEVICE_ID);
                    notifcationBody.put("date", date);

                    int dataLimit = prefs.getInt("DataLimit", 4096);
                    notificationHead.put("to", TOPIC);
                    notificationHead.put("data", notifcationBody.toString().length() < dataLimit ? notifcationBody : notifcationBody.put("icon", "none"));
                } catch (JSONException e) {
                    if(isLogging) Log.e("Noti", "onCreate: " + e.getMessage());
                }
                if (isLogging) Log.d("data", notificationHead.toString());
                sendNotification(notificationHead, sbn);
            } else {
                boolean isWhitelist = prefs.getBoolean("UseWhite", false);
                boolean isContain = getSharedPreferences(isWhitelist ? "Whitelist" : "Blacklist", MODE_PRIVATE).getBoolean(sbn.getPackageName(), false);
                if ((isWhitelist && isContain) || (!isWhitelist && !isContain)) {

                    String TITLE = extra.getString(Notification.EXTRA_TITLE);
                    String TEXT = extra.getString(Notification.EXTRA_TEXT);

                    if(prefs.getBoolean("UseBannedOption",false)) {
                        String word = prefs.getString("BannedWords","");
                        if(!word.equals("")) {
                            String[] words = word.split("/");
                            for (String s : words) {
                                if ((TEXT.contains(s) || TITLE.contains(s))) return;
                            }
                        }
                    }

                    if(prefs.getBoolean("UseInterval",false)) {
                        String Type = prefs.getString("IntervalType","Entire app");
                        int timeInterval = prefs.getInt("IntervalTime", 150);
                        if(Type.equals("Entire app")) {
                            if(isLogging) Log.d("IntervalCalculate","Package" + sbn.getPackageName() + "/CurrentTime:" + time.getTime() + "/LatestNotificationTime:" + intervalTimestamp + "/Calculated(ms):" + (time.getTime() - intervalTimestamp));
                            if (intervalTimestamp != 0 && time.getTime() - intervalTimestamp <= timeInterval) {
                                intervalTimestamp = time.getTime();
                                return;
                            } else intervalTimestamp = time.getTime();
                        } else if(Type.equals("Per app")){
                            int index = findIndex(sbn.getPackageName());
                            Query newQuery = new Query();
                            if(index != -1) {
                                Query query = intervalQuery.get(index);
                                if(time.getTime() - query.getTimestamp() <= timeInterval) {
                                    newQuery.setTimestamp(time.getTime());
                                    newQuery.setPackage(sbn.getPackageName());
                                    intervalQuery.set(index,newQuery);
                                    return;
                                }
                            } else {
                                newQuery.setPackage(sbn.getPackageName());
                                newQuery.setTimestamp(time.getTime());
                                intervalQuery.add(newQuery);
                            }
                        }
                    }

                    try {
                        JSONArray array = new JSONArray();
                        JSONObject object = new JSONObject();
                        String originString = prefs.getString("sendLogs", "");

                        if (!originString.equals("")) array = new JSONArray(originString);
                        object.put("date", DATE);
                        object.put("package", sbn.getPackageName());
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

                    Bitmap ICON = null;
                    try {
                        ICON = getBitmapFromDrawable(this.getPackageManager().getApplicationIcon(sbn.getPackageName()));
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
                        ICONS = res != 0 ? CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(getResizedBitmap(ICON, res, res))) : "none";
                    } else ICONS = "none";

                    String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
                    String DEVICE_ID = getMACAddress();
                    String TOPIC = "/topics/" + prefs.getString("UID", "");
                    String Package = "" + sbn.getPackageName();
                    String APPNAME = null;
                    try {
                        APPNAME = "" + NotiListenerService.this.getPackageManager().
                                getApplicationLabel(NotiListenerService.this.getPackageManager().getApplicationInfo(Package, PackageManager.GET_META_DATA));
                    } catch (PackageManager.NameNotFoundException e) {
                        if(isLogging) Log.d("Error", "Package not found : " + Package);
                    }

                    if(isLogging) Log.d("length", String.valueOf(ICONS.length()));
                    JSONObject notificationHead = new JSONObject();
                    JSONObject notifcationBody = new JSONObject();
                    try {
                        notifcationBody.put("type", "send|normal");
                        notifcationBody.put("title", TITLE != null ? TITLE : "New notification");
                        notifcationBody.put("message", TEXT != null ? TEXT : "notification arrived.");
                        notifcationBody.put("package", Package);
                        notifcationBody.put("appname", APPNAME);
                        notifcationBody.put("device_name", DEVICE_NAME);
                        notifcationBody.put("device_id", DEVICE_ID);
                        notifcationBody.put("date", DATE);
                        notifcationBody.put("icon", ICONS);

                        int dataLimit = prefs.getInt("DataLimit", 4096);
                        notificationHead.put("to", TOPIC);
                        notificationHead.put("data", notifcationBody.toString().length() < dataLimit ? notifcationBody : notifcationBody.put("icon", "none"));
                    } catch (JSONException e) {
                        if(isLogging) Log.e("Noti", "onCreate: " + e.getMessage());
                    }
                    if (isLogging) Log.d("data", notificationHead.toString());
                    sendNotification(notificationHead, sbn);
                }
            }
        }
        synchronized (sbn) {
            pastNotification = sbn;
        }
    }

    private int findIndex(String value) {
        for(int i = 0;i < intervalQuery.size();i++) {
            if(intervalQuery.get(i).getPackage().equals(value)) return i;
        }
        return -1;
    }

    private void sendNotification(JSONObject notification, StatusBarNotification sbn) {
        final String FCM_API = "https://fcm.googleapis.com/fcm/send";
        final String serverKey = "key=" + getString(R.string.serverKey);
        final String contentType = "application/json";
        final String TAG = "NOTIFICATION TAG";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(FCM_API, notification,
                response -> Log.i(TAG, "onResponse: " + response.toString() + " ,package: " + sbn.getPackageName()),
                error -> {
                    Toast.makeText(NotiListenerService.this, "Failed to send Notification! Please check internet and try again!", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "onErrorResponse: Didn't work" + " ,package: " + sbn.getPackageName());
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Authorization", serverKey);
                params.put("Content-Type", contentType);
                return params;
            }
        };
        MySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsonObjectRequest);
    }
}
