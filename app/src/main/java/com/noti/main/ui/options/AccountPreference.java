package com.noti.main.ui.options;

import static android.content.Context.MODE_PRIVATE;

import static com.noti.main.ui.SettingsActivity.ServiceToggle;
import static com.noti.main.ui.SettingsActivity.mBillingHelper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.messaging.FirebaseMessaging;
import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.ui.OptionActivity;
import com.noti.main.ui.SettingsActivity;
import com.noti.main.utils.BillingHelper;
import com.noti.main.utils.ui.ToastHelper;

import me.pushy.sdk.Pushy;

public class AccountPreference extends PreferenceFragmentCompat {

    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    SharedPreferences prefs;
    Activity mContext;

    ActivityResultLauncher<Intent> startAccountTask = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getData() != null) {
            GoogleSignInResult loginResult = Auth.GoogleSignInApi.getSignInResultFromIntent(result.getData());
            if (loginResult != null && loginResult.isSuccess()) {
                Log.d("Google log in", "Result : " + (loginResult.isSuccess() ? "success" : "failed") + " Status : " + loginResult.getStatus());
                GoogleSignInAccount account = loginResult.getSignInAccount();
                assert account != null;
                firebaseAuthWithGoogle(account);
            }
        }
    });

    Preference Login;
    Preference Service;
    Preference Server;
    Preference Subscribe;
    Preference AlreadySubscribed;
    Preference ServerInfo;
    Preference TestRun;

    public AccountPreference() {
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) mContext = (Activity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @SuppressLint("HardwareIds")
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.account_preferences, rootKey);
        SettingsActivity.onPurchasedListener = purchaseId -> {
            switch (purchaseId) {
                case BillingHelper.SubscribeID:
                    ToastHelper.show(mContext, "Thanks for purchase!", "OK", ToastHelper.LENGTH_SHORT);
                    Subscribe.setVisible(false);
                    AlreadySubscribed.setVisible(true);
                    break;

                case BillingHelper.DonateID:
                    MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                    dialog.setTitle("Thank you for your donation!");
                    dialog.setMessage("This donation will be used to improve Noti Sender!");
                    dialog.setIcon(R.drawable.ic_fluent_gift_24_regular);
                    dialog.setCancelable(false);
                    dialog.setPositiveButton("Close", (dialogInterface, i) -> {
                    });
                    dialog.show();
                    break;
            }
        };

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(mContext, gso);
        mAuth = FirebaseAuth.getInstance();
        prefs = mContext.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);

        Login = findPreference("Login");
        TestRun = findPreference("testNoti");
        Service = findPreference("service");
        Server = findPreference("server");
        Subscribe = findPreference("Subscribe");
        AlreadySubscribed = findPreference("AlreadySubscribed");
        ServerInfo = findPreference("ServerInfo");

        boolean ifUIDBlank = prefs.getString("UID", "").equals("");
        if (!ifUIDBlank) {
            Login.setSummary("Logined as " + prefs.getString("Email", ""));
            Login.setTitle(R.string.Logout);
            if (prefs.getString("Email", "").equals("") && mAuth.getCurrentUser() != null)
                prefs.edit().putString("Email", mAuth.getCurrentUser().getEmail()).apply();
            if (prefs.getString("server", "Firebase Cloud Message").equals("Pushy")) {
                if (mBillingHelper.isSubscribed()) {
                    ServiceToggle.setEnabled(true);
                } else {
                    ServiceToggle.setChecked(false);
                    ServiceToggle.setEnabled(false);
                }
            } else {
                ServiceToggle.setEnabled(true);
            }
        } else {
            ServiceToggle.setChecked(false);
            ServiceToggle.setEnabled(false);
        }

        if (mBillingHelper.isSubscribed()) {
            Subscribe.setVisible(false);
            AlreadySubscribed.setVisible(true);
        } else {
            Subscribe.setVisible(true);
            AlreadySubscribed.setVisible(false);
        }

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
                    ServiceToggle.setEnabled(false);
                    ServiceToggle.setChecked(false);
                }
            } else {
                ServiceToggle.setEnabled(!prefs.getString("UID", "").equals(""));
            }
            return true;
        });
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        MaterialAlertDialogBuilder dialog;

        switch (preference.getKey()) {
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
                mBillingHelper.Subscribe(mContext);
                break;

            case "ServerInfo":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setTitle("Server details");
                dialog.setMessage(getString(R.string.Server_information));
                dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                dialog.setPositiveButton("Close", (d, w) -> {
                });
                dialog.show();
                break;

            case "PremiumInfo":
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
                dialog.setTitle("Premium info");
                dialog.setMessage(getString(R.string.Premium_information));
                dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                dialog.setPositiveButton("Close", (d, w) -> {
                });
                dialog.show();
                break;

            case "Donation":
                mBillingHelper.Donate(mContext);
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void accountTask() {
        if (prefs.getString("UID", "").equals("")) {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
                Intent signInIntent = mGoogleSignInClient.getSignInIntent();
                startAccountTask.launch(signInIntent);
            } else {
                ToastHelper.show(mContext, "Check Internet and Try Again", "DISMISS", ToastHelper.LENGTH_SHORT);
            }
        } else {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
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

    private void recreate() {
        if (Application.isTablet()) {
            Fragment fragment = new AccountPreference();
            Bundle bundle = new Bundle(0);
            fragment.setArguments(bundle);

            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_frame, fragment)
                    .commit();
        } else {
            FragmentActivity activity = getActivity();
            if (activity != null) {
                OptionActivity.attachFragment(activity, new AccountPreference());
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(mContext, task -> {
                    if (!task.isSuccessful()) {
                        ToastHelper.show(mContext, "failed to login Google", "DISMISS", ToastHelper.LENGTH_SHORT);
                    } else if (mAuth.getCurrentUser() != null) {
                        ToastHelper.show(mContext, "Success to login Google", "DISMISS", ToastHelper.LENGTH_SHORT);
                        prefs.edit().putString("UID", mAuth.getUid()).apply();
                        prefs.edit().putString("Email", mAuth.getCurrentUser().getEmail()).apply();
                        SettingsActivity.getAPIKeyFromCloud(mContext);
                        recreate();
                    }
                });
    }
}