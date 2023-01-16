package com.noti.main.service.pair;

import static android.content.Context.UI_MODE_SERVICE;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;

import com.microsoft.fluent.mobile.icons.R.drawable;
import com.noti.main.R;

public class PairDeviceType {
    private final String THIS_DEVICE_TYPE;

    public static final String DEVICE_TYPE_UNKNOWN = "Unknown";
    public static final String DEVICE_TYPE_PHONE = "Phone";
    public static final String DEVICE_TYPE_TABLET = "Tablet";
    public static final String DEVICE_TYPE_TV = "Television";
    public static final String DEVICE_TYPE_DESKTOP = "Desktop";
    public static final String DEVICE_TYPE_LAPTOP = "Laptop";
    public static final String DEVICE_TYPE_WATCH = "Smartwatch";
    public static final String DEVICE_TYPE_IOT = "IOT_Device";
    public static final String DEVICE_TYPE_VR = "VR_Gear";
    public static final String DEVICE_TYPE_CAR = "Automobile";

    public PairDeviceType(String deviceType) {
        this.THIS_DEVICE_TYPE = deviceType;
    }

    public String getDeviceType() {
        return this.THIS_DEVICE_TYPE;
    }

    public static PairDeviceType getThisDeviceType(Context context) {
        String deviceType;
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);
        switch (uiModeManager.getCurrentModeType()) {
            case Configuration.UI_MODE_TYPE_TELEVISION:
                deviceType = DEVICE_TYPE_TV;
                break;

            case Configuration.UI_MODE_TYPE_WATCH:
                deviceType = DEVICE_TYPE_WATCH;
                break;

            case Configuration.UI_MODE_TYPE_DESK:
                deviceType = DEVICE_TYPE_DESKTOP;
                break;

            case Configuration.UI_MODE_TYPE_VR_HEADSET:
                deviceType = DEVICE_TYPE_VR;
                break;

            case Configuration.UI_MODE_TYPE_CAR:
                deviceType = DEVICE_TYPE_CAR;
                break;

            case Configuration.UI_MODE_TYPE_APPLIANCE:
                deviceType = DEVICE_TYPE_IOT;
                break;

            case Configuration.UI_MODE_TYPE_NORMAL:
                if (context.getResources().getBoolean(R.bool.is_tablet)) {
                    deviceType = DEVICE_TYPE_TABLET;
                } else {
                    deviceType = DEVICE_TYPE_PHONE;
                }
                break;

            case Configuration.UI_MODE_TYPE_UNDEFINED:
            default:
                deviceType = DEVICE_TYPE_UNKNOWN;
                break;
        }

        return new PairDeviceType(deviceType);
    }

    public int getDeviceTypeBitmap() {
        if(THIS_DEVICE_TYPE == null) {
            return drawable.ic_fluent_developer_board_24_regular;
        }

        switch (THIS_DEVICE_TYPE) {
            case DEVICE_TYPE_PHONE:
                return drawable.ic_fluent_phone_24_regular;

            case DEVICE_TYPE_TABLET:
                return drawable.ic_fluent_tablet_24_regular;

            case DEVICE_TYPE_TV:
                return drawable.ic_fluent_tv_24_regular;

            case DEVICE_TYPE_DESKTOP:
                return drawable.ic_fluent_desktop_24_regular;

            case DEVICE_TYPE_LAPTOP:
                return drawable.ic_fluent_laptop_24_regular;

            case DEVICE_TYPE_WATCH:
                return drawable.ic_fluent_smartwatch_24_regular;

            case DEVICE_TYPE_IOT:
                return drawable.ic_fluent_iot_24_regular;

            case DEVICE_TYPE_VR:
                return drawable.ic_fluent_headset_vr_24_regular;

            case DEVICE_TYPE_CAR:
                return drawable.ic_fluent_vehicle_car_24_regular;

            case DEVICE_TYPE_UNKNOWN:
            default:
                return drawable.ic_fluent_developer_board_24_regular;
        }
    }
}
