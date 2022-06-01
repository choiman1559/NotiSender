package com.noti.main.ui.options;

import static android.content.Context.MODE_PRIVATE;

import static com.noti.main.ui.SettingsActivity.ServiceToggle;
import static com.noti.main.ui.SettingsActivity.mBillingHelper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.application.isradeleon.notify.Notify;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.messaging.FirebaseMessaging;

import com.kieronquinn.monetcompat.core.MonetCompat;

import com.noti.main.R;
import com.noti.main.ui.SettingsActivity;
import com.noti.main.ui.AppInfoActivity;
import com.noti.main.ui.OptionActivity;
import com.noti.main.utils.ui.ToastHelper;
import com.noti.main.ui.pair.PairMainActivity;
import com.noti.main.ui.prefs.HistoryActivity;
import com.noti.main.utils.AsyncTask;
import com.noti.main.utils.BillingHelper;

import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import me.pushy.sdk.Pushy;

public class MainPreference extends PreferenceFragmentCompat {

    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private MonetCompat monet = null;
    SharedPreferences prefs;
    SharedPreferences logPrefs;
    FirebaseFirestore mFirebaseFirestore;
    Activity mContext;

    SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, k) -> {
        if (k.equals("serviceToggle")) {
            ServiceToggle.setChecked(prefs.getBoolean("serviceToggle", false));
        }
    };

    ActivityResultLauncher<Intent> startAccountTask = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if(result.getData() != null) {
            GoogleSignInResult loginResult = Auth.GoogleSignInApi.getSignInResultFromIntent(result.getData());
            if (loginResult != null && loginResult.isSuccess()) {
                Log.d("Google log in", "Result : " + (loginResult.isSuccess() ? "success" : "failed") + " Status : " + loginResult.getStatus());
                GoogleSignInAccount account = loginResult.getSignInAccount();
                assert account != null;
                firebaseAuthWithGoogle(account);
            }
        }
    });

    //General Category
    Preference Login;
    Preference Service;
    Preference Server;
    Preference Subscribe;
    Preference ServerInfo;

    //Other Category
    Preference ForWearOS;
    Preference TestRun;
    Preference FindPhone;
    Preference pairDevice;

    public MainPreference() { }

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

    @SuppressLint("HardwareIds")
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
        logPrefs = mContext.getSharedPreferences("com.noti.main_logs", MODE_PRIVATE);

        mFirebaseFirestore.collection("ApiKey")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            prefs.edit()
                                    .putString("Latest_Version_Play", document.getString("Version_Play"))
                                    .putString("ApiKey_FCM", document.getString("FCM"))
                                    .putString("ApiKey_Pushy", document.getString("Pushy"))
                                    .putString("ApiKey_Billing", document.getString("Billing"))
                                    .apply();
                        }
                    } else {
                        new MaterialAlertDialogBuilder(mContext)
                                .setTitle("Error occurred!")
                                .setMessage("Error occurred while initializing client token.\nplease check your internet connection and try again.")
                                .setPositiveButton("OK", (dialog, which) -> mContext.finishAndRemoveTask())
                                .setCancelable(false);
                    }
                });

        if (prefs.getString("FirebaseIIDPrefix", "").isEmpty()) {
            FirebaseInstallations.getInstance().getId().addOnCompleteListener(task -> {
                if (task.isSuccessful())
                    prefs.edit().putString("FirebaseIIDPrefix", task.getResult()).apply();
            });
        }

        if (prefs.getString("AndroidIDPrefix", "").isEmpty()) {
            prefs.edit().putString("AndroidIDPrefix", Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID)).apply();
        }

        if (prefs.getString("GUIDPrefix", "").isEmpty()) {
            prefs.edit().putString("GUIDPrefix", UUID.randomUUID().toString()).apply();
        }

        if (prefs.getString("MacIDPrefix", "").isEmpty()) {
            String interfaceName = "wlan0";
            try {
                List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface intf : interfaces) {
                    if (!intf.getName().equalsIgnoreCase(interfaceName)) continue;
                    byte[] mac = intf.getHardwareAddress();
                    if (mac == null) {
                        prefs.edit().putString("MacIDPrefix", "unknown").apply();
                        break;
                    }
                    StringBuilder buf = new StringBuilder();
                    for (byte b : mac) buf.append(String.format("%02X:", b));
                    if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
                    prefs.edit().putString("MacIDPrefix", buf.toString()).apply();
                    break;
                }
            } catch (Exception e) {
                prefs.edit().putString("MacIDPrefix", "unknown").apply();
            }
        }

        Login = findPreference("Login");
        TestRun = findPreference("testNoti");
        Service = findPreference("service");
        Server = findPreference("server");
        Subscribe = findPreference("Subscribe");
        ServerInfo = findPreference("ServerInfo");
        ForWearOS = findPreference("forWear");
        FindPhone = findPreference("findPhone");
        pairDevice = findPreference("pairDevice");

        mBillingHelper = BillingHelper.initialize(mContext, new BillingHelper.BillingCallback() {
            @Override
            public void onPurchased(String productId) {
                switch (productId) {
                    case BillingHelper.SubscribeID:
                        ToastHelper.show(mContext, "Thanks for purchase!", "OK", ToastHelper.LENGTH_SHORT);
                        ServiceToggle.setEnabled(!prefs.getString("UID", "").equals(""));
                        Subscribe.setVisible(false);
                        new RegisterForPushNotificationsAsync().execute();
                        break;

                    case BillingHelper.DonateID:
                        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                        dialog.setTitle("Thank you for your donation!");
                        dialog.setMessage("This donation will be used to improve Noti Sender!");
                        dialog.setIcon(R.drawable.ic_fluent_gift_24_regular);
                        dialog.setCancelable(false);
                        dialog.setPositiveButton("Close", (dialogInterface, i) -> {
                        });
                        dialog.show();
                        break;
                }
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
                    ServiceToggle.setChecked(false);
                }
            } else {
                Subscribe.setVisible(false);
                ServiceToggle.setEnabled(true);
            }
        } else {
            ServiceToggle.setEnabled(false);
            ServiceToggle.setChecked(false);
            Subscribe.setVisible(prefs.getString("server", "Firebase Cloud Message").equals("Pushy") && !mBillingHelper.isSubscribed());
        }

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

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
                    ServiceToggle.setChecked(false);
                }
            } else {
                ServiceToggle.setEnabled(!prefs.getString("UID", "").equals(""));
                Subscribe.setVisible(false);
            }
            return true;
        });

        ServiceToggle.setChecked(prefs.getBoolean("serviceToggle", false));
        ServiceToggle.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("serviceToggle", isChecked).apply());

        try {
            mContext.getPackageManager().getPackageInfo("com.google.android.wearable.app", 0);
        } catch (PackageManager.NameNotFoundException e) {
            ForWearOS.setVisible(false);
        }

        migrationHistory();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        MaterialAlertDialogBuilder dialog;

        switch (preference.getKey()) {
            case "AppInfo":
                startActivity(new Intent(mContext, AppInfoActivity.class));
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
                dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                dialog.setTitle("Server details");
                dialog.setMessage(getString(R.string.Server_information));
                dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                dialog.setPositiveButton("Close", (d, w) -> {
                });
                dialog.show();
                break;

            case "testNoti":
                Notify.build(mContext)
                        .setTitle("test (" + (int) ((new Date().getTime() / 1000L) % Integer.MAX_VALUE) + ")")
                        .setContent("messageTest")
                        .setLargeIcon(R.mipmap.ic_launcher)
                        .largeCircularIcon()
                        .setSmallIcon(R.drawable.ic_broken_image)
                        .setChannelName("Testing Channel")
                        .setChannelId("Notification Test")
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

            case "SendOption":
                startOptionsActivity("Send");
                break;

            case "ReceptionOption":
                startOptionsActivity("Reception");
                break;

            case "OtherOption":
                startOptionsActivity("Other");
                break;

            case "Donation":
                mBillingHelper.Donate();
                break;

            case "pairDevice":
                if(ServiceToggle.isEnabled()) {
                    startActivity(new Intent(mContext, PairMainActivity.class));
                } else ToastHelper.show(mContext, "Please check your account status!","Dismiss", ToastHelper.LENGTH_SHORT);
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void startOptionsActivity(String type) {
        startActivity(new Intent(mContext, OptionActivity.class).putExtra("Type", type));
    }

    private Notify.NotifyImportance getImportance() {
        String value = prefs.getString("importance", "Default");
        switch (value) {
            case "Default":
                return Notify.NotifyImportance.MAX;
            case "Low":
                return Notify.NotifyImportance.LOW;
            case "High":
                return Notify.NotifyImportance.HIGH;
            default:
                return Notify.NotifyImportance.MIN;
        }
    }

    private void migrationHistory() {
        String sendLogs = prefs.getString("sendLogs", "");
        String receivedLogs = prefs.getString("receivedLogs", "");
        SharedPreferences.Editor logEdit = logPrefs.edit();
        boolean isNeedToApply = false;

        if(!sendLogs.isEmpty()) {
            logEdit.putString("sendLogs", sendLogs);
            isNeedToApply = true;
        }
        if(!receivedLogs.isEmpty()) {
            logEdit.putString("receivedLogs", receivedLogs);
            isNeedToApply = true;
        }

        if(isNeedToApply) {
            prefs.edit().remove("sendLogs").remove("receivedLogs").apply();
            logEdit.apply();
        }
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
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
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

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(mContext, task -> {
                    if (!task.isSuccessful()) {
                        ToastHelper.show(mContext, "failed to login Google", "DISMISS",ToastHelper.LENGTH_SHORT);
                    } else if (mAuth.getCurrentUser() != null) {
                        ToastHelper.show(mContext, "Success to login Google", "DISMISS",ToastHelper.LENGTH_SHORT);
                        prefs.edit().putString("UID", mAuth.getUid()).apply();
                        prefs.edit().putString("Email", mAuth.getCurrentUser().getEmail()).apply();
                        recreate();
                    }
                });
    }

    private void recreate() {
        FragmentActivity activity = getActivity();
        if(activity != null) {
            SettingsActivity.attachFragment(activity, new MainPreference());
        }
    }

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