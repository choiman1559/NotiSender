package com.noti.main.utils;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.PurchaseInfo;
import com.anjlab.android.iab.v3.SkuDetails;

import java.util.List;

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
        mBillingProcessor.getSubscriptionListingDetailsAsync(SubscribeID, new BillingProcessor.ISkuDetailsResponseListener() {
            @Override
            public void onSkuDetailsResponse(@Nullable List<SkuDetails> products) {
                if(products != null && products.size() > 0) {
                    SkuDetails Details = products.get(0);
                    if (mBillingCallback != null && Details != null) {
                        mBillingCallback.onUpdatePrice(Details.priceValue);
                    }
                    mBillingProcessor.loadOwnedPurchasesFromGoogleAsync(new BillingProcessor.IPurchasesResponseListener() {
                        @Override
                        public void onPurchasesSuccess() { }

                        @Override
                        public void onPurchasesError() { }
                    });
                }
            }

            @Override
            public void onSkuDetailsError(String error) { }
        });
    }

    @Override
    public void onProductPurchased(@NonNull String productId, @Nullable PurchaseInfo details) {
        if (mBillingCallback != null) {
            mBillingCallback.onPurchased(productId);
        }
        mBillingProcessor.loadOwnedPurchasesFromGoogleAsync(new BillingProcessor.IPurchasesResponseListener() {
            @Override
            public void onPurchasesSuccess() { }

            @Override
            public void onPurchasesError() { }
        });
    }

    @Override
    public void onPurchaseHistoryRestored() {
        mBillingProcessor.loadOwnedPurchasesFromGoogleAsync(new BillingProcessor.IPurchasesResponseListener() {
            @Override
            public void onPurchasesSuccess() { }

            @Override
            public void onPurchasesError() { }
        });
    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {

    }
}
