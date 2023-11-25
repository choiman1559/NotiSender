package com.noti.main.service.refiler;

public class ReFileListeners {
    public static onResponseListener m_onFileQueryResponseListener;
    public static onFileSendResponseListener m_onFileSendResponseListener;
    private static ReFileListeners m_obj;

    public interface onResponseListener {
        void onReceive(boolean isSuccess, String errorMessage);
    }

    public interface onFileSendResponseListener {
        void onReceive(String fileName, boolean isSuccess);
    }

    public static void setOnFileQueryResponseListener(onResponseListener mOnFileQueryResponseListener) {
        ReFileListeners.m_onFileQueryResponseListener = mOnFileQueryResponseListener;
    }

    public static void setFileSendResponseListener(onFileSendResponseListener m_onFileSendResponseListener) {
        ReFileListeners.m_onFileSendResponseListener = m_onFileSendResponseListener;
    }

    public static ReFileListeners model() {
        if (m_obj == null) {
            m_obj = new ReFileListeners();
        }
        return m_obj;
    }
}
