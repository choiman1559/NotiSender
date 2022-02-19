package com.noti.main.service.pair;

import java.util.Map;

final public class PairListener {
    public static onDeviceFoundListener m_onDeviceFoundListener;
    public static onDevicePairResultListener m_onDevicePairResultListener;
    private static PairListener m_obj;

    public interface onDeviceFoundListener {
        void onReceive(Map<String, String> map);
    }

    public interface onDevicePairResultListener {
        void onReceive(Map<String, String> map);
    }

    public static void setOnDeviceFoundListener(onDeviceFoundListener mOnDeviceFoundListener) {
        PairListener.m_onDeviceFoundListener = mOnDeviceFoundListener;
    }

    public static void setOnDevicePairResultListener(onDevicePairResultListener mOnDevicePairResultListener) {
        PairListener.m_onDevicePairResultListener = mOnDevicePairResultListener;
    }

    public static PairListener model() {
        if (m_obj == null) {
            m_obj = new PairListener();
        }
        return m_obj;
    }
}
