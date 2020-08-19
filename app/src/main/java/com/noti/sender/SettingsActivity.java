package com.noti.sender;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

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
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

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
                .replace(R.id.settings, new SettingsFragment(SettingsActivity.this))
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

    public static class SettingsFragment extends PreferenceFragmentCompat  {

        Activity mContext;
        private static final int RC_SIGN_IN = 100;
        private FirebaseAuth mAuth;
        private GoogleSignInClient mGoogleSignInClient;
        SharedPreferences prefs;

        SettingsFragment(Activity mContext) {
            this.mContext = mContext;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();

            mGoogleSignInClient = GoogleSignIn.getClient(mContext,gso);
            mAuth = FirebaseAuth.getInstance();

            prefs = mContext.getSharedPreferences("com.noti.sender_preferences",MODE_PRIVATE);
            Preference login = findPreference("Login");

            if (!prefs.getString("UID", "").equals("")) {
                assert login != null;

                login.setSummary("Logined as " + prefs.getString("Email", ""));
                login.setTitle(R.string.Logout);
                findPreference("serviceToggle").setEnabled(true);
                if(prefs.getString("Email","").equals("")) prefs.edit().putString("Email",mAuth.getCurrentUser().getEmail()).apply();
            } else {
                findPreference("serviceToggle").setEnabled(false);
            }
            findPreference("service").setSummary("Now : " + prefs.getString("service", "not selected"));

            Boolean ifUIDBlank = prefs.getString("UID","").equals("");
            if(prefs.equals("") || ifUIDBlank) findPreference("serviceToggle").setEnabled(false);
            findPreference("testNoti").setEnabled(prefs.getString("service","").equals("send"));
            findPreference("service").setOnPreferenceChangeListener((preference, newValue) -> {
                findPreference("serviceToggle").setEnabled(!ifUIDBlank);
                preference.setSummary("Now : " + newValue.toString());
                findPreference("testNoti").setEnabled(!prefs.getString("service","").equals("send"));
                return true;
            });

            if(Build.VERSION.SDK_INT >= 26) {
                findPreference("importance").setSummary("Now : " + prefs.getString("importance", ""));
                findPreference("importance").setOnPreferenceChangeListener(((preference, newValue) -> {
                    findPreference("importance").setSummary("Now : " + newValue);
                    return true;
                }));
            } else {
                findPreference("importance").setEnabled(false);
                findPreference("importance").setSummary("Not for Android N or lower.");
            }

            Preference IconEnable = findPreference("SendIcon");
            findPreference("IconRes").setVisible(prefs.getBoolean("SendIcon",false));
            findPreference("IconWaring").setVisible(prefs.getBoolean("SendIcon",false));
            IconEnable.setOnPreferenceChangeListener((preference,newValue) -> {
                findPreference("IconRes").setVisible(!prefs.getBoolean("SendIcon",false));
                findPreference("IconWaring").setVisible(!prefs.getBoolean("SendIcon",false));
                return true;
            });
            findPreference("IconRes").setOnPreferenceChangeListener(((preference, newValue) -> {
                findPreference("IconRes").setSummary("Now : " + newValue);
                return true;
            }));

            try {
                mContext.getPackageManager().getPackageInfo("com.google.android.wearable.app", 0);
            } catch (PackageManager.NameNotFoundException e) {
                findPreference("forWear").setVisible(false);
            }
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            if (preference.getKey().equals("AppInfo"))
                startActivity(new Intent(mContext, AppinfoActiity.class));
            if (preference.getKey().equals("blacklist"))
                startActivity(new Intent(mContext, BlacklistActivity.class));

            if (preference.getKey().equals("Login")) accountTask();

            if (preference.getKey().equals("service") && !mContext.getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("UID", "").equals("")) {
                FirebaseMessaging.getInstance().subscribeToTopic(mContext.getSharedPreferences("com.noti.sender_preferences", MODE_PRIVATE).getString("UID", ""));
            }

            if(preference.getKey().equals("testNoti")) {
                Notify.create(mContext)
                        .setTitle("test")
                        .setContent("messageTest")
                        .setLargeIcon(R.drawable.ic_launcher_foreground)
                        .circleLargeIcon()
                        .setSmallIcon(R.drawable.ic_broken_image)
                        .setImportance(Notify.NotificationImportance.HIGH)
                        .enableVibration(true)
                        .setAutoCancel(true)
                        .show();
            }

            if(preference.getKey().equals("forWear")){
                startActivity(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("http://play.google.com/store/apps/details?id=com.noti.sender.wear")));
            }

            if (preference.getKey().equals("debugInfo")) {
                CheckBoxPreference cb = findPreference("debugInfo");
                if (cb.isChecked() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (mContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                            || mContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            Toast.makeText(mContext, "require storage permission!", Toast.LENGTH_SHORT).show();
                            cb.setChecked(false);
                        }
                        requestPermissions(new String[]
                                        {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                                2);
                    } else cb.setChecked(true);
                }
            }
            return super.onPreferenceTreeClick(preference);
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
                    mAuth.signOut();
                    mGoogleSignInClient.signOut();
                    SharedPreferences.Editor edit = prefs.edit();

                    edit.remove("UID");
                    edit.remove("Email");
                    findPreference("serviceToggle").setEnabled(false);

                    edit.apply();
                    recreate();
                });
               builder.setNegativeButton("No",((dialog, which) -> {}));
               builder.show();
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == RC_SIGN_IN) {
                GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
                if (result.isSuccess()) {
                    GoogleSignInAccount account = result.getSignInAccount();
                    assert account != null;
                    firebaseAuthWithGoogle(account);
                }
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
                            prefs.edit().putString("Email",mAuth.getCurrentUser().getEmail()).apply();
                            recreate();
                        }
                    });
        }

        private void recreate() {
            startActivity(new Intent(mContext, SettingsActivity.class));
            getActivity().finish();
        }
    }
}