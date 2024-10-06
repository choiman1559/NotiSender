package com.noti.main.service.livenoti;

import static com.noti.main.service.NotiListenerService.getNonNullString;

import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;

import com.noti.main.service.NotiListenerService;
import com.noti.main.utils.network.CompressStringUtil;

import java.io.IOException;
import java.io.Serializable;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;
import me.pushy.sdk.lib.jackson.core.JsonProcessingException;
import me.pushy.sdk.lib.jackson.databind.ObjectMapper;

public class LiveNotificationData implements Serializable {
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
    @JsonProperty
    public String smallIcon;
    @JsonProperty
    public String bigIcon;

    @SuppressWarnings("unused")
    public LiveNotificationData() {
        // Default constructor for creating instance by ObjectMapper
    }

    public LiveNotificationData(Context context, StatusBarNotification statusBarNotification) throws Exception {
        this.postTime = statusBarNotification.getPostTime();
        this.key = statusBarNotification.getKey();

        PackageManager pm = context.getPackageManager();
        this.appPackage = statusBarNotification.getPackageName();
        try {
            this.appName = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(this.appPackage, PackageManager.GET_META_DATA)));
        } catch (PackageManager.NameNotFoundException e) {
            this.appName = "";
        }

        Notification notification = statusBarNotification.getNotification();
        Bundle extras = notification.extras;

        String TITLE = getNonNullString(extras.getCharSequence(Notification.EXTRA_TITLE));
        String TEXT = getNonNullString(extras.getCharSequence(Notification.EXTRA_TEXT));
        String TEXT_LINES = getNonNullString(extras.getCharSequence(Notification.EXTRA_TEXT_LINES));
        if (!TEXT_LINES.isEmpty() && TEXT.isEmpty()) TEXT = TEXT_LINES;

        this.title = TITLE;
        this.message = TEXT;

        Context packageContext = context.createPackageContext(this.appPackage, Context.CONTEXT_IGNORE_SECURITY);
        Icon smallIconObj = notification.getSmallIcon();
        Icon bigIconObj = notification.getLargeIcon();

        if(smallIconObj != null) {
            Drawable iconDrawable = smallIconObj.loadDrawable(packageContext);
            if(iconDrawable != null) {
                iconDrawable.setTint(Color.BLACK);
                Bitmap iconBitmap = NotiListenerService.getBitmapFromDrawable(iconDrawable);

                if(iconBitmap != null) {
                    iconBitmap.setHasAlpha(true);
                    Bitmap resizedBitmap = NotiListenerService.getResizedBitmap(iconBitmap,52,52, Color.BLACK);
                    if(resizedBitmap != null) {
                        this.smallIcon = CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(resizedBitmap));
                    } else {
                        this.smallIcon = "";
                    }
                }
            }
        } else {
            this.smallIcon = "";
        }

        if(bigIconObj != null) {
            Bitmap bigIconBitmap = NotiListenerService.getBitmapFromDrawable(bigIconObj.loadDrawable(packageContext));
            this.bigIcon = CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(
                    NotiListenerService.getResizedBitmap(bigIconBitmap,64,64)));
        } else {
            this.bigIcon = "";
        }
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
