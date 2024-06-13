package com.noti.main.receiver.plugin;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.noti.plugin.data.PairRemoteAction;

import java.util.ArrayList;
import java.util.Map;

public class PluginPrefs {
    private final SharedPreferences pluginPref;
    private final String PLUGIN_PACKAGE;

    private boolean isPluginEnabled = false;
    private boolean isRequireSensitiveAPI = false;
    private boolean isAllowSensitiveAPI = false;
    private boolean hasNetworkProvider = false;
    private String networkProviderName;
    private PairRemoteAction[] pairRemoteActions;

    public PluginPrefs(Context context, String pluginPackage) {
        String PREF_NAME = "com.noti.main_plugin";
        pluginPref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.PLUGIN_PACKAGE = pluginPackage;
        syncFromPrefs();
    }

    public static ArrayList<String> getPluginList(Context context) {
        String PREF_NAME = "com.noti.main_plugin";
        Map<String,?> keys = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getAll();
        ArrayList<String> list = new ArrayList<>();

        for(Map.Entry<String,?> entry : keys.entrySet()){
            list.add(entry.getKey());
        }

        return list;
    }

    private void syncFromPrefs() {
        String rawData = pluginPref.getString(PLUGIN_PACKAGE, "");
        if(!rawData.isEmpty()) {
            String[] data = rawData.split("\\|");
            isPluginEnabled = parseFrom(data[0]);
            isRequireSensitiveAPI = parseFrom(data[1]);

            if(data.length > 2) {
                isAllowSensitiveAPI = parseFrom(data[2]);
            }

            if(data.length > 3) {
                hasNetworkProvider = parseFrom(data[3]);
            }

            if(data.length > 4) {
                networkProviderName = data[4];
            }

            if(data.length > 5) {
                if(!data[5].isEmpty() && !data[5].equals("null")) {
                    String[] remoteActionsParcel = new Gson().fromJson(data[5], String[].class);
                    pairRemoteActions = new PairRemoteAction[remoteActionsParcel.length];

                    for(int i = 0;i < remoteActionsParcel.length; i++) {
                        pairRemoteActions[i] = new Gson().fromJson(remoteActionsParcel[i], PairRemoteAction.class);
                    }
                }
            }
        }
    }

    private boolean parseFrom(String str) {
        if(str == null || str.isEmpty()) {
            return false;
        } else return Boolean.parseBoolean(str);
    }

    public void apply() {
        String pairRemoteActionData = "null";
        if(pairRemoteActions != null && pairRemoteActions.length > 0) {
            String[] remoteActionsParcel = new String[pairRemoteActions.length];
            for(int i = 0;i < pairRemoteActions.length; i++) {
                remoteActionsParcel[i] = new Gson().toJson(pairRemoteActions[i]);
            }

            pairRemoteActionData = new Gson().toJson(remoteActionsParcel);
        }

        String dataToSave = isPluginEnabled
                + "|" + isRequireSensitiveAPI
                + "|" + isAllowSensitiveAPI
                + "|" + hasNetworkProvider
                + "|" + networkProviderName
                + "|" + pairRemoteActionData;

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

    public boolean hasNetworkProvider() {
        syncFromPrefs();
        return hasNetworkProvider;
    }

    public String getNetworkProviderName() {
        syncFromPrefs();
        return networkProviderName;
    }

    public PairRemoteAction[] getPairRemoteActions() {
        syncFromPrefs();
        return pairRemoteActions;
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

    public PluginPrefs setHasNetworkProvider(boolean hasNetworkProvider) {
        this.hasNetworkProvider = hasNetworkProvider;
        return this;
    }

    public PluginPrefs setNetworkProviderName(String networkProviderName) {
        this.networkProviderName = networkProviderName;
        return this;
    }

    public PluginPrefs setPairRemoteActions(PairRemoteAction[] pairRemoteActions) {
        this.pairRemoteActions = pairRemoteActions;
        return this;
    }
}
