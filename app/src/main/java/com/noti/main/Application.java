package com.noti.main;

import android.content.Context;
import android.content.res.Configuration;

import com.kieronquinn.monetcompat.core.MonetCompat;
import com.noti.main.service.pair.PairDeviceInfo;
import com.noti.main.service.pair.PairDeviceType;
import com.noti.main.utils.BillingHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class Application extends android.app.Application {
    private static Application applicationInstance;
    public static boolean isFindingDeviceToPair = false;
    public static boolean isListeningToPair = false;
    public static ArrayList<PairDeviceInfo> pairingProcessList;

    public static final String PREFS_NAME = "com.noti.main_preferences";
    public static PairDeviceType thisDeviceType;
    public static boolean needRebootToChangeType = false;

    public static boolean isTablet() {
        int screenSize = (applicationInstance.getApplicationContext().getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK);
        return screenSize == Configuration.SCREENLAYOUT_SIZE_LARGE || screenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    public static Application getApplicationInstance() {
        return applicationInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        MonetCompat.enablePaletteCompat();
        if(BillingHelper.getInstance(false) == null
                && !getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE).getString("ApiKey_Billing", "").isEmpty()) {
            BillingHelper.initialize(this);
        }

        pairingProcessList = new ArrayList<>();
        applicationInstance = this;
        thisDeviceType = PairDeviceType.getThisDeviceType(applicationInstance.getApplicationContext());
    }

    public static String getDateString() {
        Date date = Calendar.getInstance().getTime();
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        BillingHelper.getInstance().Destroy();
    }
}
