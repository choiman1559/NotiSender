package com.noti.main.receiver.plugin;

import android.content.Context;
import android.content.SharedPreferences;

public class PluginPrefs {
    private final SharedPreferences pluginPref;
    private final String PLUGIN_PACKAGE;

    private boolean isPluginEnabled = false;
    private boolean isRequireSensitiveAPI = false;
    private boolean isAllowSensitiveAPI = false;

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
            isPluginEnabled = parseFrom(data[0]);
            isRequireSensitiveAPI = parseFrom(data[1]);
            isAllowSensitiveAPI = parseFrom(data[2]);
        }
    }

    private boolean parseFrom(String str) {
        if(str == null || str.isEmpty()) {
            return false;
        } else return Boolean.parseBoolean(str);
    }

    public void apply() {
        String dataToSave = isPluginEnabled + "|" + isRequireSensitiveAPI + "|" + isAllowSensitiveAPI;
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

    public boolean isAllowSensitiveAPI() {
        syncFromPrefs();
        return isAllowSensitiveAPI;
    }

    public PluginPrefs setRequireSensitiveAPI(boolean requireSensitiveAPI) {
        isRequireSensitiveAPI = requireSensitiveAPI;
        return this;
    }

    public PluginPrefs setAllowSensitiveAPI(boolean allowSensitiveAPI) {
        isAllowSensitiveAPI = allowSensitiveAPI;
        return this;
    }

    public PluginPrefs setPluginEnabled(boolean pluginEnabled) {
        isPluginEnabled = pluginEnabled;
        return this;
    }
}
