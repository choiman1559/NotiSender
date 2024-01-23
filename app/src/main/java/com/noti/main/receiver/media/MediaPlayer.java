package com.noti.main.receiver.media;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import com.noti.main.Application;
import com.noti.main.service.NotiListenerService;

import org.json.JSONException;
import org.json.JSONObject;

@SuppressWarnings("unused")
public class MediaPlayer {
    Context context;
    SharedPreferences prefs;

    private final String device_name;
    private final String device_id;

    String player = "";
    String currentSong = "";
    String title = "";
    String artist = "";
    String album = "";
    String loopStatus = "";

    int volume = 50;
    long length = -1;
    long lastPosition = 0;
    long lastPositionTime;

    boolean playing = false;
    boolean loopStatusAllowed = false;
    boolean shuffle = false;
    boolean shuffleAllowed = false;
    boolean playAllowed = true;
    boolean pauseAllowed = true;
    boolean goNextAllowed = true;
    boolean goPreviousAllowed = true;
    boolean seekAllowed = true;

    Bitmap albumArt = null;

    public MediaPlayer(Context context, String device_name, String device_id) {
        this.context = context;
        prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
        lastPositionTime = System.currentTimeMillis();
        this.device_name = device_name;
        this.device_id = device_id;
    }

    public String getCurrentSong() {
        return currentSong;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public String getPlayer() {
        return player;
    }

    boolean isSpotify() {
        return getPlayer().equalsIgnoreCase("spotify");
    }

    public String getLoopStatus() {
        return loopStatus;
    }

    public boolean getShuffle() {
        return shuffle;
    }

    public int getVolume() {
        return volume;
    }

    public long getLength() {
        return length;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isPlayAllowed() {
        return playAllowed;
    }

    public boolean isPauseAllowed() {
        return pauseAllowed;
    }

    public boolean isGoNextAllowed() {
        return goNextAllowed;
    }

    public boolean isGoPreviousAllowed() {
        return goPreviousAllowed;
    }

    public boolean isSeekAllowed() {
        return seekAllowed && getLength() >= 0 && getPosition() >= 0;
    }

    public boolean hasAlbumArt() {
        return albumArt != null;
    }

    public Bitmap getAlbumArt() {
        return albumArt;
    }

    public boolean isLoopStatusAllowed() {
        return loopStatusAllowed;
    }

    public boolean isShuffleAllowed() {
        return shuffleAllowed;
    }

    public boolean isSetVolumeAllowed() {
        return getVolume() > -1;
    }

    public long getPosition() {
        if (playing) {
            return lastPosition + (System.currentTimeMillis() - lastPositionTime);
        } else {
            return lastPosition;
        }
    }

    public void playPause() {
        if (isPauseAllowed() || isPlayAllowed()) {
            sendCommand(getPlayer(), "action", "PlayPause");
        }
    }

    public void play() {
        if (isPlayAllowed()) {
            sendCommand(getPlayer(), "action", "Play");
        }
    }

    public void pause() {
        if (isPauseAllowed()) {
            sendCommand(getPlayer(), "action", "Pause");
        }
    }

    public void stop() {
        sendCommand(getPlayer(), "action", "Stop");
    }

    public void previous() {
        if (isGoPreviousAllowed()) {
            sendCommand(getPlayer(), "action", "Previous");
        }
    }

    public void next() {
        if (isGoNextAllowed()) {
            sendCommand(getPlayer(), "action", "Next");
        }
    }

    public void setLoopStatus(String loopStatus) {
        sendCommand(getPlayer(), "setLoopStatus", loopStatus);
    }

    public void setShuffle(boolean shuffle) {
        sendCommand(getPlayer(), "setShuffle", shuffle);
    }

    public void setVolume(int volume) {
        if (isSetVolumeAllowed()) {
            sendCommand(getPlayer(), "setVolume", volume);
        }
    }

    public void setPosition(int position) {
        if (isSeekAllowed()) {
            sendCommand(getPlayer(), "SetPosition", position);

            lastPosition = position;
            lastPositionTime = System.currentTimeMillis();
        }
    }

    public void seek(int offset) {
        if (isSeekAllowed()) {
            sendCommand(getPlayer(), "Seek", offset);
        }
    }

    void sendCommand(String player, String command, Object value) {
        try {
            JSONObject np = new JSONObject();
            np.put("player", player);
            np.put(command, value);
            sendCommand(np);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void sendCommand(JSONObject object) {
        if(prefs.getBoolean("serviceToggle", false)) {
            String DEVICE_NAME = NotiListenerService.getDeviceName();
            String DEVICE_ID = NotiListenerService.getUniqueID();

            JSONObject notificationBody = new JSONObject();
            try {
                notificationBody.put("type", "media|action");
                notificationBody.put("device_name", DEVICE_NAME);
                notificationBody.put("device_id", DEVICE_ID);
                notificationBody.put("send_device_name", device_name);
                notificationBody.put("send_device_id", device_id);
                notificationBody.put("media_data", object);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            NotiListenerService.sendNotification(notificationBody, context.getPackageName(), context);
        }
    }
}