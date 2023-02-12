package com.noti.main.utils;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.PurchaseInfo;
import com.anjlab.android.iab.v3.SkuDetails;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.R;

import java.util.List;

public class BillingHelper implements BillingProcessor.IBillingHandler {

    public static BillingHelper instance;
    public static final String SubscribeID = "sub_pro";
    public static final String DonateID = "donate_3";

    private BillingCallback mBillingCallback;
    private BillingProcessor mBillingProcessor;

    public interface BillingCallback {
        void onPurchased(String productId);

        void onUpdatePrice(Double priceValue);
    }

    public static BillingHelper getInstance() throws IllegalStateException {
        return getInstance(true);
    }

    @Nullable
    public static BillingHelper getInstance(boolean needThrow) throws IllegalStateException {
        if (instance != null) return instance;
        else if(needThrow) throw new IllegalStateException("BillingHelper is not initialized");
        else return null;
    }

    public static BillingHelper initialize(Context mContext) {
        String APIKey = mContext.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE).getString("ApiKey_Billing", "");
        BillingHelper billingHelper = new BillingHelper();
        billingHelper.mBillingProcessor = new BillingProcessor(mContext, APIKey, billingHelper);
        billingHelper.mBillingProcessor.initialize();

        instance = billingHelper;
        return billingHelper;
    }

    public void setBillingCallback(BillingCallback callback) {
        this.mBillingCallback = callback;
    }

    public void Subscribe(Activity mContext) {
        if (mBillingProcessor != null && mBillingProcessor.isInitialized()) {
            mBillingProcessor.subscribe(mContext, SubscribeID);
        }
    }

    public boolean isSubscribed() {
        return mBillingProcessor.isSubscribed(SubscribeID);
    }

    public boolean isSubscribedOrDebugBuild() {
        return  isSubscribed() || BuildConfig.DEBUG;
    }

    public static void showSubscribeInfoDialog(Activity mContext, String cause) {
        showSubscribeInfoDialog(mContext, cause, true,(d, w) -> {
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
        if (mBillingProcessor != null && mBillingProcessor.isInitialized()) {
            if (mBillingProcessor.isPurchased(DonateID)) {
                mBillingProcessor.consumePurchaseAsync(DonateID, new BillingProcessor.IPurchasesResponseListener() {
                    @Override
                    public void onPurchasesSuccess() {
                    }

                    @Override
                    public void onPurchasesError() {
                    }
                });
            } else {
                mBillingProcessor.purchase(mContext, DonateID);
            }
        }
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
                if (products != null && products.size() > 0) {
                    SkuDetails Details = products.get(0);
                    if (mBillingCallback != null && Details != null) {
                        mBillingCallback.onUpdatePrice(Details.priceValue);
                    }
                    mBillingProcessor.loadOwnedPurchasesFromGoogleAsync(new BillingProcessor.IPurchasesResponseListener() {
                        @Override
                        public void onPurchasesSuccess() {
                        }

                        @Override
                        public void onPurchasesError() {
                        }
                    });
                }
            }

            @Override
            public void onSkuDetailsError(String error) {
            }
        });
    }

    @Override
    public void onProductPurchased(@NonNull String productId, @Nullable PurchaseInfo details) {
        if (mBillingCallback != null) {
            mBillingCallback.onPurchased(productId);
        }
        mBillingProcessor.loadOwnedPurchasesFromGoogleAsync(new BillingProcessor.IPurchasesResponseListener() {
            @Override
            public void onPurchasesSuccess() {
            }

            @Override
            public void onPurchasesError() {
            }
        });
    }

    @Override
    public void onPurchaseHistoryRestored() {
        mBillingProcessor.loadOwnedPurchasesFromGoogleAsync(new BillingProcessor.IPurchasesResponseListener() {
            @Override
            public void onPurchasesSuccess() {
            }

            @Override
            public void onPurchasesError() {
            }
        });
    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {

    }
}
