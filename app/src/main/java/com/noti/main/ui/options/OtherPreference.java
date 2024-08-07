package com.noti.main.ui.options;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.preference.SwitchPreference;

import com.application.isradeleon.notify.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.kieronquinn.monetcompat.core.MonetCompat;
import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.service.backend.PacketConst;
import com.noti.main.service.backend.PacketRequester;
import com.noti.main.service.backend.ResultPacket;
import com.noti.main.ui.prefs.custom.CustomFragment;
import com.noti.main.utils.network.AESCrypto;
import com.noti.main.utils.ui.ToastHelper;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;

import me.pushy.sdk.Pushy;

public class OtherPreference extends PreferenceFragmentCompat {

    SharedPreferences prefs;
    FirebaseAuth mAuth;
    MonetCompat monet;
    Activity mContext;

    Preference UseWiFiSleepPolicy;
    Preference HistoryLimit;
    Preference DataLimit;
    Preference ResetList;
    Preference DeleteHistory;
    Preference UpdateChannel;
    Preference SaveLastSelectedItem;
    Preference NewCardRadius;
    Preference PingTestBackend;

    Preference UseDataEncryption;
    Preference UseDataEncryptionPassword;
    Preference EncryptionInfo;
    Preference AlwaysEncryptData;
    Preference AllowOnlyPaired;
    Preference UseHMacAuth;

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
        prefs = mContext.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);
        mAuth = FirebaseAuth.getInstance();

        UseWiFiSleepPolicy = findPreference("UseWiFiSleepPolicy");
        HistoryLimit = findPreference("HistoryLimit");
        DataLimit = findPreference("DataLimit");
        ResetList = findPreference("ResetList");
        DeleteHistory = findPreference("DeleteHistory");
        UpdateChannel = findPreference("UpdateChannel");
        SaveLastSelectedItem = findPreference("SaveLastSelectedItem");
        NewCardRadius = findPreference("NewCardRadius");
        PingTestBackend = findPreference("PingTestBackend");

        UseDataEncryption = findPreference("UseDataEncryption");
        UseDataEncryptionPassword = findPreference("UseDataEncryptionPassword");
        EncryptionInfo = findPreference("EncryptionInfo");
        AlwaysEncryptData = findPreference("AlwaysEncryptData");
        AllowOnlyPaired = findPreference("AllowOnlyPaired");
        UseHMacAuth = findPreference("UseHMacAuth");

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

        boolean ifUIDBlank = prefs.getString("UID", "").isEmpty();
        if(ifUIDBlank) {
            ((SwitchPreference)UseDataEncryption).setChecked(false);
            UseDataEncryption.setEnabled(false);
            UseDataEncryption.setSummary("You should login first to use this feature");
            UseDataEncryptionPassword.setVisible(false);
            EncryptionInfo.setVisible(false);
        } else {
            boolean usesDataEncryption = prefs.getBoolean("UseDataEncryption", false);
            UseDataEncryptionPassword.setVisible(usesDataEncryption);
            EncryptionInfo.setVisible(usesDataEncryption);
            AlwaysEncryptData.setVisible(!usesDataEncryption);
            UseDataEncryption.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean foo = (boolean) newValue;
                UseDataEncryptionPassword.setVisible(foo);
                EncryptionInfo.setVisible(foo);
                AlwaysEncryptData.setVisible(!foo);
                return true;
            });
        }

        boolean isAllowOnlyPaired = prefs.getBoolean("AllowOnlyPaired", false);
        UseHMacAuth.setVisible(isAllowOnlyPaired);
        AllowOnlyPaired.setOnPreferenceChangeListener((preference, newValue) -> {
            UseHMacAuth.setVisible((Boolean) newValue);
            return true;
        });

        if(Application.isTablet()) {
            SaveLastSelectedItem.setVisible(true);
            NewCardRadius.setVisible(false);
        } else {
            SaveLastSelectedItem.setVisible(false);
            NewCardRadius.setVisible(true);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        MaterialAlertDialogBuilder dialog;
        EditText editText;
        LinearLayout parentLayout;
        LinearLayout.LayoutParams layoutParams;

        switch (preference.getKey()) {
            case "DataLimit":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
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
                    if (value.isEmpty()) {
                        ToastHelper.show(mContext, "Please Input Value", "DISMISS", ToastHelper.LENGTH_SHORT);
                    } else {
                        int IntValue = Integer.parseInt(value);
                        if (IntValue < 1) {
                            ToastHelper.show(mContext, "Value must be higher than 0", "DISMISS", ToastHelper.LENGTH_SHORT);
                        } else if (IntValue > 32786) {
                            ToastHelper.show(mContext, "Value must be lower than 32786", "DISMISS", ToastHelper.LENGTH_SHORT);
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
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
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
                    if (value.isEmpty()) {
                        ToastHelper.show(mContext, "Please Input Value", "DISMISS", ToastHelper.LENGTH_SHORT);
                    } else {
                        int IntValue = Integer.parseInt(value);
                        if (IntValue > 65535) {
                            ToastHelper.show(mContext, "Value must be lower than 65535", "DISMISS", ToastHelper.LENGTH_SHORT);
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
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setIcon(R.drawable.ic_fluent_delete_24_regular);
                dialog.setTitle("Waring!");
                dialog.setMessage("Are you sure to delete notification history?\nThis operation cannot be undone.");
                dialog.setPositiveButton("Delete", (d, w) -> {
                    mContext.getSharedPreferences("com.noti.main_logs", MODE_PRIVATE)
                            .edit()
                            .putString("sendLogs", "")
                            .putString("receivedLogs", "")
                            .apply();
                    ToastHelper.show(mContext, "Task done!", "OK", ToastHelper.LENGTH_SHORT);
                });
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.show();
                break;

            case "ResetList":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
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

            case "TestNotification":
                Notify.NotifyImportance importance;
                String value = prefs.getString("importance", "Default");
                importance = switch (value) {
                    case "Default" -> Notify.NotifyImportance.MAX;
                    case "Low" -> Notify.NotifyImportance.LOW;
                    case "High" -> Notify.NotifyImportance.HIGH;
                    default -> Notify.NotifyImportance.MIN;
                };

                Notify.build(mContext)
                        .setTitle("test (" + (int) ((new Date().getTime() / 1000L) % Integer.MAX_VALUE) + ")")
                        .setContent("messageTest")
                        .setLargeIcon(R.mipmap.ic_launcher)
                        .largeCircularIcon()
                        .setSmallIcon(R.drawable.ic_broken_image)
                        .setChannelName("Testing Channel")
                        .setChannelId("Notification Test")
                        .setImportance(importance)
                        .enableVibration(true)
                        .setAutoCancel(true)
                        .show();
                break;

            case "StartRegexAction":
                mContext.startActivity(new Intent(mContext, CustomFragment.class));
                break;

            case "UseDataEncryptionPassword":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setIcon(R.drawable.ic_fluent_edit_24_regular);
                dialog.setCancelable(false);
                dialog.setTitle("Input password");
                dialog.setMessage("Enter the password to be used for encryption.\nPassword is limited to a maximum of 20 characters.");

                editText = new EditText(mContext);
                editText.setInputType(InputType.TYPE_CLASS_TEXT);
                editText.setHint("Input password");
                editText.setGravity(Gravity.START);

                String rawPassword = prefs.getString("EncryptionPassword", "");
                if(rawPassword.isEmpty()) editText.setText("");
                else {
                    String uid = mAuth.getUid();
                    if(uid != null) {
                        try {
                            editText.setText(AESCrypto.decrypt(rawPassword, AESCrypto.parseAESToken(uid)));
                        } catch (Exception e) {
                            ToastHelper.show(mContext, "Error while processing crypto","DISMISS", ToastHelper.LENGTH_SHORT);
                        }
                    }
                }

                parentLayout = new LinearLayout(mContext);
                layoutParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                layoutParams.setMargins(30, 16, 30, 16);
                editText.setLayoutParams(layoutParams);
                parentLayout.addView(editText);
                dialog.setView(parentLayout);

                dialog.setPositiveButton("Apply", (d, w) -> {
                    String values = editText.getText().toString();
                    if (values.isEmpty()) {
                        ToastHelper.show(mContext, "Please Input password","DISMISS", ToastHelper.LENGTH_SHORT);
                    } else if(values.length() > 20) {
                        ToastHelper.show(mContext, "Password too long! maximum 20 chars.", "DISMISS",ToastHelper.LENGTH_SHORT);
                    } else {
                        try {
                            String uid = mAuth.getUid();
                            if(uid != null) prefs.edit().putString("EncryptionPassword", AESCrypto.encrypt(values, AESCrypto.parseAESToken(uid))).apply();
                        } catch (Exception e) {
                            ToastHelper.show(mContext, "Error while processing crypto", "DISMISS",ToastHelper.LENGTH_SHORT);
                        }
                    }
                });
                dialog.setNeutralButton("Reset Default", (d, w) -> prefs.edit().remove("EncryptionPassword").apply());
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.show();
                break;

            case "EncryptionInfo":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setTitle("Encryption Info");
                dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                dialog.setMessage(getString(R.string.Encryption_information));
                dialog.setPositiveButton("Close", (d, w) -> { });
                dialog.show();
                break;

            case "PingTestBackend":
                ToastHelper.show(mContext, "Please wait for a minute to check...", ToastHelper.LENGTH_LONG);
                long currentTime = System.currentTimeMillis();

                try {
                    JSONObject serverBody = new JSONObject();
                    PacketRequester.addToRequestQueue(mContext, PacketConst.SERVICE_TYPE_PING_SERVER, serverBody, response -> {
                        String cause = """
                                Time taken: %d (ms)
                                Server Version: %s
                                """;
                        try {
                            MaterialAlertDialogBuilder successDialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                            successDialog.setTitle("Test Success");
                            successDialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                            successDialog.setMessage(String.format(Locale.getDefault(), cause, (System.currentTimeMillis() - currentTime), ResultPacket.parseFrom(response.toString()).getExtraData()));
                            successDialog.setPositiveButton("Close", (d, w) -> { });
                            successDialog.show();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, error -> {
                        MaterialAlertDialogBuilder errorDialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                        String cause;

                        if(error.networkResponse == null) {
                            cause = """
                                Time taken: %d (ms)
                                Exception: %s
                                """;
                            errorDialog.setMessage(String.format(Locale.getDefault(), cause, (System.currentTimeMillis() - currentTime), error.getMessage()));
                        } else {
                            cause = """
                                Time taken: %d (ms)
                                Error code: %s
                                """;
                            errorDialog.setMessage(String.format(Locale.getDefault(), cause, (System.currentTimeMillis() - currentTime), error.networkResponse.statusCode));
                        }

                        errorDialog.setTitle("Test Failed");
                        errorDialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                        errorDialog.setPositiveButton("Close", (d, w) -> { });
                        errorDialog.show();
                    });
                } catch (Exception e) {
                    String cause = """
                                Time taken: %d (ms)
                                Exception: %s
                                """;
                    MaterialAlertDialogBuilder errorDialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                    errorDialog.setTitle("Test Failed");
                    errorDialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                    errorDialog.setMessage(String.format(Locale.getDefault(), cause, (System.currentTimeMillis() - currentTime), e.getMessage()));
                    errorDialog.setPositiveButton("Close", (d, w) -> { });
                    errorDialog.show();
                }
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
