package com.noti.main;

import android.Manifest;
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
import android.provider.Settings;
import android.text.InputType;
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
import androidx.preference.CheckBoxPreference;
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
import com.google.firebase.messaging.FirebaseMessaging;
import com.noti.main.BuildConfig;
import com.noti.main.R;
import com.noti.main.ui.AppinfoActiity;
import com.noti.main.ui.prefs.BlacklistActivity;
import com.noti.main.ui.prefs.HistoryActivity;

import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

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

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment(this))
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 101) {
            if (Build.VERSION.SDK_INT > 28 && !Settings.canDrawOverlays(this)) finish();
        }
        if (requestCode == 102) {
            Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
            if (!sets.contains(getPackageName())) finish();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        Activity mContext;
        private static final int RC_SIGN_IN = 100;
        private FirebaseAuth mAuth;
        private GoogleSignInClient mGoogleSignInClient;
        SharedPreferences prefs;

        Preference Login;
        Preference Service;
        Preference ServiceToggle;
        Preference Blacklist;
        Preference UseWhiteList;
        Preference TestRun;
        Preference SetImportance;
        Preference IconResolution;
        Preference IconEnabled;
        Preference IconWarning;
        Preference DebugLogEnable;
        Preference ForWearOS;
        Preference DataLimit;
        Preference HistoryLimit;
        Preference ResetList;
        Preference UseReplySms;
        Preference UseReceiveCall;

        SettingsFragment(Activity activity) {
            this.mContext = activity;
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
            prefs = mContext.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);

            Login = findPreference("Login");
            TestRun = findPreference("testNoti");
            Service = findPreference("service");
            ServiceToggle = findPreference("serviceToggle");
            Blacklist = findPreference("blacklist");
            UseWhiteList = findPreference("UseWhite");
            SetImportance = findPreference("importance");
            IconResolution = findPreference("IconRes");
            IconEnabled = findPreference("SendIcon");
            IconWarning = findPreference("IconWaring");
            DebugLogEnable = findPreference("debugInfo");
            ForWearOS = findPreference("forWear");
            DataLimit = findPreference("DataLimit");
            HistoryLimit = findPreference("HistoryLimit");
            ResetList = findPreference("ResetList");
            UseReplySms = findPreference("UseReplySms");
            UseReceiveCall = findPreference("UseReceiveCall");

            boolean ifUIDBlank = prefs.getString("UID", "").equals("");

            if (!ifUIDBlank) {
                Login.setSummary("Logined as " + prefs.getString("Email", ""));
                Login.setTitle(R.string.Logout);
                ServiceToggle.setEnabled(true);
                if (prefs.getString("Email", "").equals(""))
                    prefs.edit().putString("Email", mAuth.getCurrentUser().getEmail()).apply();
            } else {
                ServiceToggle.setEnabled(false);
            }

            Service.setSummary("Now : " + prefs.getString("service", "not selected"));
            TestRun.setEnabled(prefs.getString("service", "").equals("send"));

            Service.setOnPreferenceChangeListener((preference, newValue) -> {
                ServiceToggle.setEnabled(!ifUIDBlank);
                preference.setSummary("Now : " + newValue.toString());

                boolean isSend = prefs.getString("service", "").equals("send");
                TestRun.setEnabled(!isSend);
                return true;
            });

            if (Build.VERSION.SDK_INT >= 26) {
                SetImportance.setSummary("Now : " + prefs.getString("importance", ""));
                SetImportance.setOnPreferenceChangeListener(((preference, newValue) -> {
                    SetImportance.setSummary("Now : " + newValue);
                    return true;
                }));
            } else {
                SetImportance.setEnabled(false);
                SetImportance.setSummary("Not for Android N or lower.");
            }

            boolean isSendIconEnabled = prefs.getBoolean("SendIcon", false);
            IconResolution.setVisible(isSendIconEnabled);
            IconWarning.setVisible(isSendIconEnabled);
            IconResolution.setSummary("Now : " + prefs.getString("IconRes","52 x 52 (Default)"));
            IconEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                IconResolution.setVisible((boolean) newValue);
                IconWarning.setVisible((boolean) newValue);
                return true;
            });
            IconResolution.setOnPreferenceChangeListener(((preference, newValue) -> {
                IconResolution.setSummary("Now : " + newValue);
                return true;
            }));

            int dataLimit = prefs.getInt("DataLimit",4096);
            DataLimit.setSummary("Now : " + dataLimit + " Bytes" + (dataLimit == 4096 ? " (Default)" : ""));

            int historyLimit = prefs.getInt("HistoryLimit",150);
            HistoryLimit.setSummary("Now : " + historyLimit + " pcs" + (historyLimit == 150 ? " (Default)" : ""));

            boolean isWhiteList = prefs.getBoolean("UseWhite",false);
            Blacklist.setTitle("Edit " + (isWhiteList ? "whitelist" : "blacklist"));
            Blacklist.setSummary("select apps that you " + (isWhiteList ? "want" : "won't") + " send notification");
            UseWhiteList.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isWhite = (boolean)newValue;
                Blacklist.setTitle("Edit " + (isWhite ? "whitelist" : "blacklist"));
                Blacklist.setSummary("select apps that you " + (isWhite ? "want" : "won't") + " send notification");
                return true;
            });

            UseReceiveCall.setVisible(BuildConfig.DEBUG);

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
                    startActivity(new Intent(mContext, AppinfoActiity.class));
                    break;

                case "blacklist":
                    startActivity(new Intent(mContext, BlacklistActivity.class));
                    break;

                case "Login":
                    accountTask();
                    break;

                case "service":
                    String UID = prefs.getString("UID", "");
                    if (!UID.equals(""))
                        FirebaseMessaging.getInstance().subscribeToTopic(UID);
                    break;

                case "testNoti":
                    Notify.create(mContext)
                            .setTitle("test")
                            .setContent("messageTest")
                            .setLargeIcon(R.drawable.ic_launcher_foreground)
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

                case "debugInfo":
                    CheckBoxPreference DebugMod = (CheckBoxPreference)DebugLogEnable;
                    if (DebugMod.isChecked() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (mContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                                || mContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                        }
                    }
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
                    editText.setText(String.valueOf(prefs.getInt("DataLimit",4096)));

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
                        if(value.equals(""))  {
                            Toast.makeText(mContext,"Please Input Value",Toast.LENGTH_SHORT).show();
                        }
                        else {
                            int IntValue = Integer.parseInt(value);
                            if (IntValue < 1) {
                                Toast.makeText(mContext, "Value must be higher than 0", Toast.LENGTH_SHORT).show();
                            } else if(IntValue > 32786) {
                                Toast.makeText(mContext, "Value must be lower than 32786", Toast.LENGTH_SHORT).show();
                            } else {
                                prefs.edit().putInt("DataLimit", IntValue).apply();
                                DataLimit.setSummary("Now : " + IntValue + " Bytes");
                            }
                        }
                    });
                    dialog.setNeutralButton("Reset Default", (d,w) -> {
                        prefs.edit().putInt("DataLimit", 4096).apply();
                        DataLimit.setSummary("Now : " + 4096 + " Bytes (Default)");
                    });
                    dialog.setNegativeButton("Cancel", (d,w) -> {});
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
                    editText.setText(String.valueOf(prefs.getInt("HistoryLimit",150)));

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
                        if(value.equals(""))  {
                            Toast.makeText(mContext,"Please Input Value",Toast.LENGTH_SHORT).show();
                        }
                        else {
                            int IntValue = Integer.parseInt(value);
                            if(IntValue > 65535) {
                                Toast.makeText(mContext, "Value must be lower than 65535", Toast.LENGTH_SHORT).show();
                            } else {
                                prefs.edit().putInt("HistoryLimit", IntValue).apply();
                                HistoryLimit.setSummary("Now : " + IntValue + " pcs");
                            }
                        }
                    });
                    dialog.setNeutralButton("Reset Default", (d,w) -> {
                        prefs.edit().putInt("HistoryLimit", 150).apply();
                        HistoryLimit.setSummary("Now : " + 150 + " pcs (Default)");
                    });
                    dialog.setNegativeButton("Cancel", (d,w) -> {});
                    dialog.show();
                    break;

                case "ResetList":
                    dialog = new AlertDialog.Builder(mContext);
                    dialog.setTitle("Waring!");
                    dialog.setMessage("Are you sure to reset your white/black list?\nThis operation cannot be undone.");
                    dialog.setPositiveButton("Reset",(d,w) -> {
                        mContext.getSharedPreferences("Whitelist",MODE_PRIVATE).edit().clear().apply();
                        mContext.getSharedPreferences("Blacklist",MODE_PRIVATE).edit().clear().apply();
                        Toast.makeText(mContext,"Task done!",Toast.LENGTH_SHORT).show();
                    });
                    dialog.setNegativeButton("Cancel",(d,w) -> {});
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
                    
                case "UseReceiveCall":
                    SwitchPreference UsePhone = (SwitchPreference) UseReceiveCall;
                    if (UsePhone.isChecked() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (mContext.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
                                || mContext.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG, Manifest.permission.ANSWER_PHONE_CALLS}, 3);
                        }
                    }
                    break;
            }
            return super.onPreferenceTreeClick(preference);
        }

        private Notify.NotificationImportance getImportance() {
            String value = prefs.getString("importance","Default");
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
                    Snackbar.make(mContext.findViewById(R.id.setting_activity), "Check Internet and Try Again", Snackbar.LENGTH_SHORT)
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
                    switch (requestCode) {
                        case 1:
                            Toast.makeText(mContext, "require storage permission!", Toast.LENGTH_SHORT).show();
                            ((CheckBoxPreference)DebugLogEnable).setChecked(false);
                            break;

                        case 2:
                            Toast.makeText(mContext, "require sms permission!", Toast.LENGTH_SHORT).show();
                            ((SwitchPreference)UseReplySms).setChecked(false);
                            break;

                        case 3:
                            Toast.makeText(mContext, "require call permission!", Toast.LENGTH_SHORT).show();
                            ((SwitchPreference)UseReceiveCall).setChecked(false);
                            break;
                    }
                    return;
                }
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (requestCode == RC_SIGN_IN && result != null && result.isSuccess()) {
                GoogleSignInAccount account = result.getSignInAccount();
                assert account != null;
                firebaseAuthWithGoogle(account);
            }
        }

        private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
            AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
            mAuth.signInWithCredential(credential)
                    .addOnCompleteListener(mContext, task -> {
                        if (!task.isSuccessful()) {
                            Toast.makeText(mContext, "failed to login Google", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(mContext, "Success to login Google", Toast.LENGTH_SHORT).show();
                            prefs.edit().putString("UID", mAuth.getUid()).apply();
                            prefs.edit().putString("Email", mAuth.getCurrentUser().getEmail()).apply();
                            recreate();
                        }
                    });
        }

        private void recreate() {
            FragmentActivity activity = getActivity();
            if(activity != null) activity.getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment(mContext))
                    .commit();
        }
    }
}