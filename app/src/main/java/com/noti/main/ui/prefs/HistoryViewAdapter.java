package com.noti.main.ui.prefs;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.text.Html;
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
import com.noti.main.R;
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
        prefs = mContext.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
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
        String title = "Can't find Title";
        String content = "Can't find content";
        String device = "null";

        try {
            JSONObject obj = list.getJSONObject(position);
            PackageManager pm = mContext.getPackageManager();

            Package = obj.getString("package");
            date = obj.getString("date");
            device = mode == 1 ? obj.getString("device") : "";
            title = obj.getString("title");
            content = obj.getString("text");

            final String finalPackage = Package;
            final String finalDate = date;
            final String finalTitle = title;
            final String finalContent = content;
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

            holder.title.setSelected(true);
            holder.title.setText(title.equals("") ? "No Title" : title);
            holder.information.setSelected(true);
            holder.information.setText(String.format("%s (%s) %s", pm.getApplicationLabel(pm.getApplicationInfo(Package, PackageManager.GET_META_DATA)), Package, date));

            holder.setOnViewClickListener(() -> {
                try {
                    String message = "";
                    message += "<b>App Name : </b>" + pm.getApplicationLabel(pm.getApplicationInfo(finalPackage, PackageManager.GET_META_DATA)) + "<br>";
                    message += "<b>App Package : </b>" + finalPackage + "<br>";
                    message += "<b>Time : </b>" + finalDate + "<br>";
                    message += "<b>Title : </b>" + finalTitle + "<br>";
                    message += "<b>Content : </b>" + finalContent + "<br>";
                    if (mode == 1) message += "<b>From : </b>" + finalDevice + "<br>";

                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                    builder.setTitle("Notification Details");
                    builder.setMessage(Html.fromHtml(message));
                    builder.setPositiveButton("Close", (dialog, which) -> { });
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
            holder.setOnViewClickListener(() -> {
                String message = "";
                message += "<b>App Package : </b>" + finalPackage + "<br>";
                message += "<b>Time : </b>" + finalDate + "<br>";
                message += "<b>Title : </b>" + finalTitle + "<br>";
                message += "<b>Content : </b>" + finalContent + "<br>";
                if (mode == 1) message += "<b>From : </b>" + finalDevice + "<br>";
                message += "<b>Error Cause : </b>" + e.toString() + "<br>";

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
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
                message += "<b>Error Cause : </b>" + e.toString() + "<br>";

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                builder.setTitle("Error Details");
                builder.setMessage(Html.fromHtml(message));
                builder.setPositiveButton("Close", (dialog, which) -> { });
                builder.show();
            });
        } catch (Exception e) {
            holder.title.setText(String.format("Error : %s", Package));
            holder.information.setText(String.format("cause : %s", e.toString()));
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
                message += "<b>Error Cause : </b>" + e.toString() + "<br>";

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
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
}
