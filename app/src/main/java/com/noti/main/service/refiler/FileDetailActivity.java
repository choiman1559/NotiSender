package com.noti.main.service.refiler;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.appbar.MaterialToolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.noti.main.R;
import com.noti.main.utils.BillingHelper;
import com.noti.main.utils.ui.PrefsCard;
import com.noti.main.utils.ui.ToastHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class FileDetailActivity extends AppCompatActivity {

    public static RemoteFile remoteFile;
    public static onFileMetadataReceivedListener mOnFileMetadataReceivedListener;
    public interface onFileMetadataReceivedListener {
        void onMetadataReceived(JSONObject data) throws JSONException;
    }

    public static final String EXTRA_REMOTE_FILE = "remoteFile";
    public static final String EXTRA_LAST_MODIFIED = "lastModified";
    public static final String EXTRA_DEVICE_ID = "deviceId";
    public static final String EXTRA_DEVICE_NAME = "deviceName";
    public static final String EXTRA_FILE_ICON = "iconRes";

    private boolean isHashReceived = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refile_detail);
        Intent intent = getIntent();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            remoteFile = intent.getSerializableExtra(EXTRA_REMOTE_FILE, RemoteFile.class);
        } else {
            remoteFile = (RemoteFile) intent.getSerializableExtra(EXTRA_REMOTE_FILE);
        }

        String deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);
        String deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);

        PrefsCard fileNameItem = findViewById(R.id.fileNameItem);
        PrefsCard filePathItem = findViewById(R.id.filePathItem);
        PrefsCard fileDateItem = findViewById(R.id.fileDateItem);
        PrefsCard fileSizeItem = findViewById(R.id.fileSizeItem);
        PrefsCard fileHashItem = findViewById(R.id.fileHashItem);

        LinearLayout waringLayout = findViewById(R.id.waringLayout);
        TextView waringTextView = findViewById(R.id.waringText);
        ExtendedFloatingActionButton downloadButton = findViewById(R.id.downloadButton);

        String bigFileWarning = "";
        boolean isSubscribed = BillingHelper.getInstance().isSubscribedOrDebugBuild();
        if (remoteFile.getSize() > (isSubscribed ? 2147483648L : 104857600)) {
            downloadButton.setEnabled(false);
            bigFileWarning = isSubscribed ? """
                                    The file is too large to download!
                                    Maximum download file size is 2GB.
                                    """ : """
                                    The file is too large to download!
                                    The maximum download file size is 100MB,
                                    increasing up to 2GB with subscription.
                                    """;
        } else {
            if(!remoteFile.getPath().startsWith("/storage/emulated/0")) {
                bigFileWarning = "Warning: Downloading files from the SD card may fail.";
            }
        }

        fileNameItem.setIconDrawable(intent.getIntExtra(EXTRA_FILE_ICON, R.drawable.ic_fluent_document_24_regular));
        fileNameItem.setDescription(remoteFile.getName());
        filePathItem.setDescription(remoteFile.getPath().replace(remoteFile.getName(), ""));
        fileDateItem.setDescription(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.getDefault()).format(intent.getLongExtra(EXTRA_LAST_MODIFIED, 0)));
        fileSizeItem.setDescription(remoteFile.getSize() + " Bytes");

        if(bigFileWarning.isEmpty()) {
            waringLayout.setVisibility(View.GONE);
        } else {
            waringTextView.setText(bigFileWarning);
        }

        downloadButton.setOnClickListener(v -> {
            if(deviceId == null) {
                throw new IllegalStateException("Device ID is Null");
            }

            AtomicInteger deviceSelection = new AtomicInteger();
            Set<String> deviceLists = getSharedPreferences("com.noti.main_pair", MODE_PRIVATE).getStringSet("paired_list", new HashSet<>());

            String[] deviceNameList = new String[deviceLists.size()];
            String[] deviceIdList = new String[deviceLists.size()];

            int deviceCount = 1;
            deviceNameList[0] = "This Device";

            for(String string : deviceLists) {
                String[] data = string.split("\\|");
                if(deviceId.equals(data[1])) {
                    continue;
                }

                deviceNameList[deviceCount] = data[0];
                deviceIdList[deviceCount] = data[1];
                deviceCount += 1;
            }

            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Select device to download the file");
            dialog.setSingleChoiceItems(deviceNameList, 0, (dialog12, which) -> deviceSelection.set(which - 1));
            dialog.setPositiveButton("Download", (dialog1, which) -> {
                FileTransferService transferService = new FileTransferService(this, true)
                        .setDownloadProperties(remoteFile.getName(), true);

                if(deviceSelection.get() > 0) {
                    transferService.setProxyDownload(deviceIdList[deviceSelection.get() + 1], deviceNameList[deviceSelection.get() + 1]);
                }

                transferService.execute();
                RemoteFileProcess.pushRequestFile(this, deviceName, deviceId, remoteFile.getPath());
                ToastHelper.show(this, "Download started at background\nWatch notification to check progress", "Okay", ToastHelper.LENGTH_SHORT);
                downloadButton.setEnabled(false);
            });
            dialog.setNegativeButton("Cancel", (dialog1, which) -> { });
            dialog.show();
        });

        fileHashItem.setOnClickListener(v -> {
            if(isHashReceived) {
                copyInfoClip(fileHashItem.getDescriptionString());
            } else {
                fileHashItem.setDescription("Getting file hash...");
                RemoteFileProcess.pushFileMetadata(this, deviceName, deviceId, remoteFile.getPath(), false, null);
            }
        });

        fileNameItem.setOnClickListener(v -> copyInfoClip(fileNameItem.getDescriptionString()));
        filePathItem.setOnClickListener(v -> copyInfoClip(filePathItem.getDescriptionString()));
        fileDateItem.setOnClickListener(v -> copyInfoClip(fileDateItem.getDescriptionString()));
        fileSizeItem.setOnClickListener(v -> copyInfoClip(String.valueOf(remoteFile.getSize())));

        mOnFileMetadataReceivedListener = (data) -> {
            String hash = (String) (data == null ? null : data.get("hash"));
            runOnUiThread(() -> fileHashItem.setDescription(Objects.requireNonNullElse(hash, "Error while getting file hash")));

            if(data != null) {
                isHashReceived = true;
            }
        };

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }

    void copyInfoClip(String data) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("File Details", data);
        clipboard.setPrimaryClip(clip);
        ToastHelper.show(this, "Copied!", "Okay", ToastHelper.LENGTH_SHORT);
    }
}
