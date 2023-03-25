package com.noti.main.ui;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.noti.main.R;
import com.noti.main.ui.options.AccountPreference;
import com.noti.main.ui.options.OtherPreference;
import com.noti.main.ui.options.PairPreference;
import com.noti.main.ui.options.ReceptionPreference;
import com.noti.main.ui.options.SendPreference;
import com.noti.main.ui.pair.PairMainFragment;
import com.noti.main.ui.prefs.HistoryFragment;
import com.noti.main.ui.prefs.custom.CustomFragment;

public class OptionActivity extends AppCompatActivity {

    boolean hideDefaultTitleBar = false;

    private static String title = "Default Message";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Fragment fragment;
        fragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);

        if(savedInstanceState == null || fragment == null) {
            switch (getIntent().getStringExtra("Type")) {
                case "Send":
                    fragment = new SendPreference();
                    title = "Send Options";
                    break;

                case "Reception":
                    fragment = new ReceptionPreference();
                    title = "Reception Options";
                    break;

                case "Other":
                    fragment = new OtherPreference();
                    title = "Other Options";
                    break;

                case "Pair":
                    fragment = new PairPreference();
                    title = "Connection\npreferences";
                    break;

                case "PairMain":
                    fragment = new PairMainFragment();
                    title = "Connected Devices";
                    break;

                case "Account":
                    fragment = new AccountPreference();
                    title = "Service & Account";
                    break;

                case "Customize":
                    fragment = new CustomFragment();
                    hideDefaultTitleBar = true;
                    title = "Plugin & User scripts";
                    break;

                case "History":
                    fragment = new HistoryFragment();
                    hideDefaultTitleBar = true;
                    title = "Notification history";
                    break;

                case "About":
                    fragment = new AboutFragment();
                    title = "About NotiSender";
                    break;

                default:
                    fragment = null;
                    title = getString(R.string.app_name);
                    break;
            }
        }

        setContentView(hideDefaultTitleBar ? R.layout.activity_options_notitle : R.layout.activity_options);
        DynamicColors.applyToActivityIfAvailable(this);

        Bundle bundle = new Bundle(0);
        if(fragment != null) {
            fragment.setArguments(bundle);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_frame, fragment)
                    .commit();
        }

        if(!hideDefaultTitleBar) {
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setTitle(title);
            toolbar.setNavigationOnClickListener((v) -> this.finish());
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(hideDefaultTitleBar ? R.layout.activity_options_notitle : R.layout.activity_options);
    }

    public static void attachFragment(FragmentActivity activity, Fragment fragment) {
        Bundle bundle = new Bundle(0);
        fragment.setArguments(bundle);

        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit();
    }
}
