package com.noti.main.ui.options;

import static android.content.Context.MODE_PRIVATE;

import static com.noti.main.service.NotiListenerService.getUniqueID;
import static com.noti.main.service.NotiListenerService.sendNotification;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kieronquinn.monetcompat.core.MonetCompat;
import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.service.NotiListenerService;
import com.noti.main.utils.ui.ToastHelper;

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
        MaterialAlertDialogBuilder dialog;
        EditText editText;
        LinearLayout parentLayout;
        LinearLayout.LayoutParams layoutParams;

        switch (preference.getKey()) {
            case "pairKillSwitch" -> {
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setTitle("Reset Confirmation");
                dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                dialog.setMessage("Are you sure you want to unpair all the paired devices?\n\nThis task cannot be undone.");
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.setPositiveButton("Reset", (d, w) -> {
                    String DEVICE_NAME = NotiListenerService.getDeviceName();
                    String DEVICE_ID = getUniqueID();

                    try {
                        JSONObject notificationBody = new JSONObject();
                        notificationBody.put("type", "pair|request_remove_all");
                        notificationBody.put("device_name", DEVICE_NAME);
                        notificationBody.put("device_id", DEVICE_ID);

                        sendNotification(notificationBody, mContext.getPackageName(), mContext);
                    } catch (JSONException e) {
                        Log.e("Noti", "onCreate: " + e.getMessage());
                    }

                    mContext.getSharedPreferences("com.noti.main_pair", MODE_PRIVATE).edit().remove("paired_list").apply();
                    mContext.finish();
                });
                dialog.show();
            }
            case "indexMaximumSize" -> {
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setIcon(R.drawable.ic_fluent_edit_24_regular);
                dialog.setCancelable(false);
                dialog.setTitle("Input Value");
                dialog.setMessage("The maximum input limit is 2147483647 ms.");

                editText = new EditText(mContext);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setHint("Input Limit Value");
                editText.setGravity(Gravity.CENTER);
                editText.setText(String.valueOf(prefs.getInt("indexMaximumSize", 150)));

                parentLayout = new LinearLayout(mContext);
                layoutParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                layoutParams.setMargins(30, 16, 30, 16);
                editText.setLayoutParams(layoutParams);
                parentLayout.addView(editText);
                dialog.setView(parentLayout);
                dialog.setPositiveButton("Apply", (d, w) -> {
                    String value = editText.getText().toString();
                    if (value.equals("")) {
                        ToastHelper.show(mContext, "Please Input Value", "DISMISS", ToastHelper.LENGTH_SHORT);
                    } else {
                        int IntValue = Integer.parseInt(value);
                        if (IntValue > 0x7FFFFFFF - 1) {
                            ToastHelper.show(mContext, "Value must be lower than 2147483647", "DISMISS", ToastHelper.LENGTH_SHORT);
                        } else {
                            prefs.edit().putInt("indexMaximumSize", IntValue).apply();
                        }
                    }
                });
                dialog.setNeutralButton("Reset Default", (d, w) -> prefs.edit().putInt("indexMaximumSize", 150).apply());
                dialog.setNegativeButton("Cancel", (d, w) -> {});
                dialog.show();
            }
        }

        return true;
    }
}
