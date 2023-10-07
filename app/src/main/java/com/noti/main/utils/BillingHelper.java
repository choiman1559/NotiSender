package com.noti.main.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.ImmutableList;

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.R;

import com.vojtkovszky.billinghelper.BillingEvent;
import com.vojtkovszky.billinghelper.BillingListener;

import java.util.Objects;

@SuppressLint("StaticFieldLeak")
public class BillingHelper implements BillingListener {

    public static BillingHelper instance;
    public static final String SubscribeID = "sub_pro";
    public static final String DonateID = "donate_3";
    private static boolean isNeedToConsume = false;

    private Context mContext;
    private BillingCallback mBillingCallback;
    private com.vojtkovszky.billinghelper.BillingHelper mBillingHelper;
    private String lastPurchasedItem = "";

    @Override
    public void onBillingEvent(@NonNull BillingEvent billingEvent, @Nullable String s, @Nullable Integer integer) {
        if (billingEvent == BillingEvent.PURCHASE_COMPLETE) {
            if(!lastPurchasedItem.isEmpty()) {
                instance.mBillingCallback.onPurchased(instance.lastPurchasedItem);
                if(instance.lastPurchasedItem.equals(DonateID)) {
                    isNeedToConsume = true;
                    instance.mBillingHelper.endClientConnection();
                    instance = initialize(mContext);
                }
                lastPurchasedItem = "";
            }
        } else if(billingEvent == BillingEvent.QUERY_OWNED_PURCHASES_COMPLETE && isNeedToConsume) {
            instance.mBillingHelper.consumePurchase(Objects.requireNonNull(instance.mBillingHelper.getPurchaseWithProductName(DonateID)));
            isNeedToConsume = false;
        }
    }

    public interface BillingCallback {
        void onPurchased(String productId);
    }

    public static BillingHelper getInstance() throws IllegalStateException {
        return getInstance(true);
    }

    @Nullable
    public static BillingHelper getInstance(boolean needThrow) throws IllegalStateException {
        if (instance != null && !instance.mBillingHelper.isConnectionFailure()) return instance;
        else if (needThrow) throw new IllegalStateException("BillingHelper is not initialized");
        else return null;
    }

    public static BillingHelper initialize(Context mContext) {
        BillingHelper billingHelper = new BillingHelper();
        String APIKey = mContext.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE).getString("ApiKey_Billing", "");
        if (APIKey.isEmpty()) throw new IllegalStateException("IAP API key is required");

        billingHelper.mBillingHelper = new com.vojtkovszky.billinghelper.BillingHelper(
                mContext,
                ImmutableList.of(DonateID),
                ImmutableList.of(SubscribeID),
                true, APIKey, true, true, true,
                BuildConfig.DEBUG, billingHelper
        );

        billingHelper.mContext = mContext;
        instance = billingHelper;
        return billingHelper;
    }

    public void setBillingCallback(BillingCallback callback) {
        instance.mBillingCallback = callback;
    }

    public void Subscribe(Activity mContext) {
        instance.lastPurchasedItem = SubscribeID;
        instance.mBillingHelper.launchPurchaseFlow(mContext, SubscribeID, null, null, null, 0);
    }

    public boolean isSubscribed() {
        return instance.mBillingHelper.isPurchased(SubscribeID);
    }

    public boolean isSubscribedOrDebugBuild() {
        return isSubscribed() || BuildConfig.DEBUG;
    }

    public static void showSubscribeInfoDialog(Activity mContext, String cause) {
        showSubscribeInfoDialog(mContext, cause, true, (d, w) -> {
        });
    }

    public static void showSubscribeInfoDialog(Activity mContext, String cause, boolean isCancelable, android.content.DialogInterface.OnClickListener okButtonListener) {
        MaterialAlertDialogBuilder dialog;
        dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
        dialog.setTitle("Subscription Information");
        dialog.setMessage(cause + mContext.getString(R.string.Premium_waring));
        dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
        dialog.setPositiveButton("Close", okButtonListener);
        dialog.setCancelable(isCancelable);
        dialog.show();
    }

    public void Donate(Activity mContext) {
        instance.lastPurchasedItem = DonateID;
        instance.mBillingHelper.launchPurchaseFlow(mContext, DonateID, null, null, null, 0);
    }

    public void Destroy() {
        instance.mBillingHelper.endClientConnection();
        instance.mBillingCallback = null;
    }
}
