package com.noti.main.service.mirnoti;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;

import androidx.annotation.Nullable;

import java.util.Objects;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class NotificationAction {
    @JsonProperty
    public String actionName;
    @JsonProperty
    public boolean isInputAction;
    @JsonProperty
    public String inputResultKey;
    @JsonProperty
    public String inputLabel;

    @SuppressWarnings("unused")
    public NotificationAction() {
        // Default constructor for creating instance by ObjectMapper
    }

    public NotificationAction(Notification.Action action) {
        this.actionName = action.title.toString();

        RemoteInput[] actions = action.getRemoteInputs();
        this.isInputAction = actions != null && actions.length > 0;
        if(this.isInputAction) {
            this.inputLabel = Objects.requireNonNullElse(actions[0].getLabel(), "").toString();
            this.inputResultKey = actions[0].getResultKey();
        }
    }

    @Nullable
    public Notification.Action getAction(PendingIntent pendingIntent) {
        if(actionName == null) {
            return null;
        }

        Notification.Action.Builder actionBuilder = new Notification.Action.Builder(null, this.actionName, pendingIntent);
        if(isInputAction) {
            actionBuilder.addRemoteInput(new RemoteInput.Builder(inputResultKey)
                    .setLabel(inputLabel)
                    .build());
        }

        return actionBuilder.build();
    }
}
