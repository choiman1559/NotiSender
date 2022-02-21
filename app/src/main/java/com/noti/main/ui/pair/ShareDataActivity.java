package com.noti.main.ui.pair;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import com.noti.main.R;
import com.noti.main.service.pair.DataProcess;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class ShareDataActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String data = intent.getStringExtra(Intent.EXTRA_TEXT);
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.activity_pair_share);

        TextInputEditText textPreview = dialog.findViewById(R.id.textPreview);
        MaterialAutoCompleteTextView deviceSelectSpinner = dialog.findViewById(R.id.deviceSelectSpinner);
        MaterialAutoCompleteTextView taskSelectSpinner = dialog.findViewById(R.id.taskSelectSpinner);
        MaterialButton cancel = dialog.findViewById(R.id.cancel);
        MaterialButton ok = dialog.findViewById(R.id.ok);

        AtomicInteger deviceSelection = new AtomicInteger();
        SharedPreferences pairPrefs = getSharedPreferences("com.noti.main_pair", MODE_PRIVATE);
        ArrayList<String> nameList = new ArrayList<>();
        ArrayList<String> rawList = new ArrayList<>(pairPrefs.getStringSet("paired_list", new HashSet<>()));
        for (String str : rawList) {
            nameList.add(str.split("\\|")[0]);
        }

        ArrayList<String> taskList = new ArrayList<>();
        taskList.add("Open link in Browser");
        taskList.add("Copy text to clipboard");

        if(data.startsWith("http")) taskSelectSpinner.setText(taskList.get(0));
        deviceSelectSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, nameList));
        deviceSelectSpinner.setOnItemClickListener((parent, view, position, id) -> deviceSelection.set(position));
        taskSelectSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, taskList));

        textPreview.setText(data);
        cancel.setOnClickListener(v -> dialog.dismiss());
        ok.setOnClickListener(v -> {
            if(deviceSelectSpinner.getText() == null) {
                deviceSelectSpinner.setError("Please select target device");
            } else if(taskSelectSpinner.getText() == null) {
                deviceSelectSpinner.setError("Please select action to execute");
            } else {
                String[] array = rawList.get(deviceSelection.get()).split("\\|");
                DataProcess.requestAction(this, array[0], array[1], taskSelectSpinner.getText().toString(), data);
                dialog.dismiss();
            }
        });

        dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.setOnDismissListener(dialog1 -> finish());
    }
}
