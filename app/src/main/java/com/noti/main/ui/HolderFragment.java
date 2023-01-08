package com.noti.main.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.noti.main.R;
import com.noti.main.ui.options.AccountPreference;
import com.noti.main.ui.options.OtherPreference;
import com.noti.main.ui.options.ReceptionPreference;
import com.noti.main.ui.options.SendPreference;

public class HolderFragment extends Fragment {

    private String Type;
    public MaterialToolbar mToolbar;
    private AppCompatActivity mActivity;

    public void setType(String type) {
        Type = type;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) {
            mActivity = (AppCompatActivity) context;
        } else {
            throw new RuntimeException("must implement AppCompatActivity");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_options, container, false);
        mToolbar = view.findViewById(R.id.toolbar);
        mToolbar.setTitle(R.string.app_name);
        mToolbar.setNavigationIcon(null);
        mActivity.getDelegate().setSupportActionBar(mToolbar);

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

            case "Pair":
                //fragment = new PairPreference();
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
                title = getString(R.string.app_name);
                break;
        }

        if(fragment != null) {
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
