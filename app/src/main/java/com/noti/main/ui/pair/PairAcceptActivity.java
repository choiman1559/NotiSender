package com.noti.main.ui.pair;

import static com.noti.main.Application.pairingProcessList;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.service.NotiListenerService;
import com.noti.main.service.pair.PairDeviceInfo;
import com.noti.main.ui.receive.ExitActivity;
import com.noti.main.utils.BillingHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class PairAcceptActivity extends AppCompatActivity {
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pair_accept);
        SharedPreferences prefs = getSharedPreferences("com.noti.main_pair", MODE_PRIVATE);
        Intent intent = getIntent();

        MaterialButton AcceptButton = findViewById(R.id.ok);
        MaterialButton CancelButton = findViewById(R.id.cancel);

        String Device_name = intent.getStringExtra("device_name");
        String Device_id = intent.getStringExtra("device_id");
        String Device_type = intent.getStringExtra("device_type");

        TextView info = findViewById(R.id.notiDetail);
        info.setText(Html.fromHtml("Are you sure you want to grant the pairing request?<br><b>Requested Device:</b> " + Device_name));

        try {
            BillingHelper billingHelper = BillingHelper.getInstance();
            if (prefs.getStringSet("paired_list", new HashSet<>()).size() >= 2 && !billingHelper.isSubscribedOrDebugBuild()) {
                AcceptButton.setEnabled(false);
                BillingHelper.showSubscribeInfoDialog(this, "Without a subscription, you can only pair up to 2 devices!", false, ((dialog, which) -> {}));
            }
        } catch (IllegalStateException e) {
            if (prefs.getStringSet("paired_list", new HashSet<>()).size() >= 2) {
                AcceptButton.setEnabled(false);
                BillingHelper.showSubscribeInfoDialog(this, "Error: Can't get purchase information! Please contact developer.", false, ((dialog, which) -> {}));
            }
            e.printStackTrace();
        }

        AcceptButton.setOnClickListener(v -> {
            sendAcceptedMessage(Device_name, Device_id, true, this);
            boolean isNotRegistered = true;
            String dataToSave = Device_name + "|" + Device_id + "|" + Device_type;

            Set<String> list = new HashSet<>(prefs.getStringSet("paired_list", new HashSet<>()));
            for (String str : list) {
                if (str.equals(dataToSave)) {
                    isNotRegistered = false;
                    break;
                }
            }

            if (isNotRegistered) {
                list.add(dataToSave);
                prefs.edit().putStringSet("paired_list", list).apply();
            }
        });
        CancelButton.setOnClickListener(v -> sendAcceptedMessage(Device_name, Device_id, false, this));
    }

    public static void sendAcceptedMessage(String Device_name, String Device_id, boolean isAccepted, Context context) {
        JSONObject notificationBody = new JSONObject();
        try {
            notificationBody.put("type", "pair|accept_pair");
            notificationBody.put("device_name", NotiListenerService.getDeviceName());
            notificationBody.put("device_id", NotiListenerService.getUniqueID());
            notificationBody.put("device_type", Application.thisDeviceType.getDeviceType());
            notificationBody.put("send_device_name", Device_name);
            notificationBody.put("send_device_id", Device_id);
            notificationBody.put("pair_accept", isAccepted);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }

        NotiListenerService.sendNotification(notificationBody, "pair.func", context, true);
        ExitActivity.exitApplication(context);

        if (isAccepted) {
            for (PairDeviceInfo info : pairingProcessList) {
                if (info.getDevice_name().equals(Device_name) && info.getDevice_id().equals(Device_id)) {
                    Application.isListeningToPair = false;
                    pairingProcessList.remove(info);
                    break;
                }
            }
        }
    }
}
