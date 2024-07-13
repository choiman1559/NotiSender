package com.noti.main.service.media;

import static com.noti.main.utils.network.AESCrypto.shaAndHex;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.service.NotiListenerService;
import com.noti.main.service.backend.PacketConst;
import com.noti.main.service.backend.PacketRequester;
import com.noti.main.service.backend.ResultPacket;
import com.noti.main.utils.PowerUtils;
import com.noti.main.utils.network.CompressStringUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;

public class MediaReceiver {
    private MediaSessionChangeListener mediaSessionChangeListener;
    private final HashMap<String, MediaReceiverPlayer> players;

    private final SharedPreferences prefs;
    private final Context context;

    private volatile String lastSentPacket = "";
    private volatile String lastSendAlbumArt = "";

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
        prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);

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
                case "Play" -> player.play();
                case "Pause" -> player.pause();
                case "PlayPause" -> player.playPause();
                case "Next" -> player.next();
                case "Previous" -> player.previous();
                case "Stop" -> player.stop();
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
        controller.registerCallback(new MediaReceiverCallback(context,this, player), new Handler(Looper.getMainLooper()));
        players.put(player.getName(), player);
    }

    synchronized void sendMetadata(MediaReceiverPlayer player) {
        PowerUtils.getInstance(context).acquire();
        if(!prefs.getBoolean("serviceToggle", false) || !prefs.getBoolean("UseMediaSync", false)) {
            return;
        }

        try {
            JSONObject np = new JSONObject();
            if(player.getTitle().isEmpty()) return;

            Bitmap albumArt = player.getAlbumArt();
            boolean isNeedSendAlbumArt = false;
            String serializedBitmap = "";

            if(albumArt != null) {
                serializedBitmap = CompressStringUtil.getStringFromBitmap(albumArt);
                isNeedSendAlbumArt = prefs.getBoolean("UseAlbumArt", false) && !lastSendAlbumArt.equals(serializedBitmap);
            }

            if(BuildConfig.DEBUG) {
                Log.d("AlbumArt", "isNeedTransfer: " + isNeedSendAlbumArt + " serialLength: " + serializedBitmap.length());
            }

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
            np.put("isAlbumArtSent", isNeedSendAlbumArt);

            String DEVICE_NAME = NotiListenerService.getDeviceName();
            String DEVICE_ID = NotiListenerService.getUniqueID();

            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type", "media|meta_data");
            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("media_data", np.toString());

            if(lastSentPacket.isEmpty() || !notificationBody.toString().equals(lastSentPacket)) {
                NotiListenerService.sendNotification(notificationBody, context.getPackageName(), context);
                lastSentPacket = notificationBody.toString();
            } else {
                return;
            }

            if(isNeedSendAlbumArt) {
                lastSendAlbumArt = serializedBitmap;
                if(prefs.getBoolean("UseFcmWhenSendImage", false)) {
                    albumArt.setHasAlpha(true);
                    String albumArtStream = CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(NotiListenerService.getResizedBitmap(albumArt, 16, 16)));

                    np = new JSONObject();
                    np.put("albumArtBytes", albumArtStream);

                    notificationBody = new JSONObject();
                    notificationBody.put("type", "media|meta_data");
                    notificationBody.put("device_name", DEVICE_NAME);
                    notificationBody.put("device_id", DEVICE_ID);
                    notificationBody.put("media_data", np.toString());

                    NotiListenerService.sendNotification(notificationBody, context.getPackageName(), context);
                } else {
                    int bitmapHashCode = albumArt.hashCode();
                    String finalUniqueId = shaAndHex(DEVICE_ID + bitmapHashCode);
                    albumArt.recycle();

                    JSONObject serverBody = new JSONObject();
                    serverBody.put(PacketConst.KEY_ACTION_TYPE, PacketConst.REQUEST_POST_SHORT_TERM_DATA);
                    serverBody.put(PacketConst.KEY_DATA_KEY, finalUniqueId);
                    serverBody.put(PacketConst.KEY_EXTRA_DATA, serializedBitmap);

                    PacketRequester.addToRequestQueue(context, PacketConst.SERVICE_TYPE_IMAGE_CACHE, serverBody, response -> {
                        try {
                            ResultPacket resultPacket = ResultPacket.parseFrom(response.toString());
                            if(resultPacket.isResultOk()) {
                                JSONObject json = new JSONObject();
                                json.put("albumArtHash", bitmapHashCode);
                                JSONObject newNotificationBody = new JSONObject();

                                newNotificationBody.put("type", "media|meta_data");
                                newNotificationBody.put("device_name", DEVICE_NAME);
                                newNotificationBody.put("device_id", DEVICE_ID);
                                newNotificationBody.put("media_data", json.toString());

                                NotiListenerService.sendNotification(newNotificationBody, context.getPackageName(), context);
                            }  else {
                                throw new IOException(resultPacket.getErrorCause());
                            }
                        } catch (IOException | JSONException e) {
                            e.printStackTrace();
                        }
                    }, error -> { });
                }
            }
        } catch (JSONException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
}
