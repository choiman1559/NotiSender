package com.noti.main.service.pair;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class PairDeviceStatus {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Device_Not_Paired, Device_Process_Pairing, Device_Already_Paired})
    public @interface Status { }

    public static final int Device_Not_Paired = 0;
    public static final int Device_Process_Pairing = 1;
    public static final int Device_Already_Paired = 2;
}
