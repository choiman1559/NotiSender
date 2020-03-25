package com.noti.sender;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
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

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Objects;
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

    public static class SettingsFragment extends PreferenceFragmentCompat implements GoogleApiClient.OnConnectionFailedListener {

        Activity mcontext;
        private static final int RC_SIGN_IN = 100;
        private static final int REQUEST_PACKAGE = 103;
        private FirebaseAuth mAuth;
        private GoogleApiClient mGoogleApiClient;
        SharedPreferences prefs;

        SettingsFragment(Activity mcontext) {
            this.mcontext = mcontext;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Log.d("debug", "init fregment");
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();

            mGoogleApiClient = new GoogleApiClient.Builder(mcontext)
                    .enableAutoManage((FragmentActivity) mcontext, this)
                    .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                    .build();
            mAuth = FirebaseAuth.getInstance();

            prefs = mcontext.getPreferences(MODE_PRIVATE);
            Preference login = findPreference("Login");
            Preference uid = findPreference("UID");

            if (!prefs.getString("UID", "").equals("")) {
                assert login != null;
                assert uid != null;

                uid.setSummary("UID : " + prefs.getString("UID", ""));
                login.setSummary("Logined");
                login.setTitle(R.string.Logout);
                findPreference("serviceToggle").setEnabled(true);
            } else {
                findPreference("serviceToggle").setEnabled(false);
            }
            findPreference("service").setSummary("Now : " +  mcontext.getSharedPreferences("com.noti.sender_preferences",MODE_PRIVATE).getString("service",""));
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
                if (preference.getKey().equals("AppInfo"))
                    startActivity(new Intent(mcontext, AppinfoActiity.class));
                if (preference.getKey().equals("blacklist"))
                    startActivity(new Intent(mcontext, BlacklistActivity.class));

            if (preference.getKey().equals("Login")) accountTask();

            if(preference.getKey().equals("service")) {
                if(mcontext.getSharedPreferences("com.noti.sender_preferences",MODE_PRIVATE).getString("service","").equals("send")) {
                    if(!mcontext.getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "").equals(""))
                        FirebaseMessaging.getInstance().subscribeToTopic(mcontext.getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "") + "_receive");
                }

                if(mcontext.getSharedPreferences("com.noti.sender_preferences",MODE_PRIVATE).getString("service","").equals("reception")) {
                    if(!mcontext.getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", "").equals(""))
                        FirebaseMessaging.getInstance().subscribeToTopic(mcontext.getSharedPreferences("SettingsActivity", MODE_PRIVATE).getString("UID", ""));
                }
                preference.setSummary("Now : " +  mcontext.getSharedPreferences("com.noti.sender_preferences",MODE_PRIVATE).getString("service",""));
            }

            return super.onPreferenceTreeClick(preference);
        }

        private void accountTask() {
            if (prefs.getString("UID", "").equals("")) {
                if(AppStatus.getInstance(mcontext).isOnline()) {
                    Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
                    startActivityForResult(signInIntent, RC_SIGN_IN);
                } else Snackbar.make(mcontext.findViewById(R.id.setting_activity), "Check Internet and Try Again", Snackbar.LENGTH_SHORT)
                        .setAction("Action", null).show();
            } else {
                mAuth.signOut();
                Auth.GoogleSignInApi.signOut(mGoogleApiClient);
                SharedPreferences.Editor edit = prefs.edit();

                edit.putString("UID", "");
                findPreference("serviceToggle").setEnabled(false);

                edit.apply();
                recreate();
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
                    .addOnCompleteListener(mcontext, task -> {
                        if (!task.isSuccessful()) {
                            Toast.makeText(mcontext, "failed to login Google", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(mcontext, "Success to login Google", Toast.LENGTH_SHORT).show();
                            prefs.edit().putString("UID", mAuth.getUid()).apply();
                            recreate();
                        }
                    });
        }

        private void recreate() {
            startActivity(new Intent(mcontext, SettingsActivity.class));
            Objects.requireNonNull(getActivity()).finish();
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Toast.makeText(mcontext,"Faild to login",Toast.LENGTH_SHORT).show();
        }
    }
}