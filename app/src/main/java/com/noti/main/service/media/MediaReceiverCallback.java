package com.noti.main.service.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.session.MediaController;
import android.media.session.PlaybackState;

import com.noti.main.Application;

class MediaReceiverCallback extends MediaController.Callback {

    private volatile PlaybackState lastState;
    private final MediaReceiverPlayer player;
    private final MediaReceiver plugin;
    private final SharedPreferences prefs;

    MediaReceiverCallback(Context context, MediaReceiver plugin, MediaReceiverPlayer player) {
        this.player = player;
        this.plugin = plugin;
        prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void onPlaybackStateChanged(PlaybackState state) {
        if(prefs.getBoolean("UseMediaSync", false)) {
            if(lastState != null && lastState.equals(state)) {
                return;
            }

            plugin.sendMetadata(player);
            lastState = state;
        }
    }
}