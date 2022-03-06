package com.noti.main.ui.pair;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.noti.main.R;
import com.noti.main.service.pair.DataProcess;
import com.noti.main.ui.ToastHelper;

import java.util.Objects;

public class RequestActionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair_action);
        Intent intent = getIntent();

        MaterialAutoCompleteTextView taskSelectSpinner = findViewById(R.id.taskSelectSpinner);
        TextInputEditText deviceName = findViewById(R.id.deviceName);
        TextInputEditText taskArgs0 = findViewById(R.id.taskArgs0);
        TextInputEditText taskArgs1 = findViewById(R.id.taskArgs1);
        ExtendedFloatingActionButton sendButton = findViewById(R.id.sendButton);

        String Device_name = intent.getStringExtra("device_name");
        String Device_id = intent.getStringExtra("device_id");
        deviceName.setText(Device_name);

        taskArgs0.setVisibility(View.GONE);
        taskArgs1.setVisibility(View.GONE);

        taskSelectSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, getResources().getStringArray(R.array.pairAction)));
        taskSelectSpinner.setOnItemClickListener((parent, view, position, id) -> {
            switch(position) {
                case 0:
                    taskArgs0.setVisibility(View.VISIBLE);
                    taskArgs1.setVisibility(View.VISIBLE);
                    taskArgs0.setHint("Notification Title");
                    taskArgs1.setHint("Notification Content");
                    break;

                case 1:
                    taskArgs0.setVisibility(View.VISIBLE);
                    taskArgs1.setVisibility(View.GONE);
                    taskArgs0.setHint("Text to send");
                    break;

                case 2:
                    taskArgs0.setVisibility(View.VISIBLE);
                    taskArgs1.setVisibility(View.GONE);
                    taskArgs0.setHint("Url to open in browser");
                    break;

                case 3:
                    taskArgs0.setVisibility(View.GONE);
                    taskArgs1.setVisibility(View.GONE);
                    break;

                case 4:
                    taskArgs0.setVisibility(View.VISIBLE);
                    taskArgs1.setVisibility(View.GONE);
                    taskArgs0.setHint("app's package name to open");
                    break;

                case 5:
                    taskArgs0.setVisibility(View.VISIBLE);
                    taskArgs1.setVisibility(View.GONE);
                    taskArgs0.setHint("Type terminal command to run");
                    break;
            }
        });

        sendButton.setOnClickListener(v -> {
            if (taskSelectSpinner.getText().toString().isEmpty()) {
                taskSelectSpinner.setError("Please select task to run");
            } else if (taskArgs0.getVisibility() == View.VISIBLE && taskArgs0.getText() == null) {
                taskArgs0.setError("Please type argument");
            } else if (taskArgs1.getVisibility() == View.VISIBLE && taskArgs1.getText() == null) {
                taskArgs1.setError("Please type argument");
            } else {
                if (taskArgs0.getVisibility() == View.VISIBLE && taskArgs1.getVisibility() == View.VISIBLE) {
                    DataProcess.requestAction(this, Device_name, Device_id, taskSelectSpinner.getText().toString(), Objects.requireNonNull(taskArgs0.getText()).toString(), Objects.requireNonNull(taskArgs1.getText()).toString());
                } else if (taskArgs0.getVisibility() == View.VISIBLE) {
                    DataProcess.requestAction(this, Device_name, Device_id, taskSelectSpinner.getText().toString(), Objects.requireNonNull(taskArgs0.getText()).toString());
                } else {
                    DataProcess.requestAction(this, Device_name, Device_id, taskSelectSpinner.getText().toString());
                }

                ToastHelper.show(this, "Your request is posted!", "OK", ToastHelper.LENGTH_SHORT);
                finish();
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }
}
