package com.noti.main.service.refiler;

import static android.content.Context.MODE_PRIVATE;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.OpenableColumns;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.service.pair.DataProcess;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class FileTransferService {

    private final Context mContext;
    private String fileName;
    private String fileType;
    private String device_name;
    private String device_id;

    private String channelName;
    private boolean isError;
    private boolean isUri;
    private boolean isNeedToWaitUpload;
    private final boolean isDownloadTask;

    public FileTransferService(Context context, boolean isDownloadTask) {
        this.mContext = context;
        this.isDownloadTask = isDownloadTask;
    }

    public FileTransferService setDownloadProperties(String fileName, boolean isNeedToWaitUpload) {
        this.fileName = fileName;
        this.isNeedToWaitUpload = isNeedToWaitUpload;

        return this;
    }

    public FileTransferService setUploadProperties(String fileName, String fileType, String deviceName, String deviceId) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.isUri = true;
        this.device_id = deviceId;
        this.device_name = deviceName;
        this.isError = false;

        return this;
    }

    public FileTransferService setUploadProperties(String fileName, String fileType, String channelName, boolean isError, String deviceName, String deviceId) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.isUri = false;
        this.device_id = deviceId;
        this.device_name = deviceName;
        this.isError = isError;
        this.channelName = channelName;

        return this;
    }

    public void execute() {
        Uri fileUri;
        String uriFileName;
        String cloudFilePath = mContext.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).getString("UID", "") + "/fileTransfer/";

        if(isUri) {
            fileUri = Uri.parse(fileName);
            Cursor returnCursor = mContext.getContentResolver().query(fileUri, null, null, null, null);
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            returnCursor.moveToFirst();
            uriFileName = returnCursor.getString(nameIndex);
            returnCursor.close();
            cloudFilePath += uriFileName;
        } else {
            fileUri = null;
            uriFileName = null;
            String[] fileNameArr = fileName.split("/");
            cloudFilePath += fileNameArr[fileNameArr.length - 1];
        }

        int notificationId = (int) ((new Date().getTime() / 1000L) % Integer.MAX_VALUE);
        String notificationChannel = "DownloadFile";
        NotificationManager mNotifyManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notificationChannel, "Download File Notification", NotificationManager.IMPORTANCE_DEFAULT);
            mNotifyManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mContext, notificationChannel)
                .setSmallIcon(R.drawable.ic_fluent_arrow_download_24_regular)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setGroupSummary(false)
                .setOngoing(true)
                .setAutoCancel(false)
                .setProgress(0, 0, true);

        if(isDownloadTask) {
            if (isNeedToWaitUpload) {
                mBuilder.setContentTitle("Waiting for file upload completion...")
                        .setContentText("File name: " + fileName);
            } else {
                mBuilder.setContentTitle("File Download")
                        .setContentText("File name: " + fileName);
            }
        } else {
            mBuilder.setContentTitle("File Uploading...")
                    .setContentText("File name: " + (isUri ? uriFileName : fileName));
        }

        OnFailureListener onFailureListener = e -> {
            e.printStackTrace();
            mBuilder.setContentText((isUri ? uriFileName : fileName) + (isDownloadTask  ? " download" : " upload") + " failed")
                    .setProgress(0, 0, false)
                    .setOngoing(false);

            if(!isDownloadTask) {
                mBuilder.setContentTitle("File Upload Failed");
                if(!isUri) {
                    RemoteFileProcess.pushResponseFile(mContext, device_name, device_id, false, fileName);
                }
            }

            mNotifyManager.notify(notificationId, mBuilder.build());
        };

        mNotifyManager.notify(notificationId, mBuilder.build());
        String finalCloudFilePath = cloudFilePath;

        Thread downloadThread = new Thread(() -> {
            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference storageRef = storage.getReferenceFromUrl("gs://notisender-41c1b.appspot.com");
            StorageReference fileRef = storageRef.child(finalCloudFilePath);

            try {
                File targetFile = new File(Environment.getExternalStorageDirectory(), "Download/NotiSender/" + fileName);
                targetFile.mkdirs();
                if (targetFile.exists()) targetFile.delete();
                targetFile.createNewFile();
                FileDownloadTask task = fileRef.getFile(targetFile);

                task.addOnFailureListener(onFailureListener);
                task.addOnSuccessListener(taskSnapshot -> {
                    mBuilder.setContentTitle("File Download Completed")
                            .setContentText("File name: " + fileName)
                            .setProgress(0, 0, false)
                            .setOngoing(false);
                    mNotifyManager.notify(notificationId, mBuilder.build());
                });
            } catch (IOException e) {
                e.printStackTrace();
                mNotifyManager.cancel(notificationId);
            }
        });

        Thread uploadThread = new Thread(() -> {
            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference storageRef = storage.getReferenceFromUrl("gs://notisender-41c1b.appspot.com");
            StorageReference fileRef = storageRef.child(finalCloudFilePath);
            StorageMetadata.Builder metaBuilder = new StorageMetadata.Builder();

            if(fileType != null) {
                metaBuilder.setContentType(fileType);
            }

            StorageMetadata metadata =  metaBuilder.build();
            UploadTask uploadTask = null;

            if(isUri && fileUri != null) {
                uploadTask = fileRef.putFile(fileUri, metadata);
            } else {
                try {
                    InputStream inputStream;
                    if(RemoteFileProcess.USE_CONTENT_PROVIDER_IPC) {
                        Uri providerFileUri = Uri.parse("content://com.noti.plugin.filer.ReFileStreamProvider/" + channelName);
                        ContentResolver contentResolver = mContext.getContentResolver();
                        inputStream = contentResolver.openInputStream(providerFileUri);
                    } else {
                        LocalSocket clientSocket = new LocalSocket();
                        clientSocket.connect(new LocalSocketAddress(channelName));
                        inputStream = clientSocket.getInputStream();
                    }

                    if(inputStream != null) {
                        uploadTask = fileRef.putStream(inputStream);
                    } else {
                        throw new IOException("UploadTask Failed to upload - closed InputStream IPC channel: " + channelName);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    onFailureListener.onFailure(e);
                }
            }

            if(uploadTask != null) {
                uploadTask.addOnFailureListener(onFailureListener);
                uploadTask.addOnSuccessListener(taskSnapshot -> {
                    if (isUri) {
                        DataProcess.requestAction(mContext, device_name, device_id, "Share file", uriFileName);
                    } else {
                        RemoteFileProcess.pushResponseFile(mContext, device_name, device_id, true, fileName);
                    }

                    mBuilder.setContentTitle("File Upload Completed")
                            .setContentText("File name: " + (isUri ? uriFileName : fileName))
                            .setProgress(0, 0, false)
                            .setOngoing(false);
                    mNotifyManager.notify(notificationId, mBuilder.build());
                });

                uploadTask.addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    mBuilder.setContentTitle("File Uploading...")
                            .setContentText("File name: " + (isUri ? uriFileName : fileName))
                            .setProgress(100, (int) progress, false);
                    mNotifyManager.notify(notificationId, mBuilder.build());
                });
            }
        });

        if(isDownloadTask) {
            if (isNeedToWaitUpload) {
                ReFileListeners.setFileSendResponseListener((fileName, isSuccess) -> {
                    String[] fileNameArr = fileName.split("/");
                    if(fileNameArr[fileNameArr.length - 1].equals(this.fileName)) {
                        if (isSuccess) {
                            downloadThread.start();
                        } else {
                            onFailureListener.onFailure(new Exception("Upload failed"));
                        }
                    }
                });
            } else {
                downloadThread.start();
            }
        } else if(isError){
            onFailureListener.onFailure(new Exception("Upload failed"));
        } else {
            uploadThread.start();
        }
    }
}
