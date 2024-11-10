package com.noti.main.ui.pair.action;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.noti.main.R;
import com.noti.main.service.livenoti.LiveNotiProcess;
import com.noti.main.service.livenoti.LiveNotiRequests;
import com.noti.main.service.mirnoti.NotificationAction;
import com.noti.main.service.mirnoti.NotificationRequest;
import com.noti.main.service.mirnoti.NotificationsData;
import com.noti.main.utils.ui.ToastHelper;

import java.util.Locale;

public class LiveNotificationActivity extends AppCompatActivity {

    String Device_name;
    String Device_id;
    LiveNotiItemHolder lastSelectedItemHolder = null;

    ScrollView liveNotiScrollView;
    SwipeRefreshLayout liveNotiRefreshLayout;
    LinearLayoutCompat liveNotiLayout;
    LinearLayoutCompat liveNotiStateLayout;
    ImageView liveNotiErrorEmoji;
    TextView liveNotiStateDescription;
    ProgressBar liveNotiStateProgress;
    FloatingActionButton liveNotiDismissAllAction;

    final String BLANK_INFO_TEXT = "There are currently no notifications\ndisplayed on your device.";

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair_live_notification);
        getWindow().setStatusBarColor(getResources().getColor(R.color.ui_bg_toolbar, null));
        Intent intent = getIntent();

        Device_name = intent.getStringExtra("device_name");
        Device_id = intent.getStringExtra("device_id");
        
        liveNotiScrollView = findViewById(R.id.liveNotiScrollView);
        liveNotiRefreshLayout = findViewById(R.id.liveNotiRefreshLayout);
        liveNotiLayout = findViewById(R.id.liveNotiLayout);
        liveNotiStateLayout = findViewById(R.id.liveNotiStateLayout);
        liveNotiErrorEmoji = findViewById(R.id.liveNotiErrorEmoji);
        liveNotiStateDescription = findViewById(R.id.liveNotiStateDescription);
        liveNotiStateProgress = findViewById(R.id.liveNotiStateProgress);
        liveNotiDismissAllAction = findViewById(R.id.liveNotiDismissAllAction);

        liveNotiLayout.setVisibility(View.GONE);
        liveNotiErrorEmoji.setVisibility(View.GONE);
        liveNotiScrollView.setOnScrollChangeListener((view, scrollX, scrollY, beforeX, beforeY) -> {
            liveNotiRefreshLayout.setEnabled(liveNotiLayout.getVisibility() == View.VISIBLE && scrollY == 0);
            if (scrollY > beforeX + 12 && liveNotiDismissAllAction.isShown()) {
                liveNotiDismissAllAction.hide();
            } else if (scrollY < beforeY - 12 && !liveNotiDismissAllAction.isShown()) {
                liveNotiDismissAllAction.show();
            } else if (scrollY == 0) {
                liveNotiDismissAllAction.show();
            }
        });

        LiveNotiProcess.mOnLiveNotificationUploadCompleteListener = (isSuccess, unused) -> runOnUiThread(() -> {
            if(isSuccess) {
                liveNotiStateDescription.setText("Downloading notifications\nfrom server...");
            }
        });

        LiveNotiProcess.mOnLiveNotificationDownloadCompleteListener = (isSuccess, liveNotifications) -> runOnUiThread(() -> {
            if(isSuccess && liveNotifications != null) {
                makeListView(liveNotifications);
            } else {
                showError("Something is wrong.\nSwipe down to try again.", false);
            }
        });

        liveNotiRefreshLayout.setOnRefreshListener(() -> runOnUiThread(() -> {
            liveNotiRefreshLayout.setRefreshing(false);
            getLiveNotiList();
        }));

        liveNotiDismissAllAction.setOnClickListener((v) -> {
            if(liveNotiLayout.getChildCount() > 0) {
                liveNotiLayout.removeAllViews();
                showError(BLANK_INFO_TEXT, true);
                lastSelectedItemHolder = null;
                NotificationRequest.requestDismissAllNotifications(this, Device_name, Device_id);
            }
        });

        getLiveNotiList();
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if(lastSelectedItemHolder != null
                        && lastSelectedItemHolder.liveNotificationActionInputLayout.getVisibility() != View.GONE) {
                    lastSelectedItemHolder.onBackPressed();
                } else {
                    finish();
                }
            }
        });
    }

    @SuppressLint("SetTextI18n")
    void getLiveNotiList() {
        setNotiListVisibility(false);
        liveNotiLayout.removeAllViews();
        liveNotiErrorEmoji.setVisibility(View.GONE);
        liveNotiStateDescription.setText("Uploading live notifications\nfrom target device...");

        try {
            LiveNotiRequests.requestLiveNotificationData(this, Device_id, Device_name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void showError(String errorMessage, boolean isBlank) {
        setNotiListVisibility(false);
        liveNotiErrorEmoji.setVisibility(View.VISIBLE);
        liveNotiErrorEmoji.setImageDrawable(AppCompatResources.getDrawable(this,
                isBlank ? R.drawable.ic_fluent_border_none_24_regular : R.drawable.ic_fluent_warning_24_regular));
        liveNotiStateProgress.setVisibility(View.GONE);
        liveNotiStateDescription.setText(errorMessage);
    }

    void setNotiListVisibility(boolean visible) {
        liveNotiLayout.setVisibility(visible ? View.VISIBLE : View.GONE);
        liveNotiDismissAllAction.setVisibility(visible? View.VISIBLE : View.GONE);
        liveNotiStateLayout.setVisibility(visible ? View.GONE : View.VISIBLE);
        liveNotiStateProgress.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private class LiveNotiItemHolder {
        NotificationsData liveNotificationObj;
        CoordinatorLayout liveNotiItem;
        MaterialCardView liveNotiItemParent;
        LinearLayoutCompat notificationActionListLayout;

        RelativeLayout liveNotiActionMenuLayout;
        Button remoteRunButton;
        Button dismissButton;

        TextView liveNotiTitle;
        TextView liveNotiDescription;
        TextView liveNotiAppName;

        ImageView liveNotiSmallIcon;
        ImageView liveNotiBigIcon;

        RelativeLayout liveNotificationActionInputLayout;
        TextInputEditText liveNotificationActionInputField;
        ImageButton liveNotificationActionInputSend;

        public LiveNotiItemHolder(Context context, NotificationsData notificationsData) {
            liveNotificationObj = notificationsData;
            liveNotiItem = (CoordinatorLayout) View.inflate(context, R.layout.cardview_livenoti_item, null);
            liveNotiItemParent = liveNotiItem.findViewById(R.id.liveNotiItemParent);
            notificationActionListLayout = liveNotiItem.findViewById(R.id.notificationActionLayout);

            liveNotiTitle = liveNotiItem.findViewById(R.id.notiTitle);
            liveNotiDescription = liveNotiItem.findViewById(R.id.notiDescription);
            liveNotiAppName = liveNotiItem.findViewById(R.id.appName);
            liveNotiSmallIcon = liveNotiItem.findViewById(R.id.notificationSmallIcon);
            liveNotiBigIcon = liveNotiItem.findViewById(R.id.notificationBigIcon);

            liveNotiActionMenuLayout = liveNotiItem.findViewById(R.id.liveNotiActionMenuLayout);
            remoteRunButton = liveNotiItem.findViewById(R.id.remoteRunButton);
            dismissButton = liveNotiItem.findViewById(R.id.dismissButton);

            liveNotificationActionInputLayout = liveNotiItem.findViewById(R.id.notificationActionInputLayout);
            liveNotificationActionInputField = liveNotiItem.findViewById(R.id.liveNotificationActionInputField);
            liveNotificationActionInputSend = liveNotiItem.findViewById(R.id.liveNotificationActionInputSend);

            liveNotiTitle.setText(liveNotificationObj.title);
            liveNotiDescription.setText(liveNotificationObj.message);
            liveNotiAppName.setText(String.format(Locale.getDefault(),"%s • %s",
                    liveNotificationObj.appName, getReadableTimeDiff(liveNotificationObj.postTime)));

            liveNotificationActionInputLayout.setVisibility(View.GONE);
            for(int i = 0; i < liveNotificationObj.actions.length; i++) {
                notificationActionListLayout.addView(createButtonFromAction(context, liveNotificationObj.actions[i], i));
            }

            Bitmap smallBitmap = liveNotificationObj.getSmallIcon();
            if(smallBitmap != null) {
                liveNotiSmallIcon.setImageBitmap(smallBitmap);
            }

            Bitmap bigBitmap = liveNotificationObj.getBigIcon();
            if(bigBitmap != null) {
                liveNotiBigIcon.setImageBitmap(bigBitmap);
            } else {
                liveNotiBigIcon.setVisibility(View.GONE);
            }

            remoteRunButton.setOnClickListener((v) -> {
                NotificationRequest.receptionNotification(context, liveNotificationObj.appPackage, Device_name, Device_id, liveNotificationObj.key, true, true);
                ToastHelper.show((Activity) context, "Remote run request has been sent","Ok", ToastHelper.LENGTH_SHORT);
            });

            dismissButton.setOnClickListener((v) -> {
                clearItemFromList();
                NotificationRequest.receptionNotification(context, liveNotificationObj.appPackage, Device_name, Device_id, liveNotificationObj.key, false, true);
                ToastHelper.show((Activity) context, "Dismiss request has been sent","Ok", ToastHelper.LENGTH_SHORT);
            });

            liveNotiActionMenuLayout.setVisibility(View.GONE);
            liveNotiItemParent.setOnClickListener(v -> {
                focusCurrentItem();

                if(liveNotiActionMenuLayout.getVisibility() == View.GONE) {
                    liveNotiTitle.setSingleLine(false);
                    liveNotiDescription.setSingleLine(false);
                    liveNotiActionMenuLayout.setVisibility(View.VISIBLE);
                } else {
                    liveNotiTitle.setSingleLine(true);
                    liveNotiDescription.setSingleLine(true);
                    liveNotiActionMenuLayout.setVisibility(View.GONE);
                }

                notificationActionListLayout.setVisibility(View.VISIBLE);
                liveNotificationActionInputLayout.setVisibility(View.GONE);
            });
        }

        void focusCurrentItem() {
            if(lastSelectedItemHolder != this) {
                if(lastSelectedItemHolder != null) {
                    lastSelectedItemHolder.liveNotiTitle.setSingleLine(true);
                    lastSelectedItemHolder.liveNotiDescription.setSingleLine(true);
                    lastSelectedItemHolder.liveNotiActionMenuLayout.setVisibility(View.GONE);
                    lastSelectedItemHolder.liveNotificationActionInputLayout.setVisibility(View.GONE);
                }
                lastSelectedItemHolder = this;
            }
        }

        MaterialButton createButtonFromAction(Context context, NotificationAction action, int actionIndex) {
            MaterialButton button = new MaterialButton(context);
            button.setTextSize(15);
            button.setFocusable(true);
            button.setText(action.actionName);
            button.setTextColor(ContextCompat.getColor(context, R.color.ui_fg));
            button.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent));
            button.setAllCaps(false);
            button.setElevation(0);
            button.setStateListAnimator(null);

            button.setOnClickListener((v) -> {
                if(action.isInputAction) {
                    focusCurrentItem();
                    notificationActionListLayout.setVisibility(View.GONE);
                    liveNotiActionMenuLayout.setVisibility(View.GONE);
                    liveNotificationActionInputLayout.setVisibility(View.VISIBLE);
                    liveNotificationActionInputSend.setEnabled(false);

                    liveNotificationActionInputField.setHint(action.inputLabel);
                    liveNotificationActionInputField.setText("");
                    liveNotificationActionInputField.requestFocus();
                    liveNotificationActionInputField.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

                        @Override
                        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

                        @Override
                        public void afterTextChanged(Editable editable) {
                            if(editable != null && editable.length() > 0) {
                                liveNotificationActionInputSend.setEnabled(true);
                            }
                        }
                    });

                    liveNotificationActionInputSend.setOnClickListener((view) -> {
                        Editable editText = liveNotificationActionInputField.getText();
                        if(editText != null && editText.length() > 0) {
                            NotificationRequest.sendPerformActionWithInput(context,
                                    liveNotificationObj.key, actionIndex, action.inputResultKey,
                                    editText.toString(), Device_name, Device_id);
                            clearItemFromList();
                        }
                    });
                } else {
                    NotificationRequest.sendPerformAction(context, liveNotificationObj.key, actionIndex, Device_name, Device_id);
                    clearItemFromList();
                }
            });

            return button;
        }

        void clearItemFromList() {
            liveNotiLayout.removeView(liveNotiItem);

            if(liveNotiLayout.getChildCount() == 0) {
                showError(BLANK_INFO_TEXT, true);
            }

            if(lastSelectedItemHolder == this) {
                lastSelectedItemHolder = null;
            }
        }

        public void onBackPressed() {
            notificationActionListLayout.setVisibility(View.VISIBLE);
            liveNotificationActionInputLayout.setVisibility(View.GONE);
        }
    }

    private void makeListView(NotificationsData[] liveNotificationList) {
        if(liveNotificationList.length > 0) {
            int liveNotificationCount = 0;
            for(NotificationsData liveNotificationObj : liveNotificationList) {
                if(liveNotificationObj.title.isEmpty() && liveNotificationObj.message.isEmpty() && liveNotificationObj.getBigIcon() != null) {
                    continue;
                }

                LiveNotiItemHolder liveNotiItemHolder = new LiveNotiItemHolder(this, liveNotificationObj);
                liveNotiLayout.addView(liveNotiItemHolder.liveNotiItem);
                liveNotificationCount += 1;
            }

            if(liveNotificationCount > 0) {
                setNotiListVisibility(true);
            } else {
                showError(BLANK_INFO_TEXT, true);
            }
        } else {
            showError(BLANK_INFO_TEXT, true);
        }
    }

    private String getReadableTimeDiff(long time) {
        long diff = System.currentTimeMillis() - time;

        float diffMinutes = (float) diff / (60 * 1000) % 60;
        if(diffMinutes < 1) {
            return "now";
        } else if(diffMinutes < 60) {
            return String.format(Locale.getDefault(),"%.0fm ago", diffMinutes);
        }

        float diffHours = (float) diff / (60 * 60 * 1000) % 24;
        if(diffHours < 1) {
            return "1h ago";
        } else if(diffHours < 24) {
            return String.format(Locale.getDefault(),"%.0fh ago", diffHours);
        }

        float diffDays = (float) diff / (24 * 60 * 60 * 1000);
        if(diffDays < 1) {
            return "1d ago";
        } else if(diffDays < 7) {
            return String.format(Locale.getDefault(),"%.0fd ago", diffDays);
        }

        float diffWeeks = diffDays / 7;
        if(diffWeeks < 1) {
            return "1 week ago";
        } else if(diffWeeks < 4) {
            return String.format(Locale.getDefault(),"%.0f weeks ago", diffDays);
        }

        float diffMonths = diffDays / 30;
        if(diffMonths < 1) {
            return "1 month ago";
        } else if(diffMonths < 12) {
            return String.format(Locale.getDefault(),"%.0f months ago", diffMonths);
        }

        return "long time ago";
    }
}
