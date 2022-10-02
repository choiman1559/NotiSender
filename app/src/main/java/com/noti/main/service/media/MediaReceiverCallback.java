package com.noti.main.service.media;


import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;

import androidx.annotation.Nullable;

class MediaReceiverCallback extends MediaController.Callback {

    private final MediaReceiverPlayer player;
    private final MediaReceiver plugin;

    MediaReceiverCallback(MediaReceiver plugin, MediaReceiverPlayer player) {
        this.player = player;
        this.plugin = plugin;
    }

    @Override
    public void onPlaybackStateChanged(PlaybackState state) {
        plugin.sendMetadata(player);
    }

    @Override
    public void onMetadataChanged(@Nullable MediaMetadata metadata) {
        plugin.sendMetadata(player);
    }

    @Override
    public void onAudioInfoChanged(MediaController.PlaybackInfo info) {
        plugin.sendMetadata(player);
    }
}