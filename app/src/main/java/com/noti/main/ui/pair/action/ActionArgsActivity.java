package com.noti.main.ui.pair.action;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.noti.main.R;
import com.noti.main.service.pair.DataProcess;
import com.noti.main.utils.ui.ToastHelper;

import java.util.Objects;

public class ActionArgsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair_action_args);
        Intent intent = getIntent();
        PairActionObj pairActionObj = (PairActionObj) intent.getSerializableExtra(PairActionObj.EXTRA_TAG);

        if(pairActionObj == null) {
            throw new IllegalArgumentException("pairActionObj is null");
        }

        TextInputEditText deviceName = findViewById(R.id.deviceName);
        TextInputEditText taskType = findViewById(R.id.taskType);
        TextInputEditText taskArgs0 = findViewById(R.id.taskArgs0);
        TextInputEditText taskArgs1 = findViewById(R.id.taskArgs1);
        ExtendedFloatingActionButton sendButton = findViewById(R.id.sendButton);

        String Device_name = intent.getStringExtra("device_name");
        String Device_id = intent.getStringExtra("device_id");

        taskType.setText(pairActionObj.actionType);
        deviceName.setText(Device_name);

        taskArgs0.setVisibility(View.GONE);
        taskArgs1.setVisibility(View.GONE);

        switch (pairActionObj.needArgsCount) {
            case 2:
                taskArgs0.setVisibility(View.VISIBLE);
                taskArgs1.setVisibility(View.VISIBLE);
                taskArgs0.setHint(pairActionObj.argsHintTexts[0]);
                taskArgs1.setHint(pairActionObj.argsHintTexts[1]);
                break;

            case 1:
                taskArgs0.setVisibility(View.VISIBLE);
                taskArgs1.setVisibility(View.GONE);
                taskArgs0.setHint(pairActionObj.argsHintTexts[0]);
                break;

            case 0:
                taskArgs0.setVisibility(View.GONE);
                taskArgs1.setVisibility(View.GONE);
                break;
        }

        sendButton.setOnClickListener(v -> {
           if (taskArgs0.getVisibility() == View.VISIBLE && taskArgs0.getText() == null) {
                taskArgs0.setError("Please type argument");
            } else if (taskArgs1.getVisibility() == View.VISIBLE && taskArgs1.getText() == null) {
                taskArgs1.setError("Please type argument");
            } else {
                if (taskArgs0.getVisibility() == View.VISIBLE && taskArgs1.getVisibility() == View.VISIBLE) {
                    DataProcess.requestAction(this, Device_name, Device_id, pairActionObj.actionType, Objects.requireNonNull(taskArgs0.getText()).toString(), Objects.requireNonNull(taskArgs1.getText()).toString());
                } else if (taskArgs0.getVisibility() == View.VISIBLE) {
                    DataProcess.requestAction(this, Device_name, Device_id, pairActionObj.actionType, Objects.requireNonNull(taskArgs0.getText()).toString());
                } else {
                    DataProcess.requestAction(this, Device_name, Device_id, pairActionObj.actionType);
                }

                ToastHelper.show(this, "Your request is posted!", "OK", ToastHelper.LENGTH_SHORT);
                finish();
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }
}
