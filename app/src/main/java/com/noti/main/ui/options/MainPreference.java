package com.noti.main.ui.options;

import static android.content.Context.MODE_PRIVATE;

import static com.noti.main.SettingsActivity.mBillingHelper;

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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import com.kieronquinn.monetcompat.core.MonetCompat;

import com.noti.main.R;
import com.noti.main.SettingsActivity;
import com.noti.main.ui.AppInfoActivity;
import com.noti.main.ui.OptionActivity;
import com.noti.main.ui.prefs.HistoryActivity;
import com.noti.main.utils.AsyncTask;
import com.noti.main.utils.BillingHelper;

import java.util.Date;

import me.pushy.sdk.Pushy;

public class MainPreference extends PreferenceFragmentCompat {

    private final int RC_SIGN_IN = 100;
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private MonetCompat monet = null;
    SharedPreferences prefs;
    FirebaseFirestore mFirebaseFirestore;
    Activity mContext;

    //General Category
    Preference Login;
    Preference Service;
    Preference ServiceToggle;
    Preference Server;
    Preference Subscribe;
    Preference ServerInfo;

    //Other Category
    Preference ForWearOS;
    Preference TestRun;

    public MainPreference() { }

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
                        new MaterialAlertDialogBuilder(mContext)
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
        ForWearOS = findPreference("forWear");

        mBillingHelper = BillingHelper.initialize(mContext, new BillingHelper.BillingCallback() {
            @Override
            public void onPurchased(String productId) {
                switch(productId) {
                    case BillingHelper.SubscribeID:
                        Toast.makeText(mContext, "Thanks for purchase!", Toast.LENGTH_SHORT).show();
                        ServiceToggle.setEnabled(!prefs.getString("UID", "").equals(""));
                        ServiceToggle.setSummary("");
                        Subscribe.setVisible(false);
                        new RegisterForPushNotificationsAsync().execute();
                        break;

                    case BillingHelper.DonateID:
                        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.MaterialAlertDialog_Material3));
                        dialog.setTitle("Thank you for your donation!");
                        dialog.setMessage("This donation will be used to improve Noti Sender!");
                        dialog.setIcon(R.drawable.ic_fluent_gift_24_regular);
                        dialog.setCancelable(false);
                        dialog.setPositiveButton("Close", (dialogInterface, i) -> { });
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

        try {
            mContext.getPackageManager().getPackageInfo("com.google.android.wearable.app", 0);
        } catch (PackageManager.NameNotFoundException e) {
            ForWearOS.setVisible(false);
        }
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
        if (requestCode == RC_SIGN_IN && result != null && result.isSuccess()) {
            Log.d("Google log in", "Result : " + (result.isSuccess() ? "success" : "failed") + " Status : " + result.getStatus().toString());
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
        if(activity != null) {
            SettingsActivity.attachFragment(activity, new MainPreference());
        }
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