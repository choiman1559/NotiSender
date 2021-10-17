package com.noti.main.ui.prefs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.noti.main.utils.PackageShowInfo;
import com.noti.main.R;
import com.noti.main.utils.ThreadProxy;

import java.util.List;

public class BlacklistActivity extends Activity {
    private ListView packageListView;
    private List<PackageShowInfo> packageShowInfo;
    PackageManager pm;
    private ProgressBar pg;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blacklist);
        pg = findViewById(R.id.pg);
        pm = getPackageManager();
        packageListView = findViewById(R.id.package_list);

        ThreadProxy.getInstance().execute(new Runnable() {
            private ShowPackageAdapter showPackageAdapter;

            @Override
            public void run() {
                packageShowInfo = PackageShowInfo.getPackageShowInfo(getApplicationContext());
                showPackageAdapter = new ShowPackageAdapter(BlacklistActivity.this);
                runOnUiThread(() -> {
                    packageListView.setAdapter(showPackageAdapter);
                    pg.setVisibility(View.GONE);
                });
            }
        });

        packageListView.setOnItemClickListener((parent, view, position, id) -> {
            SharedPreferences prefs = getSharedPreferences(isWhite() ? "Whitelist" : "Blacklist", MODE_PRIVATE);
            prefs.edit().putBoolean(packageShowInfo.get(position).packageName, !prefs.getBoolean(packageShowInfo.get(position).packageName, false)).apply();
            TextView textView = view.findViewById(R.id.app_name);
            textView.setTextColor(prefs.getBoolean(packageShowInfo.get(position).packageName, false) ? (isWhite() ? Color.BLUE : Color.RED) : Color.BLACK);
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }

    protected boolean isWhite() {
        return getSharedPreferences(getPackageName() + "_preferences",MODE_PRIVATE).getBoolean("UseWhite",false);
    }

    class ShowPackageAdapter extends BaseAdapter {
        Drawable defaultDrawable;

        ShowPackageAdapter(Context context) {
            defaultDrawable = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background);
        }

        @Override
        public int getCount() {
            return packageShowInfo.size();
        }

        @Override
        public Object getItem(int position) {
            return packageShowInfo.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final Holder holder;
            if (convertView == null) {
                convertView = View.inflate(BlacklistActivity.this, R.layout.item_select_package, null);
                holder = new Holder(convertView, position);
                convertView.setTag(holder);
            } else {
                holder = (Holder) convertView.getTag();
                holder.holderPosition = position;
            }
            final PackageShowInfo packageShowInfo = BlacklistActivity.this.packageShowInfo.get(position);
            if (packageShowInfo.appName == null) {
                holder.appName.setText(packageShowInfo.packageName);
            } else {
                holder.appName.setText(packageShowInfo.appName);
            }
            holder.appName.setTextColor(getSharedPreferences(isWhite() ? "Whitelist" : "Blacklist" ,MODE_PRIVATE).getBoolean(packageShowInfo.packageName, false) ? (isWhite() ? Color.BLUE : Color.RED) : Color.BLACK);
            holder.icon.setImageDrawable(defaultDrawable);
            final View alertIconView = convertView;
            ThreadProxy.getInstance().execute(() -> {
                Holder iconHolder = (Holder) alertIconView.getTag();
                if (iconHolder.holderPosition != position) {
                    return;
                }
                final Drawable drawable = packageShowInfo.applicationInfo.loadIcon(pm);
                runOnUiThread(() -> {
                    Holder iconHolder1 = (Holder) alertIconView.getTag();
                    if (iconHolder1.holderPosition != position) {
                        return;
                    }
                    holder.icon.setImageDrawable(drawable);
                });
            });
            return convertView;
        }

        private class Holder {
            TextView appName;
            ImageView icon;
            View baseView;
            int holderPosition;

            Holder(View view, int position) {
                baseView = view;
                appName = view.findViewById(R.id.app_name);
                icon = view.findViewById(R.id.select_icon);
                this.holderPosition = position;
            }
        }
    }

}

