package com.noti.main.service.refiler;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StreamDownloadTask;
import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.service.refiler.db.RemoteFile;
import com.noti.main.utils.BillingHelper;
import com.noti.main.utils.ui.ToastHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.CharacterIterator;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class ReFileActivity extends AppCompatActivity {

    SharedPreferences prefs;
    String device_name;
    String device_id;
    RemoteFile allFileList;
    RemoteFile lastRemoteFile;

    ScrollView remoteFileScrollView;
    SwipeRefreshLayout remoteFileRefreshLayout;
    LinearLayoutCompat remoteFileLayout;
    LinearLayoutCompat remoteFileStateLayout;
    TextView remoteFileErrorEmoji;
    TextView remoteFileStateDescription;
    ProgressBar remoteFileStateProgress;

    OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (lastRemoteFile != null) {
                if (allFileList.getPath().equals(lastRemoteFile.getPath())) {
                    ReFileActivity.this.finish();
                } else {
                    lastRemoteFile = lastRemoteFile.getParent();
                    loadFileListLayout();
                }
            }
        }
    };

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refiler);
        getWindow().setStatusBarColor(getResources().getColor(R.color.ui_bg_toolbar));
        prefs = getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);

        Intent intent = getIntent();
        device_name = intent.getStringExtra("device_name");
        device_id = intent.getStringExtra("device_id");

        remoteFileScrollView = findViewById(R.id.remoteFileScrollView);
        remoteFileRefreshLayout = findViewById(R.id.remoteFileRefreshLayout);
        remoteFileLayout = findViewById(R.id.remoteFileLayout);
        remoteFileStateLayout = findViewById(R.id.remoteFileStateLayout);
        remoteFileErrorEmoji = findViewById(R.id.remoteFileErrorEmoji);
        remoteFileStateProgress = findViewById(R.id.remoteFileStateProgress);
        remoteFileStateDescription = findViewById(R.id.remoteFileStateDescription);

        remoteFileLayout.setVisibility(View.GONE);
        remoteFileErrorEmoji.setVisibility(View.GONE);
        remoteFileScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> remoteFileRefreshLayout.setEnabled(remoteFileLayout.getVisibility() == View.VISIBLE && remoteFileScrollView.getScrollY() == 0));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());

        getOnBackPressedDispatcher().addCallback(backPressedCallback);
        loadQueryFromDB();
    }

    void loadQueryFromDB() {
        showProgress("Retrieving file list from database...");
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReferenceFromUrl("gs://notisender-41c1b.appspot.com");
        StorageReference fileRef = storageRef.child(prefs.getString("UID", "") + "/deviceFileQuery/" + device_id);
        StreamDownloadTask downloadTask = fileRef.getStream();

        downloadTask.addOnSuccessListener(taskSnapshot -> {
            showProgress("Parsing file list...");
            new Thread(() -> {
                RemoteFile remoteFile = null;
                InputStream stream = taskSnapshot.getStream();
                StringBuilder textBuilder = new StringBuilder();
                try (Reader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    int c;
                    while ((c = reader.read()) != -1) {
                        textBuilder.append((char) c);
                    }
                } catch (IOException e) {
                    runOnUiThread(() -> showError("An error occurred: Cannot read from stream"));
                    e.printStackTrace();
                }

                try {
                    remoteFile = new RemoteFile(new JSONObject(textBuilder.toString()));
                } catch (JSONException e) {
                    runOnUiThread(() -> showError("An error occurred: Cannot parse raw JSON"));
                    e.printStackTrace();
                }

                if (remoteFile != null) {
                    allFileList = remoteFile;
                    runOnUiThread(this::loadFileListLayout);
                }
            }).start();
        });

        downloadTask.addOnFailureListener(e -> loadFreshQuery());
    }

    @SuppressLint("SetTextI18n")
    void loadFreshQuery() {
        showProgress("Querying file list from target device...\n\nThis task may take some time");
        RemoteFileProcess.pushRequestQuery(this, device_name, device_id);
        ReFileListeners.setOnFileQueryResponseListener((isSuccess, errorCause) -> runOnUiThread(() -> {
            if (isSuccess) {
                loadQueryFromDB();
            } else {
                showError("An error occurred: " + errorCause);
            }
        }));
    }

    void loadFileListLayout() {
        showProgress("Showing file list...");
        boolean listFolderFirst = prefs.getBoolean("listFolderFirst", true);

        remoteFileRefreshLayout.setOnRefreshListener(() -> {
            remoteFileRefreshLayout.setRefreshing(false);
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Reload File List");
            dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
            dialog.setMessage("""
                    Select where to get the list:
                    
                    Database: Data cached and uploaded in advance
                    Target device: Overwrite pre-cached data and query the list anew
                    """);
            dialog.setNegativeButton("Database", (d, w) -> loadQueryFromDB());
            dialog.setPositiveButton("Target Device", (d, w) -> loadFreshQuery());
            dialog.setNeutralButton("Cancel", (d, w) -> { });
            dialog.show();
        });
        remoteFileLayout.removeViews(0, remoteFileLayout.getChildCount());

        if (lastRemoteFile == null) lastRemoteFile = allFileList;
        if (!lastRemoteFile.getPath().equals(allFileList.getPath())) {
            LinearLayout goParentLayout = (LinearLayout) View.inflate(this, R.layout.cardview_refile_item, null);
            new RemoteFileHolder(null, goParentLayout, true, false);
            goParentLayout.setOnClickListener(v -> {
                lastRemoteFile = lastRemoteFile.getParent();
                loadFileListLayout();
            });

            remoteFileLayout.addView(goParentLayout);
        }

        if(lastRemoteFile.isIndexSkipped()) {
            LinearLayout skippedLayout = (LinearLayout) View.inflate(this, R.layout.cardview_refile_item, null);
            new RemoteFileHolder(lastRemoteFile, skippedLayout, false, true);
            remoteFileLayout.addView(skippedLayout);
        } else {
            ArrayList<RemoteFileHolder> folders = new ArrayList<>();
            ArrayList<RemoteFileHolder> files = new ArrayList<>();

            for (RemoteFile file : lastRemoteFile.getList()) {
                LinearLayout layout = (LinearLayout) View.inflate(this, R.layout.cardview_refile_item, null);
                RemoteFileHolder holder = new RemoteFileHolder(file, layout, false, false);

                layout.setOnClickListener(v -> {
                    if (file.isFile()) {
                        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
                        String bigFileWarning = "";
                        boolean isSubscribed = BillingHelper.getInstance().isSubscribedOrDebugBuild();
                        if (file.getSize() > (isSubscribed ? 2147483648L : 104857600)) {
                            bigFileWarning = isSubscribed ? """
                                    <br>
                                    The file is too large to download!<br>
                                    Maximum download file size is 2GB.
                                    """ : """
                                    <br>
                                    The file is too large to download!<br>
                                    The maximum download file size is 100MB,<br>
                                    increasing up to 2GB with subscription.
                                    """;
                        } else {
                            if(!file.getPath().startsWith("/storage/emulated/0")) {
                                bigFileWarning = """
                                        <br>
                                        Warning: Downloading files from the SD card may fail.
                                        """;
                            }

                            dialog.setPositiveButton("Download", (d, w) -> {
                                new FileTransferService(this, true)
                                        .setDownloadProperties(file.getName(), true)
                                        .execute();
                                RemoteFileProcess.pushRequestFile(this, device_name, device_id, file.getPath());
                                ToastHelper.show(this, "Download started at background\nWatch notification to check progress", "Okay", ToastHelper.LENGTH_SHORT);
                            });
                        }

                        dialog.setTitle("File information/download");
                        dialog.setIcon(R.drawable.ic_fluent_cloud_arrow_down_24_regular);
                        dialog.setMessage(Html.fromHtml(String.format(Locale.getDefault(), """
                                <b>Name:</b> %s<br>
                                <b>Path:</b> %s<br>
                                <b>Queried time:</b> %s<br>
                                <b>Size:</b> %s Bytes
                                <br>%s
                                """,
                                file.getName(),
                                file.getPath().replace(file.getName(), ""),
                                new SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.getDefault()).format(allFileList.getLastModified()),
                                file.getSize(),
                                bigFileWarning
                        )));

                        dialog.setNegativeButton("Cancel", (d, w) -> { });
                        dialog.show();
                    } else {
                        lastRemoteFile = file;
                        loadFileListLayout();
                    }
                });

                if (!listFolderFirst || holder.remoteFile.isFile()) {
                    files.add(holder);
                } else {
                    folders.add(holder);
                }
            }

            if(listFolderFirst) Collections.sort(folders);
            Collections.sort(files);

            if(listFolderFirst) for (RemoteFileHolder holder : folders) {
                remoteFileLayout.addView(holder.parentView);
            }

            for (RemoteFileHolder holder : files) {
                remoteFileLayout.addView(holder.parentView);
            }
        }

        setFileListVisibility(true);
    }

    void showProgress(String message) {
        setFileListVisibility(false);
        remoteFileErrorEmoji.setVisibility(View.GONE);
        remoteFileStateProgress.setVisibility(View.VISIBLE);
        remoteFileStateDescription.setText(message);
    }

    void showError(String errorMessage) {
        setFileListVisibility(false);
        remoteFileErrorEmoji.setVisibility(View.VISIBLE);
        remoteFileStateProgress.setVisibility(View.GONE);
        remoteFileStateDescription.setText(errorMessage);
    }

    void setFileListVisibility(boolean visible) {
        remoteFileLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        remoteFileStateLayout.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    static class RemoteFileHolder implements Comparable<RemoteFileHolder> {

        View parentView;
        RemoteFile remoteFile;
        ImageView remoteFileIcon;
        TextView remoteFileTitle;
        TextView remoteFileDescription;

        @SuppressLint("SetTextI18n")
        public RemoteFileHolder(RemoteFile remoteFile, View view, boolean isGoingParentButton, boolean isSkipped) {
            this.parentView = view;
            this.remoteFile = remoteFile;
            this.remoteFileIcon = view.findViewById(R.id.remoteFileIcon);
            this.remoteFileTitle = view.findViewById(R.id.remoteFileTitle);
            this.remoteFileDescription = view.findViewById(R.id.remoteFileDescription);

            if(isSkipped) {
                remoteFileTitle.setText("This folder is not indexed");
                remoteFileDescription.setText("Indexing was skipped because\nthe folder had too much content");
                remoteFileIcon.setImageResource(R.drawable.ic_fluent_warning_24_regular);
            } else if (isGoingParentButton) {
                remoteFileTitle.setText("...");
                remoteFileDescription.setText("Parent folder");
                remoteFileIcon.setImageResource(R.drawable.ic_fluent_folder_24_filled);
            } else {
                String description = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.getDefault()).format(remoteFile.getLastModified());
                if (remoteFile.isFile()) {
                    description += " " + humanReadableByteCountBin(remoteFile.getSize());
                }

                if (remoteFile.getPath().equals("/storage/emulated/0")) {
                    remoteFileTitle.setText("Internal Storage");
                } else remoteFileTitle.setText(remoteFile.getName());

                remoteFileDescription.setText(description);

                if(remoteFile.isFile()) {
                    String mime = URLConnection.guessContentTypeFromName(remoteFile.getPath());
                    if(mime != null) {
                        String[] mimeArr = mime.split("/");
                        int resId = switch (mimeArr[0]) {
                            case "audio" -> R.drawable.ic_fluent_music_note_1_24_regular;
                            case "video" -> R.drawable.ic_fluent_video_clip_24_regular;
                            case "image" -> R.drawable.ic_fluent_image_24_regular;
                            case "text" -> R.drawable.ic_fluent_slide_text_24_regular;
                            case "font" -> R.drawable.ic_fluent_text_font_size_24_regular;
                            case "application" -> {
                                if(mimeArr[1].contains("zip") || mimeArr[1].contains("tar") || mimeArr[1].contains("rar") || mimeArr[1].contains("7z"))
                                    yield R.drawable.ic_fluent_folder_zip_24_regular;
                                else yield  R.drawable.ic_fluent_document_24_regular;
                            }
                            default -> R.drawable.ic_fluent_document_24_regular;
                        };
                        remoteFileIcon.setImageResource(resId);
                    } else {
                        remoteFileIcon.setImageResource(R.drawable.ic_fluent_document_24_regular);
                    }
                } else {
                    remoteFileIcon.setImageResource(R.drawable.ic_fluent_folder_24_filled);
                }
            }
        }

        @Override
        public int compareTo(RemoteFileHolder o) {
            return o.remoteFile.compareTo(this.remoteFile);
        }
    }

    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format(Locale.getDefault(), "%.1f %ciB", value / 1024.0, ci.current());
    }
}
