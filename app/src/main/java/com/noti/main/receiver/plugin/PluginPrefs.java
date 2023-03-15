package com.noti.main.receiver.plugin;

import android.content.Context;
import android.content.SharedPreferences;

public class PluginPrefs {
    private final SharedPreferences pluginPref;
    private final String PLUGIN_PACKAGE;

    private boolean isPluginEnabled = false;
    private boolean isRequireSensitiveAPI = false;

    public PluginPrefs(Context context, String pluginPackage) {
        String PREF_NAME = "com.noti.main_plugin";
        pluginPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.PLUGIN_PACKAGE = pluginPackage;
        syncFromPrefs();
    }

    private void syncFromPrefs() {
        String rawData = pluginPref.getString(PLUGIN_PACKAGE, "");
        if(!rawData.isEmpty()) {
            String[] data = rawData.split("\\|");
            isPluginEnabled = Boolean.parseBoolean(data[0]);
            isRequireSensitiveAPI = Boolean.parseBoolean(data[1]);
        }
    }

    public void apply() {
        String dataToSave = isPluginEnabled + "|" + isRequireSensitiveAPI;
        pluginPref.edit().putString(PLUGIN_PACKAGE, dataToSave).apply();
    }

    public boolean isPluginEnabled() {
        syncFromPrefs();
        return isPluginEnabled;
    }

    public boolean isRequireSensitiveAPI() {
        syncFromPrefs();
        return isRequireSensitiveAPI;
    }

    public PluginPrefs setRequireSensitiveAPI(boolean requireSensitiveAPI) {
        isRequireSensitiveAPI = requireSensitiveAPI;
        return this;
    }

    public PluginPrefs setPluginEnabled(boolean pluginEnabled) {
        isPluginEnabled = pluginEnabled;
        return this;
    }
}
