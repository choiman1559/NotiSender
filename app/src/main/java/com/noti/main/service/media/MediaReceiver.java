package com.noti.main.service.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.noti.main.service.NotiListenerService;
import com.noti.main.utils.CompressStringUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

public class MediaReceiver {
    private MediaSessionChangeListener mediaSessionChangeListener;
    private final HashMap<String, MediaReceiverPlayer> players;

    private final SharedPreferences prefs;
    private final Context context;

    private static int lastSendAlbumArt = 0;

    private final class MediaSessionChangeListener implements MediaSessionManager.OnActiveSessionsChangedListener {
        @Override
        public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {
            if (null == controllers) {
                return;
            }

            players.clear();
            createPlayers(controllers);
        }
    }

    public MediaReceiver(Context context) {
        this.context = context;
        players = new HashMap<>();
        prefs = context.getSharedPreferences("com.noti.main_preferences", Context.MODE_PRIVATE);

        try {
            MediaSessionManager manager = ContextCompat.getSystemService(context, MediaSessionManager.class);
            assert(mediaSessionChangeListener == null);
            mediaSessionChangeListener = new MediaSessionChangeListener();

            assert manager != null;
            manager.addOnActiveSessionsChangedListener(mediaSessionChangeListener, new ComponentName(context, NotiListenerService.class), new Handler(Looper.getMainLooper()));
            createPlayers(manager.getActiveSessions(new ComponentName(context, NotiListenerService.class)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onDestroy() {
        MediaSessionManager manager = ContextCompat.getSystemService(context, MediaSessionManager.class);
        if (manager != null && mediaSessionChangeListener != null) {
            manager.removeOnActiveSessionsChangedListener(mediaSessionChangeListener);
            mediaSessionChangeListener = null;
        }
    }

    public void onDataReceived(JSONObject np) throws JSONException {
        if (!np.has("player")) {
            return;
        }
        MediaReceiverPlayer player = players.get(np.getString("player"));

        if (null == player) {
            return;
        }

        if (np.has("requestNowPlaying") && np.getBoolean("requestNowPlaying")) {
            sendMetadata(player);
            return;
        }

        if (np.has("SetPosition")) {
            long position = np.getLong("SetPosition");
            player.setPosition(position);
        }

        if (np.has("setVolume")) {
            int volume = np.getInt("setVolume");
            player.setVolume(volume);
            sendMetadata(player);
        }

        if (np.has("action")) {
            String action = np.getString("action");

            switch (action) {
                case "Play":
                    player.play();
                    break;
                case "Pause":
                    player.pause();
                    break;
                case "PlayPause":
                    player.playPause();
                    break;
                case "Next":
                    player.next();
                    break;
                case "Previous":
                    player.previous();
                    break;
                case "Stop":
                    player.stop();
                    break;
            }
        }
    }

    private void createPlayers(List<MediaController> sessions) {
        for (MediaController controller : sessions) {
            createPlayer(controller);
        }
    }

    public static String appNameLookup(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (final PackageManager.NameNotFoundException e) {
            Log.e("AppsHelper", "Could not resolve name " + packageName, e);
            return null;
        }
    }

    private void createPlayer(MediaController controller) {
        if (controller.getPackageName().equals(context.getPackageName())) return;

        MediaReceiverPlayer player = new MediaReceiverPlayer(controller, appNameLookup(context, controller.getPackageName()));
        controller.registerCallback(new MediaReceiverCallback(this, player), new Handler(Looper.getMainLooper()));
        players.put(player.getName(), player);
    }

    void sendMetadata(MediaReceiverPlayer player) {
        try {
            JSONObject np = new JSONObject();
            if(player.getTitle().isEmpty()) return;

            np.put("player", player.getName());
            if (player.getArtist().isEmpty()) {
                np.put("nowPlaying", player.getTitle());
            } else {
                np.put("nowPlaying", player.getArtist() + " - " + player.getTitle());
            }

            np.put("title", player.getTitle());
            np.put("artist", player.getArtist());
            np.put("album", player.getAlbum());
            np.put("isPlaying", player.isPlaying());
            np.put("pos", player.getPosition());
            np.put("length", player.getLength());
            np.put("canPlay", player.canPlay());
            np.put("canPause", player.canPause());
            np.put("canGoPrevious", player.canGoPrevious());
            np.put("canGoNext", player.canGoNext());
            np.put("canSeek", player.canSeek());
            np.put("volume", player.getVolume());

            String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
            String DEVICE_ID = NotiListenerService.getUniqueID();
            String TOPIC = "/topics/" + prefs.getString("UID", "");

            JSONObject notificationHead = new JSONObject();
            JSONObject notificationBody = new JSONObject();

            notificationBody.put("type", "media|meta_data");
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("media_data", np.toString());

            notificationHead.put("to", TOPIC);
            notificationHead.put("data", notificationBody);

            NotiListenerService.sendNotification(notificationHead, context.getPackageName(), context);

            Bitmap albumArt = player.getAlbumArt();
            int hashCode = albumArt == null ? 0 : albumArt.hashCode();

            if(player.isPlaying() && albumArt != null && lastSendAlbumArt != hashCode) {
                lastSendAlbumArt = hashCode;
                albumArt.setHasAlpha(true);

                String albumArtStream = CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(NotiListenerService.getResizedBitmap(albumArt, 16, 16)));
                albumArt.recycle();

                np = new JSONObject();
                np.put("albumArt", albumArtStream);

                notificationHead = new JSONObject();
                notificationBody = new JSONObject();

                notificationBody.put("type", "media|meta_data");
                notificationBody.put("device_name", DEVICE_NAME);
                notificationBody.put("device_id", DEVICE_ID);
                notificationBody.put("media_data", np.toString());

                notificationHead.put("to", TOPIC);
                notificationHead.put("data", notificationBody);

                NotiListenerService.sendNotification(notificationHead, context.getPackageName(), context);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
