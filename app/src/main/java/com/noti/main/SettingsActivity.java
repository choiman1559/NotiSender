package com.noti.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.application.isradeleon.notify.Notify;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import com.noti.main.ui.AppInfoActivity;
import com.noti.main.ui.prefs.BlacklistActivity;
import com.noti.main.ui.prefs.HistoryActivity;
import com.noti.main.utils.AsyncTask;
import com.noti.main.utils.BillingHelper;
import com.noti.main.utils.DetectAppSource;

import java.util.Date;
import java.util.Set;

import me.pushy.sdk.Pushy;

public class SettingsActivity extends AppCompatActivity {

    @SuppressLint("StaticFieldLeak")
    static BillingHelper mBillingHelper;

    @SuppressLint("BatteryLife")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);
        if (Build.VERSION.SDK_INT > 28 && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder alert_confirm = new AlertDialog.Builder(this);
            alert_confirm.setMessage("You need to permit overlay permission to use this app")
                    .setCancelable(false).setPositiveButton("Bring me there",
                    (dialog, which) -> startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 101)).setNegativeButton("Cancel",
                    (dialog, which) -> finish());
            AlertDialog alert = alert_confirm.create();
            alert.show();
        }

        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
        if (!sets.contains(getPackageName())) {
            AlertDialog.Builder alert_confirm = new AlertDialog.Builder(this);
            alert_confirm.setMessage("You need to permit Alarm access permission to use this app")
                    .setCancelable(false).setPositiveButton("Bring me there",
                    (dialog, which) -> startActivityForResult(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            , 102)).setNegativeButton("Cancel",
                    (dialog, which) -> finish());
            AlertDialog alert = alert_confirm.create();
            alert.show();
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT > 22 && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            AlertDialog.Builder alert_confirm = new AlertDialog.Builder(this);
            alert_confirm.setMessage("You need to permit ignore battery optimizations permission to use this app")
                    .setCancelable(false).setPositiveButton("Bring me there",
                    (dialog, which) -> startActivityForResult(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:" + getPackageName()))
                            , 103)).setNegativeButton("Cancel",
                    (dialog, which) -> finish());
            AlertDialog alert = alert_confirm.create();
            alert.show();
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, newInstance())
                .commitNowAllowingStateLoss();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBillingHelper.Destroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 101) {
            if (Build.VERSION.SDK_INT > 28 && !Settings.canDrawOverlays(this)) finish();
        } else if (requestCode == 102) {
            Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
            if (!sets.contains(getPackageName())) finish();
        } else if (requestCode == 103) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (Build.VERSION.SDK_INT > 22 && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                finish();
            }
        }
    }

    public static SettingsFragment newInstance() {
        SettingsFragment fragment = new SettingsFragment();
        Bundle bundle = new Bundle(0);
        fragment.setArguments(bundle);
        return fragment;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private static final int RC_SIGN_IN = 100;
        private GoogleSignInClient mGoogleSignInClient;
        private FirebaseAuth mAuth;
        FirebaseFirestore mFirebaseFirestore;
        SharedPreferences prefs;
        Activity mContext;

        Preference Login;
        Preference Service;
        Preference ServiceToggle;
        Preference Server;
        Preference Subscribe;
        Preference ServerInfo;
        Preference Blacklist;
        Preference UseWhiteList;
        Preference TestRun;
        Preference SetImportance;
        Preference UseWiFiSleepPolicy;
        Preference IconResolution;
        Preference IconEnabled;
        Preference IconWarning;
        Preference IconUseNotification;
        Preference ForWearOS;
        Preference DataLimit;
        Preference HistoryLimit;
        Preference ResetList;
        Preference UseReplySms;
        Preference UseInterval;
        Preference IntervalTime;
        Preference IntervalType;
        Preference IntervalInfo;
        Preference UseBannedOption;
        Preference BannedWords;
        Preference UpdateChannel;
        Preference UseNullStrict;
        Preference DefaultTitle;
        Preference DefaultMessage;

        SettingsFragment() {
        }

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            if (context instanceof Activity) mContext = (Activity) context;
            else throw new RuntimeException("Can't get Activity instanceof Context!");
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();

            mGoogleSignInClient = GoogleSignIn.getClient(mContext, gso);
            mAuth = FirebaseAuth.getInstance();
            mFirebaseFirestore = FirebaseFirestore.getInstance();
            prefs = mContext.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);

            mFirebaseFirestore.collection("ApiKey")
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                prefs.edit()
                                        .putString("ApiKey_FCM", document.getString("FCM"))
                                        .putString("ApiKey_Pushy", document.getString("Pushy"))
                                        .putString("ApiKey_Billing", document.getString("Billing"))
                                        .apply();
                            }
                        } else {
                            new AlertDialog.Builder(mContext)
                                    .setTitle("Error occurred!")
                                    .setMessage("Error occurred while initializing client token.\nplease check your internet connection and try again.")
                                    .setPositiveButton("OK", (dialog, which) -> mContext.finishAndRemoveTask())
                                    .setCancelable(false);
                        }
                    });

            Login = findPreference("Login");
            TestRun = findPreference("testNoti");
            Service = findPreference("service");
            ServiceToggle = findPreference("serviceToggle");
            Server = findPreference("server");
            Subscribe = findPreference("Subscribe");
            ServerInfo = findPreference("ServerInfo");
            Blacklist = findPreference("blacklist");
            UseWhiteList = findPreference("UseWhite");
            SetImportance = findPreference("importance");
            UseWiFiSleepPolicy = findPreference("UseWiFiSleepPolicy");
            IconResolution = findPreference("IconRes");
            IconEnabled = findPreference("SendIcon");
            IconWarning = findPreference("IconWaring");
            IconUseNotification = findPreference("IconUseNotification");
            ForWearOS = findPreference("forWear");
            DataLimit = findPreference("DataLimit");
            HistoryLimit = findPreference("HistoryLimit");
            ResetList = findPreference("ResetList");
            UseReplySms = findPreference("UseReplySms");
            UseInterval = findPreference("UseInterval");
            IntervalTime = findPreference("IntervalTime");
            IntervalType = findPreference("IntervalType");
            IntervalInfo = findPreference("IntervalInfo");
            UseBannedOption = findPreference("UseBannedOption");
            BannedWords = findPreference("BannedWords");
            UpdateChannel = findPreference("UpdateChannel");
            UseNullStrict = findPreference("StrictStringNull");
            DefaultTitle = findPreference("DefaultTitle");
            DefaultMessage = findPreference("DefaultMessage");

            mBillingHelper = BillingHelper.initialize(mContext, new BillingHelper.BillingCallback() {
                @Override
                public void onPurchased(String productId) {
                    Toast.makeText(mContext, "Thanks for purchase!", Toast.LENGTH_SHORT).show();
                    ServiceToggle.setEnabled(!prefs.getString("UID", "").equals(""));
                    ServiceToggle.setSummary("");
                    Subscribe.setVisible(false);
                    new RegisterForPushNotificationsAsync().execute();
                }

                @Override
                public void onUpdatePrice(Double priceValue) {

                }
            });

            if (mBillingHelper.isSubscribed()) {
                new RegisterForPushNotificationsAsync().execute();
            }

            boolean ifUIDBlank = prefs.getString("UID", "").equals("");

            if (!ifUIDBlank) {
                Login.setSummary("Logined as " + prefs.getString("Email", ""));
                Login.setTitle(R.string.Logout);
                if (prefs.getString("Email", "").equals("") && mAuth.getCurrentUser() != null)
                    prefs.edit().putString("Email", mAuth.getCurrentUser().getEmail()).apply();
                if (prefs.getString("server", "Firebase Cloud Message").equals("Pushy")) {
                    if (mBillingHelper.isSubscribed()) {
                        Subscribe.setVisible(false);
                        ServiceToggle.setEnabled(true);
                    } else {
                        Subscribe.setVisible(true);
                        ServiceToggle.setEnabled(false);
                        ServiceToggle.setSummary("Needs subscribe to use Pushy server");
                    }
                } else {
                    Subscribe.setVisible(false);
                    ServiceToggle.setEnabled(true);
                }
            } else {
                ServiceToggle.setEnabled(false);
                if (prefs.getString("server", "Firebase Cloud Message").equals("Pushy") && !mBillingHelper.isSubscribed()) {
                    Subscribe.setVisible(true);
                    ServiceToggle.setSummary("Needs subscribe to use Pushy server");
                } else Subscribe.setVisible(false);
            }

            prefs.registerOnSharedPreferenceChangeListener((p, k) -> {
                if (k.equals("serviceToggle")) {
                    ((SwitchPreference) ServiceToggle).setChecked(prefs.getBoolean("serviceToggle", false));
                }
            });

            Service.setSummary("Now : " + prefs.getString("service", "not selected"));
            Service.setOnPreferenceChangeListener((p, n) -> {
                p.setSummary("Now : " + n.toString());
                return true;
            });

            Server.setSummary("Now : " + prefs.getString("server", "Firebase Cloud Message"));
            Server.setOnPreferenceChangeListener((p, n) -> {
                p.setSummary("Now : " + n.toString());
                if (n.toString().equals("Pushy")) {
                    if (mBillingHelper.isSubscribed()) ServiceToggle.setEnabled(true);
                    else {
                        Subscribe.setVisible(true);
                        ServiceToggle.setEnabled(false);
                        ServiceToggle.setSummary("Needs subscribe to use Pushy server");
                    }
                } else {
                    ServiceToggle.setEnabled(!prefs.getString("UID", "").equals(""));
                    ServiceToggle.setSummary("");
                    Subscribe.setVisible(false);
                }
                return true;
            });

            if (Build.VERSION.SDK_INT >= 26) {
                SetImportance.setSummary("Now : " + prefs.getString("importance", ""));
                SetImportance.setOnPreferenceChangeListener(((p, n) -> {
                    SetImportance.setSummary("Now : " + n);
                    return true;
                }));
            } else {
                SetImportance.setEnabled(false);
                SetImportance.setSummary("Not for Android N or lower.");
            }

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

            int dataLimit = prefs.getInt("DataLimit", 4096);
            DataLimit.setSummary("Now : " + dataLimit + " Bytes" + (dataLimit == 4096 ? " (Default)" : ""));

            int historyLimit = prefs.getInt("HistoryLimit", 150);
            HistoryLimit.setSummary("Now : " + historyLimit + " pcs" + (historyLimit == 150 ? " (Default)" : ""));

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
            boolean useInterval = prefs.getBoolean("UseInterval", false);
            IntervalInfo.setVisible(useInterval);
            IntervalType.setVisible(useInterval);
            IntervalTime.setVisible(useInterval);
            UseInterval.setOnPreferenceChangeListener((p, n) -> {
                boolean useIt = (boolean) n;
                IntervalInfo.setVisible(useIt);
                IntervalType.setVisible(useIt);
                IntervalTime.setVisible(useIt);
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

            UpdateChannel.setSummary("Now : " + prefs.getString("UpdateChannel", "Automatically specified"));
            UpdateChannel.setOnPreferenceChangeListener((p, n) -> {
                UpdateChannel.setSummary("Now : " + n);
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

            UseWiFiSleepPolicy.setOnPreferenceChangeListener((p, n) -> {
                Pushy.toggleWifiPolicyCompliance((boolean) n, mContext);
                return true;
            });

            try {
                mContext.getPackageManager().getPackageInfo("com.google.android.wearable.app", 0);
            } catch (PackageManager.NameNotFoundException e) {
                ForWearOS.setVisible(false);
            }
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            AlertDialog.Builder dialog;
            EditText editText;
            LinearLayout parentLayout;
            LinearLayout.LayoutParams layoutParams;

            switch (preference.getKey()) {
                case "AppInfo":
                    startActivity(new Intent(mContext, AppInfoActivity.class));
                    break;

                case "blacklist":
                    startActivity(new Intent(mContext, BlacklistActivity.class));
                    break;

                case "Login":
                    accountTask();
                    break;

                case "service":
                    String UID = prefs.getString("UID", "");
                    if (!UID.equals("")) {
                        FirebaseMessaging.getInstance().subscribeToTopic(UID);
                        new Thread(() -> {
                            try {
                                Pushy.subscribe(UID, mContext);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();
                    }
                    break;

                case "Subscribe":
                    mBillingHelper.Subscribe();
                    break;

                case "ServerInfo":
                    dialog = new AlertDialog.Builder(mContext);
                    dialog.setTitle("Server details");
                    dialog.setMessage(getString(R.string.Server_information));
                    dialog.setPositiveButton("Close", (d, w) -> {
                    });
                    dialog.show();
                    break;

                case "testNoti":
                    Notify.create(mContext)
                            .setTitle("test (" + (int) ((new Date().getTime() / 1000L) % Integer.MAX_VALUE) + ")")
                            .setContent("messageTest")
                            .setLargeIcon(R.mipmap.ic_launcher)
                            .circleLargeIcon()
                            .setSmallIcon(R.drawable.ic_broken_image)
                            .setImportance(getImportance())
                            .enableVibration(true)
                            .setAutoCancel(true)
                            .show();
                    break;

                case "forWear":
                    startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("http://play.google.com/store/apps/details?id=com.noti.main.wear")));
                    break;

                case "NotiLog":
                    startActivity(new Intent(mContext, HistoryActivity.class));
                    break;

                case "DataLimit":
                    dialog = new AlertDialog.Builder(mContext);
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
                            Toast.makeText(mContext, "Please Input Value", Toast.LENGTH_SHORT).show();
                        } else {
                            int IntValue = Integer.parseInt(value);
                            if (IntValue < 1) {
                                Toast.makeText(mContext, "Value must be higher than 0", Toast.LENGTH_SHORT).show();
                            } else if (IntValue > 32786) {
                                Toast.makeText(mContext, "Value must be lower than 32786", Toast.LENGTH_SHORT).show();
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
                    dialog = new AlertDialog.Builder(mContext);
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
                            Toast.makeText(mContext, "Please Input Value", Toast.LENGTH_SHORT).show();
                        } else {
                            int IntValue = Integer.parseInt(value);
                            if (IntValue > 65535) {
                                Toast.makeText(mContext, "Value must be lower than 65535", Toast.LENGTH_SHORT).show();
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

                case "IntervalTime":
                    dialog = new AlertDialog.Builder(mContext);
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
                            Toast.makeText(mContext, "Please Input Value", Toast.LENGTH_SHORT).show();
                        } else {
                            int IntValue = Integer.parseInt(value);
                            if (IntValue > 0x7FFFFFFF - 1) {
                                Toast.makeText(mContext, "Value must be lower than 2147483647", Toast.LENGTH_SHORT).show();
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

                case "IntervalInfo":
                    dialog = new AlertDialog.Builder(mContext);
                    dialog.setTitle("Interval details");
                    dialog.setMessage(getString(R.string.Interval_information));
                    dialog.setPositiveButton("Close", (d, w) -> {
                    });
                    dialog.show();
                    break;

                case "BannedWords":
                    dialog = new AlertDialog.Builder(mContext);
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
                            Toast.makeText(mContext, "Please Input Value", Toast.LENGTH_SHORT).show();
                        } else prefs.edit().putString("BannedWords", value).apply();
                    });
                    dialog.setNeutralButton("Clear", (d, w) -> prefs.edit().putString("BannedWords", "").apply());
                    dialog.setNegativeButton("Cancel", (d, w) -> {
                    });
                    dialog.show();
                    break;

                case "ResetList":
                    dialog = new AlertDialog.Builder(mContext);
                    dialog.setTitle("Waring!");
                    dialog.setMessage("Are you sure to reset your white/black list?\nThis operation cannot be undone.");
                    dialog.setPositiveButton("Reset", (d, w) -> {
                        mContext.getSharedPreferences("Whitelist", MODE_PRIVATE).edit().clear().apply();
                        mContext.getSharedPreferences("Blacklist", MODE_PRIVATE).edit().clear().apply();
                        Toast.makeText(mContext, "Task done!", Toast.LENGTH_SHORT).show();
                    });
                    dialog.setNegativeButton("Cancel", (d, w) -> {
                    });
                    dialog.show();
                    break;

                case "UseReplySms":
                    SwitchPreference UseSMS = (SwitchPreference) UseReplySms;
                    if (UseSMS.isChecked() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (mContext.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED
                                || mContext.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS}, 2);
                        }
                    }
                    break;

                case "DefaultTitle":
                    dialog = new AlertDialog.Builder(mContext);
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
                            Toast.makeText(mContext, "Please Input Value", Toast.LENGTH_SHORT).show();
                        } else prefs.edit().putString("DefaultTitle", value).apply();
                    });
                    dialog.setNeutralButton("Reset Default", (d, w) -> prefs.edit().putString("DefaultTitle", "New notification").apply());
                    dialog.setNegativeButton("Cancel", (d, w) -> {
                    });
                    dialog.show();
                    break;

                case "DefaultMessage":
                    dialog = new AlertDialog.Builder(mContext);
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
                            Toast.makeText(mContext, "Please Input Value", Toast.LENGTH_SHORT).show();
                        } else prefs.edit().putString("DefaultMessage", value).apply();
                    });
                    dialog.setNeutralButton("Reset Default", (d, w) -> prefs.edit().putString("DefaultMessage", "notification arrived.").apply());
                    dialog.setNegativeButton("Cancel", (d, w) -> {
                    });
                    dialog.show();
                    break;

                case "DeleteHistory":
                    dialog = new AlertDialog.Builder(mContext);
                    dialog.setTitle("Waring!");
                    dialog.setMessage("Are you sure to delete notification history?\nThis operation cannot be undone.");
                    dialog.setPositiveButton("Delete", (d, w) -> {
                        prefs.edit().putString("sendLogs", "").putString("receivedLogs", "").apply();
                        Toast.makeText(mContext, "Task done!", Toast.LENGTH_SHORT).show();
                    });
                    dialog.setNegativeButton("Cancel", (d, w) -> {
                    });
                    dialog.show();
                    break;
            }
            return super.onPreferenceTreeClick(preference);
        }

        private Notify.NotificationImportance getImportance() {
            String value = prefs.getString("importance", "Default");
            switch (value) {
                case "Default":
                    return Notify.NotificationImportance.MAX;
                case "Low":
                    return Notify.NotificationImportance.LOW;
                case "High":
                    return Notify.NotificationImportance.HIGH;
                default:
                    return Notify.NotificationImportance.MIN;
            }
        }

        private void accountTask() {
            if (prefs.getString("UID", "").equals("")) {
                ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

                if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
                    Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                    startActivityForResult(signInIntent, RC_SIGN_IN);
                } else
                    Snackbar.make(mContext.findViewById(R.id.layout), "Check Internet and Try Again", Snackbar.LENGTH_SHORT)
                            .setAction("Action", null).show();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setTitle("Confirm").setMessage("Are you sure to Log out?");
                builder.setPositiveButton("Yes", (dialog, which) -> {
                    ServiceToggle.setEnabled(false);
                    mAuth.signOut();
                    mGoogleSignInClient.signOut();

                    SharedPreferences.Editor edit = prefs.edit();
                    edit.remove("UID");
                    edit.remove("Email");
                    edit.apply();
                    recreate();
                });
                builder.setNegativeButton("No", ((dialog, which) -> {
                }));
                builder.show();
            }
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    if (requestCode == 2) {
                        int SourceCode = DetectAppSource.detectSource(mContext);
                        if (SourceCode == 1 || SourceCode == 2) {
                            Toast.makeText(mContext, "require sms permission!", Toast.LENGTH_SHORT).show();
                        } else if (SourceCode == 3) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                            builder.setTitle("Information").setMessage(getString(R.string.Dialog_rather_github));
                            builder.setPositiveButton("Go to github", (dialog, which) -> startActivity()).setNegativeButton("Close", (d, w) -> {
                            }).show();
                        } else
                            Toast.makeText(mContext, "Error while getting SHA-1 hash!", Toast.LENGTH_SHORT).show();
                        ((SwitchPreference) UseReplySms).setChecked(false);
                    }
                    return;
                }
            }
        }

        void startActivity() {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender/releases/latest")));
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (requestCode == RC_SIGN_IN && result != null && result.isSuccess()) {
                Log.d("Google log in", "Result : " + (result.isSuccess() ? "success" : "failed") + " Status : " + result.getStatus().toString());
                GoogleSignInAccount account = result.getSignInAccount();
                assert account != null;
                firebaseAuthWithGoogle(account);
            } else mBillingHelper.handleActivityResult(requestCode, resultCode, data);
        }

        private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
            AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
            mAuth.signInWithCredential(credential)
                    .addOnCompleteListener(mContext, task -> {
                        if (!task.isSuccessful()) {
                            Toast.makeText(mContext, "failed to login Google", Toast.LENGTH_SHORT).show();
                        } else if (mAuth.getCurrentUser() != null) {
                            Toast.makeText(mContext, "Success to login Google", Toast.LENGTH_SHORT).show();
                            prefs.edit().putString("UID", mAuth.getUid()).apply();
                            prefs.edit().putString("Email", mAuth.getCurrentUser().getEmail()).apply();
                            recreate();
                        }
                    });
        }

        private void recreate() {
            FragmentActivity activity = getActivity();
            if (activity != null) activity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, newInstance())
                    .commitNowAllowingStateLoss();
        }

        @SuppressLint("StaticFieldLeak")
        private class RegisterForPushNotificationsAsync extends AsyncTask<Void, Void, Void> {
            protected Void doInBackground(Void... params) {
                try {
                    Pushy.register(mContext);
                } catch (Exception exc) {
                    exc.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                Pushy.listen(mContext);
            }
        }
    }
}