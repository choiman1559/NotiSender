package com.noti.main.ui.prefs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.R;
import com.noti.main.service.NotiListenerService;
import com.noti.main.utils.ui.ToastHelper;
import com.noti.main.utils.network.CompressStringUtil;
import com.noti.main.utils.ThreadProxy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HistoryViewAdapter extends RecyclerView.Adapter<HistoryViewAdapter.HistoryViewHolder> {
    JSONArray list;
    Drawable defaultDrawable;
    Activity mContext;
    SharedPreferences prefs;
    int mode;

    HistoryViewAdapter(JSONArray list, int mode, Activity mContext) {
        this.list = list;
        this.mode = mode;
        this.mContext = mContext;
        this.defaultDrawable = ContextCompat.getDrawable(mContext, R.drawable.ic_launcher_background);
        prefs = mContext.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.cardview_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        String Package = "null";
        String date = "1900.01.01 00:00:00";
        String title = "Error: Can't find Title";
        String content = "Can't find content";
        String device = "null";
        String information = "Can't find package : null";

        try {
            JSONObject obj = list.getJSONObject(position);
            PackageManager pm = mContext.getPackageManager();

            date = obj.getString("date");
            Package = obj.getString("package");
            device = mode == 1 ? obj.getString("device") : "";

            final String finalPackage = Package;
            final String finalDate = date;
            final String finalDevice = device;

            ThreadProxy.getInstance().execute(() -> {
                try {
                    Drawable icon = pm.getApplicationIcon(finalPackage);
                    mContext.runOnUiThread(() -> holder.icon.setImageDrawable(icon));
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                    holder.icon.setImageDrawable(defaultDrawable);
                }
            });

            information = String.format("%s (%s) %s", pm.getApplicationLabel(pm.getApplicationInfo(Package, PackageManager.GET_META_DATA)), Package, date);

            title = obj.getString("title");
            content = obj.getString("text");

            final String finalTitle = title;
            final String finalContent = content;

            holder.title.setSelected(true);
            holder.title.setText(title.equals("") ? "No Title" : title);
            holder.information.setSelected(true);
            holder.information.setText(information);

            holder.setOnViewClickListener(() -> {
                try {
                    String message = "";
                    message += "<b>App Name : </b>" + pm.getApplicationLabel(pm.getApplicationInfo(finalPackage, PackageManager.GET_META_DATA)) + "<br>";
                    message += "<b>App Package : </b>" + finalPackage + "<br>";
                    message += "<b>Time : </b>" + finalDate + "<br>";
                    message += "<b>Title : </b>" + finalTitle + "<br>";
                    message += "<b>Content : </b>" + finalContent + "<br>";
                    if (mode == 1) message += "<b>From : </b>" + finalDevice + "<br>";

                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                    builder.setTitle("Notification Details");
                    builder.setMessage(Html.fromHtml(message));
                    builder.setPositiveButton("Close", (dialog, which) -> { });
                    builder.setNeutralButton("Send Again",  (dialog, which) -> {
                        if(!prefs.getString("UID", "").equals("") && prefs.getBoolean("serviceToggle", false)) {
                            sendNormalNotificationAgain(finalPackage, finalDate, finalTitle, finalContent);
                        } else {
                            ToastHelper.show(mContext, "Please check service type and toggle and try again!","DISMISS", ToastHelper.LENGTH_SHORT);
                        }
                    });
                    builder.show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (JSONException e) {
            final String finalPackage = Package;
            final String finalDate = date;
            final String finalTitle = title;
            final String finalContent = content;
            final String finalDevice = device;

            holder.title.setText(title);
            holder.information.setText(information);
            holder.setOnViewClickListener(() -> {
                String message = "";
                message += "<b>App Package : </b>" + finalPackage + "<br>";
                message += "<b>Time : </b>" + finalDate + "<br>";
                message += "<b>Title : </b>" + finalTitle + "<br>";
                message += "<b>Content : </b>" + finalContent + "<br>";
                if (mode == 1) message += "<b>From : </b>" + finalDevice + "<br>";
                message += "<b>Error Cause : </b>" + e + "<br>";

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                builder.setTitle("Error Details");
                builder.setMessage(Html.fromHtml(message));
                builder.setPositiveButton("Close", (dialog, which) -> { });
                builder.show();
            });
        } catch (PackageManager.NameNotFoundException e) {
            final String finalPackage = Package;
            final String finalDate = date;
            final String finalTitle = title;
            final String finalContent = content;
            final String finalDevice = device;

            holder.title.setText(finalTitle);
            holder.information.setText(String.format("Can't find package : %s", finalPackage));
            holder.icon.setImageDrawable(defaultDrawable);

            holder.setOnViewClickListener(() -> {
                String message = "";
                message += "<b>App Package : </b>" + finalPackage + "<br>";
                message += "<b>Time : </b>" + finalDate + "<br>";
                message += "<b>Title : </b>" + finalTitle + "<br>";
                message += "<b>Content : </b>" + finalContent + "<br>";
                if (mode == 1) message += "<b>From : </b>" + finalDevice + "<br>";
                message += "<b>Error Cause : </b>" + e + "<br>";

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                builder.setTitle("Error Details");
                builder.setMessage(Html.fromHtml(message));
                builder.setPositiveButton("Close", (dialog, which) -> { });
                builder.show();
            });
        } catch (Exception e) {
            holder.title.setText(String.format("Error : %s", Package));
            holder.information.setText(String.format("cause : %s", e));
            holder.icon.setImageDrawable(defaultDrawable);

            final String finalPackage = Package;
            final String finalDate = date;
            final String finalTitle = title;
            final String finalContent = content;
            final String finalDevice = device;

            holder.setOnViewClickListener(() -> {
                String message = "";
                message += "<b>App Package : </b>" + finalPackage + "<br>";
                message += "<b>Time : </b>" + finalDate + "<br>";
                message += "<b>Title : </b>" + finalTitle + "<br>";
                message += "<b>Content : </b>" + finalContent + "<br>";
                if (mode == 1) message += "<b>From : </b>" + finalDevice + "<br>";
                message += "<b>Error Cause : </b>" + e + "<br>";

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                builder.setTitle("Error Details");
                builder.setMessage(Html.fromHtml(message));
                builder.setPositiveButton("Close", (dialog, which) -> { });
                builder.show();
            });
        }
    }

    @Override
    public int getItemCount() {
        return list.length();
    }

    public static class HistoryViewHolder extends RecyclerView.ViewHolder {
        protected ImageView icon;
        protected TextView title;
        protected TextView information;
        protected View parent;
        OnViewClickListener mListener;

        public HistoryViewHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.icon);
            title = view.findViewById(R.id.title);
            parent = view.findViewById(R.id.layout);
            information = view.findViewById(R.id.information);
            view.setOnClickListener(v -> {
                if(mListener!= null) mListener.onViewClick();
            });
        }

        public interface OnViewClickListener {
            void onViewClick();
        }

        public void setOnViewClickListener(OnViewClickListener listener) {
            if(listener != null) this.mListener = listener;
        }
    }

    void sendNormalNotificationAgain(String PackageName, String DATE, String TITLE, String TEXT) {
        boolean isLogging = BuildConfig.DEBUG;
        PackageManager pm = mContext.getPackageManager();
        Bitmap ICON = null;
        try {
            ICON = NotiListenerService.getBitmapFromDrawable(pm.getApplicationIcon(PackageName));
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
            ICONS = res == 0 ? "none" : CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(NotiListenerService.getResizedBitmap(ICON, res, res)));
        } else ICONS = "none";

        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = NotiListenerService.getUniqueID();
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
            notificationBody.put("title", TITLE != null ? TITLE : prefs.getString("DefaultTitle", "New notification"));
            notificationBody.put("message", TEXT != null ? TEXT : prefs.getString("DefaultMessage", "notification arrived."));
            notificationBody.put("package", PackageName);
            notificationBody.put("appname", APPNAME);
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("date", DATE);
            notificationBody.put("icon", ICONS);

            int dataLimit = prefs.getInt("DataLimit", 4096);
            if(notificationBody.toString().length() >= dataLimit - 20) {
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
        NotiListenerService.sendNotification(notificationHead, PackageName, mContext);
        ToastHelper.show(mContext, "Your request is posted!","OK", ToastHelper.LENGTH_SHORT);
    }
}
