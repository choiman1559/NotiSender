package com.noti.main;

import com.kieronquinn.monetcompat.core.MonetCompat;

public class Application extends android.app.Application {
    public static boolean isFindingDeviceToPair = false;
    public static boolean isListeningToPair = false;

    @Override
    public void onCreate() {
        super.onCreate();
        MonetCompat.enablePaletteCompat();
    }
}
