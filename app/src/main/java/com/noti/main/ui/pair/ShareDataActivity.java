package com.noti.main.ui.pair;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.noti.main.R;
import com.noti.main.service.pair.DataProcess;
import com.noti.main.service.refiler.FileTransferService;
import com.noti.main.utils.BillingHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class ShareDataActivity extends AppCompatActivity {

    static boolean isNeedToFinishActivityNow = true;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.activity_pair_share);

        TextView title = dialog.findViewById(R.id.titleDetail);
        TextView fileTooBigWarning = dialog.findViewById(R.id.fileTooBigWarning);
        TextInputEditText textPreview = dialog.findViewById(R.id.textPreview);
        TextInputLayout taskSelectSpinnerLayout = dialog.findViewById(R.id.taskSelectSpinnerLayout);
        MaterialAutoCompleteTextView deviceSelectSpinner = dialog.findViewById(R.id.deviceSelectSpinner);
        MaterialAutoCompleteTextView taskSelectSpinner = dialog.findViewById(R.id.taskSelectSpinner);
        MaterialButton cancel = dialog.findViewById(R.id.cancel);
        MaterialButton ok = dialog.findViewById(R.id.ok);

        fileTooBigWarning.setVisibility(View.GONE);
        AtomicInteger deviceSelection = new AtomicInteger();
        SharedPreferences pairPrefs = getSharedPreferences("com.noti.main_pair", MODE_PRIVATE);
        ArrayList<String> nameList = new ArrayList<>();
        ArrayList<String> rawList = new ArrayList<>(pairPrefs.getStringSet("paired_list", new HashSet<>()));
        for (String str : rawList) {
            nameList.add(str.split("\\|")[0]);
        }
        deviceSelectSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, nameList));
        deviceSelectSpinner.setOnItemClickListener((parent, view, position, id) -> deviceSelection.set(position));

        String type = intent.getType();
        if(Intent.ACTION_SEND.equals(intent.getAction()) && type != null) {
            if(type.startsWith("text") && intent.hasExtra(Intent.EXTRA_TEXT)) {
                String data = intent.getStringExtra(Intent.EXTRA_TEXT);
                ArrayList<String> taskList = new ArrayList<>();
                taskList.add("Open link in Browser");
                taskList.add("Copy text to clipboard");

                if (data.startsWith("http")) taskSelectSpinner.setText(taskList.get(0));
                taskSelectSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, taskList));

                textPreview.setText(data);
                cancel.setOnClickListener(v -> dialog.dismiss());
                ok.setOnClickListener(v -> {
                    if (deviceSelectSpinner.getText().toString().isEmpty()) {
                        deviceSelectSpinner.setError("Please select target device");
                    } else if (taskSelectSpinner.getText().toString().isEmpty()) {
                        deviceSelectSpinner.setError("Please select action to execute");
                    } else {
                        String[] array = rawList.get(deviceSelection.get()).split("\\|");
                        DataProcess.requestAction(this, array[0], array[1], taskSelectSpinner.getText().toString(), data);
                        dialog.dismiss();
                    }
                });
            } else if(intent.hasExtra(Intent.EXTRA_STREAM)) {
                Uri dataUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if(dataUri != null) {
                    title.setText("Send file to another device");
                    taskSelectSpinnerLayout.setVisibility(View.GONE);
                    taskSelectSpinner.setVisibility(View.GONE);
                    textPreview.setHint("File name to send");

                    Cursor returnCursor = getContentResolver().query(dataUri, null, null, null, null);
                    int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    returnCursor.moveToFirst();
                    String name = returnCursor.getString(nameIndex);
                    returnCursor.close();
                    textPreview.setText(name);

                    Cursor returnCursor2 = getContentResolver().query(dataUri, new String[]{OpenableColumns.SIZE}, null, null, null);
                    int sizeIndex = returnCursor2.getColumnIndexOrThrow(OpenableColumns.SIZE);
                    returnCursor2.moveToFirst();
                    long size = returnCursor2.getLong(sizeIndex);
                    returnCursor2.close();

                    try {
                        BillingHelper billingHelper = BillingHelper.getInstance(false);
                        if(billingHelper == null) billingHelper = BillingHelper.initialize(this);
                        boolean isSubscribed = billingHelper.isSubscribedOrDebugBuild();

                        if (size > (isSubscribed ? 2147483648L : 104857600)) {
                            if (isSubscribed) {
                                fileTooBigWarning.setVisibility(View.VISIBLE);
                                ok.setEnabled(false);
                            } else {
                                BillingHelper.showSubscribeInfoDialog(ShareDataActivity.this, "You have exceeded the maximum allowed file size!\nThe current limit is 100MB, but the limit increases to 2GB with a subscription.", false, (dialog12, which) -> finish());
                                isNeedToFinishActivityNow = false;
                            }
                        }
                    } catch (IllegalStateException e) {
                        BillingHelper.showSubscribeInfoDialog(ShareDataActivity.this, "Error: Can't get purchase information! Please contact developer.", false, (dialog12, which) -> finish());
                        isNeedToFinishActivityNow = false;
                        e.printStackTrace();
                    }

                    ok.setOnClickListener(v -> {
                        if (deviceSelectSpinner.getText().toString().isEmpty()) {
                            deviceSelectSpinner.setError("Please select target device");
                        } else {
                            deviceSelectSpinner.setEnabled(false);
                            ok.setEnabled(false);

                            String[] array = rawList.get(deviceSelection.get()).split("\\|");
                            new FileTransferService(this, false)
                                    .setUploadProperties(dataUri.toString(), type,true, array[0], array[1])
                                    .execute();

                            MaterialAlertDialogBuilder completeDialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
                            completeDialog.setTitle("File upload started!");
                            completeDialog.setMessage("Watch notification to check upload progress.\n\nAfter upload completion, The file download task will start automatically on the target device.");
                            completeDialog.setIcon(R.drawable.ic_fluent_arrow_sync_checkmark_24_regular);
                            completeDialog.setCancelable(false);
                            completeDialog.setPositiveButton("Close", (dialogInterface, i) -> finish());
                            completeDialog.show();
                        }
                    });
                    cancel.setOnClickListener(v -> dialog.dismiss());
                }
            }
        }

        if(isNeedToFinishActivityNow) dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.setOnDismissListener(dialog1 -> finish());
    }
}
