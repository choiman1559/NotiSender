package com.noti.main.ui.pair;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.kieronquinn.monetcompat.view.MonetSwitch;
import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.service.pair.PairDeviceType;
import com.noti.main.ui.OptionActivity;
import com.noti.main.utils.ui.ToastHelper;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

@SuppressLint("SetTextI18n")
public class PairMainFragment extends Fragment {

    AppCompatActivity mActivity;
    SharedPreferences mainPrefs;
    SharedPreferences pairPrefs;

    LinearLayout deviceListLayout;
    SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (sharedPreferences, key) -> {
        if(key.equals("paired_list")) {
            mActivity.runOnUiThread(this::loadDeviceList);
        }
    };

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if(context instanceof AppCompatActivity) mActivity = (AppCompatActivity) context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_pair_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout addNewDevice = view.findViewById(R.id.addNewDevice);
        LinearLayout connectionPreference = view.findViewById(R.id.connectionPreference);

        MonetSwitch pairingSwitch = view.findViewById(R.id.PairingSwitch);
        TextView deviceNameInfo = view.findViewById(R.id.deviceNameInfo);

        deviceListLayout = view.findViewById(R.id.deviceListLayout);
        pairPrefs = mActivity.getSharedPreferences("com.noti.main_pair", MODE_PRIVATE);
        mainPrefs = mActivity.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE);

        deviceNameInfo.setText("Visible as \"" + Build.MODEL + "\" to other devices");
        addNewDevice.setOnClickListener(v -> {
            if(mainPrefs.getBoolean("pairToggle", false)) {
                startActivity(new Intent(mActivity, PairingActivity.class));
            } else {
                ToastHelper.show(mActivity, "Pairing is not enabled",ToastHelper.LENGTH_SHORT);
            }
        });
        connectionPreference.setOnClickListener(v -> startActivity(new Intent(mActivity, OptionActivity.class).putExtra("Type", "Pair")));

        boolean isNightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        pairingSwitch.setTextColor(getResources().getColor(isNightMode ? R.color.ui_bg : R.color.ui_fg));
        if (mainPrefs.getString("UID", "").isEmpty()) {
            pairingSwitch.setEnabled(false);
            pairingSwitch.setChecked(false);
        } else {
            pairingSwitch.setChecked(mainPrefs.getBoolean("pairToggle", false));
        }
        pairingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> mainPrefs.edit().putBoolean("pairToggle", isChecked).apply());

        loadDeviceList();
        pairPrefs.registerOnSharedPreferenceChangeListener(prefsListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDeviceList();
    }

    void loadDeviceList() {
        Set<String> list = pairPrefs.getStringSet("paired_list", new HashSet<>());
        if(list.size() == deviceListLayout.getChildCount()) return;
        deviceListLayout.removeViews(0, deviceListLayout.getChildCount());

        for(String string : list) {
            String[] data = string.split("\\|");
            RelativeLayout layout = (RelativeLayout) View.inflate(mActivity, R.layout.cardview_pair_device_setting, null);
            Holder holder = new Holder(layout);

            String[] colorLow = mActivity.getResources().getStringArray(R.array.material_color_low);
            String[] colorHigh = mActivity.getResources().getStringArray(R.array.material_color_high);
            int randomIndex = new Random(data[0].hashCode()).nextInt(colorHigh.length);

            holder.deviceName.setText(data[0]);
            if(data.length > 2) holder.icon.setImageResource(new PairDeviceType(data[2]).getDeviceTypeBitmap());
            holder.icon.setImageTintList(ColorStateList.valueOf(Color.parseColor(colorHigh[randomIndex])));
            holder.icon.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(colorLow[randomIndex])));
            holder.setting.setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, PairDetailActivity.class);
                intent.putExtra("device_name", data[0]);
                intent.putExtra("device_id", data[1]);
                if(data.length > 2) intent.putExtra("device_type", data[2]);
                startActivity(intent);
            });
            holder.baseLayout.setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, RequestActionActivity.class);
                intent.putExtra("device_name", data[0]);
                intent.putExtra("device_id", data[1]);
                startActivity(intent);
            });

            deviceListLayout.addView(layout);
        }
    }

    static class Holder {
        TextView deviceName;
        TextView pairStatus;
        RelativeLayout baseLayout;
        ImageView icon;
        ImageView setting;

        Holder(View view) {
            deviceName = view.findViewById(R.id.deviceName);
            pairStatus = view.findViewById(R.id.deviceStatus);
            baseLayout = view.findViewById(R.id.baseLayout);
            icon = view.findViewById(R.id.icon);
            setting = view.findViewById(R.id.deviceDetail);
        }
    }
}
