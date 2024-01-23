package com.noti.main.ui.prefs.custom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.service.NotiListenerService;
import com.noti.main.utils.ui.ToastHelper;
import com.noti.plugin.data.NotificationData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PlaygroundFragment extends Fragment {
    Activity mContext;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) mContext = (Activity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playground_regex, container, false);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SharedPreferences prefs = mContext.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
        ProgressBar progress = mContext.findViewById(R.id.progress);
        progress.setVisibility(View.GONE);

        TextInputEditText titleValue = view.findViewById(R.id.titleValue);
        TextInputEditText contentValue = view.findViewById(R.id.contentValue);
        TextInputEditText packageNameValue = view.findViewById(R.id.packageNameValue);
        TextInputEditText appNameValue = view.findViewById(R.id.appNameValue);
        TextInputEditText deviceNameValue = view.findViewById(R.id.deviceNameValue);
        TextInputEditText dateValue = view.findViewById(R.id.dateValue);
        TextInputEditText exprValue = view.findViewById(R.id.exprValue);

        MaterialButton clearButton = view.findViewById(R.id.clearButton);
        MaterialButton defaultButton = view.findViewById(R.id.defaultButton);
        MaterialButton GoButton = view.findViewById(R.id.GoButton);

        clearButton.setOnClickListener((v) -> {
            titleValue.setText("");
            contentValue.setText("");
            packageNameValue.setText("");
            appNameValue.setText("");
            deviceNameValue.setText("");
            dateValue.setText("");
            exprValue.setText("");
        });

        defaultButton.setOnClickListener((v) -> {
            titleValue.setText(prefs.getString("DefaultTitle", "New notification"));
            contentValue.setText(prefs.getString("DefaultMessage", "notification arrived."));
            packageNameValue.setText(mContext.getPackageName());
            appNameValue.setText("Noti Sender");
            deviceNameValue.setText(NotiListenerService.getDeviceName());
            dateValue.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date().getTime()));
        });

        GoButton.setOnClickListener((v) -> {
            NotificationData data = new NotificationData();
            data.TITLE = getText(titleValue);
            data.CONTENT = getText(contentValue);
            data.PACKAGE_NAME = getText(packageNameValue);
            data.APP_NAME = getText(appNameValue);
            data.DEVICE_NAME = getText(deviceNameValue);
            data.DATE = getText(dateValue);

            RegexInterpreter.evalRegex(mContext, getText(exprValue), data, result -> ToastHelper.show(mContext, "Eval Result is: " + (boolean) result, ToastHelper.LENGTH_SHORT));
        });
    }

    String getText(TextInputEditText view) {
        if(view != null) {
            Editable text = view.getText();
            if(text != null) return text.toString();
        }
        return "";
    }
}
