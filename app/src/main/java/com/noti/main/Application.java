package com.noti.main;

import com.kieronquinn.monetcompat.core.MonetCompat;
import com.noti.main.service.pair.PairDeviceInfo;

import java.util.ArrayList;

public class Application extends android.app.Application {
    public static boolean isFindingDeviceToPair = false;
    public static boolean isListeningToPair = false;
    public static ArrayList<PairDeviceInfo> pairingProcessList;

    public static final String PREFS_NAME = "com.noti.main_preferences";
    public static boolean isTablet = false;

    @Override
    public void onCreate() {
        super.onCreate();
        MonetCompat.enablePaletteCompat();
        pairingProcessList = new ArrayList<>();
        isTablet = getResources().getBoolean(R.bool.is_tablet);
    }
}
