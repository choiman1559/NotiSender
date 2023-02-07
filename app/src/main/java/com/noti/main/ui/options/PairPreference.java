package com.noti.main.ui.options;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.kieronquinn.monetcompat.core.MonetCompat;
import com.noti.main.Application;
import com.noti.main.R;

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
}
