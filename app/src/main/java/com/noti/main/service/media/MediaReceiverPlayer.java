package com.noti.main.service.media;

import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;

import androidx.annotation.Nullable;

class MediaReceiverPlayer {

    private final MediaController controller;
    private final String name;

    MediaReceiverPlayer(MediaController controller, String name) {
        this.controller = controller;
        this.name = name;
    }

    boolean isPlaying() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        return state.getState() == PlaybackState.STATE_PLAYING;
    }

    boolean canPlay() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        if (state.getState() == PlaybackState.STATE_PLAYING) return true;

        return (state.getActions() & (PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_PAUSE)) != 0;
    }

    boolean canPause() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        if (state.getState() == PlaybackState.STATE_PAUSED) return true;

        return (state.getActions() & (PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE)) != 0;
    }

    boolean canGoPrevious() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        return (state.getActions() & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0;
    }

    boolean canGoNext() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        return (state.getActions() & PlaybackState.ACTION_SKIP_TO_NEXT) != 0;
    }

    boolean canSeek() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return false;

        return (state.getActions() & PlaybackState.ACTION_SEEK_TO) != 0;
    }

    void playPause() {
        if (isPlaying()) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
    }

    String getName() {
        return name;
    }

    String getAlbum() {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) return "";

        return defaultString(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM));
    }

    @Nullable
    Bitmap getAlbumArt() {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) return null;

        return metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
    }

    String getAlbumArtUri() {
        MediaMetadata metadata = controller.getMetadata();
        if(metadata == null) return "";

        return metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
    }

    String getArtist() {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) return "";

        return defaultString(firstNonEmpty(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR),
                metadata.getString(MediaMetadata.METADATA_KEY_WRITER)));
    }

    String getTitle() {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) return "";

        return defaultString(firstNonEmpty(metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)));
    }

    void previous() {
        controller.getTransportControls().skipToPrevious();
    }

    void next() {
        controller.getTransportControls().skipToNext();
    }

    void play() {
        controller.getTransportControls().play();
    }

    void pause() {
        controller.getTransportControls().pause();
    }

    void stop() {
        controller.getTransportControls().stop();
    }

    int getVolume() {
        MediaController.PlaybackInfo info = controller.getPlaybackInfo();
        if (info == null) return 0;
        if (info.getMaxVolume() == 0) return 0;
        return 100 * info.getCurrentVolume() / info.getMaxVolume();
    }

    void setVolume(int volume) {
        MediaController.PlaybackInfo info = controller.getPlaybackInfo();
        if (info == null) return;
        double unRoundedVolume = info.getMaxVolume() * volume / 100.0 + 0.5;
        controller.setVolumeTo((int) unRoundedVolume, 0);
    }

    long getPosition() {
        PlaybackState state = controller.getPlaybackState();
        if (state == null) return 0;

        return state.getPosition();
    }

    void setPosition(long position) {
        controller.getTransportControls().seekTo(position);
    }

    long getLength() {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata == null) return 0;

        return metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
    }

    String defaultString(Object str) {
        return str instanceof String ? (String) str : "";
    }

    String firstNonEmpty(String... strings) {
        for(String item : strings) {
            if(item != null && !item.isEmpty()) return item;
        }
        return "";
    }
}