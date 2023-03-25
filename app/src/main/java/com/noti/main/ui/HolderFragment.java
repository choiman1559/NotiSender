package com.noti.main.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.noti.main.R;
import com.noti.main.ui.options.AccountPreference;
import com.noti.main.ui.options.OtherPreference;
import com.noti.main.ui.options.ReceptionPreference;
import com.noti.main.ui.options.SendPreference;
import com.noti.main.ui.pair.PairMainFragment;
import com.noti.main.ui.prefs.HistoryFragment;
import com.noti.main.ui.prefs.custom.CustomFragment;

public class HolderFragment extends Fragment {

    private String Type;
    public MaterialToolbar mToolbar;

    public void setType(String type) {
        Type = type;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_options, container, false);
        view.setBackgroundColor(getResources().getColor(R.color.ui_bg_surface));

        mToolbar = view.findViewById(R.id.toolbar);
        mToolbar.setTitle(R.string.app_name);
        mToolbar.setNavigationIcon(null);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(Type == null) Type = "Account";
        commitFragment();
    }

    public void commitFragment() {
        Fragment fragment = null;
        String title;

        switch (Type) {
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
                View view = getView();
                if(view != null) view.findViewById(R.id.app_bar_layout).setVisibility(View.GONE);
                title = "Plugin & User scripts";
                break;

            case "History":
                fragment = new HistoryFragment();
                View view2 = getView();
                if(view2 != null) view2.findViewById(R.id.app_bar_layout).setVisibility(View.GONE);
                title = "Notification history";
                break;

            case "About":
                fragment = new AboutFragment();
                title = "About NotiSender";
                break;

            default:
                title = getString(R.string.app_name);
                break;
        }

        if(fragment != null) {
            mToolbar.setTitle(title);
            Bundle bundle = new Bundle(0);
            fragment.setArguments(bundle);

            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_frame, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }
}
