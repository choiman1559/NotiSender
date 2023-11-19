package com.noti.main.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.card.MaterialCardView;
import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.receiver.plugin.PluginConst;
import com.noti.main.receiver.plugin.PluginPrefs;

import java.util.List;

public class NetSelectActivity extends AppCompatActivity {

    static SharedPreferences prefs;
    static AppCompatRadioButton lastSelected;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_net_select);
        prefs = getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
        PackageManager packageManager = getPackageManager();

        LinearLayout AppListLayout = findViewById(R.id.appListLayout);
        ProviderApplication FcmProvider = new ProviderApplication(this, "Firebase Cloud Message", true);
        ProviderApplication PushyProvider = new ProviderApplication(this, "Pushy", true);

        FcmProvider.setShownName("Firebase Cloud Message");
        FcmProvider.setDrawable(AppCompatResources.getDrawable(this, R.drawable.googleg_standard_color_18));
        PushyProvider.setShownName("Pushy.me (Premium only)");
        PushyProvider.setDrawable(AppCompatResources.getDrawable(this, R.drawable.pushy_icon));

        CoordinatorLayout FcmLayout = (CoordinatorLayout) View.inflate(this, R.layout.cardview_net_provider, null);
        CoordinatorLayout PushyLayout = (CoordinatorLayout) View.inflate(this, R.layout.cardview_net_provider, null);

        RadioObject FcmHolder = new RadioObject(FcmProvider, FcmLayout);
        RadioObject PushyHolder = new RadioObject(PushyProvider,PushyLayout);

        AppListLayout.addView(FcmLayout);
        AppListLayout.addView(PushyLayout);

        Intent intent = new Intent(PluginConst.RECEIVER_ACTION_NAME);
        List<ResolveInfo> listApps;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listApps = packageManager.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(0));
        } else listApps = packageManager.queryBroadcastReceivers(intent, 0);

        boolean isRadioChecked = false;
        for (ResolveInfo info : listApps) {
            String packageName = info.activityInfo.packageName;
            PluginPrefs pluginPrefs = new PluginPrefs(this, packageName);
            if(pluginPrefs.isPluginEnabled() && pluginPrefs.hasNetworkProvider()) {
                CoordinatorLayout layout = (CoordinatorLayout) View.inflate(this, R.layout.cardview_net_provider, null);
                ProviderApplication providerApplication = new ProviderApplication(this, packageName, false);
                providerApplication.setDrawable();

                if(pluginPrefs.getNetworkProviderName().isEmpty()) {
                    providerApplication.setShownName();
                } else {
                    providerApplication.setShownName(pluginPrefs.getNetworkProviderName());
                }

                RadioObject holder = new RadioObject(providerApplication, layout);
                if(prefs.getString("server", "Firebase Cloud Message").equals(packageName)) {
                    holder.setRadioChecked(true);
                    isRadioChecked = true;
                }
                AppListLayout.addView(layout);
            }
        }

        if(!isRadioChecked) {
            switch (prefs.getString("server", "Firebase Cloud Message")) {
                case "Firebase Cloud Message" -> FcmHolder.setRadioChecked(true);
                case "Pushy" -> PushyHolder.setRadioChecked(true);
            }
        }
    }

    static class RadioObject {
        ProviderApplication application;
        MaterialCardView Parent;
        AppCompatRadioButton providerSelected;
        ImageView providerIcon;
        TextView providerName;

        public RadioObject(ProviderApplication application, View view) {
            this.application = application;
            Parent = view.findViewById(R.id.Parent);
            providerSelected = view.findViewById(R.id.providerSelected);
            providerIcon = view.findViewById(R.id.providerIcon);
            providerName = view.findViewById(R.id.providerName);

            if (application.appIcon != null) {
                providerIcon.setImageDrawable(application.appIcon);
            }

            if(application.shownName != null) {
                providerName.setText(application.shownName);
            }

            Parent.setOnClickListener(v -> {
                prefs.edit().putString("server", application.packageName).apply();
                setRadioChecked(true);
            });
        }

        public void setRadioChecked(boolean isChecked) {
            if(lastSelected != null) {
                if (lastSelected.equals(providerSelected) && lastSelected.isChecked() == isChecked) {
                    return;
                }

                if(isChecked) {
                    lastSelected.setChecked(false);
                }
            }

            this.providerSelected.setChecked(isChecked);
            if(isChecked) {
                lastSelected = providerSelected;
            }
        }
    }

    static class ProviderApplication {
        PackageManager packageManager;
        String shownName;
        String packageName;
        Drawable appIcon;
        boolean isBuiltIn;

        public ProviderApplication(Context context, String packageName, boolean isBuiltIn) {
            this.packageManager = context.getPackageManager();
            this.packageName = packageName;
            this.isBuiltIn = isBuiltIn;
        }

        public void setDrawable() {
            if (!isBuiltIn) {
                try {
                    this.appIcon = packageManager.getApplicationIcon(this.packageName);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        public void setDrawable(Drawable drawable) {
            this.appIcon = drawable;
        }

        public void setShownName(String shownName) {
            this.shownName = shownName;
        }

        public void setShownName() {
            try {
                this.shownName = (String) packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(lastSelected != null) {
            lastSelected.setChecked(true);
        }
    }
}
