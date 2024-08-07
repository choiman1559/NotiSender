package com.noti.main.service.livenoti;

import android.content.Context;
import android.content.pm.PackageManager;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Nullable;

import com.noti.main.BuildConfig;
import com.noti.main.service.FirebaseMessageService;
import com.noti.main.service.backend.PacketConst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import me.pushy.sdk.lib.jackson.core.JsonProcessingException;
import me.pushy.sdk.lib.jackson.databind.ObjectMapper;

public class LiveNotiProcess {

    public static final String REQUEST_LIVE_NOTIFICATION = "request_live_notification";
    public static final String RESPONSE_LIVE_NOTIFICATION = "response_live_notification";
    public static final String REQUEST_NOTIFICATION_ACTION = "response_notification_action";

    public static onLiveNotificationUploadCompleteListener mOnLiveNotificationDownloadCompleteListener;
    public static onLiveNotificationUploadCompleteListener mOnLiveNotificationUploadCompleteListener;
    public static onNotificationListListener mOnNotificationListListener;

    public interface onNotificationListListener {
        StatusBarNotification[] onRequested();
    }

    public interface onLiveNotificationUploadCompleteListener {
        void onReceive(boolean isSuccess, @Nullable LiveNotificationData[] liveNotifications);
    }

    public static void callLiveNotificationUploadCompleteListener(boolean isSuccess, @Nullable LiveNotificationData[] liveNotifications) {
        if(mOnLiveNotificationDownloadCompleteListener != null) {
            mOnLiveNotificationDownloadCompleteListener.onReceive(isSuccess, liveNotifications);
        }
    }

    public static void onProcessReceive(Map<String, String> map, Context context) {
        try {
            switch (Objects.requireNonNull(map.get(PacketConst.KEY_ACTION_TYPE))) {
                case REQUEST_LIVE_NOTIFICATION -> LiveNotiRequests.postLiveNotificationData(context, map);
                case RESPONSE_LIVE_NOTIFICATION -> LiveNotiRequests.getLiveNotificationData(context, map);
                case REQUEST_NOTIFICATION_ACTION -> FirebaseMessageService.startNewRemoteActivity(context, map);
                default -> {
                    if(BuildConfig.DEBUG) {
                        Log.e("LiveNotification", "onProcessReceive failed: Action type is not supported: " + map.get(PacketConst.KEY_ACTION_TYPE));
                    }
                }
            }
        } catch (Exception e) {
            Log.e("LiveNotification", "onProcessReceive failed: Exception thrown" + e);
        }
    }

    public static String toStringLiveNotificationList(Context context) throws JsonProcessingException, PackageManager.NameNotFoundException {
        if(mOnNotificationListListener != null) {
            ArrayList<StatusBarNotification> statusBarNotifications = new ArrayList<>(Arrays.asList(mOnNotificationListListener.onRequested()));
            ArrayList<String> keyList = new ArrayList<>();
            ArrayList<String> finalDataList = new ArrayList<>();

            for(int i = 0; i < statusBarNotifications.size(); i++) {
                StatusBarNotification statusBarNotification = statusBarNotifications.get(i);
                if(keyList.contains(statusBarNotification.getKey())) {
                    continue;
                }

                finalDataList.add(new LiveNotificationData(context, statusBarNotification).toString());
                keyList.add(statusBarNotification.getKey());
            }

            return new ObjectMapper().writeValueAsString(finalDataList.toArray());
        } else return "";
    }
}
