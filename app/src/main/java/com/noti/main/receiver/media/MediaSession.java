package com.noti.main.receiver.media;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;

import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.StartActivity;
import com.noti.main.service.NotiListenerService;
import com.noti.main.service.backend.PacketConst;
import com.noti.main.service.backend.ResultPacket;
import com.noti.main.utils.network.AESCrypto;
import com.noti.main.utils.network.CompressStringUtil;
import com.noti.main.utils.PowerUtils;
import com.noti.main.utils.network.JsonRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class MediaSession {
    private static final int NOTIFICATION_ID = 0xbdd2e171;
    private static final String NOTIFICATION_CHANNEL_ID = "MediaNotification";
    public final static String MEDIA_CONTROL = "media_control";

    private final SharedPreferences prefs;
    private final String UID;
    private static String deviceId = "";
    private static String deviceName = "";

    volatile MediaPlayer notificationPlayer;
    JSONObject lastFetchedData = new JSONObject();
    android.media.session.MediaSession mediaSession;
    Context context;

    private final android.media.session.MediaSession.Callback mediaSessionCallback = new android.media.session.MediaSession.Callback() {

        @Override
        public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
            PowerUtils.getInstance(context).acquire();
            return super.onMediaButtonEvent(mediaButtonIntent);
        }

        @Override
        public void onPlay() {
            if(prefs.getBoolean("UseMediaSync", false)) notificationPlayer.play();
        }

        @Override
        public void onPause() {
            if(prefs.getBoolean("UseMediaSync", false)) notificationPlayer.pause();
        }

        @Override
        public void onSkipToNext() {
            if(prefs.getBoolean("UseMediaSync", false)) notificationPlayer.next();
        }

        @Override
        public void onSkipToPrevious() {
            if(prefs.getBoolean("UseMediaSync", false)) notificationPlayer.previous();
        }

        @Override
        public void onStop() {
            if(prefs.getBoolean("UseMediaSync", false)) notificationPlayer.stop();
        }

        @Override
        public void onSeekTo(long pos) {
            if(prefs.getBoolean("UseMediaSync", false)) notificationPlayer.setPosition((int) pos);
        }
    };

    public MediaSession(Context context, String device_name, String device_id, String userID) {
        this.context = context;
        this.notificationPlayer = new MediaPlayer(context, device_name, device_id);

        prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
        deviceId = device_id;
        deviceName = device_name;
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

    public void updateBitmap(JSONObject npd) throws JSONException, NoSuchAlgorithmException {
        PowerUtils.getInstance(context).acquire();
        if (lastFetchedData.equals(npd)) return;
        DefaultValueJSONObject np = new DefaultValueJSONObject(npd);

        if (np.has("albumArtHash")) {
            String albumArtHash = np.getString("albumArtHash");
            String finalUniqueId = AESCrypto.shaAndHex(deviceId + albumArtHash);

            JSONObject serverBody = new JSONObject();
            serverBody.put(PacketConst.KEY_ACTION_TYPE, PacketConst.REQUEST_GET_SHORT_TERM_DATA);
            serverBody.put(PacketConst.KEY_DATA_KEY, finalUniqueId);
            serverBody.put(PacketConst.KEY_UID, UID);

            serverBody.put(PacketConst.KEY_DEVICE_ID, NotiListenerService.getUniqueID());
            serverBody.put(PacketConst.KEY_DEVICE_NAME, NotiListenerService.getDeviceName());

            serverBody.put(PacketConst.KEY_SEND_DEVICE_ID, deviceId);
            serverBody.put(PacketConst.KEY_SEND_DEVICE_NAME, deviceName);

            final String URI = PacketConst.getApiAddress(PacketConst.SERVICE_TYPE_IMAGE_CACHE);
            final String contentType = "application/json";

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URI, serverBody, response -> {
                try {
                    ResultPacket resultPacket = ResultPacket.parseFrom(response.toString());
                    if(resultPacket.isResultOk()) {
                        System.out.println(resultPacket.getExtraData().length());
                        Bitmap albumArt = CompressStringUtil.getBitmapFromString(resultPacket.getExtraData());
                        if (albumArt != null && notificationPlayer != null) {
                            notificationPlayer.albumArt = albumArt;
                            ContextCompat.getMainExecutor(context).execute(this::publishMediaNotification);
                        }
                    }  else {
                        throw new IOException(resultPacket.getErrorCause());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, error -> { }) {
                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> params = new HashMap<>();
                    params.put("Content-Type", contentType);
                    return params;
                }
            };

            JsonRequest.getInstance(context).addToRequestQueue(jsonObjectRequest);
        } else if(np.has("albumArtBytes")) {
            Bitmap albumArtRaw = CompressStringUtil.getBitmapFromString(CompressStringUtil.decompressString(np.getString("albumArtBytes")));
            Bitmap albumArt = null;
            if (albumArtRaw != null) {
                albumArt = Bitmap.createBitmap(albumArtRaw.getWidth(), albumArtRaw.getHeight(), albumArtRaw.getConfig());
                Canvas canvas = new Canvas(albumArt);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(albumArtRaw, 0, 0, null);
            }

            if (albumArt != null && notificationPlayer != null) {
                if(notificationPlayer.albumArt != null) notificationPlayer.albumArt.recycle();
                notificationPlayer.albumArt = albumArt;
            }
            publishMediaNotification();
        }
    }

    public void update(JSONObject npd) throws JSONException, NoSuchAlgorithmException {
        if (lastFetchedData.equals(npd)) return;
        DefaultValueJSONObject np = new DefaultValueJSONObject(npd);

        if(np.has("albumArtHash") || np.has("albumArtBytes")) {
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
                if(np.getBoolean("isAlbumArtSent", false)) playerStatus.albumArt = null;
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
        NotificationCompat.Action aPlay = new NotificationCompat.Action.Builder(
                R.drawable.ic_play_white, context.getString(R.string.media_play), piPlay).build();

        Intent iPause = new Intent(context, MediaBroadcastReceiver.class);
        iPause.setAction(MediaBroadcastReceiver.ACTION_PAUSE);
        iPause.putExtra(MediaBroadcastReceiver.EXTRA_DEVICE_ID, deviceId);
        PendingIntent piPause = PendingIntent.getBroadcast(context, 0, iPause, getIntentFlag());
        NotificationCompat.Action aPause = new NotificationCompat.Action.Builder(
                R.drawable.ic_pause_white, context.getString(R.string.media_pause), piPause).build();

        Intent iPrevious = new Intent(context, MediaBroadcastReceiver.class);
        iPrevious.setAction(MediaBroadcastReceiver.ACTION_PREVIOUS);
        iPrevious.putExtra(MediaBroadcastReceiver.EXTRA_DEVICE_ID, deviceId);
        PendingIntent piPrevious = PendingIntent.getBroadcast(context, 0, iPrevious, getIntentFlag());
        NotificationCompat.Action aPrevious = new NotificationCompat.Action.Builder(
                R.drawable.ic_previous_white, context.getString(R.string.media_previous), piPrevious).build();

        Intent iNext = new Intent(context, MediaBroadcastReceiver.class);
        iNext.setAction(MediaBroadcastReceiver.ACTION_NEXT);
        iNext.putExtra(MediaBroadcastReceiver.EXTRA_DEVICE_ID, deviceId);
        PendingIntent piNext = PendingIntent.getBroadcast(context, 0, iNext, getIntentFlag());
        NotificationCompat.Action aNext = new NotificationCompat.Action.Builder(
                R.drawable.ic_next_white, context.getString(R.string.media_next), piNext).build();

        Intent iClose = new Intent(context, MediaBroadcastReceiver.class);
        iClose.setAction(MediaBroadcastReceiver.ACTION_CLOSE_NOTIFICATION);
        iClose.putExtra(MediaBroadcastReceiver.EXTRA_DEVICE_ID, deviceId);
        PendingIntent piClose = PendingIntent.getBroadcast(context, 0, iClose, getIntentFlag());
        NotificationCompat.Action aClose = new NotificationCompat.Action.Builder(
                R.drawable.ic_close_white, context.getString(R.string.media_close), piClose).build();

        Intent iOpenActivity = new Intent(context, StartActivity.class);
        PendingIntent piOpenActivity = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(iOpenActivity)
                .getPendingIntent(0, getIntentFlag());

        NotificationCompat.Builder notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new NotificationCompat.Builder(context, MEDIA_CONTROL);
        } else {
            notification = new NotificationCompat.Builder(context);
        }

        notification
                .setAutoCancel(false)
                .setContentIntent(piOpenActivity)
                .setSmallIcon(R.mipmap.ic_notification)
                .setShowWhen(false)
                .setColor(context.getColor(R.color.primary))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
            notification.addAction(aPrevious);
            playbackActions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            ++numActions;
        }
        if (notificationPlayer.isPlaying() && notificationPlayer.isPauseAllowed()) {
            notification.addAction(aPause);
            playbackActions |= PlaybackState.ACTION_PAUSE;
            ++numActions;
        }
        if (!notificationPlayer.isPlaying() && notificationPlayer.isPlayAllowed()) {
            notification.addAction(aPlay);
            playbackActions |= PlaybackState.ACTION_PLAY;
            ++numActions;
        }
        if (notificationPlayer.isGoNextAllowed()) {
            notification.addAction(aNext);
            playbackActions |= PlaybackState.ACTION_SKIP_TO_NEXT;
            ++numActions;
        }

        notification.addAction(aClose);
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

        androidx.media.app.NotificationCompat.MediaStyle mediaStyle = new androidx.media.app.NotificationCompat.MediaStyle();
        if (numActions == 1) {
            mediaStyle.setShowActionsInCompactView(1);
        } else if (numActions == 2) {
            mediaStyle.setShowActionsInCompactView(1, 2);
        } else {
            mediaStyle.setShowActionsInCompactView(1, 2, 3);
        }

        mediaStyle.setMediaSession(MediaSessionCompat.Token.fromToken(mediaSession.getSessionToken()));
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
        return PendingIntent.FLAG_IMMUTABLE;
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
