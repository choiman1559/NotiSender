package com.noti.plugin.data;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationData implements Parcelable {
    public String TITLE;
    public String CONTENT;
    public String PACKAGE_NAME;
    public String APP_NAME;
    public String DEVICE_NAME;
    public String DATE;

    public NotificationData() {

    }

    protected NotificationData(Parcel in) {
        TITLE = in.readString();
        CONTENT = in.readString();
        PACKAGE_NAME = in.readString();
        APP_NAME = in.readString();
        DEVICE_NAME = in.readString();
        DATE = in.readString();
    }

    public NotificationData(com.noti.main.service.mirnoti.NotificationData data, String deviceName) {
        this.TITLE = data.title;
        this.CONTENT = data.message;
        this.PACKAGE_NAME = data.appPackage;
        this.APP_NAME = data.appName;
        this.DATE =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(data.postTime));
        this.DEVICE_NAME = deviceName;
    }

    public static final Creator<NotificationData> CREATOR = new Creator<>() {
        @Override
        public NotificationData createFromParcel(Parcel in) {
            return new NotificationData(in);
        }

        @Override
        public NotificationData[] newArray(int size) {
            return new NotificationData[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeString(TITLE);
        parcel.writeString(CONTENT);
        parcel.writeString(PACKAGE_NAME);
        parcel.writeString(APP_NAME);
        parcel.writeString(DEVICE_NAME);
        parcel.writeString(DATE);
    }
}
