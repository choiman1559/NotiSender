package com.noti.sender;

import android.app.Notification;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class NotiListenerClass extends NotificationListenerService {

    String toString(Boolean boo) {
        return boo ? "true" : "false";
    }

    void Log(String message,Boolean isSaveFile) {
        if (getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("debugInfo", false)) {
            Log.d("debug", message);
            if(isSaveFile) {
                File txt = new File(Environment.getExternalStorageDirectory(), "NotiSenderLog.txt");
                try {
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
                if (mac==null) return "";
                StringBuilder buf = new StringBuilder();
                for (byte b : mac) buf.append(String.format("%02X:", b));
                if (buf.length()>0) buf.deleteCharAt(buf.length()-1);
                return buf.toString();
            }
        } catch (Exception ignored) { }
        return "";
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        Notification notification = sbn.getNotification();
        Bundle extra = notification.extras;

        if (BuildConfig.DEBUG || getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("debugInfo", false)) {
            String str = "";
            str += "\n";
            str += "***onNotificationPosted debug info***\n";
            str += "date : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Calendar.getInstance().getTime()) + "\n";
            str += "uid : " + getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("UID", "") + "\n";
            str += "package : " + sbn.getPackageName() + "\n";
            str += "service type : " + getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("service", "") + "\n";
            str += "if uid is blank : " + toString(Objects.equals(getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("UID", ""), "")) + "\n";
            str += "if service Enabled : " + toString(getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("serviceToggle", false)) + "\n";
            str += "if include blacklist : " + toString(getSharedPreferences("Blacklist", MODE_PRIVATE).getBoolean(sbn.getPackageName(), false)) + "\n";
            str += "EXTRA_TITLE : " + extra.getString(Notification.EXTRA_TITLE) + "\n";
            str += "EXTRA_TEXT : " + extra.getString(Notification.EXTRA_TEXT) + "\n";
            str += "**************************************\n";
            str += "\n";

            Log(str,true);
        }

        if (NotiListenerClass.this.getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("service", "").equals("send") &&
                !getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("UID", "").equals("") &&
                getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getBoolean("serviceToggle", false) &&
                !getSharedPreferences("Blacklist", MODE_PRIVATE).getBoolean(sbn.getPackageName(), false) &&
                !sbn.getPackageName().equals(getPackageName())) {

            Bitmap ICON = Build.VERSION.SDK_INT > 22 ? getBitmapFromDrawable(sbn.getNotification().getSmallIcon().loadDrawable(NotiListenerClass.this)) : extra.getParcelable(Notification.EXTRA_SMALL_ICON);
            String ICONS;
            if(ICON != null) {
                ICON.setHasAlpha(true);
                ICONS = CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(getResizedBitmap(ICON,52,52)));
            } else ICONS = "none";

            String DEVICE_NAME = Build.MANUFACTURER  + " " + Build.MODEL;
            String DEVICE_ID = getMACAddress();
            String TOPIC = "/topics/" + getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("UID", "");
            String TITLE = extra.getString(Notification.EXTRA_TITLE);
            String TEXT = extra.getString(Notification.EXTRA_TEXT);
            String DATE =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(sbn.getPostTime());
            String Package = "" + sbn.getPackageName();
            String APPNAME = null;
            try {
                APPNAME = "" + NotiListenerClass.this.getPackageManager().
                        getApplicationLabel(NotiListenerClass.this.getPackageManager().getApplicationInfo(Package, PackageManager.GET_META_DATA));
            } catch (PackageManager.NameNotFoundException e) {
                Log.d("Error", "Package not found : " + Package);
            }

            Log.d("length", String.valueOf(ICONS.length()));
            JSONObject notificationHead = new JSONObject();
            JSONObject notifcationBody = new JSONObject();
            try {
                notifcationBody.put("type","send");
                notifcationBody.put("title", TITLE != null ? TITLE : "New notification");
                notifcationBody.put("message", TEXT != null ? TEXT : "notification arrived.");
                notifcationBody.put("package", Package);
                notifcationBody.put("appname", APPNAME);
                notifcationBody.put("device_name",DEVICE_NAME);
                notifcationBody.put("device_id",DEVICE_ID);
                notifcationBody.put("date",DATE);
                notifcationBody.put("icon",ICONS);
                if (Build.VERSION.SDK_INT > 25)
                    notifcationBody.put("cid", extra.getString(Notification.EXTRA_CHANNEL_ID));

                notificationHead.put("to", TOPIC);
                notificationHead.put("data",
                        notifcationBody.toString().length() < 4000 ? notifcationBody : notifcationBody.put("icon","none"));
            } catch (JSONException e) {
                Log.e("Noti", "onCreate: " + e.getMessage());
            }
            Log(notificationHead.toString(),false);
            sendNotification(notificationHead, sbn);
        }
    }

    private void sendNotification(JSONObject notification, StatusBarNotification sbn) {
        final String FCM_API = "https://fcm.googleapis.com/fcm/send";
        final String serverKey = "key=" + getString(R.string.serverKey);
        final String contentType = "application/json";
        final String TAG = "NOTIFICATION TAG";

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(FCM_API, notification,
                response -> Log.i(TAG, "onResponse: " + response.toString() + " ,package: " + sbn.getPackageName()),
                error -> {
                    Toast.makeText(NotiListenerClass.this, "Failed to send Notification! Please check internet and try again!", Toast.LENGTH_SHORT).show();
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
