package com.noti.main.ui.prefs.custom;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
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
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.R;
import com.noti.main.receiver.plugin.PluginActions;
import com.noti.main.receiver.plugin.PluginConst;
import com.noti.main.receiver.plugin.PluginPrefs;
import com.noti.main.receiver.plugin.PluginReceiver;
import com.noti.main.updater.tasks.Version;
import com.noti.main.utils.network.JsonRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class PluginFragment extends Fragment {
    AppCompatActivity mContext;
    ArrayList<PluginAppHolder> pluginAppHolderArrayList = new ArrayList<>();
    ArrayList<PluginMarketHolder> pluginMarketArrayList = new ArrayList<>();

    LinearLayoutCompat pluginSuggestLayout;
    SwitchMaterial taskerPluginEnabled;
    RelativeLayout taskerActionMenuLayout;
    TextView taskerDescriptionText;

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

        LinearLayoutCompat itemNotAvailableLayout = view.findViewById(R.id.itemNotAvailableLayout);
        LinearLayoutCompat pluginListLayout = view.findViewById(R.id.pluginListLayout);

        SharedPreferences prefs = mContext.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
        MaterialCardView taskerPluginParent = view.findViewById(R.id.taskerPluginParent);
        Button taskerPluginInfo = view.findViewById(R.id.taskerPluginInfo);

        pluginSuggestLayout = view.findViewById(R.id.pluginSuggestLayout);
        taskerPluginEnabled = view.findViewById(R.id.taskerPluginEnabled);
        taskerActionMenuLayout = view.findViewById(R.id.taskerActionMenuLayout);
        taskerDescriptionText = view.findViewById(R.id.taskerDescriptionText);

        ArrayList<String> taskerPluginList = new ArrayList<>();
        taskerPluginList.add("net.dinglisch.android.taskerm");
        taskerPluginList.add("com.llamalab.automate");
        taskerPluginList.add("com.twofortyfouram.locale.x");
        taskerPluginList.add("com.arlosoft.macrodroid");
        int packageCount = 0;

        for (String packageName : taskerPluginList) {
            try {
                mContext.getPackageManager().getPackageInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                packageCount++;
            }
        }

        if (packageCount == taskerPluginList.size()) {
            taskerPluginEnabled.setEnabled(false);
            taskerPluginEnabled.setChecked(false);
            taskerDescriptionText.setText("This option requires the Tasker-compatible app.");
            taskerDescriptionText.setTextColor(Color.RED);
        } else {
            taskerPluginEnabled.setEnabled(true);
            taskerPluginEnabled.setChecked(prefs.getBoolean("UseTaskerExtension", false));
        }

        taskerPluginEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean("UseTaskerExtension", isChecked).apply());
        taskerActionMenuLayout.setVisibility(View.GONE);
        taskerPluginParent.setOnClickListener(v -> {
            boolean isDetailGone = taskerActionMenuLayout.getVisibility() == View.GONE;

            if (isDetailGone) {
                for (PluginAppHolder holder1 : pluginAppHolderArrayList) {
                    setDetailVisibility(holder1, false);
                }
            }

            taskerActionMenuLayout.setVisibility(isDetailGone ? View.VISIBLE : View.GONE);
            taskerDescriptionText.setSingleLine(!isDetailGone);
        });

        taskerPluginInfo.setOnClickListener(v -> {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Tasker compatible apps");
            dialog.setMessage(getString(R.string.Dialog_Tasker_compatible));
            dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
            dialog.setPositiveButton("Close", (d, w) -> {
            });
            dialog.show();
        });

        loadPluginList(packageManager);
        PluginReceiver.receivePluginInformation = data -> {
            itemNotAvailableLayout.setVisibility(View.GONE);
            CoordinatorLayout layout = (CoordinatorLayout) View.inflate(mContext, R.layout.cardview_plugin_item, null);
            PluginAppHolder holder = new PluginAppHolder(layout);
            String packageName = data.getString(PluginConst.PLUGIN_PACKAGE_NAME);
            PluginPrefs pluginPrefs = new PluginPrefs(mContext, packageName);
            pluginPrefs.setRequireSensitiveAPI(data.getBoolean(PluginConst.PLUGIN_REQUIRE_SENSITIVE_API, false)).apply();

            if(data.containsKey(PluginConst.NET_PROVIDER_METADATA)) {
                String[] providerMetadata = Objects.requireNonNull(data.getString(PluginConst.NET_PROVIDER_METADATA)).split("\\|");
                pluginPrefs.setHasNetworkProvider(Boolean.parseBoolean(providerMetadata[0]));
                if (providerMetadata.length > 1) {
                    pluginPrefs.setNetworkProviderName(providerMetadata[1]);
                }
                pluginPrefs.apply();
            }

            try {
                String title = data.getString(PluginConst.PLUGIN_TITLE);
                Version requireVersion = new Version(data.getString(PluginConst.PLUGIN_REQUIRE_VERSION));
                Version currentVersion = new Version(BuildConfig.VERSION_NAME);

                PackageInfo packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
                holder.pluginTitle.setText(title.isEmpty() ? packageInfo.applicationInfo.loadLabel(packageManager) : title);
                holder.pluginIcon.setImageDrawable(packageInfo.applicationInfo.loadIcon(mContext.getPackageManager()));

                if (currentVersion.compareTo(requireVersion) >= 0) {
                    if (data.getBoolean(PluginConst.PLUGIN_READY)) {
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

                holder.pluginEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked && !pluginPrefs.isPluginEnabled()) {
                        if(pluginPrefs.isRequireSensitiveAPI()) {
                            pluginPrefs.setPluginEnabled(false).apply();
                            holder.pluginEnabled.setChecked(false);

                            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                            builder.setTitle("Sensitive API Warning");
                            builder.setMessage(getString(R.string.Sensitive_API_Plugin_waring));
                            builder.setPositiveButton("Enable", (dialog, which) -> {
                                pluginPrefs
                                        .setPluginEnabled(true)
                                        .setAllowSensitiveAPI(true)
                                        .apply();
                                holder.pluginEnabled.setChecked(true);
                            });
                            builder.setNegativeButton("Cancel", (dialog, which) -> {});
                            builder.show();
                        } else {
                            pluginPrefs
                                    .setPluginEnabled(true)
                                    .setAllowSensitiveAPI(false)
                                    .apply();
                        }
                    } else {
                        pluginPrefs.setPluginEnabled(isChecked).apply();
                    }
                });

                holder.pluginActionMenuLayout.setVisibility(View.GONE);
                holder.Parent.setOnClickListener((v) -> {
                    boolean isVisible = holder.pluginActionMenuLayout.getVisibility() == View.VISIBLE;
                    if (!isVisible) {
                        taskerActionMenuLayout.setVisibility(View.GONE);
                        taskerDescriptionText.setSingleLine(true);

                        for (PluginAppHolder holder1 : pluginAppHolderArrayList) {
                            setDetailVisibility(holder1, false);
                        }

                        for (PluginMarketHolder holder1 : pluginMarketArrayList) {
                            setDetailVisibility(holder1, false);
                        }
                    }

                    setDetailVisibility(holder, !isVisible);
                });
                holder.settingButton.setOnClickListener((v) -> startActivity(new Intent().setComponent(new ComponentName(packageName, data.getString(PluginConst.PLUGIN_SETTING_ACTIVITY)))));
                holder.infoButton.setOnClickListener((v) -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + packageName))));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            pluginAppHolderArrayList.add(holder);
            pluginListLayout.addView(layout);
        };

        String API_URL = "https://api.github.com/repos/choiman1559/NotiSender-PluginMarket/git/trees/master";
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(API_URL, response -> {
            try {
                JSONArray jsonArray = response.getJSONArray("tree");
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    if ("tree".equals(jsonObject.getString("type"))) {
                        String manifestUrl = String.format("https://raw.githubusercontent.com/choiman1559/NotiSender-PluginMarket/master/%s/MANIFEST", jsonObject.getString("path"));
                        StringRequest stringRequest = new StringRequest(Request.Method.GET, manifestUrl, response1 -> {
                            try {
                                TrimProperties properties = new TrimProperties();
                                properties.load(new StringReader(response1));
                                initPluginMarketItem(properties);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }, Throwable::printStackTrace);
                        JsonRequest.getInstance(mContext).addToRequestQueue(stringRequest, 1);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, Throwable::printStackTrace);
        JsonRequest.getInstance(mContext).addToRequestQueue(jsonObjectRequest, 1);
    }

    @SuppressLint("DiscouragedApi")
    void initPluginMarketItem(TrimProperties properties) {
        if (isAppInstalled(properties.getProperty("packageName"))) return;
        if ("false".equals(properties.getProperty("visible"))) return;

        CoordinatorLayout layout = (CoordinatorLayout) View.inflate(mContext, R.layout.cardview_plugin_market, null);
        PluginMarketHolder holder = new PluginMarketHolder(layout);

        holder.pluginTitle.setText(properties.getProperty("pluginName"));
        holder.pluginDescription.setText(properties.getProperty("description"));

        String useFluentIcon = properties.getProperty("useFluentIcon");
        if ("true".equals(useFluentIcon)) {
            holder.pluginIcon.setImageDrawable(AppCompatResources.getDrawable(mContext, mContext.getResources().getIdentifier(String.format("ic_fluent_%s_24_regular", properties.getProperty("iconName")), "drawable", mContext.getPackageName())));
        } else if ("false".equals(useFluentIcon)) {
            Glide.with(mContext).load(properties.getProperty("iconUrl")).into(holder.pluginIcon);
        }

        holder.downloadButton.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(properties.getProperty("downloadLink")))));
        holder.infoButton.setOnClickListener(v -> {
            String message = "";
            message += String.format("<b>Author</b>: %s<br>", properties.getProperty("author"));
            message += String.format("<b>Contact</b>: %s<br>", properties.getProperty("contact"));
            message += String.format("<b>License</b>: %s<br>", properties.getProperty("license"));
            message += String.format("<b>Version</b>: %s<br>", properties.getProperty("latestVersion"));
            message += String.format("<b>Tag</b>: %s<br>", properties.getProperty("tag"));

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
            builder.setTitle("Plugin Details");
            builder.setMessage(Html.fromHtml(message));
            builder.setPositiveButton("Close", (dialog, which) -> {
            });
            builder.show();
        });

        holder.pluginActionMenuLayout.setVisibility(View.GONE);
        holder.Parent.setOnClickListener((v) -> {
            boolean isVisible = holder.pluginActionMenuLayout.getVisibility() == View.VISIBLE;
            if (!isVisible) {
                taskerActionMenuLayout.setVisibility(View.GONE);
                taskerDescriptionText.setSingleLine(true);

                for (PluginAppHolder holder1 : pluginAppHolderArrayList) {
                    setDetailVisibility(holder1, false);
                }

                for (PluginMarketHolder holder1 : pluginMarketArrayList) {
                    setDetailVisibility(holder1, false);
                }
            }

            setDetailVisibility(holder, !isVisible);
        });

        pluginSuggestLayout.addView(layout);
        pluginMarketArrayList.add(holder);
    }

    void setDetailVisibility(PluginAppHolder holder, boolean isVisible) {
        holder.pluginActionMenuLayout.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        holder.pluginTitle.setSingleLine(!isVisible);
        holder.pluginDescription.setSingleLine(!isVisible);
    }

    void setDetailVisibility(PluginMarketHolder holder, boolean isVisible) {
        holder.pluginActionMenuLayout.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        holder.pluginTitle.setSingleLine(!isVisible);
        holder.pluginDescription.setSingleLine(!isVisible);
    }

    void loadPluginList(PackageManager packageManager) {
        Intent intent = new Intent(PluginConst.RECEIVER_ACTION_NAME);
        List<ResolveInfo> listApps;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listApps = packageManager.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(0));
        } else listApps = packageManager.queryBroadcastReceivers(intent, 0);

        for (ResolveInfo info : listApps) {
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

    static class PluginMarketHolder {
        MaterialCardView Parent;
        RelativeLayout pluginActionMenuLayout;
        ImageView pluginIcon;
        TextView pluginTitle;
        TextView pluginDescription;
        SwitchMaterial pluginEnabled;
        Button downloadButton;
        Button infoButton;

        PluginMarketHolder(View view) {
            Parent = view.findViewById(R.id.Parent);
            pluginIcon = view.findViewById(R.id.pluginIcon);
            pluginTitle = view.findViewById(R.id.pluginTitle);
            pluginDescription = view.findViewById(R.id.pluginDescription);
            pluginEnabled = view.findViewById(R.id.pluginEnabled);
            downloadButton = view.findViewById(R.id.downloadButton);
            infoButton = view.findViewById(R.id.infoButton);
            pluginActionMenuLayout = view.findViewById(R.id.pluginActionMenuLayout);
            pluginActionMenuLayout.setVisibility(View.GONE);
        }
    }

    public static class TrimProperties extends Properties {
        @Override
        public String getProperty(String key) {
            String value = super.getProperty(key);
            return value == null ? "" : value.trim();
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
