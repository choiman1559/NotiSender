package com.noti.main.ui.options;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.kieronquinn.monetcompat.core.MonetCompat;
import com.noti.main.R;
import com.noti.main.ui.ToastHelper;
import com.noti.main.ui.prefs.BlacklistActivity;
import com.noti.main.utils.AESCrypto;
import com.noti.main.utils.DetectAppSource;

public class SendPreference extends PreferenceFragmentCompat {

    SharedPreferences prefs;
    FirebaseAuth mAuth;

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

    Preference UseReplySms;
    Preference UseReplyTelecom;
    Preference UseCallLog;

    Preference UseBannedOption;
    Preference BannedWords;

    Preference UseNullStrict;
    Preference DefaultTitle;
    Preference DefaultMessage;

    Preference UseDataEncryption;
    Preference UseDataEncryptionPassword;
    Preference EncryptionInfo;

    ActivityResultLauncher<String> startCallLogPermit = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if(!isGranted) {
            int SourceCode = DetectAppSource.detectSource(mContext);
            if(SourceCode == 1 || SourceCode == 2) {
                ToastHelper.show(mContext, "require read phone state permission!", "DISMISS", ToastHelper.LENGTH_SHORT);
            } else if (SourceCode == 3) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                builder.setTitle("Information").setMessage(getString(R.string.Dialog_rather_github));
                builder.setPositiveButton("Go to github", (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender/releases/latest"))));
                builder.setNegativeButton("Close", (d, w) -> { }).show();
            } else {
                ToastHelper.show(mContext, "Error while getting SHA-1 hash!", "OK",ToastHelper.LENGTH_SHORT);
            }

            ((SwitchPreference) UseCallLog).setChecked(false);
        }
    });

    ActivityResultLauncher<String> startReplyTelecomPermit = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if(!isGranted) {
            ToastHelper.show(mContext, "require read call log permission!", "DISMISS", ToastHelper.LENGTH_SHORT);
            ((SwitchPreference) UseReplyTelecom).setChecked(false);
            UseCallLog.setVisible(false);
        }
    });

    ActivityResultLauncher<String[]> startSmsRwPermit = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
        for(Boolean isGranted : result.values()) {
            if(!isGranted) {
                int SourceCode = DetectAppSource.detectSource(mContext);
                if(SourceCode == 1 || SourceCode == 2) {
                    ToastHelper.show(mContext, "require read call log permission!", "DISMISS", ToastHelper.LENGTH_SHORT);
                } else if (SourceCode == 3) {
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                    builder.setTitle("Information").setMessage(getString(R.string.Dialog_rather_github));
                    builder.setPositiveButton("Go to github", (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender/releases/latest"))));
                    builder.setNegativeButton("Close", (d, w) -> { }).show();
                } else {
                    ToastHelper.show(mContext, "require sms permission!", "DISMISS", ToastHelper.LENGTH_SHORT);
                }

                ((SwitchPreference) UseReplySms).setChecked(false);
                break;
            }
        }
    });

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

        prefs = mContext.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
        mAuth = FirebaseAuth.getInstance();

        Blacklist = findPreference("blacklist");
        UseWhiteList = findPreference("UseWhite");

        IconResolution = findPreference("IconRes");
        IconEnabled = findPreference("SendIcon");
        IconWarning = findPreference("IconWaring");
        IconUseNotification = findPreference("IconUseNotification");

        UseReplySms = findPreference("UseReplySms");
        UseReplyTelecom = findPreference("UseReplyTelecom");
        UseCallLog = findPreference("UseCallLog");

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

        UseDataEncryption = findPreference("UseDataEncryption");
        UseDataEncryptionPassword = findPreference("UseDataEncryptionPassword");
        EncryptionInfo = findPreference("EncryptionInfo");

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
            boolean isUseNullStricts = (boolean) n;
            DefaultTitle.setVisible(!isUseNullStricts);
            DefaultMessage.setVisible(!isUseNullStricts);
            return true;
        });

        boolean ifUIDBlank = prefs.getString("UID", "").equals("");
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
            UseDataEncryption.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean foo = (boolean) newValue;
                UseDataEncryptionPassword.setVisible(foo);
                EncryptionInfo.setVisible(foo);
                return true;
            });
        }

        UseCallLog.setVisible(prefs.getBoolean("UseReplyTelecom", false));
        UseReplyTelecom.setOnPreferenceChangeListener((preference, newValue) -> {
            UseCallLog.setVisible((boolean) newValue);
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
            case "blacklist":
                startActivity(new Intent(mContext, BlacklistActivity.class));
                break;

            case "IntervalTime":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
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
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
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
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                dialog.setTitle("Interval details");
                dialog.setMessage(getString(R.string.Interval_information));
                dialog.setPositiveButton("Close", (d, w) -> {
                });
                dialog.show();
                break;

            case "BannedWords":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
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

            case "UseReplySms":
                SwitchPreference UseSMS = (SwitchPreference) UseReplySms;
                if (UseSMS.isChecked() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (mContext.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
                            || mContext.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                        startSmsRwPermit.launch(new String[]{Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS});
                    }
                }
                break;

            case "UseReplyTelecom":
                SwitchPreference UseTelecom = (SwitchPreference) UseReplyTelecom;
                if (UseTelecom.isChecked() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (mContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                        startReplyTelecomPermit.launch(Manifest.permission.READ_PHONE_STATE);
                    }
                }
                break;

            case "UseCallLog":
                SwitchPreference CallLogSwitch = (SwitchPreference) UseCallLog;
                if(CallLogSwitch.isChecked() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if(mContext.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                        startCallLogPermit.launch(Manifest.permission.READ_CALL_LOG);
                    }
                }
                break;

            case "DefaultTitle":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
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
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
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

            case "UseDataEncryptionPassword":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                dialog.setIcon(R.drawable.ic_fluent_edit_24_regular);
                dialog.setCancelable(false);
                dialog.setTitle("Input password");
                dialog.setMessage("Enter the password to be used for encryption.\nPassword is limited to a maximum of 20 characters.");

                editText = new EditText(mContext);
                editText.setInputType(InputType.TYPE_CLASS_TEXT);
                editText.setHint("Input password");
                editText.setGravity(Gravity.START);

                String rawPassword = prefs.getString("EncryptionPassword", "");
                if(rawPassword.equals("")) editText.setText("");
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
                    String value = editText.getText().toString();
                    if (value.equals("")) {
                        ToastHelper.show(mContext, "Please Input password","DISMISS", ToastHelper.LENGTH_SHORT);
                    } else if(value.length() > 20) {
                        ToastHelper.show(mContext, "Password too long! maximum 20 chars.", "DISMISS",ToastHelper.LENGTH_SHORT);
                    } else {
                        try {
                            String uid = mAuth.getUid();
                            if(uid != null) prefs.edit().putString("EncryptionPassword", AESCrypto.encrypt(value, AESCrypto.parseAESToken(uid))).apply();
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
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                dialog.setTitle("Encryption Info");
                dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                dialog.setMessage(getString(R.string.Encryption_information));
                dialog.setPositiveButton("Close", (d, w) -> { });
                dialog.show();
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
