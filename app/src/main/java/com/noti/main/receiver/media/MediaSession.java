package com.noti.main.receiver.media;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StreamDownloadTask;
import com.noti.main.R;
import com.noti.main.StartActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class MediaSession {
    private static final int NOTIFICATION_ID = 0xbdd2e171;
    private static final String NOTIFICATION_CHANNEL_ID = "MediaNotification";
    public final static String MEDIA_CONTROL = "media_control";
    public final FirebaseStorage storage;

    private final String UID;
    private static String deviceId = "";
    private static String deviceName = "";

    JSONObject lastFetchedData = new JSONObject();
    android.media.session.MediaSession mediaSession;
    MediaPlayer notificationPlayer;
    Context context;

    private final android.media.session.MediaSession.Callback mediaSessionCallback = new android.media.session.MediaSession.Callback() {
        @Override
        public void onPlay() {
            notificationPlayer.play();
        }

        @Override
        public void onPause() {
            notificationPlayer.pause();
        }

        @Override
        public void onSkipToNext() {
            notificationPlayer.next();
        }

        @Override
        public void onSkipToPrevious() {
            notificationPlayer.previous();
        }

        @Override
        public void onStop() {
            notificationPlayer.stop();
        }

        @Override
        public void onSeekTo(long pos) {
            Log.d("seek", pos + "");
            notificationPlayer.setPosition((int) pos);
        }
    };

    public MediaSession(Context context, String device_name, String device_id, String userID) {
        this.context = context;
        this.notificationPlayer = new MediaPlayer(context, device_name, device_id);

        deviceId = device_id;
        deviceName = device_name;
        storage = FirebaseStorage.getInstance();
        UID = userID;
        initMediaSession();
    }

    private void initMediaSession() {
        this.mediaSession = new android.media.session.MediaSession(context, deviceId);
        ContextCompat.getMainExecutor(context).execute(() -> mediaSession.setCallback(mediaSessionCallback));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mediaSession.setMediaButtonBroadcastReceiver(new ComponentName(context, MediaBroadcastReceiver.class));
        } else {
            mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(context, 0, new Intent(context, MediaBroadcastReceiver.class), getIntentFlag()));
        }
        mediaSession.setFlags(android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    public void updateBitmap(JSONObject npd) throws JSONException {
        if (lastFetchedData.equals(npd)) return;
        DefaultValueJSONObject np = new DefaultValueJSONObject(npd);

        if (np.has("albumArt")) {
            String albumArtHash = np.getString("albumArt");

            StorageReference storageRef = storage.getReferenceFromUrl("gs://notisender-41c1b.appspot.com");
            StorageReference albumArtRef = storageRef.child(UID + "/albumArt/" + albumArtHash + ".jpg");

            StreamDownloadTask task = albumArtRef.getStream();
            task.addOnSuccessListener(taskSnapshot -> new Thread(() -> {
                Bitmap albumArt = BitmapFactory.decodeStream(taskSnapshot.getStream());
                if (albumArt != null && notificationPlayer != null) {
                    notificationPlayer.albumArt = albumArt;
                    albumArt.recycle();
                }

                ContextCompat.getMainExecutor(context).execute(this::publishMediaNotification);
                albumArtRef.delete().addOnSuccessListener(unused -> { });
            }).start());
        }
    }

    public void update(JSONObject npd) throws JSONException {
        if (lastFetchedData.equals(npd)) return;
        DefaultValueJSONObject np = new DefaultValueJSONObject(npd);

        if(np.has("albumArt")) {
            updateBitmap(npd);
            return;
        }

        if (np.has("player")) {
            MediaPlayer playerStatus = notificationPlayer;
            if (playerStatus != null) {
                playerStatus.player = np.getString("player", playerStatus.player);
                playerStatus.currentSong = np.getString("nowPlaying", playerStatus.currentSong);
                playerStatus.title = np.getString("title", playerStatus.title);
                playerStatus.artist = np.getString("artist", playerStatus.artist);
                playerStatus.album = np.getString("album", playerStatus.album);
                if (np.has("loopStatus")) {
                    playerStatus.loopStatus = np.getString("loopStatus", playerStatus.loopStatus);
                    playerStatus.loopStatusAllowed = true;
                }
                if (np.has("shuffle")) {
                    playerStatus.shuffle = np.getBoolean("shuffle", playerStatus.shuffle);
                    playerStatus.shuffleAllowed = true;
                }
                playerStatus.volume = np.getInt("volume", playerStatus.volume);
                playerStatus.length = np.getLong("length", playerStatus.length);
                if (np.has("pos")) {
                    playerStatus.lastPosition = np.getLong("pos", playerStatus.lastPosition);
                    playerStatus.lastPositionTime = System.currentTimeMillis();
                }
                playerStatus.playing = np.getBoolean("isPlaying", playerStatus.playing);
                playerStatus.playAllowed = np.getBoolean("canPlay", playerStatus.playAllowed);
                playerStatus.pauseAllowed = np.getBoolean("canPause", playerStatus.pauseAllowed);
                playerStatus.goNextAllowed = np.getBoolean("canGoNext", playerStatus.goNextAllowed);
                playerStatus.goPreviousAllowed = np.getBoolean("canGoPrevious", playerStatus.goPreviousAllowed);
                playerStatus.seekAllowed = np.getBoolean("canSeek", playerStatus.seekAllowed);
                if(np.getBoolean("sendAlbumArt", false)) {
                    playerStatus.albumArt = null;
                }
            } else if (np.getBoolean("isPlaying", false)) {
                notificationPlayer = new MediaPlayer(context, deviceName, deviceId);
                update(npd);
            }
            publishMediaNotification();
        }
        lastFetchedData = npd;
    }

    static class DefaultValueJSONObject extends JSONObject {
        public DefaultValueJSONObject(JSONObject raw) throws JSONException {
            super(raw.toString());
        }

        @NonNull
        public String getString(@NonNull String name, @NonNull String defaultValue) {
            String value = null;
            try {
                value = super.getString(name);
            } catch (JSONException e) {
                //ignore
            }
            return value == null ? defaultValue : value;
        }

        @NonNull
        public Integer getInt(@NonNull String name, @NonNull Integer defaultValue) {
            Integer value = null;
            try {
                value = super.getInt(name);
            } catch (JSONException e) {
                //ignore
            }
            return value == null ? defaultValue : value;
        }

        @NonNull
        public Long getLong(@NonNull String name, @NonNull Long defaultValue) {
            Long value = null;
            try {
                value = super.getLong(name);
            } catch (JSONException e) {
                //ignore
            }
            return value == null ? defaultValue : value;
        }

        @NonNull
        public Boolean getBoolean(@NonNull String name, @NonNull Boolean defaultValue) {
            Boolean value = null;
            try {
                value = super.getBoolean(name);
            } catch (JSONException e) {
                //ignore
            }
            return value == null ? defaultValue : value;
        }
    }

    public MediaPlayer getPlayer() {
        return notificationPlayer;
    }

    public void publishMediaNotification() {
        if (notificationPlayer == null) {
            closeMediaNotification();
            return;
        }

        if (mediaSession == null) {
            initMediaSession();
        }

        MediaMetadata.Builder metadata = new MediaMetadata.Builder();

        if (!notificationPlayer.getTitle().isEmpty()) {
            metadata.putString(MediaMetadata.METADATA_KEY_TITLE, notificationPlayer.getTitle());
        } else {
            metadata.putString(MediaMetadata.METADATA_KEY_TITLE, notificationPlayer.getCurrentSong());
        }
        if (!notificationPlayer.getArtist().isEmpty()) {
            metadata.putString(MediaMetadata.METADATA_KEY_AUTHOR, notificationPlayer.getArtist());
            metadata.putString(MediaMetadata.METADATA_KEY_ARTIST, notificationPlayer.getArtist());
        }
        if (!notificationPlayer.getAlbum().isEmpty()) {
            metadata.putString(MediaMetadata.METADATA_KEY_ALBUM, notificationPlayer.getAlbum());
        }
        if (notificationPlayer.getLength() > 0) {
            metadata.putLong(MediaMetadata.METADATA_KEY_DURATION, notificationPlayer.getLength());
        }

        Bitmap albumArt = notificationPlayer.getAlbumArt();
        if (albumArt != null) {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt);
        }

        mediaSession.setMetadata(metadata.build());
        PlaybackState.Builder playbackState = new PlaybackState.Builder();

        if (notificationPlayer.isPlaying()) {
            playbackState.setState(PlaybackState.STATE_PLAYING, notificationPlayer.getPosition(), 1.0f);
        } else {
            playbackState.setState(PlaybackState.STATE_PAUSED, notificationPlayer.getPosition(), 0.0f);
        }

        Intent iPlay = new Intent(context, MediaBroadcastReceiver.class);
        iPlay.setAction(MediaBroadcastReceiver.ACTION_PLAY);
        iPlay.putExtra(MediaBroadcastReceiver.EXTRA_DEVICE_ID, deviceId);
        PendingIntent piPlay = PendingIntent.getBroadcast(context, 0, iPlay, getIntentFlag());
        Notification.Action.Builder aPlay = new Notification.Action.Builder(
                R.drawable.ic_play_white, context.getString(R.string.media_play), piPlay);

        Intent iPause = new Intent(context, MediaBroadcastReceiver.class);
        iPause.setAction(MediaBroadcastReceiver.ACTION_PAUSE);
        iPause.putExtra(MediaBroadcastReceiver.EXTRA_DEVICE_ID, deviceId);
        PendingIntent piPause = PendingIntent.getBroadcast(context, 0, iPause, getIntentFlag());
        Notification.Action.Builder aPause = new Notification.Action.Builder(
                R.drawable.ic_pause_white, context.getString(R.string.media_pause), piPause);

        Intent iPrevious = new Intent(context, MediaBroadcastReceiver.class);
        iPrevious.setAction(MediaBroadcastReceiver.ACTION_PREVIOUS);
        iPrevious.putExtra(MediaBroadcastReceiver.EXTRA_DEVICE_ID, deviceId);
        PendingIntent piPrevious = PendingIntent.getBroadcast(context, 0, iPrevious, getIntentFlag());
        Notification.Action.Builder aPrevious = new Notification.Action.Builder(
                R.drawable.ic_previous_white, context.getString(R.string.media_previous), piPrevious);

        Intent iNext = new Intent(context, MediaBroadcastReceiver.class);
        iNext.setAction(MediaBroadcastReceiver.ACTION_NEXT);
        iNext.putExtra(MediaBroadcastReceiver.EXTRA_DEVICE_ID, deviceId);
        PendingIntent piNext = PendingIntent.getBroadcast(context, 0, iNext, getIntentFlag());
        Notification.Action.Builder aNext = new Notification.Action.Builder(
                R.drawable.ic_next_white, context.getString(R.string.media_next), piNext);

        Intent iClose = new Intent(context, MediaBroadcastReceiver.class);
        iClose.setAction(MediaBroadcastReceiver.ACTION_CLOSE_NOTIFICATION);
        iClose.putExtra(MediaBroadcastReceiver.EXTRA_DEVICE_ID, deviceId);
        PendingIntent piClose = PendingIntent.getBroadcast(context, 0, iClose, getIntentFlag());
        Notification.Action.Builder aClose = new Notification.Action.Builder(
                R.drawable.ic_close_white, context.getString(R.string.media_close), piClose);

        Intent iOpenActivity = new Intent(context, StartActivity.class);
        PendingIntent piOpenActivity = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(iOpenActivity)
                .getPendingIntent(0, getIntentFlag());

        Notification.Builder notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(context, MEDIA_CONTROL);
        } else {
            notification = new Notification.Builder(context);
        }

        notification
                .setAutoCancel(false)
                .setContentIntent(piOpenActivity)
                .setSmallIcon(R.mipmap.ic_notification)
                .setShowWhen(false)
                .setColor(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? context.getColor(R.color.primary) : context.getResources().getColor(R.color.primary))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setSubText(deviceName);

        if (!notificationPlayer.getTitle().isEmpty()) {
            notification.setContentTitle(notificationPlayer.getTitle());
        } else {
            notification.setContentTitle(notificationPlayer.getCurrentSong());
        }

        if (!notificationPlayer.getArtist().isEmpty() && !notificationPlayer.getAlbum().isEmpty()) {
            notification.setContentText(notificationPlayer.getArtist() + " - " + notificationPlayer.getAlbum() + " (" + notificationPlayer.getPlayer() + ")");
        } else if (!notificationPlayer.getArtist().isEmpty()) {
            notification.setContentText(notificationPlayer.getArtist() + " (" + notificationPlayer.getPlayer() + ")");
        } else if (!notificationPlayer.getAlbum().isEmpty()) {
            notification.setContentText(notificationPlayer.getAlbum() + " (" + notificationPlayer.getPlayer() + ")");
        } else {
            notification.setContentText(notificationPlayer.getPlayer());
        }

        if (albumArt != null) {
            notification.setLargeIcon(albumArt);
        }

        if (!notificationPlayer.isPlaying()) {
            Intent iCloseNotification = new Intent(context, MediaBroadcastReceiver.class);
            iCloseNotification.setAction(MediaBroadcastReceiver.ACTION_CLOSE_NOTIFICATION);
            iCloseNotification.putExtra(MediaBroadcastReceiver.EXTRA_DEVICE_ID, deviceId);
            PendingIntent piCloseNotification = PendingIntent.getBroadcast(context, 0, iCloseNotification, getIntentFlag());
            notification.setDeleteIntent(piCloseNotification);
        }

        int numActions = 0;
        long playbackActions = 0;
        if (notificationPlayer.isGoPreviousAllowed()) {
            notification.addAction(aPrevious.build());
            playbackActions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            ++numActions;
        }
        if (notificationPlayer.isPlaying() && notificationPlayer.isPauseAllowed()) {
            notification.addAction(aPause.build());
            playbackActions |= PlaybackState.ACTION_PAUSE;
            ++numActions;
        }
        if (!notificationPlayer.isPlaying() && notificationPlayer.isPlayAllowed()) {
            notification.addAction(aPlay.build());
            playbackActions |= PlaybackState.ACTION_PLAY;
            ++numActions;
        }
        if (notificationPlayer.isGoNextAllowed()) {
            notification.addAction(aNext.build());
            playbackActions |= PlaybackState.ACTION_SKIP_TO_NEXT;
            ++numActions;
        }

        notification.addAction(aClose.build());
        playbackActions |= PlaybackState.ACTION_STOP;
        ++numActions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (notificationPlayer.isSeekAllowed()) {
                playbackActions |= PlaybackState.ACTION_SEEK_TO;
            }
        }

        playbackState.setActions(playbackActions);
        mediaSession.setPlaybackState(playbackState.build());
        notification.setOngoing(notificationPlayer.isPlaying());

        Notification.MediaStyle mediaStyle = new Notification.MediaStyle();
        if (numActions == 1) {
            mediaStyle.setShowActionsInCompactView(1);
        } else if (numActions == 2) {
            mediaStyle.setShowActionsInCompactView(1, 2);
        } else {
            mediaStyle.setShowActionsInCompactView(1, 2, 3);
        }

        mediaStyle.setMediaSession(mediaSession.getSessionToken());
        notification.setStyle(mediaStyle);
        notification.setGroup("MediaSession");
        mediaSession.setActive(true);

        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.setChannelId(NOTIFICATION_CHANNEL_ID);
            NotificationChannel mChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "MediaSession", NotificationManager.IMPORTANCE_DEFAULT);
            mChannel.setDescription("Control media from other devices remotely.");
            nm.createNotificationChannel(mChannel);
        }

        nm.notify(NOTIFICATION_ID, notification.build());
    }

    private int getIntentFlag() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
    }

    public void closeMediaNotification() {
        final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);

        notificationPlayer = null;
        if (mediaSession != null) {
            mediaSession.setPlaybackState(new PlaybackState.Builder().build());
            mediaSession.setMetadata(new MediaMetadata.Builder().build());
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
    }
}
