package com.noti.main.ui.pair.action;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import com.noti.main.R;
import com.noti.main.service.livenoti.LiveNotiProcess;
import com.noti.main.service.livenoti.LiveNotiRequests;
import com.noti.main.service.livenoti.LiveNotificationData;

import java.util.Locale;

public class LiveNotificationActivity extends AppCompatActivity {

    String Device_name;
    String Device_id;

    ScrollView liveNotiScrollView;
    SwipeRefreshLayout liveNotiRefreshLayout;
    LinearLayoutCompat liveNotiLayout;
    LinearLayoutCompat liveNotiStateLayout;
    ImageView liveNotiErrorEmoji;
    TextView liveNotiStateDescription;
    ProgressBar liveNotiStateProgress;

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

        liveNotiLayout.setVisibility(View.GONE);
        liveNotiErrorEmoji.setVisibility(View.GONE);
        liveNotiScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> liveNotiRefreshLayout.setEnabled(liveNotiLayout.getVisibility() == View.VISIBLE && liveNotiScrollView.getScrollY() == 0));

        LiveNotiProcess.mOnLiveNotificationUploadCompleteListener = (isSuccess, unused ) -> runOnUiThread(() -> {
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

        getLiveNotiList();
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
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
        liveNotiStateLayout.setVisibility(visible ? View.GONE : View.VISIBLE);
        liveNotiStateProgress.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private void makeListView(LiveNotificationData[] liveNotificationList) {
        if(liveNotificationList.length > 0) for(LiveNotificationData liveNotificationObj : liveNotificationList) {
            CoordinatorLayout liveNotiItem = (CoordinatorLayout) View.inflate(this, R.layout.cardview_rilenoti_item, null);
            MaterialCardView liveNotiItemParent = liveNotiItem.findViewById(R.id.liveNotiItemParent);

            TextView liveNotiTitle = liveNotiItem.findViewById(R.id.notiTitle);
            TextView liveNotiDescription = liveNotiItem.findViewById(R.id.notiDescription);
            TextView liveNotiAppName = liveNotiItem.findViewById(R.id.appName);

            liveNotiTitle.setText(liveNotificationObj.title);
            liveNotiDescription.setText(liveNotificationObj.message);
            liveNotiAppName.setText(String.format(Locale.getDefault(),"%s • %s",
                    liveNotificationObj.appName, getReadableTimeDiff(liveNotificationObj.postTime)));

            liveNotiItemParent.setOnClickListener(v -> {

            });

            liveNotiLayout.addView(liveNotiItem);
            setNotiListVisibility(true);
        } else {
            showError("There are currently no notifications\ndisplayed on your device.", true);
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
