package com.noti.main.ui.options;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
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
import com.noti.main.R;
import com.noti.main.ui.ToastHelper;

import java.util.ArrayList;

import me.pushy.sdk.Pushy;

public class OtherPreference extends PreferenceFragmentCompat {

    SharedPreferences prefs;
    MonetCompat monet;
    Activity mContext;

    Preference UseTaskerExtension;
    Preference TaskerCompatibleInfo;
    Preference UseWiFiSleepPolicy;
    Preference HistoryLimit;
    Preference DataLimit;
    Preference ResetList;
    Preference DeleteHistory;
    Preference UpdateChannel;

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
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.other_preferences, rootKey);
        prefs = mContext.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);

        UseTaskerExtension = findPreference("UseTaskerExtension");
        TaskerCompatibleInfo = findPreference("TaskerCompatibleInfo");
        UseWiFiSleepPolicy = findPreference("UseWiFiSleepPolicy");
        HistoryLimit = findPreference("HistoryLimit");
        DataLimit = findPreference("DataLimit");
        ResetList = findPreference("ResetList");
        DeleteHistory = findPreference("DeleteHistory");
        UpdateChannel = findPreference("UpdateChannel");

        ArrayList<String> taskerPluginList = new ArrayList<>();
        taskerPluginList.add("net.dinglisch.android.taskerm");
        taskerPluginList.add("com.llamalab.automate");
        taskerPluginList.add("com.twofortyfouram.locale.x");
        taskerPluginList.add("com.arlosoft.macrodroid");
        int packageCount = 0;

        for(String packageName : taskerPluginList) {
            try {
                mContext.getPackageManager().getPackageInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                packageCount++;
            }
        }

        if(packageCount == taskerPluginList.size()) {
            UseTaskerExtension.setEnabled(false);
            UseTaskerExtension.setSummary("This option requires the Tasker-compatible app.");
        }

        UseWiFiSleepPolicy.setOnPreferenceChangeListener((p, n) -> {
            Pushy.toggleWifiPolicyCompliance((boolean) n, mContext);
            return true;
        });

        int dataLimit = prefs.getInt("DataLimit", 4096);
        DataLimit.setSummary("Now : " + dataLimit + " Bytes" + (dataLimit == 4096 ? " (Default)" : ""));

        int historyLimit = prefs.getInt("HistoryLimit", 150);
        HistoryLimit.setSummary("Now : " + historyLimit + " pcs" + (historyLimit == 150 ? " (Default)" : ""));

        UpdateChannel.setSummary("Now : " + prefs.getString("UpdateChannel", "Automatically specified"));
        UpdateChannel.setOnPreferenceChangeListener((p, n) -> {
            UpdateChannel.setSummary("Now : " + n);
            return true;
        });
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        MaterialAlertDialogBuilder dialog;
        EditText editText;
        LinearLayout parentLayout;
        LinearLayout.LayoutParams layoutParams;

        switch (preference.getKey()) {
            case "DataLimit":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                dialog.setIcon(R.drawable.ic_fluent_edit_24_regular);
                dialog.setCancelable(false);
                dialog.setTitle("Input Data Limit");
                dialog.setMessage("If data size is bigger than 4kb (4096 bytes), then data may not send.");

                editText = new EditText(mContext);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setHint("Input Limit Value");
                editText.setGravity(Gravity.CENTER);
                editText.setText(String.valueOf(prefs.getInt("DataLimit", 4096)));

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
                        ToastHelper.show(mContext, "Please Input Value", "DISMISS",ToastHelper.LENGTH_SHORT);
                    } else {
                        int IntValue = Integer.parseInt(value);
                        if (IntValue < 1) {
                            ToastHelper.show(mContext, "Value must be higher than 0", "DISMISS",ToastHelper.LENGTH_SHORT);
                        } else if (IntValue > 32786) {
                            ToastHelper.show(mContext, "Value must be lower than 32786", "DISMISS",ToastHelper.LENGTH_SHORT);
                        } else {
                            prefs.edit().putInt("DataLimit", IntValue).apply();
                            DataLimit.setSummary("Now : " + IntValue + (IntValue == 4096 ? " bytes (Default)" : " bytes"));
                        }
                    }
                });
                dialog.setNeutralButton("Reset Default", (d, w) -> {
                    prefs.edit().putInt("DataLimit", 4096).apply();
                    DataLimit.setSummary("Now : " + 4096 + " Bytes (Default)");
                });
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.show();
                break;

            case "HistoryLimit":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                dialog.setIcon(R.drawable.ic_fluent_edit_24_regular);
                dialog.setCancelable(false);
                dialog.setTitle("Input Data Limit");
                dialog.setMessage("The history data maximum limit is 65535 pcs.");

                editText = new EditText(mContext);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setHint("Input Limit Value");
                editText.setGravity(Gravity.CENTER);
                editText.setText(String.valueOf(prefs.getInt("HistoryLimit", 150)));

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
                        ToastHelper.show(mContext, "Please Input Value","DISMISS", ToastHelper.LENGTH_SHORT);
                    } else {
                        int IntValue = Integer.parseInt(value);
                        if (IntValue > 65535) {
                            ToastHelper.show(mContext, "Value must be lower than 65535", "DISMISS",ToastHelper.LENGTH_SHORT);
                        } else {
                            prefs.edit().putInt("HistoryLimit", IntValue).apply();
                            HistoryLimit.setSummary("Now : " + IntValue + (IntValue == 150 ? " pcs (Default)" : " pcs"));
                        }
                    }
                });
                dialog.setNeutralButton("Reset Default", (d, w) -> {
                    prefs.edit().putInt("HistoryLimit", 150).apply();
                    HistoryLimit.setSummary("Now : " + 150 + " pcs (Default)");
                });
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.show();
                break;

            case "DeleteHistory":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                dialog.setIcon(R.drawable.ic_fluent_delete_24_regular);
                dialog.setTitle("Waring!");
                dialog.setMessage("Are you sure to delete notification history?\nThis operation cannot be undone.");
                dialog.setPositiveButton("Delete", (d, w) -> {
                    mContext.getSharedPreferences("com.noti.main_logs", MODE_PRIVATE)
                            .edit()
                            .putString("sendLogs", "")
                            .putString("receivedLogs", "")
                            .apply();
                    ToastHelper.show(mContext, "Task done!","OK", ToastHelper.LENGTH_SHORT);
                });
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.show();
                break;

            case "ResetList":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                dialog.setIcon(R.drawable.ic_fluent_delete_24_regular);
                dialog.setTitle("Waring!");
                dialog.setMessage("Are you sure to reset your white/black list?\nThis operation cannot be undone.");
                dialog.setPositiveButton("Reset", (d, w) -> {
                    mContext.getSharedPreferences("Whitelist", MODE_PRIVATE).edit().clear().apply();
                    mContext.getSharedPreferences("Blacklist", MODE_PRIVATE).edit().clear().apply();
                    ToastHelper.show(mContext, "Task done!", "OK", ToastHelper.LENGTH_SHORT);
                });
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.show();
                break;

            case "TaskerCompatibleInfo":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                dialog.setTitle("Tasker compatible apps");
                dialog.setMessage(getString(R.string.Dialog_Tasker_compatible));
                dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                dialog.setPositiveButton("Close", (d, w) -> {
                });
                dialog.show();
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
