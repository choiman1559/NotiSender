package com.noti.main.ui.pair.action;

import android.content.Context;
import android.content.Intent;

import com.google.gson.Gson;

import java.io.Serializable;

public class PairActionObj implements Serializable {
    public static final String EXTRA_TAG = "pair_action_obj";

    String actionType;
    String actionDescription;
    String actionIcon;
    String actionClass;

    boolean isNeedArgs;
    Integer needArgsCount;
    String[] argsHintTexts;
    String[] targetDeviceTypeScope;

    public PairActionObj() {

    }

    public static PairActionObj buildFrom(String rawJson) {
        return new Gson().fromJson(rawJson, PairActionObj.class);
    }

    public void openArgActivity(Context context, String... deviceInfo) throws ClassNotFoundException {
        Intent intent = new Intent(context, isNeedArgs ? ActionArgsActivity.class : Class.forName(actionClass));
        intent.putExtra(PairActionObj.EXTRA_TAG, this);
        intent.putExtra("device_name", deviceInfo[0]);
        intent.putExtra("device_id", deviceInfo[1]);
        intent.putExtra("device_type", deviceInfo[2]);

        context.startActivity(intent);
    }
}
