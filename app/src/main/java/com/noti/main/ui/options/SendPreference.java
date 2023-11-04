package com.noti.main.ui.options;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
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
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kieronquinn.monetcompat.core.MonetCompat;

import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.utils.BillingHelper;
import com.noti.main.utils.ui.ToastHelper;
import com.noti.main.ui.prefs.BlacklistActivity;

import java.util.Set;

public class SendPreference extends PreferenceFragmentCompat {

    SharedPreferences prefs;

    MonetCompat monet;
    Activity mContext;

    Preference Blacklist;
    Preference UseWhiteList;

    Preference IconResolution;
    Preference IconEnabled;
    Preference IconWarning;
    Preference IconUseNotification;

    Preference UseInterval;
    Preference IntervalTime;
    Preference IntervalType;
    Preference IntervalInfo;
    Preference IntervalQueryGCTrigger;
    Preference UseBannedOption;
    Preference BannedWords;

    Preference UseNullStrict;
    Preference DefaultTitle;
    Preference DefaultMessage;

    Preference UseMediaSync;
    Preference UseAlbumArt;
    Preference UseFcmWhenSendImage;
    Preference FcmWhenSendImageInfo;

    Preference UseSplitData;
    Preference SplitInterval;
    Preference SplitAfterEncryption;

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
        setPreferencesFromResource(R.xml.send_preferences, rootKey);

        prefs = mContext.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);

        Blacklist = findPreference("blacklist");
        UseWhiteList = findPreference("UseWhite");

        IconResolution = findPreference("IconRes");
        IconEnabled = findPreference("SendIcon");
        IconWarning = findPreference("IconWaring");
        IconUseNotification = findPreference("IconUseNotification");

        UseInterval = findPreference("UseInterval");
        IntervalTime = findPreference("IntervalTime");
        IntervalType = findPreference("IntervalType");
        IntervalInfo = findPreference("IntervalInfo");
        IntervalQueryGCTrigger = findPreference("IntervalQueryGCTrigger");

        UseBannedOption = findPreference("UseBannedOption");
        BannedWords = findPreference("BannedWords");
        UseNullStrict = findPreference("StrictStringNull");
        DefaultTitle = findPreference("DefaultTitle");
        DefaultMessage = findPreference("DefaultMessage");

        UseMediaSync = findPreference("UseMediaSync");
        UseAlbumArt = findPreference("UseAlbumArt");
        UseFcmWhenSendImage = findPreference("UseFcmWhenSendImage");
        FcmWhenSendImageInfo = findPreference("FcmWhenSendImageInfo");

        UseSplitData = findPreference("UseSplitData");
        SplitInterval = findPreference("SplitInterval");
        SplitAfterEncryption = findPreference("SplitAfterEncryption");

        boolean isntUpOsM = Build.VERSION.SDK_INT < 22;
        if (isntUpOsM) {
            IconUseNotification.setEnabled(false);
            IconUseNotification.setSummary("Works only on Android M and above!");
        }
        boolean isSendIconEnabled = prefs.getBoolean("SendIcon", false);
        IconResolution.setVisible(isSendIconEnabled);
        IconWarning.setVisible(isSendIconEnabled);
        IconUseNotification.setVisible(isSendIconEnabled);
        IconResolution.setSummary("Now : " + prefs.getString("IconRes", "52 x 52 (Default)"));
        IconEnabled.setOnPreferenceChangeListener((p, n) -> {
            IconResolution.setVisible((boolean) n);
            IconWarning.setVisible((boolean) n);
            IconUseNotification.setVisible((boolean) n);
            return true;
        });
        IconResolution.setOnPreferenceChangeListener(((p, n) -> {
            IconResolution.setSummary("Now : " + n);
            return true;
        }));

        boolean isWhiteList = prefs.getBoolean("UseWhite", false);
        Blacklist.setTitle("Edit " + (isWhiteList ? "whitelist" : "blacklist"));
        Blacklist.setSummary("select apps that you " + (isWhiteList ? "want" : "won't") + " send notification");
        UseWhiteList.setOnPreferenceChangeListener((p, n) -> {
            boolean isWhite = (boolean) n;
            Blacklist.setTitle("Edit " + (isWhite ? "whitelist" : "blacklist"));
            Blacklist.setSummary("select apps that you " + (isWhite ? "want" : "won't") + " send notification");
            return true;
        });

        int intervalTime = prefs.getInt("IntervalTime", 150);
        IntervalTime.setSummary("Now : " + intervalTime + (intervalTime == 150 ? " ms (Default)" : " ms"));
        int gcTriggerValue = prefs.getInt("IntervalQueryGCTrigger", 50);
        IntervalQueryGCTrigger.setSummary("Now : " + (gcTriggerValue < 1 ? " Disabled (Default)" : gcTriggerValue + " objects"));

        boolean useInterval = prefs.getBoolean("UseInterval", false);
        IntervalInfo.setVisible(useInterval);
        IntervalType.setVisible(useInterval);
        IntervalTime.setVisible(useInterval);
        IntervalQueryGCTrigger.setVisible(useInterval);
        UseInterval.setOnPreferenceChangeListener((p, n) -> {
            boolean useIt = (boolean) n;
            IntervalInfo.setVisible(useIt);
            IntervalType.setVisible(useIt);
            IntervalTime.setVisible(useIt);
            IntervalQueryGCTrigger.setVisible(useIt);
            return true;
        });
        IntervalType.setSummary("Now : " + prefs.getString("IntervalType", "Entire app"));
        IntervalType.setOnPreferenceChangeListener((p, n) -> {
            IntervalType.setSummary("Now : " + n);
            return true;
        });

        BannedWords.setVisible(prefs.getBoolean("UseBannedOption", false));
        UseBannedOption.setOnPreferenceChangeListener((p, n) -> {
            BannedWords.setVisible((boolean) n);
            return true;
        });

        boolean isUseNullStrict = prefs.getBoolean("StrictStringNull", false);
        DefaultTitle.setVisible(!isUseNullStrict);
        DefaultMessage.setVisible(!isUseNullStrict);
        UseNullStrict.setOnPreferenceChangeListener((p, n) -> {
            boolean isUseNullStructs = (boolean) n;
            DefaultTitle.setVisible(!isUseNullStructs);
            DefaultMessage.setVisible(!isUseNullStructs);
            return true;
        });

        boolean isUseMediaSync = prefs.getBoolean("UseMediaSync", false);
        UseFcmWhenSendImage.setVisible(isUseMediaSync);
        UseAlbumArt.setVisible(isUseMediaSync);
        FcmWhenSendImageInfo.setVisible(isUseMediaSync && prefs.getBoolean("UseFcmWhenSendImage", false));
        UseMediaSync.setOnPreferenceChangeListener(((preference, newValue) -> {
            boolean foo = (boolean) newValue;
            UseFcmWhenSendImage.setVisible(foo);
            UseAlbumArt.setVisible(foo);
            FcmWhenSendImageInfo.setVisible(foo && prefs.getBoolean("UseFcmWhenSendImage", false));
            return true;
        }));

        try {
            BillingHelper billingHelper = BillingHelper.getInstance();
            if (!billingHelper.isSubscribedOrDebugBuild()) {
                ((SwitchPreference) UseAlbumArt).setChecked(false);
                UseAlbumArt.setEnabled(false);
                ((SwitchPreference) UseFcmWhenSendImage).setChecked(false);
                UseFcmWhenSendImage.setEnabled(false);
                UseAlbumArt.setSummary("Requires Premium subscription");
            }
        } catch (IllegalStateException e) {
            ToastHelper.show(mContext, "Error: Can't get purchase information!", ToastHelper.LENGTH_SHORT);
            ((SwitchPreference) UseAlbumArt).setChecked(false);
            UseAlbumArt.setEnabled(false);
            ((SwitchPreference) UseFcmWhenSendImage).setChecked(false);
            UseFcmWhenSendImage.setEnabled(false);
            UseAlbumArt.setSummary("Requires Premium subscription");
        }

        UseFcmWhenSendImage.setOnPreferenceChangeListener(((preference, newValue) -> {
            FcmWhenSendImageInfo.setVisible((boolean) newValue);
            return true;
        }));

        int splitIntervalValue = prefs.getInt("SplitInterval", 500);
        boolean useSplit = prefs.getBoolean("UseSplitData", false);
        SplitInterval.setVisible(useSplit);
        SplitAfterEncryption.setVisible(useSplit);
        SplitInterval.setSummary("Now : " + (splitIntervalValue == 500 ? "500 ms (Default)" : (splitIntervalValue < 1 ? "0 ms (Disabled)" : splitIntervalValue + " ms")));
        UseSplitData.setOnPreferenceChangeListener(((preference, newValue) -> {
            SplitInterval.setVisible((boolean) newValue);
            SplitAfterEncryption.setVisible((boolean) newValue);
            return true;
        }));

        if(!isAlarmPermissionGranted()) {
            ToastHelper.show(mContext, "Options in this menu do not work because you do not have Alarm access permission.", "Okay",ToastHelper.LENGTH_SHORT);
        }
    }

    boolean isAlarmPermissionGranted() {
        UiModeManager uiModeManager = (UiModeManager) mContext.getSystemService(Context.UI_MODE_SERVICE);
        boolean isTelevisionsEnabled = uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(mContext);
        return !isTelevisionsEnabled && sets.contains(mContext.getPackageName());
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        MaterialAlertDialogBuilder dialog;
        EditText editText;
        LinearLayout parentLayout;
        LinearLayout.LayoutParams layoutParams;

        switch (preference.getKey()) {
            case "blacklist":
                startActivity(new Intent(mContext, BlacklistActivity.class));
                break;

            case "IntervalTime":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setIcon(R.drawable.ic_fluent_edit_24_regular);
                dialog.setCancelable(false);
                dialog.setTitle("Input Value");
                dialog.setMessage("The interval time maximum limit is 2147483647 ms.");

                editText = new EditText(mContext);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setHint("Input Limit Value");
                editText.setGravity(Gravity.CENTER);
                editText.setText(String.valueOf(prefs.getInt("IntervalTime", 150)));

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
                        if (IntValue > 0x7FFFFFFF - 1) {
                            ToastHelper.show(mContext, "Value must be lower than 2147483647", "DISMISS",ToastHelper.LENGTH_SHORT);
                        } else {
                            prefs.edit().putInt("IntervalTime", IntValue).apply();
                            IntervalTime.setSummary("Now : " + IntValue + (IntValue == 150 ? " ms (Default)" : " ms"));
                        }
                    }
                });
                dialog.setNeutralButton("Reset Default", (d, w) -> {
                    prefs.edit().putInt("IntervalTime", 150).apply();
                    IntervalTime.setSummary("Now : " + 150 + " ms (Default)");
                });
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.show();
                break;

            case "IntervalQueryGCTrigger":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setIcon(R.drawable.ic_fluent_edit_24_regular);
                dialog.setCancelable(false);
                dialog.setTitle("Input Value");
                dialog.setMessage("When the Query Object used during interval calculation is no longer used, it is cleaned up to free memory.\n\nThe GC trigger count maximum limit is 2147483647 objects and Input 0 or lower to disable this option.");

                editText = new EditText(mContext);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setHint("Input GC Trigger count Value");
                editText.setGravity(Gravity.CENTER);
                editText.setText(String.valueOf(prefs.getInt("IntervalQueryGCTrigger", 0)));

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
                        if (IntValue > 0x7FFFFFFF - 1) {
                            ToastHelper.show(mContext, "Value must be lower than 2147483647", "DISMISS",ToastHelper.LENGTH_SHORT);
                        } else {
                            prefs.edit().putInt("IntervalQueryGCTrigger", IntValue).apply();
                            IntervalQueryGCTrigger.setSummary("Now : " + (IntValue < 1 ? " Disabled (Default)" : IntValue + " objects"));
                        }
                    }
                });
                dialog.setNeutralButton("Reset Default", (d, w) -> {
                    prefs.edit().putInt("IntervalQueryGCTrigger", 0).apply();
                    IntervalQueryGCTrigger.setSummary("Now : Disabled (Default)");
                });
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.show();
                break;

            case "IntervalInfo":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                dialog.setTitle("Interval details");
                dialog.setMessage(getString(R.string.Interval_information));
                dialog.setPositiveButton("Close", (d, w) -> {
                });
                dialog.show();
                break;

            case "BannedWords":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setIcon(R.drawable.ic_fluent_edit_24_regular);
                dialog.setCancelable(false);
                dialog.setTitle("Input Value");
                dialog.setMessage("Each entry is separated by \"/\".");

                editText = new EditText(mContext);
                editText.setInputType(InputType.TYPE_CLASS_TEXT);
                editText.setHint("Input Value");
                editText.setGravity(Gravity.START);
                editText.setText(prefs.getString("BannedWords", ""));

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
                    } else prefs.edit().putString("BannedWords", value).apply();
                });
                dialog.setNeutralButton("Clear", (d, w) -> prefs.edit().putString("BannedWords", "").apply());
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.show();
                break;

            case "DefaultTitle":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setIcon(R.drawable.ic_fluent_edit_24_regular);
                dialog.setCancelable(false);
                dialog.setTitle("Input Value");
                dialog.setMessage("Input default title string that used when notifications title string is null.");

                editText = new EditText(mContext);
                editText.setInputType(InputType.TYPE_CLASS_TEXT);
                editText.setHint("Input Value");
                editText.setGravity(Gravity.START);
                editText.setText(prefs.getString("DefaultTitle", "New notification"));

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
                    } else prefs.edit().putString("DefaultTitle", value).apply();
                });
                dialog.setNeutralButton("Reset Default", (d, w) -> prefs.edit().putString("DefaultTitle", "New notification").apply());
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.show();
                break;

            case "DefaultMessage":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setIcon(R.drawable.ic_fluent_edit_24_regular);
                dialog.setCancelable(false);
                dialog.setTitle("Input Value");
                dialog.setMessage("Input default message string that used when notifications message string is null.");

                editText = new EditText(mContext);
                editText.setInputType(InputType.TYPE_CLASS_TEXT);
                editText.setHint("Input Value");
                editText.setGravity(Gravity.START);
                editText.setText(prefs.getString("DefaultMessage", "notification arrived."));

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
                    } else prefs.edit().putString("DefaultMessage", value).apply();
                });
                dialog.setNeutralButton("Reset Default", (d, w) -> prefs.edit().putString("DefaultMessage", "notification arrived.").apply());
                dialog.setNegativeButton("Cancel", (d, w) -> {
                });
                dialog.show();
                break;

            case "SplitInterval":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setIcon(R.drawable.ic_fluent_edit_24_regular);
                dialog.setCancelable(false);
                dialog.setTitle("Input Value");
                dialog.setMessage("The interval maximum limit is 2147483647 ms and Input 0 or lower to disable this option.");

                editText = new EditText(mContext);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setHint("Input interval value");
                editText.setGravity(Gravity.CENTER);
                editText.setText(String.valueOf(prefs.getInt("SplitInterval", 0)));

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
                        if (IntValue > 0x7FFFFFFF - 1) {
                            ToastHelper.show(mContext, "Value must be lower than 2147483647", "DISMISS",ToastHelper.LENGTH_SHORT);
                        } else {
                            prefs.edit().putInt("SplitInterval", IntValue).apply();
                            SplitInterval.setSummary("Now : " + (IntValue == 500 ? "500 ms (Default)" : (IntValue < 1 ? "0 ms (Disabled)" : IntValue + " ms")));
                        }
                    }
                });
                dialog.setNeutralButton("Reset Default", (d, w) -> {
                    prefs.edit().putInt("SplitInterval", 500).apply();
                    SplitInterval.setSummary("Now : 500 ms (Default)");
                });
                dialog.setNegativeButton("Cancel", (d, w) -> {});
                dialog.show();
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
