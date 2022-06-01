package com.noti.main.service;

import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class QuickSettingService extends TileService {
    private Tile tile;
    private SharedPreferences prefs;

    SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (sharedPreferences, key) -> {
        if(key.equals("serviceToggle")) {
            if(prefs.getString("UID","").equals("")) tile.setState(Tile.STATE_UNAVAILABLE);
            else tile.setState(prefs.getBoolean("serviceToggle",false) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("com.noti.main_preferences",MODE_PRIVATE);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        tile = getQsTile();
        if(prefs.getString("UID","").equals("")) tile.setState(Tile.STATE_UNAVAILABLE);
        else tile.setState(prefs.getBoolean("serviceToggle",false) ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();

        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        tile = getQsTile();
        if(prefs.getString("UID","").equals("")) tile.setState(Tile.STATE_UNAVAILABLE);
    }

    @Override
    public void onClick() {
        super.onClick();
        int tileState = tile.getState();
        Log.d("Onclick",tileState + "");
        if(tileState != Tile.STATE_UNAVAILABLE) {
            prefs.edit().putBoolean("serviceToggle",tileState == Tile.STATE_INACTIVE).apply();
        }
    }
}
