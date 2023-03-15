package com.noti.main.ui.prefs.custom;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.noti.main.BuildConfig;
import com.noti.main.R;
import com.noti.main.receiver.plugin.PluginActions;
import com.noti.main.receiver.plugin.PluginConst;
import com.noti.main.receiver.plugin.PluginPrefs;
import com.noti.main.receiver.plugin.PluginReceiver;
import com.noti.main.updater.tasks.Version;

import java.util.List;

public class PluginFragment extends Fragment {
    AppCompatActivity mContext;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) mContext = (AppCompatActivity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list_plugin, container, false);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PackageManager packageManager = mContext.getPackageManager();
        mContext.findViewById(R.id.progress).setVisibility(View.GONE);

        LinearLayoutCompat pluginListLayout = view.findViewById(R.id.pluginListLayout);
        LinearLayoutCompat pluginSuggestLayout = view.findViewById(R.id.pluginSuggestLayout);
        MaterialCardView TelephonyPluginSuggest = view.findViewById(R.id.TelephonyPluginSuggest);
        MaterialCardView LibraryTestPluginSuggest = view.findViewById(R.id.LibraryTestPluginSuggest);

        boolean isTelephonyPluginInstalled = isAppInstalled("com.noti.plugin.telephony");
        boolean isLibraryTestPluginInstalled = isAppInstalled("com.noti.plugin.showcase");

        if(isTelephonyPluginInstalled && isLibraryTestPluginInstalled) {
            pluginSuggestLayout.setVisibility(View.GONE);
        } else if(isTelephonyPluginInstalled) {
            TelephonyPluginSuggest.setVisibility(View.GONE);
        } else if(isLibraryTestPluginInstalled) {
            LibraryTestPluginSuggest.setVisibility(View.GONE);
        }

        TelephonyPluginSuggest.setOnClickListener((v) -> mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender-TelephonyPlugin"))));
        LibraryTestPluginSuggest.setOnClickListener((v) -> mContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender-PluginLibrary"))));

        loadPluginList(packageManager);
        PluginReceiver.receivePluginInformation = data -> {
            CoordinatorLayout layout = (CoordinatorLayout) View.inflate(mContext, R.layout.cardview_plugin_item, null);
            PluginAppHolder holder = new PluginAppHolder(layout);
            String packageName = data.getString(PluginConst.PLUGIN_PACKAGE_NAME);
            PluginPrefs pluginPrefs = new PluginPrefs(mContext, packageName);
            pluginPrefs.setRequireSensitiveAPI(data.getBoolean(PluginConst.PLUGIN_REQUIRE_SENSITIVE_API, false)).apply();

            try {
                String title = data.getString(PluginConst.PLUGIN_TITLE);
                Version requireVersion = new Version(data.getString(PluginConst.PLUGIN_REQUIRE_VERSION));
                Version currentVersion = new Version(BuildConfig.VERSION_NAME);

                PackageInfo packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
                holder.pluginTitle.setText(title.isEmpty() ? packageInfo.applicationInfo.loadLabel(packageManager) : title);
                holder.pluginIcon.setImageDrawable(packageInfo.applicationInfo.loadIcon(mContext.getPackageManager()));

                if(currentVersion.compareTo(requireVersion) >= 0) {
                    if(data.getBoolean(PluginConst.PLUGIN_READY)) {
                        String description = data.getString(PluginConst.PLUGIN_DESCRIPTION);
                        holder.pluginDescription.setText(description.isEmpty() ? "Description is not available." : description);
                        holder.pluginEnabled.setEnabled(true);
                        holder.pluginEnabled.setChecked(pluginPrefs.isPluginEnabled());
                    } else {
                        holder.pluginDescription.setText("Plugin not ready. Please open setting!");
                        holder.pluginDescription.setTextColor(Color.RED);
                    }
                } else {
                    holder.pluginDescription.setText("Not compatible with this version of NotiSender");
                    holder.pluginDescription.setTextColor(Color.RED);
                }

                holder.pluginEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> pluginPrefs.setPluginEnabled(isChecked).apply());
                holder.pluginActionMenuLayout.setVisibility(View.GONE);
                holder.Parent.setOnClickListener((v) -> {
                    boolean isDetailGone = holder.pluginActionMenuLayout.getVisibility() == View.GONE;
                    holder.pluginActionMenuLayout.setVisibility(isDetailGone ? View.VISIBLE : View.GONE);
                    holder.pluginTitle.setSingleLine(!isDetailGone);
                    holder.pluginDescription.setSingleLine(!isDetailGone);
                });
                holder.settingButton.setOnClickListener((v) -> startActivity(new Intent().setComponent(new ComponentName(packageName, data.getString(PluginConst.PLUGIN_SETTING_ACTIVITY)))));
                holder.infoButton.setOnClickListener((v) -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + packageName))));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            pluginListLayout.addView(layout);
        };
    }

    void loadPluginList(PackageManager packageManager) {
        Intent intent = new Intent(PluginConst.RECEIVER_ACTION_NAME);
        List<ResolveInfo> listApps;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listApps = packageManager.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(0));
        } else listApps = packageManager.queryBroadcastReceivers(intent, 0);

        for(ResolveInfo info : listApps) {
            PluginActions.requestInformation(mContext, info.activityInfo.packageName);
        }
    }

    static class PluginAppHolder {
        MaterialCardView Parent;
        RelativeLayout pluginActionMenuLayout;
        ImageView pluginIcon;
        TextView pluginTitle;
        TextView pluginDescription;
        SwitchMaterial pluginEnabled;
        Button settingButton;
        Button infoButton;

        PluginAppHolder(View view) {
            Parent = view.findViewById(R.id.Parent);
            pluginIcon = view.findViewById(R.id.pluginIcon);
            pluginTitle = view.findViewById(R.id.pluginTitle);
            pluginDescription = view.findViewById(R.id.pluginDescription);
            pluginEnabled = view.findViewById(R.id.pluginEnabled);
            settingButton = view.findViewById(R.id.settingButton);
            infoButton = view.findViewById(R.id.infoButton);
            pluginActionMenuLayout = view.findViewById(R.id.pluginActionMenuLayout);
            pluginActionMenuLayout.setVisibility(View.GONE);
        }
    }

    boolean isAppInstalled(String packageName) {
        PackageManager packageManager = mContext.getPackageManager();
        try {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
