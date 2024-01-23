package com.noti.main.service.refiler;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.noti.main.Application;
import com.noti.main.BuildConfig;
import com.noti.main.service.NotiListenerService;
import com.noti.main.service.refiler.db.RemoteFolderDoc;
import com.noti.main.utils.PowerUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RemoteFileProcess {

    private static boolean isBusy = false;

    public static void onReceive(Map<String, String> map, Context context) {
        PowerUtils.getInstance(context).acquire(8 * 60 * 1000L); // 8 Minutes Timeout until re-locked
        SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
        String type = map.get(ReFileConst.TASK_TYPE);

        if(type != null) switch (type) {
            case ReFileConst.TYPE_REQUEST_QUERY -> {
                if(isBusy) {
                    pushResponseQuery(context, map.get("device_name"), map.get("device_id"), false, "Query process is already busy");
                }

                isBusy = true;
                FirebaseStorage storage = FirebaseStorage.getInstance();
                StorageReference storageRef = storage.getReferenceFromUrl("gs://notisender-41c1b.appspot.com");
                StorageReference fileRef = storageRef.child(prefs.getString("UID", "") + "/deviceFileQuery/" + NotiListenerService.getUniqueID());
                StorageMetadata metadata = new StorageMetadata.Builder().setContentType("application/json").build();

                String internalPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                Map<String, Object> drives = new HashMap<>();
                File[] allExternalFilesDirs = ContextCompat.getExternalFilesDirs(context, null);

                for(File filesDir : allExternalFilesDirs) {
                    if(filesDir != null) {
                        int nameSubPos = filesDir.getAbsolutePath().lastIndexOf("/Android/data");
                        if(nameSubPos > 0) {
                            String filesDirName = filesDir.getAbsolutePath().substring(0, nameSubPos);
                            RemoteFolderDoc remoteFolderDoc =  new RemoteFolderDoc(prefs, new File(filesDirName));

                            if(filesDirName.equals(internalPath)) {
                                drives.put(ReFileConst.DATA_TYPE_INTERNAL_STORAGE, remoteFolderDoc.getLists());
                            } else {
                                String[] dividerArr = filesDirName.split("/");
                                drives.put(dividerArr[dividerArr.length - 1], remoteFolderDoc.getLists());
                            }
                        }
                    }
                }

                drives.put(ReFileConst.DATA_TYPE_LAST_MODIFIED, Calendar.getInstance().getTimeInMillis());
                fileRef.putBytes(new JSONObject(drives).toString().getBytes(StandardCharsets.UTF_8), metadata)
                        .addOnSuccessListener(task -> {
                            pushResponseQuery(context, map.get("device_name"), map.get("device_id"), true, null);
                            isBusy = false;
                        }).addOnFailureListener(e -> {
                            pushResponseQuery(context, map.get("device_name"), map.get("device_id"), true, e.getMessage());
                            isBusy = false;
                        });
            }

            case ReFileConst.TYPE_REQUEST_SEND -> {
                String fileName = map.get(ReFileConst.DATA_PATH);
                new FileTransferService(context, false)
                    .setUploadProperties(fileName, URLConnection.guessContentTypeFromName(fileName),false, map.get("device_name"), map.get("device_id"))
                    .execute();
            }

            case ReFileConst.TYPE_RESPONSE_QUERY -> {
                if(ReFileListeners.m_onFileQueryResponseListener != null) {
                    ReFileListeners.m_onFileQueryResponseListener.onReceive(Boolean.parseBoolean(map.get(ReFileConst.DATA_RESULTS)), map.get(ReFileConst.DATA_ERROR_CAUSE));
                }
            }

            case ReFileConst.TYPE_RESPONSE_SEND -> {
                if(ReFileListeners.m_onFileSendResponseListener != null) {
                    ReFileListeners.m_onFileSendResponseListener.onReceive(map.get(ReFileConst.DATA_PATH), Boolean.parseBoolean(map.get(ReFileConst.DATA_RESULTS)));
                }
            }

            case ReFileConst.TYPE_REQUEST_METADATA -> {
                String filePath = map.get(ReFileConst.DATA_PATH);
                File file = new File(Objects.requireNonNull(filePath));

                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("hash", getFileMD5Hash(file));

                    pushFileMetadata(context, map.get("device_name"), map.get("device_id"), filePath, true, jsonObject);
                } catch (JSONException | NoSuchAlgorithmException | IOException e) {
                    pushFileMetadata(context, map.get("device_name"), map.get("device_id"), filePath, true, null);
                    e.printStackTrace();
                }
            }

            case ReFileConst.TYPE_RESPONSE_METADATA -> {
                String filePath = map.get(ReFileConst.DATA_PATH);
                if(FileDetailActivity.mOnFileMetadataReceivedListener != null) {
                    if(FileDetailActivity.remoteFile != null && FileDetailActivity.remoteFile.getPath().equals(filePath)) {
                        try {
                            String result = map.get(ReFileConst.DATA_RESULTS);
                            if(result == null || result.isEmpty()) {
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

    private static String getFileMD5Hash(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        InputStream is = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
        }

        is.close();
        byte[] md5sum = digest.digest();
        BigInteger bigInt = new BigInteger(1, md5sum);
        return bigInt.toString(16);
    }

    public static void pushRequestQuery(Context context, String device_name, String device_id) {
        try {
            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type","pair|remote_file");
            notificationBody.put(ReFileConst.TASK_TYPE, ReFileConst.TYPE_REQUEST_QUERY);
            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", device_name);
            notificationBody.put("send_device_id", device_id);

            if(BuildConfig.DEBUG) Log.d("data-receive", notificationBody.toString());
            NotiListenerService.sendNotification(notificationBody, "com.noti.main", context);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage() );
        }
    }

    public static void pushResponseQuery(Context context, String device_name, String device_id, boolean isSuccess, @Nullable String errorCause) {
        try {
            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type","pair|remote_file");
            notificationBody.put(ReFileConst.TASK_TYPE, ReFileConst.TYPE_RESPONSE_QUERY);
            notificationBody.put(ReFileConst.DATA_RESULTS, Boolean.toString(isSuccess));
            if(errorCause != null) notificationBody.put(ReFileConst.DATA_ERROR_CAUSE, errorCause);

            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", device_name);
            notificationBody.put("send_device_id", device_id);

            if(BuildConfig.DEBUG) Log.d("data-receive", notificationBody.toString());
            NotiListenerService.sendNotification(notificationBody, "com.noti.main", context);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage() );
        }
    }

    public static void pushRequestFile(Context context, String device_name, String device_id, String path) {
        try {
            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type","pair|remote_file");
            notificationBody.put(ReFileConst.TASK_TYPE, ReFileConst.TYPE_REQUEST_SEND);
            notificationBody.put(ReFileConst.DATA_PATH, path);
            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", device_name);
            notificationBody.put("send_device_id", device_id);

            if(BuildConfig.DEBUG) Log.d("data-receive", notificationBody.toString());
            NotiListenerService.sendNotification(notificationBody, "com.noti.main", context);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage() );
        }
    }

    public static void pushResponseFile(Context context, String device_name, String device_id, boolean isSuccess, String fileName) {
        try {
            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type","pair|remote_file");
            notificationBody.put(ReFileConst.TASK_TYPE, ReFileConst.TYPE_RESPONSE_SEND);
            notificationBody.put(ReFileConst.DATA_PATH, fileName);
            notificationBody.put(ReFileConst.DATA_RESULTS, Boolean.toString(isSuccess));

            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", device_name);
            notificationBody.put("send_device_id", device_id);

            if(BuildConfig.DEBUG) Log.d("data-receive", notificationBody.toString());
            NotiListenerService.sendNotification(notificationBody, "com.noti.main", context);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage() );
        }
    }

    public static void pushFileMetadata(Context context, String device_name, String device_id, String fileName, boolean isResponding, @Nullable JSONObject data) {
        try {
            JSONObject notificationBody = new JSONObject();
            notificationBody.put("type","pair|remote_file");
            notificationBody.put(ReFileConst.TASK_TYPE, isResponding ? ReFileConst.TYPE_RESPONSE_METADATA : ReFileConst.TYPE_REQUEST_METADATA);
            notificationBody.put(ReFileConst.DATA_PATH, fileName);
            if(data != null) {
                notificationBody.put(ReFileConst.DATA_RESULTS, data);
            }

            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("send_device_name", device_name);
            notificationBody.put("send_device_id", device_id);

            if(BuildConfig.DEBUG) Log.d("data-receive", notificationBody.toString());
            NotiListenerService.sendNotification(notificationBody, "com.noti.main", context);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage() );
        }
    }
}
