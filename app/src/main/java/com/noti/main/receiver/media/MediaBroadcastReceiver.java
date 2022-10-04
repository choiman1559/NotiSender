package com.noti.main.receiver.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;
import android.util.Log;

import com.noti.main.service.FirebaseMessageService;

public class MediaBroadcastReceiver extends BroadcastReceiver {
    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "ACTION_NEXT";
    public static final String ACTION_CLOSE_NOTIFICATION = "ACTION_CLOSE_NOTIFICATION";
    public static final String EXTRA_DEVICE_ID = "device_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        com.noti.main.receiver.media.MediaSession mMediaSession = FirebaseMessageService.playingSessionMap.get(intent.getStringExtra(EXTRA_DEVICE_ID));
        if (mMediaSession == null) return;
        MediaSession mediaSession = mMediaSession.mediaSession;
        if (mediaSession == null) return;

        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            mediaSession.getController().dispatchMediaButtonEvent(intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT));
        } else {
            MediaPlayer player = mMediaSession.getPlayer();
            if (player == null) return;

            Log.d("intent", intent.getAction());

            switch (intent.getAction()) {
                case ACTION_PLAY:
                    player.play();
                    break;
                case ACTION_PAUSE:
                    player.pause();
                    break;
                case ACTION_PREVIOUS:
                    player.previous();
                    break;
                case ACTION_NEXT:
                    player.next();
                    break;
                case ACTION_CLOSE_NOTIFICATION:
                    player.stop();
                    mMediaSession.closeMediaNotification();
                    break;
            }
        }
    }
}
