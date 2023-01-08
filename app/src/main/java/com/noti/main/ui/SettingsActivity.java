package com.noti.main.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.installations.FirebaseInstallations;
import com.kieronquinn.monetcompat.app.MonetCompatActivity;
import com.kieronquinn.monetcompat.core.MonetCompat;
import com.kieronquinn.monetcompat.view.MonetSwitch;
import com.noti.main.BuildConfig;
import com.noti.main.R;
import com.noti.main.ui.pair.PairMainActivity;
import com.noti.main.ui.prefs.HistoryActivity;
import com.noti.main.utils.AsyncTask;
import com.noti.main.utils.BillingHelper;
import com.noti.main.utils.ui.ToastHelper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import me.pushy.sdk.Pushy;

public class SettingsActivity extends MonetCompatActivity {

    @SuppressLint("StaticFieldLeak")
    public static BillingHelper mBillingHelper;
    public static MonetSwitch ServiceToggle;

    private ImageView AccountIcon;
    private FirebaseAuth mAuth;
    private MonetCompat monet = null;
    private SharedPreferences prefs;

    SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, k) -> {
        if (k.equals("serviceToggle")) {
            ServiceToggle.setChecked(prefs.getBoolean("serviceToggle", false));
        } else if (k.equals("UID")) {
            updateProfileImage();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        MonetCompat.setup(context);
        monet = MonetCompat.getInstance();
        monet.updateMonetColors();
        return super.onCreateView(name, context, attrs);
    }

    @SuppressLint("HardwareIds")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);
        prefs = getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        MaterialCardView PairPreferences = findViewById(R.id.PairPreferences);
        MaterialCardView AccountPreferences = findViewById(R.id.AccountPreferences);
        MaterialCardView SendPreferences = findViewById(R.id.SendPreferences);
        MaterialCardView ReceptionPreferences = findViewById(R.id.ReceptionPreferences);
        MaterialCardView OtherPreferences = findViewById(R.id.OtherPreferences);
        MaterialCardView HistoryPreferences = findViewById(R.id.HistoryPreferences);
        MaterialCardView InfoPreferences = findViewById(R.id.InfoPreferences);

        AccountIcon = findViewById(R.id.AccountIcon);
        AccountIcon.setVisibility(View.GONE);
        mAuth = FirebaseAuth.getInstance();
        updateProfileImage();

        @SuppressLint("NonConstantResourceId")
        View.OnClickListener onClickListener = v -> {
            Intent intent = new Intent(this, OptionActivity.class);

            switch (v.getId()) {
                case R.id.PairPreferences:
                    intent = new Intent(this, PairMainActivity.class);
                    break;

                case R.id.AccountPreferences:
                    intent.putExtra("Type", "Account");
                    break;

                case R.id.SendPreferences:
                    intent.putExtra("Type", "Send");
                    break;

                case R.id.ReceptionPreferences:
                    intent.putExtra("Type", "Reception");
                    break;

                case R.id.OtherPreferences:
                    intent.putExtra("Type", "Other");
                    break;

                case R.id.HistoryPreferences:
                    intent = new Intent(this, HistoryActivity.class);
                    break;

                case R.id.InfoPreferences:
                    intent.putExtra("Type", "About");
                    break;

                default:
                    break;
            }

            startActivity(intent);
        };

        PairPreferences.setOnClickListener(onClickListener);
        AccountPreferences.setOnClickListener(onClickListener);
        SendPreferences.setOnClickListener(onClickListener);
        ReceptionPreferences.setOnClickListener(onClickListener);
        OtherPreferences.setOnClickListener(onClickListener);
        HistoryPreferences.setOnClickListener(onClickListener);
        InfoPreferences.setOnClickListener(onClickListener);

        ServiceToggle = findViewById(R.id.serviceToggle);
        ServiceToggle.setChecked(prefs.getBoolean("serviceToggle", false));
        ServiceToggle.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("serviceToggle", isChecked).apply());

        boolean isUIDBlank = prefs.getString("UID", "").equals("");
        if (isUIDBlank) {
            ServiceToggle.setEnabled(false);
            ServiceToggle.setChecked(false);
        } else {
            if (prefs.getString("server", "Firebase Cloud Message").equals("Pushy")) {
                if (mBillingHelper.isSubscribed()) {
                    ServiceToggle.setEnabled(true);
                } else {
                    ServiceToggle.setEnabled(false);
                    ServiceToggle.setChecked(false);
                }
            } else {
                ServiceToggle.setEnabled(true);
            }
        }

        if (prefs.getString("FirebaseIIDPrefix", "").isEmpty()) {
            FirebaseInstallations.getInstance().getId().addOnCompleteListener(task -> {
                if (task.isSuccessful())
                    prefs.edit().putString("FirebaseIIDPrefix", task.getResult()).apply();
            });
        }

        if (prefs.getString("AndroidIDPrefix", "").isEmpty()) {
            prefs.edit().putString("AndroidIDPrefix", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID)).apply();
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

        FirebaseFirestore mFirebaseFireStore = FirebaseFirestore.getInstance();
        mFirebaseFireStore.collection("ApiKey")
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
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Error occurred!")
                                .setMessage("Error occurred while initializing client token.\nplease check your internet connection and try again.")
                                .setPositiveButton("OK", (dialog, which) -> finishAndRemoveTask())
                                .setCancelable(false);
                    }
                });

        mBillingHelper = BillingHelper.initialize(this, new BillingHelper.BillingCallback() {
            @Override
            public void onPurchased(String productId) {
                switch (productId) {
                    case BillingHelper.SubscribeID:
                        ToastHelper.show(SettingsActivity.this, "Thanks for purchase!", "OK", ToastHelper.LENGTH_SHORT);
                        ServiceToggle.setEnabled(!prefs.getString("UID", "").equals(""));
                        //Subscribe.setVisible(false);
                        new RegisterForPushNotificationsAsync(SettingsActivity.this).execute();
                        break;

                    case BillingHelper.DonateID:
                        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(SettingsActivity.this, R.style.Theme_App_Palette_Dialog));
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
            new RegisterForPushNotificationsAsync(this).execute();
        }
    }

    private static class RegisterForPushNotificationsAsync extends AsyncTask<Void, Void, Void> {
        Context context;

        RegisterForPushNotificationsAsync(Context context) {
            this.context = context;
        }

        protected Void doInBackground(Void... params) {
            try {
                Pushy.register(context);
            } catch (Exception exc) {
                exc.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Pushy.listen(context);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        monet = null;
        mBillingHelper.Destroy();
    }

    private void updateProfileImage() {
        if (!prefs.getString("UID", "").isEmpty() && mAuth.getCurrentUser() != null) {
            Uri imageUri = mAuth.getCurrentUser().getPhotoUrl();
            if (imageUri != null) {
                new Thread(() -> {
                    Bitmap imageData = null;
                    try {
                        URL aURL = new URL(imageUri.toString());
                        URLConnection conn = aURL.openConnection();
                        conn.connect();
                        InputStream is = conn.getInputStream();
                        BufferedInputStream bis = new BufferedInputStream(is);
                        imageData = BitmapFactory.decodeStream(bis);

                        bis.close();
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if(imageData != null) {
                            Bitmap finalImageData = imageData;
                            runOnUiThread(() -> {
                                AccountIcon.setImageBitmap(finalImageData);
                                AccountIcon.setVisibility(View.VISIBLE);
                            });
                        }
                    }
                }).start();
            }
        } else {
            AccountIcon.setVisibility(View.GONE);
        }
    }
}