package com.noti.main.ui.pair.action;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.noti.main.R;
import com.noti.main.receiver.plugin.PluginPrefs;
import com.noti.main.utils.ui.PrefsCard;
import com.noti.plugin.data.PairRemoteAction;

import java.util.Arrays;
import java.util.Objects;

public class PairActionActivity extends AppCompatActivity {

    String Device_name;
    String Device_id;
    String Device_type;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair_action);

        Intent intent = getIntent();
        Device_name = intent.getStringExtra("device_name");
        Device_id = intent.getStringExtra("device_id");
        Device_type = intent.getStringExtra("device_type");

        LinearLayout toolsListLayout = findViewById(R.id.tools_list);
        String[] actionArray = getResources().getStringArray(R.array.pairAction);

        for(String action: actionArray) {
            PairActionObj obj = PairActionObj.buildFrom(action);
            if(obj.targetDeviceTypeScope.length == 0 || Arrays.asList(obj.targetDeviceTypeScope).contains(Device_type)) {
                toolsListLayout.addView(buildPrefsCard(obj));
            }
        }

        for(String pluginName : PluginPrefs.getPluginList(this)) {
            PluginPrefs pluginPrefs = new PluginPrefs(this, pluginName);
            PairRemoteAction[] pairRemoteActions = pluginPrefs.getPairRemoteActions();

            if(pluginPrefs.isPluginEnabled() && pairRemoteActions != null) {
                for (PairRemoteAction action : pairRemoteActions) {
                    if(action.getTargetDeviceTypeScope().length > 0
                            && !Arrays.asList(action.getTargetDeviceTypeScope()).contains(Device_type)) {
                        continue;
                    }

                    PrefsCard prefsCard = buildPrefsCard(new PairActionObj(action));
                    toolsListLayout.addView(prefsCard);
                }
            }
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }

    private PrefsCard buildPrefsCard(PairActionObj pairActionObj) {
        int displayMode = 1;

        Drawable actionIcon = pairActionObj.getDrawable(PairActionActivity.this);
        if(actionIcon != null) {
            displayMode |= 8;
        }

        if(pairActionObj.actionDescription != null && !pairActionObj.actionDescription.isEmpty()) {
            displayMode |= 2;
        }

        PrefsCard prefsCard = new PrefsCard(this, null);
        prefsCard.setCardType(displayMode);
        prefsCard.setIsIconAlignEnd(false);
        prefsCard.initDynamic();

        if(actionIcon != null) {
            prefsCard.setIconDrawable(actionIcon);
        }

        prefsCard.setTitle(Objects.requireNonNullElse(pairActionObj.actionType, ""));
        prefsCard.setDescription(Objects.requireNonNullElse(pairActionObj.actionDescription, ""));
        prefsCard.setOnClickListener((v) -> {
            try {
                pairActionObj.openArgActivity(PairActionActivity.this, Device_name, Device_id, Device_type);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        return prefsCard;
    }
}
