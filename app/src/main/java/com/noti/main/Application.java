package com.noti.main;

import com.kieronquinn.monetcompat.core.MonetCompat;

public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MonetCompat.enablePaletteCompat();
    }
}
