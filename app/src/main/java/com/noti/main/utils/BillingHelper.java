package com.noti.main.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.SkuDetails;
import com.anjlab.android.iab.v3.TransactionDetails;

public class BillingHelper implements BillingProcessor.IBillingHandler {

    private static final String SubscribeID = "pushy_subscribe";
    private BillingCallback mBillingCallback;
    private BillingProcessor mBillingProcessor;
    private Activity mContext;

    public interface BillingCallback {
        void onPurchased(String productId);
        void onUpdatePrice(Double priceValue);
    }

    public static BillingHelper initialize(Activity context, BillingCallback billingCallback) {
        String APIKey = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE).getString("ApiKey_Billing", "");
        BillingHelper billingHelper = new BillingHelper();
        billingHelper.mBillingCallback = billingCallback;
        billingHelper.mContext = context;
        billingHelper.mBillingProcessor = new BillingProcessor(context, APIKey, billingHelper);
        billingHelper.mBillingProcessor.initialize();
        return billingHelper;
    }

    public void handleActivityResult(int requestCode, int resultCode, Intent data) {
        mBillingProcessor.handleActivityResult(requestCode, resultCode, data);
    }

    public void Subscribe() {
        if (mBillingProcessor != null && mBillingProcessor.isInitialized()) {
            mBillingProcessor.subscribe(mContext, SubscribeID);
        }
    }

    public boolean isSubscribed() {
        return mBillingProcessor.isSubscribed(SubscribeID);
    }

    public void Destroy() {
        if (mBillingProcessor != null) {
            mBillingProcessor.release();
        }
    }

    @Override
    public void onBillingInitialized() {
        SkuDetails Details = mBillingProcessor.getSubscriptionListingDetails(SubscribeID);
        if (mBillingCallback != null && Details != null) {
            mBillingCallback.onUpdatePrice(Details.priceValue);
        }
        mBillingProcessor.loadOwnedPurchasesFromGoogle();
    }

    @Override
    public void onProductPurchased(@NonNull String productId, TransactionDetails details) {
        if (mBillingCallback != null) {
            mBillingCallback.onPurchased(productId);
        }
        mBillingProcessor.loadOwnedPurchasesFromGoogle();
    }

    @Override
    public void onPurchaseHistoryRestored() {
        mBillingProcessor.loadOwnedPurchasesFromGoogle();
    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {

    }
}
