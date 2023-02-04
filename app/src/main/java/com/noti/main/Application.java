package com.noti.main;

import android.content.res.Configuration;
import android.util.Log;

import com.google.firebase.messaging.RemoteMessage;
import com.kieronquinn.monetcompat.core.MonetCompat;
import com.noti.main.service.pair.PairDeviceInfo;
import com.noti.main.service.pair.PairDeviceType;

import java.util.ArrayList;

public class Application extends android.app.Application {
    private static Application applicationInstance;

    public static boolean isFindingDeviceToPair = false;
    public static boolean isListeningToPair = false;
    public static ArrayList<PairDeviceInfo> pairingProcessList;

    public static final String PREFS_NAME = "com.noti.main_preferences";
    public static PairDeviceType thisDeviceType;

    public static boolean isTablet() {
        int screenSize = (applicationInstance.getApplicationContext().getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK);
        return screenSize == Configuration.SCREENLAYOUT_SIZE_LARGE || screenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        MonetCompat.enablePaletteCompat();
        pairingProcessList = new ArrayList<>();
        applicationInstance = this;
        thisDeviceType = PairDeviceType.getThisDeviceType(applicationInstance.getApplicationContext());
    }
}
