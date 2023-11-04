package com.noti.main.ui.options;

import static android.content.Context.MODE_PRIVATE;

import static com.noti.main.service.NotiListenerService.getUniqueID;
import static com.noti.main.service.NotiListenerService.sendNotification;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kieronquinn.monetcompat.core.MonetCompat;
import com.noti.main.Application;
import com.noti.main.R;

import org.json.JSONException;
import org.json.JSONObject;

public class PairPreference extends PreferenceFragmentCompat  {

    Activity mContext;
    MonetCompat monet;
    SharedPreferences prefs;

    Preference customDeviceType;
    Preference deviceTypeChangeReboot;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        MonetCompat.setup(requireContext());
        monet = MonetCompat.getInstance();
        monet.updateMonetColors();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        monet = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) mContext = (Activity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.pair_preferences, rootKey);
        prefs = mContext.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);

        customDeviceType = findPreference("customDeviceType");
        deviceTypeChangeReboot = findPreference("deviceTypeChangeReboot");

        String deviceType = prefs.getString("customDeviceType", "Auto Detect");
        customDeviceType.setSummary("Now : " + deviceType + (deviceType.equals("Auto Detect") ? " (Default)" : ""));
        deviceTypeChangeReboot.setVisible(Application.needRebootToChangeType);

        customDeviceType.setOnPreferenceChangeListener((preference, newValue) -> {
            customDeviceType.setSummary("Now : " + newValue + (newValue.equals("Auto Detect") ? " (Default)" : ""));
            deviceTypeChangeReboot.setVisible(true);
            Application.needRebootToChangeType = true;
            return true;
        });
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference) {
        super.onPreferenceTreeClick(preference);

        if(preference.getKey().equals("pairKillSwitch")) {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Reset Confirmation");
            dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
            dialog.setMessage("Are you sure you want to unpair all the paired devices?\n\nThis task cannot be undone.");
            dialog.setNegativeButton("Cancel", (d, w) -> { });
            dialog.setPositiveButton("Reset", (d, w) -> {
                String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
                String DEVICE_ID = getUniqueID();
                String TOPIC = "/topics/" + mContext.getSharedPreferences(Application.PREFS_NAME,MODE_PRIVATE).getString("UID", "");

                JSONObject notificationHead = new JSONObject();
                JSONObject notificationBody = new JSONObject();
                try {
                    notificationBody.put("type", "pair|request_remove_all");
                    notificationBody.put("device_name", DEVICE_NAME);
                    notificationBody.put("device_id", DEVICE_ID);

                    notificationHead.put("to", TOPIC);
                    notificationHead.put("data", notificationBody);
                } catch (JSONException e) {
                    Log.e("Noti", "onCreate: " + e.getMessage());
                }

                sendNotification(notificationHead, mContext.getPackageName(), mContext);
                mContext.getSharedPreferences("com.noti.main_pair", MODE_PRIVATE).edit().remove("paired_list").apply();
                mContext.finish();
            });
            dialog.show();
        }

        return true;
    }
}
