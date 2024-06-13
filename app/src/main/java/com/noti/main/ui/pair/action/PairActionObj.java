package com.noti.main.ui.pair.action;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.gson.Gson;

import com.noti.plugin.data.PairRemoteAction;

import java.io.Serializable;

public class PairActionObj implements Serializable {
    public static final String EXTRA_TAG = "pair_action_obj";

    String actionType;
    String actionDescription;
    String actionIcon;
    String actionClass;
    String[] targetDeviceTypeScope;

    boolean isPluginAction = false;
    PairRemoteAction pairRemoteAction;

    boolean isNeedArgs;
    Integer needArgsCount;
    String[] argsHintTexts;

    public PairActionObj() {

    }

    public PairActionObj(PairRemoteAction pairRemoteAction) {
        this.isPluginAction = true;
        this.isNeedArgs = false;
        this.pairRemoteAction = pairRemoteAction;

        this.actionType = pairRemoteAction.getActionType();
        this.actionDescription = pairRemoteAction.getActionDescription();
        this.targetDeviceTypeScope = pairRemoteAction.getTargetDeviceTypeScope();
    }

    public static PairActionObj buildFrom(String rawJson) {
        return new Gson().fromJson(rawJson, PairActionObj.class);
    }

    @Nullable
    public Drawable getDrawable(Context context) {
        if(isPluginAction) {
            if(pairRemoteAction.getActionIcon() != null) {
                return new BitmapDrawable(context.getResources(), pairRemoteAction.getActionIcon());
            } else return null;
        } else if (!actionIcon.isEmpty()){
            @SuppressLint("DiscouragedApi")
            int resId = context.getResources().getIdentifier(String.format("ic_fluent_%s_24_regular", actionIcon), "drawable", context.getPackageName());
            return AppCompatResources.getDrawable(context, resId);
        } else return null;
    }

    public void openArgActivity(Context context, String... deviceInfo) throws ClassNotFoundException {
        Intent intent;
        if(isPluginAction) {
            intent = new Intent().setComponent(new ComponentName(pairRemoteAction.getPluginPackageName(), pairRemoteAction.getActionClassName()));
        } else {
            intent = new Intent(context, isNeedArgs ? ActionArgsActivity.class : Class.forName(actionClass));
            intent.putExtra(PairActionObj.EXTRA_TAG, this);
        }

        intent.putExtra("device_name", deviceInfo[0]);
        intent.putExtra("device_id", deviceInfo[1]);
        intent.putExtra("device_type", deviceInfo[2]);

        context.startActivity(intent);
    }
}
