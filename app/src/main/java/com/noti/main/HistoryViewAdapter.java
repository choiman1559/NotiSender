package com.noti.main;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HistoryViewAdapter extends RecyclerView.Adapter<HistoryViewAdapter.HistoryViewHolder> {
    JSONArray list;
    Drawable defaultDrawable;
    Activity mContext;
    int mode;

    HistoryViewAdapter(JSONArray list, int mode, Activity mContext) {
        this.list = list;
        this.mode = mode;
        this.mContext = mContext;
        this.defaultDrawable = ContextCompat.getDrawable(mContext, R.drawable.ic_launcher_background);
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

            holder.title.setSelected(true);
            holder.title.setText(title.equals("") ? "No Title" : title);
            holder.information.setSelected(true);
            holder.information.setText(String.format("%s (%s) %s", pm.getApplicationLabel(pm.getApplicationInfo(Package, PackageManager.GET_META_DATA)), Package, date));

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

            holder.setOnViewClickListener(() -> {
                try {
                    String message = "";
                    message += "App Name : " + pm.getApplicationLabel(pm.getApplicationInfo(finalPackage, PackageManager.GET_META_DATA)) + "\n";
                    message += "App Package : " + finalPackage + "\n";
                    message += "Time : " + finalDate + "\n";
                    message += "Title : " + finalTitle + "\n";
                    message += "Content : " + finalContent + "\n";
                    if (mode == 1) message += "From : " + finalDevice + "\n";

                    AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                    builder.setTitle("Notification Details");
                    builder.setMessage(message);
                    builder.setPositiveButton("Close", (dialog, which) -> { });
                    builder.show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        } catch (JSONException | PackageManager.NameNotFoundException e) {
            e.printStackTrace();
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
                message += "App Package : " + finalPackage + "\n";
                message += "Time : " + finalDate + "\n";
                message += "Title : " + finalTitle + "\n";
                message += "Content : " + finalContent + "\n";
                if (mode == 1) message += "From : " + finalDevice + "\n";
                message += "Error Cause : " + e.toString() + "\n";

                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setTitle("Error Details");
                builder.setMessage(message);
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
        OnViewClickListener mListener;

        public HistoryViewHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.icon);
            title = view.findViewById(R.id.title);
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
