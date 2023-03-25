package com.noti.main.receiver.plugin;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;

import com.noti.main.utils.BillingHelper;

@VisibleForTesting
public class IAPTestActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BillingHelper mBillingHelper;

        if (BillingHelper.getInstance(false) == null) {
            mBillingHelper = BillingHelper.initialize(this);
        } else mBillingHelper = BillingHelper.getInstance();

        mBillingHelper.setBillingCallback(new BillingHelper.BillingCallback() {
            @Override
            public void onPurchased(String productId) {
                finish();
            }

            @Override
            public void onUpdatePrice(Double priceValue) {

            }
        });

        mBillingHelper.Donate(this);
    }
}
