package com.noti.main.service.livenoti;

import static com.noti.main.service.NotiListenerService.getNonNullString;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;

import java.io.IOException;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;
import me.pushy.sdk.lib.jackson.core.JsonProcessingException;
import me.pushy.sdk.lib.jackson.databind.ObjectMapper;

public class LiveNotificationData {
    @JsonProperty
    public long postTime;
    @JsonProperty
    public String key;
    @JsonProperty
    public String appPackage;
    @JsonProperty
    public String appName;
    @JsonProperty
    public String title;
    @JsonProperty
    public String message;

    @SuppressWarnings("unused")
    public LiveNotificationData() {
        // Default constructor for creating instance by ObjectMapper
    }

    public LiveNotificationData(Context context, StatusBarNotification statusBarNotification) {
        this.postTime = statusBarNotification.getPostTime();
        this.key = statusBarNotification.getKey();

        PackageManager pm = context.getPackageManager();
        this.appPackage = statusBarNotification.getPackageName();
        try {
            this.appName = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(this.appPackage, PackageManager.GET_META_DATA)));
        } catch (PackageManager.NameNotFoundException e) {
            this.appName = "";
        }

        Bundle extras = statusBarNotification.getNotification().extras;
        String TITLE = getNonNullString(extras.getString(Notification.EXTRA_TITLE));
        String TEXT = getNonNullString(extras.getString(Notification.EXTRA_TEXT));
        String TEXT_LINES = getNonNullString(extras.getString(Notification.EXTRA_TEXT_LINES));
        if (!TEXT_LINES.isEmpty() && TEXT.isEmpty()) TEXT = TEXT_LINES;

        this.title = TITLE;
        this.message = TEXT;
    }

    public static LiveNotificationData parseFrom(String serializedMessage) throws IOException {
        return new ObjectMapper().readValue(serializedMessage, LiveNotificationData.class);
    }

    @NonNull
    @Override
    public String toString() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
