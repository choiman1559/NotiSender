package com.noti.main.service.mirnoti;

import static com.noti.main.service.NotiListenerService.getNonNullString;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.noti.main.R;
import com.noti.main.service.NotiListenerService;
import com.noti.main.utils.network.CompressStringUtil;

import java.io.IOException;
import java.io.Serializable;

import me.pushy.sdk.lib.jackson.annotation.JsonGetter;
import me.pushy.sdk.lib.jackson.annotation.JsonIgnore;
import me.pushy.sdk.lib.jackson.annotation.JsonProperty;
import me.pushy.sdk.lib.jackson.core.JsonProcessingException;
import me.pushy.sdk.lib.jackson.databind.ObjectMapper;

public class NotificationsData implements Serializable {
    @JsonProperty
    public long postTime;
    @JsonProperty
    public String key;
    @JsonProperty
    public String groupKey;
    @JsonProperty
    public String appPackage;
    @JsonProperty
    public String appName;
    @JsonProperty
    public String title;
    @JsonProperty
    public String message;
    public String priority;
    @JsonProperty
    public NotificationAction[] actions;

    @JsonProperty
    protected String smallIcon;
    @JsonProperty
    protected String bigIcon;
    @JsonProperty
    protected String bigPicture;

    @JsonIgnore
    boolean isTextEmpty = false;

    @SuppressWarnings("unused")
    public NotificationsData() {
        // Default constructor for creating instance by ObjectMapper
    }

    public NotificationsData(Context context, StatusBarNotification statusBarNotification) throws PackageManager.NameNotFoundException {
        this.postTime = statusBarNotification.getPostTime();
        this.key = statusBarNotification.getKey();
        this.groupKey = statusBarNotification.getGroupKey();

        PackageManager pm = context.getPackageManager();
        this.appPackage = statusBarNotification.getPackageName();
        try {
            this.appName = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(this.appPackage, PackageManager.GET_META_DATA)));
        } catch (PackageManager.NameNotFoundException e) {
            this.appName = "";
        }

        Notification notification = statusBarNotification.getNotification();
        Bundle extras = notification.extras;
        this.priority = String.valueOf(notification.priority);

        String TITLE = getNonNullString(extras.getCharSequence(Notification.EXTRA_TITLE));
        String TEXT = getNonNullString(extras.getCharSequence(Notification.EXTRA_TEXT));
        String TEXT_LINES = getNonNullString(extras.getCharSequence(Notification.EXTRA_TEXT_LINES));
        if(TITLE.isEmpty() || TEXT.isEmpty() || TEXT_LINES.isEmpty()) isTextEmpty = true;
        if (!TEXT_LINES.isEmpty() && TEXT.isEmpty()) TEXT = TEXT_LINES;

        SharedPreferences prefs = NotiListenerService.getPrefs();
        this.title = TITLE.isEmpty() || TITLE.equals("null") ? prefs.getString("DefaultTitle", "New notification") : TITLE;
        this.message = TEXT.isEmpty() || TEXT.equals("null") ? prefs.getString("DefaultMessage", "notification arrived.") : TEXT;

        Notification.Action[] actionArray = notification.actions;
        if(actionArray != null && actionArray.length > 0) {
            this.actions = new NotificationAction[actionArray.length];
            int i = 0;
            for (Notification.Action action : actionArray) {
                this.actions[i++] = new NotificationAction(action);
            }
        } else {
            this.actions = new NotificationAction[0];
        }

        int resizeIconRes = getIconSize();
        Context packageContext = context.createPackageContext(this.appPackage, Context.CONTEXT_IGNORE_SECURITY);

        Icon smallIconObj = notification.getSmallIcon();
        Icon bigIconObj = notification.getLargeIcon();

        Bitmap largeIconBitmap = convertImage(packageContext, notification.extras.get(NotificationCompat.EXTRA_LARGE_ICON_BIG));
        Bitmap bigPictureBitmap = convertImage(packageContext, notification.extras.get(NotificationCompat.EXTRA_PICTURE));

        this.smallIcon = "";
        if (smallIconObj != null) {
            Drawable iconDrawable = smallIconObj.loadDrawable(packageContext);
            if (iconDrawable != null) {
                iconDrawable.setTint(Color.BLACK);
                Bitmap iconBitmap = NotiListenerService.getBitmapFromDrawable(iconDrawable);

                if (iconBitmap != null && iconBitmap.getWidth() > 0 && iconBitmap.getHeight() > 0) {
                    iconBitmap.setHasAlpha(true);
                    Bitmap resizedBitmap = NotiListenerService.getResizedBitmap(iconBitmap, resizeIconRes, resizeIconRes, Color.BLACK);

                    if(resizedBitmap != null) {
                        this.smallIcon = CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(resizedBitmap));
                    }
                }
            }
        }

        this.bigIcon = "";
        if (largeIconBitmap != null && largeIconBitmap.getWidth() > 0 && largeIconBitmap.getHeight() > 0) {
            this.bigIcon = CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(
                    NotiListenerService.getResizedBitmap(largeIconBitmap, resizeIconRes, resizeIconRes)));
        } else if (bigIconObj != null) {
            Bitmap bigIconBitmap = convertImage(packageContext, bigIconObj);
            if(bigIconBitmap != null && bigIconBitmap.getWidth() > 0 && bigIconBitmap.getHeight() > 0) {
                this.bigIcon = CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(
                        NotiListenerService.getResizedBitmap(bigIconBitmap, resizeIconRes, resizeIconRes)));
            }
        }

        this.bigPicture = "";
        if (bigPictureBitmap != null) {
            this.bigPicture = CompressStringUtil.compressString(CompressStringUtil.getStringFromBitmap(
                    NotiListenerService.getResizedBitmap(bigPictureBitmap, bigPictureBitmap.getWidth() / 2, bigPictureBitmap.getHeight() / 2)));
        }
    }

    private int getIconSize() {
        SharedPreferences prefs = NotiListenerService.getPrefs();
        return switch (prefs.getString("IconRes", "52 x 52 (Default)")) {
            case "68 x 68 (Not Recommend)" -> 68;
            case "36 x 36" -> 36;
            default -> 52;
        };
    }

    public static NotificationsData parseFrom(String serializedMessage) throws IOException {
        return new ObjectMapper().readValue(serializedMessage, NotificationsData.class);
    }

    public Notification.Builder getBuilder(Context context) {
        Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, context.getString(R.string.notify_channel_id));
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setContentTitle(title + " (" + appName + ")")
                .setContentText(message)
                .setAutoCancel(true);

        Bitmap smallIconBitmap = getSmallIcon();
        Bitmap bigIconBitmap = getBigIcon();
        Bitmap bigPictureBitmap = getBigPicture();

        if(smallIconBitmap != null) {
            builder.setSmallIcon(IconCompat.createWithBitmap(smallIconBitmap).toIcon(context));
        }

        if(bigIconBitmap != null) {
            builder.setLargeIcon(bigIconBitmap);
        }

        if(bigPictureBitmap != null) {
             builder.setStyle(new Notification.BigPictureStyle().bigPicture(bigPictureBitmap));
        }

        if (Build.VERSION.SDK_INT < 33) {
            builder.setGroupSummary(true);
            if(groupKey != null && !groupKey.isEmpty()) {
                builder.setGroup(groupKey);
            } else {
                builder.setGroup(context.getPackageName() + ".NOTIFICATION");
            }
        }

        return builder;
    }

    @JsonIgnore
    public boolean isTextEmpty() {
        return isTextEmpty;
    }

    @SuppressWarnings("unused")
    @JsonGetter("smallIcon")
    protected String getSmallIconStr() {
        return smallIcon;
    }

    @SuppressWarnings("unused")
    @JsonGetter("bigIcon")
    protected String getBigIconStr() {
        return bigIcon;
    }

    @SuppressWarnings("unused")
    @JsonGetter("bigPicture")
    protected String getBigPictureStr() {
        return bigPicture;
    }

    @Nullable
    public Bitmap getBigIcon() {
        return deserializeBitmap(bigIcon);
    }

    @Nullable
    public Bitmap getSmallIcon() {
        return deserializeBitmap(smallIcon);
    }

    @Nullable
    public Bitmap getBigPicture() {
        return deserializeBitmap(bigPicture);
    }

    @Nullable
    private static Bitmap deserializeBitmap(String serializedMessage) {
        return (serializedMessage == null || serializedMessage.isEmpty()) ? null :
                CompressStringUtil.getBitmapFromString(CompressStringUtil.decompressString(serializedMessage));
    }

    @Nullable
    private Bitmap convertImage(Context context, Object object) {
        if(object == null) return null;
        if(object instanceof Icon) {
            return NotiListenerService.getBitmapFromDrawable(((Icon) object).loadDrawable(context));
        } else if(object instanceof Drawable) {
            return NotiListenerService.getBitmapFromDrawable((Drawable) object);
        } else if(object instanceof Bitmap) {
            return (Bitmap) object;
        }
        return null;
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
