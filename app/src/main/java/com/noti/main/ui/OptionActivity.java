package com.noti.main.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.noti.main.R;
import com.noti.main.ui.options.AccountPreference;
import com.noti.main.ui.options.OtherPreference;
import com.noti.main.ui.options.PairPreference;
import com.noti.main.ui.options.ReceptionPreference;
import com.noti.main.ui.options.SendPreference;

public class OptionActivity extends AppCompatActivity {

    private static String title = "Default Message";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);

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

                case "Account":
                    fragment = new AccountPreference();
                    title = "Service & Account";
                    break;

                case "History":
                    //fragment = new HistoryPreference();
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

        Bundle bundle = new Bundle(0);
        if(fragment != null) {
            fragment.setArguments(bundle);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_frame, fragment)
                    .commit();
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
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
