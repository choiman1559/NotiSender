package com.noti.main.receiver.plugin;

import static com.noti.main.service.refiler.RemoteFileProcess.pushFileMetadata;

import android.content.Context;

import androidx.annotation.Nullable;

import com.noti.main.BuildConfig;
import com.noti.main.service.NotiListenerService;
import com.noti.main.service.refiler.RemoteFileProcess;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;

public class PluginHostInject {
    public static class HostInjectAPIName {
        public static final String ACTION_PUSH_MESSAGE_DATA = "push_message_data";
        public static final String ACTION_PUSH_CALL_DATA = "push_call_data";

        public static final String PLUGIN_FILE_LIST_PACKAGE = "com.noti.plugin.filer";
        public static final String ACTION_REQUEST_FILE_LIST = "request_file_list";
        public static final String ACTION_RESPONSE_FILE_LIST = "response_file_list";
        public static final String ACTION_REQUEST_UPLOAD = "request_file_upload";
        public static final String ACTION_RESPONSE_UPLOAD = "response_file_upload";
        public static final String ACTION_REQUEST_METADATA = "request_file_metadata";
        public static final String ACTION_RESPONSE_METADATA = "response_file_metadata";
    }

    public static void onHostInjectResponse(Context context, @Nullable String device, @Nullable String dataType, String... data) {
        String[] deviceInfo = new String[2];
        if(device != null) {
            deviceInfo = device.split("\\|");
        }

        if(dataType != null) switch (dataType) {
            case HostInjectAPIName.ACTION_PUSH_CALL_DATA ->
                    NotiListenerService.getInstance().sendTelecomNotification(context, BuildConfig.DEBUG, data[0], data.length > 1 ? data[1] : "");
            case HostInjectAPIName.ACTION_PUSH_MESSAGE_DATA ->
                    NotiListenerService.getInstance().sendSmsNotification(context, BuildConfig.DEBUG, "noti.func", data[0], data[1], data[2], Calendar.getInstance().getTime());
            case HostInjectAPIName.ACTION_RESPONSE_FILE_LIST ->
                    RemoteFileProcess.onFileListReceived(context, deviceInfo[0], deviceInfo[1], data[0]);
            case HostInjectAPIName.ACTION_RESPONSE_UPLOAD ->
                    RemoteFileProcess.onFileUploadReceived(context, deviceInfo[0], deviceInfo[1], data[0], data[1]);
            case HostInjectAPIName.ACTION_RESPONSE_METADATA -> {
                String filePath = data[0];
                String hashData = data[1];

                try {
                    if(hashData.startsWith("Error")) {
                        throw new IOException("Can't get hash d");
                    } else {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("hash", hashData);
                        pushFileMetadata(context, deviceInfo[0], deviceInfo[1], filePath, true, jsonObject);
                    }
                } catch (JSONException | IOException e) {
                    pushFileMetadata(context, deviceInfo[0], deviceInfo[1], filePath, true, null);
                    e.printStackTrace();
                }
            }
        }
    }
}
