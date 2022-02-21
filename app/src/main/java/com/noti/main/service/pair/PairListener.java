package com.noti.main.service.pair;

import java.util.ArrayList;
import java.util.Map;

final public class PairListener {
    public static onDeviceFoundListener m_onDeviceFoundListener;
    public static onDevicePairResultListener m_onDevicePairResultListener;
    public static ArrayList<onDataReceivedListener> m_onDataReceivedListener = new ArrayList<>();
    private static PairListener m_obj;

    public interface onDeviceFoundListener {
        void onReceive(Map<String, String> map);
    }

    public interface onDevicePairResultListener {
        void onReceive(Map<String, String> map);
    }

    public interface onDataReceivedListener {
        void onReceive(Map<String, String> map);
    }

    public static void setOnDeviceFoundListener(onDeviceFoundListener mOnDeviceFoundListener) {
        PairListener.m_onDeviceFoundListener = mOnDeviceFoundListener;
    }

    public static void setOnDevicePairResultListener(onDevicePairResultListener mOnDevicePairResultListener) {
        PairListener.m_onDevicePairResultListener = mOnDevicePairResultListener;
    }

    public static void addOnDataReceivedListener(onDataReceivedListener mOnDataReceivedListener) {
        if(!m_onDataReceivedListener.contains(mOnDataReceivedListener)) m_onDataReceivedListener.add(mOnDataReceivedListener);
    }

    public static void callOnDataReceived(Map<String, String> map) {
        if(m_onDataReceivedListener != null) {
            for (onDataReceivedListener listener : m_onDataReceivedListener) {
                listener.onReceive(map);
            }
        }
    }

    public static PairListener model() {
        if (m_obj == null) {
            m_obj = new PairListener();
        }
        return m_obj;
    }
}
