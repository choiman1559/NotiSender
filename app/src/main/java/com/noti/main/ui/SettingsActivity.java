package com.noti.main.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

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

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.R;
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

    public static BillingHelper mBillingHelper;
    public static MonetSwitch ServiceToggle;

    public static onPurchasedListener onPurchasedListener;

    public interface onPurchasedListener {
        void onPurchased(String purchaseId);
    }

    private String lastSelectedItem;
    private static String lastSelectedItemStatic;

    private ImageView AccountIcon;
    private FirebaseAuth mAuth;
    private MonetCompat monet = null;
    private SharedPreferences prefs;
    private MaterialCardView selectedCardView;

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

        if(BillingHelper.getInstance(false) == null) {
            mBillingHelper = BillingHelper.initialize(this);
        } else mBillingHelper = BillingHelper.getInstance();

        mBillingHelper.setBillingCallback(new BillingHelper.BillingCallback() {
            @Override
            public void onPurchased(String productId) {
                if (productId.equals(BillingHelper.SubscribeID)) {
                    ServiceToggle.setEnabled(!prefs.getString("UID", "").equals(""));
                    new RegisterForPushNotificationsAsync(SettingsActivity.this).execute();
                }

                if (SettingsActivity.onPurchasedListener != null) {
                    SettingsActivity.onPurchasedListener.onPurchased(productId);
                }
            }

            @Override
            public void onUpdatePrice(Double priceValue) {

            }
        });

        MaterialCardView PairPreferences = findViewById(R.id.PairPreferences);
        MaterialCardView AccountPreferences = findViewById(R.id.AccountPreferences);
        MaterialCardView SendPreferences = findViewById(R.id.SendPreferences);
        MaterialCardView ReceptionPreferences = findViewById(R.id.ReceptionPreferences);
        MaterialCardView OtherPreferences = findViewById(R.id.OtherPreferences);
        MaterialCardView HistoryPreferences = findViewById(R.id.HistoryPreferences);
        MaterialCardView InfoPreferences = findViewById(R.id.InfoPreferences);

        AccountIcon = findViewById(R.id.AccountIcon);
        if (Application.isTablet()) {
            HolderFragment fragment = (HolderFragment) getSupportFragmentManager().findFragmentById(R.id.content_frame);
            if (fragment == null) {
                fragment = new HolderFragment();
            }

            MaterialCardView lastSelectedCardView;
            if (getLastSelectedItem() != null) {
                switch (getLastSelectedItem()) {
                    case "PairMain":
                        lastSelectedCardView = PairPreferences;
                        break;

                    case "Send":
                        lastSelectedCardView = SendPreferences;
                        break;

                    case "Reception":
                        lastSelectedCardView = ReceptionPreferences;
                        break;

                    case "Other":
                        lastSelectedCardView = OtherPreferences;
                        break;

                    case "History":
                        lastSelectedCardView = HistoryPreferences;
                        break;

                    case "About":
                        lastSelectedCardView = InfoPreferences;
                        break;

                    case "Account":
                    default:
                        lastSelectedCardView = AccountPreferences;
                        break;
                }

                markSelectedMenu(lastSelectedCardView);
                fragment.setType(getLastSelectedItem());
            } else markSelectedMenu(AccountPreferences);

            Bundle bundle = new Bundle(0);
            fragment.setArguments(bundle);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_frame, fragment)
                    .commit();
        }

        AccountIcon = findViewById(R.id.AccountIcon);
        AccountIcon.setVisibility(View.GONE);
        mAuth = FirebaseAuth.getInstance();
        updateProfileImage();

        @SuppressLint("NonConstantResourceId")
        View.OnClickListener onClickListener = v -> {
            if (selectedCardView == null || selectedCardView.getId() != v.getId()) {
                String fragmentType = "";

                switch (v.getId()) {
                    case R.id.PairPreferences:
                        fragmentType = "PairMain";
                        break;

                    case R.id.AccountPreferences:
                        fragmentType = "Account";
                        break;

                    case R.id.SendPreferences:
                        fragmentType = "Send";
                        break;

                    case R.id.ReceptionPreferences:
                        fragmentType = "Reception";
                        break;

                    case R.id.OtherPreferences:
                        fragmentType = "Other";
                        break;

                    case R.id.HistoryPreferences:
                        fragmentType = "History";
                        break;

                    case R.id.InfoPreferences:
                        fragmentType = "About";
                        break;
                }

                if (Application.isTablet()) {
                    markSelectedMenu((MaterialCardView) v);
                    Bundle bundle = new Bundle(0);
                    setLastSelectedItem(fragmentType);

                    HolderFragment fragment = new HolderFragment();
                    fragment.setArguments(bundle);
                    fragment.setType(fragmentType);

                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.content_frame, fragment)
                            .commit();
                } else {
                    Intent intent = new Intent(this, OptionActivity.class);
                    intent.putExtra("Type", fragmentType);
                    startActivity(intent);
                }
            }
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
        ServiceToggle.setOnClickListener(v -> {
            if (!ServiceToggle.isEnabled()) {
                ToastHelper.show(this, "Not logined or service is not available", ToastHelper.LENGTH_SHORT);
            }
        });

        boolean isNightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        ServiceToggle.setTextColor(getResources().getColor(isNightMode ? R.color.ui_bg : R.color.ui_fg));

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

        if (mBillingHelper.isSubscribed()) {
            new RegisterForPushNotificationsAsync(this).execute();
        }
    }

    private String getLastSelectedItem() {
        return prefs.getBoolean("SaveLastSelectedItem", false) ? lastSelectedItemStatic : lastSelectedItem;
    }

    private void setLastSelectedItem(String itemValue) {
        lastSelectedItemStatic = itemValue;
        lastSelectedItem = itemValue;
    }

    void markSelectedMenu(MaterialCardView cardView) {
        boolean isNightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (Application.isTablet()) {
            if (selectedCardView == null) {
                selectedCardView = cardView;
                selectedCardView.setCardBackgroundColor(getResources().getColor(R.color.ui_menu_accent));

                if (isNightMode) {
                    ImageView icon = (ImageView) ((RelativeLayout) selectedCardView.getChildAt(0)).getChildAt(0);
                    icon.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.ui_bg)));

                    LinearLayout layout = (LinearLayout) ((RelativeLayout) selectedCardView.getChildAt(0)).getChildAt(1);
                    ((TextView) layout.getChildAt(0)).setTextColor(getResources().getColor(R.color.ui_bg));
                    ((TextView) layout.getChildAt(1)).setTextColor(getResources().getColor(R.color.ui_bg));
                }
            } else if (selectedCardView.getId() != cardView.getId()) {
                selectedCardView.setCardBackgroundColor(getResources().getColor(R.color.ui_bg));
                cardView.setCardBackgroundColor(getResources().getColor(R.color.ui_menu_accent));

                if (isNightMode) {
                    ((ImageView) ((RelativeLayout) selectedCardView.getChildAt(0)).getChildAt(0)).setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.ui_fg)));
                    ((ImageView) ((RelativeLayout) cardView.getChildAt(0)).getChildAt(0)).setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.ui_bg)));

                    LinearLayout layout1 = (LinearLayout) ((RelativeLayout) selectedCardView.getChildAt(0)).getChildAt(1);
                    ((TextView) layout1.getChildAt(0)).setTextColor(getResources().getColor(R.color.ui_fg));
                    ((TextView) layout1.getChildAt(1)).setTextColor(getResources().getColor(R.color.ui_fg));

                    LinearLayout layout2 = (LinearLayout) ((RelativeLayout) cardView.getChildAt(0)).getChildAt(1);
                    ((TextView) layout2.getChildAt(0)).setTextColor(getResources().getColor(R.color.ui_bg));
                    ((TextView) layout2.getChildAt(1)).setTextColor(getResources().getColor(R.color.ui_bg));
                }
                selectedCardView = cardView;
            }
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
                        if (imageData != null) {
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