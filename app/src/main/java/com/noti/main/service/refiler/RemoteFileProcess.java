package com.noti.main.service.refiler;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.receiver.plugin.PluginActions;
import com.noti.main.receiver.plugin.PluginHostInject;
import com.noti.main.receiver.plugin.PluginPrefs;
import com.noti.main.service.NotiListenerService;
import com.noti.main.service.backend.PacketConst;
import com.noti.main.service.backend.PacketRequester;
import com.noti.main.service.backend.ResultPacket;
import com.noti.main.ui.prefs.custom.PluginFragment;
import com.noti.main.utils.PowerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class RemoteFileProcess {

    public static boolean isBusy = false;
    public static Thread pluginWaitThread;
    private static ArrayList<String> waitingDeviceList;
    public static final boolean USE_CONTENT_PROVIDER_IPC = true;

    public static void onReceive(Map<String, String> map, Context context) {
        PowerUtils.getInstance(context).acquire(8 * 60 * 1000L); // 8 Minutes Timeout until re-locked
        String type = map.get(ReFileConst.TASK_TYPE);

        if (type != null) switch (type) {
            case ReFileConst.TYPE_REQUEST_QUERY -> {
                String deviceInfo = map.get("device_name") + "|" + map.get("device_id");

                if (isBusy) {
                    if(!waitingDeviceList.contains(deviceInfo)) {
                        waitingDeviceList.add(deviceInfo);
                    }

                    pushResponseQuery(context, map.get("device_name"), map.get("device_id"), false, "Query process is already busy!\nWaiting for process to finish...");
                    return;
                }

                if(!PluginFragment.isAppInstalled(context, PluginHostInject.HostInjectAPIName.PLUGIN_FILE_LIST_PACKAGE)) {
                    pushResponseQuery(context, map.get("device_name"), map.get("device_id"), false, "Plugin Not Installed");
                    return;
                }

                if(!new PluginPrefs(context, PluginHostInject.HostInjectAPIName.PLUGIN_FILE_LIST_PACKAGE).isPluginEnabled()) {
                    pushResponseQuery(context, map.get("device_name"), map.get("device_id"), false, "Plugin Not Enabled");
                    return;
                }

                isBusy = true;
                waitingDeviceList = new ArrayList<>();
                waitingDeviceList.add(deviceInfo);

                SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
                String args = String.format(Locale.getDefault(),"%d|%s", prefs.getInt("indexMaximumSize", 150), prefs.getBoolean("indexHiddenFiles", false));

                PluginActions.requestHostApiInject(context, deviceInfo, PluginHostInject.HostInjectAPIName.PLUGIN_FILE_LIST_PACKAGE, PluginHostInject.HostInjectAPIName.ACTION_REQUEST_FILE_LIST, args);
                startFilePluginWaitThread(context);
            }

            case ReFileConst.TYPE_REQUEST_SEND -> {
                String fileName = map.get(ReFileConst.DATA_PATH);
                String deviceInfo = map.get("device_name") + "|" + map.get("device_id");
                PluginActions.requestHostApiInject(context, deviceInfo, PluginHostInject.HostInjectAPIName.PLUGIN_FILE_LIST_PACKAGE, PluginHostInject.HostInjectAPIName.ACTION_REQUEST_UPLOAD, fileName);
            }

            case ReFileConst.TYPE_RESPONSE_QUERY -> {
                if (ReFileListeners.m_onFileQueryResponseListener != null) {
                    ReFileListeners.m_onFileQueryResponseListener.onReceive(Boolean.parseBoolean(map.get(ReFileConst.DATA_RESULTS)), map.get(ReFileConst.DATA_ERROR_CAUSE));
                }
            }

            case ReFileConst.TYPE_RESPONSE_SEND -> {
                if (ReFileListeners.m_onFileSendResponseListener != null) {
                    ReFileListeners.m_onFileSendResponseListener.onReceive(map.get(ReFileConst.DATA_PATH), Boolean.parseBoolean(map.get(ReFileConst.DATA_RESULTS)));
                }
            }

            case ReFileConst.TYPE_REQUEST_METADATA -> {
                String filePath = map.get(ReFileConst.DATA_PATH);
                String deviceInfo = map.get("device_name") + "|" + map.get("device_id");
                PluginActions.requestHostApiInject(context, deviceInfo, PluginHostInject.HostInjectAPIName.PLUGIN_FILE_LIST_PACKAGE, PluginHostInject.HostInjectAPIName.ACTION_REQUEST_METADATA, filePath);
            }

            case ReFileConst.TYPE_RESPONSE_METADATA -> {
                String filePath = map.get(ReFileConst.DATA_PATH);
                if (FileDetailActivity.mOnFileMetadataReceivedListener != null) {
                    if (FileDetailActivity.remoteFile != null && FileDetailActivity.remoteFile.getPath().equals(filePath)) {
                        try {
                            String result = map.get(ReFileConst.DATA_RESULTS);
                            if (result == null || result.isEmpty()) {
                                FileDetailActivity.mOnFileMetadataReceivedListener.onMetadataReceived(null);
                            } else {
                                JSONObject object = new JSONObject(result);
                                FileDetailActivity.mOnFileMetadataReceivedListener.onMetadataReceived(object);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private static void startFilePluginWaitThread(final Context context) {
        pluginWaitThread = new Thread(() -> {
            try {
                if(!pluginWaitThread.isInterrupted()) {
                    Thread.sleep(15000);
                }
                isBusy = false;

                for(String devices : waitingDeviceList) {
                    String[] devicesInfo = devices.split("\\|");
                    pushResponseQuery(context, devicesInfo[0], devicesInfo[1], false, "File plugin is not responding");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        pluginWaitThread.start();
    }

    public static void killFilePluginWaitThread() {
        if(RemoteFileProcess.isBusy && RemoteFileProcess.pluginWaitThread.isAlive()) {
            if(BuildConfig.DEBUG)
                Log.d("File Process", "Killed plugin wait thread");
            RemoteFileProcess.pluginWaitThread.interrupt();
        }
    }

    public static void onFileListReceived(Context context, String channelName) {
        RemoteFileProcess.killFilePluginWaitThread();

        if(USE_CONTENT_PROVIDER_IPC) {
            try {
                Uri fileUri = Uri.parse("content://com.noti.plugin.filer.ReFileStreamProvider/" + channelName);
                ContentResolver contentResolver = context.getContentResolver();
                processFileList(context, Objects.requireNonNull(contentResolver.openInputStream(fileUri)));
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        } else try(LocalSocket clientSocket = new LocalSocket()) {
            clientSocket.connect(new LocalSocketAddress(channelName));
            InputStream inputStream = clientSocket.getInputStream();
            processFileList(context, inputStream);
        } catch (JSONException | IOException e) {
            e.printStackTrace();
            isBusy = false;
        }
    }

    static void processFileList(Context context, InputStream inputStream) throws IOException, JSONException {
        int i;
        StringBuilder buffer = new StringBuilder();
        byte[] b = new byte[4096];
        while((i = inputStream.read(b)) != -1){
            buffer.append(new String(b, 0, i));
        }
        String jsonData = buffer.toString();

        try {
            JSONObject serverBody = new JSONObject();
            serverBody.put(PacketConst.KEY_ACTION_TYPE, PacketConst.REQUEST_POST_LONG_TERM_DATA);
            serverBody.put(PacketConst.KEY_DATA_KEY, NotiListenerService.getUniqueID());
            serverBody.put(PacketConst.KEY_EXTRA_DATA, jsonData);

            PacketRequester.addToRequestQueue(context, PacketConst.SERVICE_TYPE_FILE_LIST, serverBody, response -> {
                try {
                    ResultPacket resultPacket = ResultPacket.parseFrom(response.toString());
                    if (resultPacket.isResultOk()) {
                        for(String devices : waitingDeviceList) {
                            String[] devicesInfo = devices.split("\\|");
                            pushResponseQuery(context, devicesInfo[0], devicesInfo[1], true, null);
                        }
                    } else {
                        sendErrorPacket(context, resultPacket.getErrorCause());
                    }
                } catch (IOException e) {
                    sendErrorPacket(context, e.getMessage());
                    e.printStackTrace();
                } finally {
                    isBusy = false;
                }
            }, e -> sendErrorPacket(context, e.getMessage()));
        } catch (Exception e) {
            sendErrorPacket(context, e.getMessage());
            e.printStackTrace();
        }
    }

    protected static void sendErrorPacket(Context context, String message) {
        for(String devices : waitingDeviceList) {
            String[] devicesInfo = devices.split("\\|");
            pushResponseQuery(context, devicesInfo[0], devicesInfo[1], false, message);
        }
    }

    public static void onFileUploadReceived(Context context, String deviceName, String deviceId, String fileName, String channelName) {
        FileTransferService transferService = new FileTransferService(context, false);
        if(channelName.startsWith("Error")) {
            transferService.setUploadProperties(fileName, null, null, true, deviceName, deviceId);
        } else {
            transferService = transferService.setUploadProperties(fileName, URLConnection.guessContentTypeFromName(fileName), channelName, false, deviceName, deviceId);
        }
        transferService.execute();
    }

    public static void pushRequestQuery(Context context, String device_name, String device_id) {
        try {
            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type", "pair|remote_file");
            notificationBody.put(ReFileConst.TASK_TYPE, ReFileConst.TYPE_REQUEST_QUERY);
            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", device_name);
            notificationBody.put("send_device_id", device_id);

            if (BuildConfig.DEBUG) Log.d("data-receive", notificationBody.toString());
            NotiListenerService.sendNotification(notificationBody, "com.noti.main", context);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
    }

    public static void pushResponseQuery(Context context, String device_name, String device_id, boolean isSuccess, @Nullable String errorCause) {
        try {
            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type", "pair|remote_file");
            notificationBody.put(ReFileConst.TASK_TYPE, ReFileConst.TYPE_RESPONSE_QUERY);
            notificationBody.put(ReFileConst.DATA_RESULTS, Boolean.toString(isSuccess));
            if (errorCause != null) notificationBody.put(ReFileConst.DATA_ERROR_CAUSE, errorCause);

            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", device_name);
            notificationBody.put("send_device_id", device_id);

            if (BuildConfig.DEBUG) Log.d("data-receive", notificationBody.toString());
            NotiListenerService.sendNotification(notificationBody, "com.noti.main", context);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
    }

    public static void pushRequestFile(Context context, String device_name, String device_id, String path) {
        try {
            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type", "pair|remote_file");
            notificationBody.put(ReFileConst.TASK_TYPE, ReFileConst.TYPE_REQUEST_SEND);
            notificationBody.put(ReFileConst.DATA_PATH, path);
            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", device_name);
            notificationBody.put("send_device_id", device_id);

            if (BuildConfig.DEBUG) Log.d("data-receive", notificationBody.toString());
            NotiListenerService.sendNotification(notificationBody, "com.noti.main", context);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
    }

    public static void pushResponseFile(Context context, String device_name, String device_id, boolean isSuccess, String fileName) {
        try {
            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type", "pair|remote_file");
            notificationBody.put(ReFileConst.TASK_TYPE, ReFileConst.TYPE_RESPONSE_SEND);
            notificationBody.put(ReFileConst.DATA_PATH, fileName);
            notificationBody.put(ReFileConst.DATA_RESULTS, Boolean.toString(isSuccess));

            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", device_name);
            notificationBody.put("send_device_id", device_id);

            if (BuildConfig.DEBUG) Log.d("data-receive", notificationBody.toString());
            NotiListenerService.sendNotification(notificationBody, "com.noti.main", context);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
    }

    public static void pushFileMetadata(Context context, String device_name, String device_id, String fileName, boolean isResponding, @Nullable JSONObject data) {
        try {
            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type", "pair|remote_file");
            notificationBody.put(ReFileConst.TASK_TYPE, isResponding ? ReFileConst.TYPE_RESPONSE_METADATA : ReFileConst.TYPE_REQUEST_METADATA);
            notificationBody.put(ReFileConst.DATA_PATH, fileName);
            if (data != null) {
                notificationBody.put(ReFileConst.DATA_RESULTS, data);
            }

            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", device_name);
            notificationBody.put("send_device_id", device_id);

            if (BuildConfig.DEBUG) Log.d("data-receive", notificationBody.toString());
            NotiListenerService.sendNotification(notificationBody, "com.noti.main", context);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
    }
}
