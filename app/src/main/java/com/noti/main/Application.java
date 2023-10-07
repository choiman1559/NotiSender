package com.noti.main;

import android.content.res.Configuration;

import com.kieronquinn.monetcompat.core.MonetCompat;
import com.noti.main.service.pair.PairDeviceInfo;
import com.noti.main.service.pair.PairDeviceType;
import com.noti.main.utils.BillingHelper;

import java.util.ArrayList;

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
        if(BillingHelper.getInstance(false) == null) BillingHelper.initialize(this);
        pairingProcessList = new ArrayList<>();
        applicationInstance = this;
        thisDeviceType = PairDeviceType.getThisDeviceType(applicationInstance.getApplicationContext());
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        BillingHelper.getInstance().Destroy();
    }
}
