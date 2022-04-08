package com.noti.main.ui.options;

import static android.content.Context.MODE_PRIVATE;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.kieronquinn.monetcompat.core.MonetCompat;
import com.noti.main.R;
import com.noti.main.service.NotiListenerService;
import com.noti.main.utils.ui.ToastHelper;

public class PairPreference extends PreferenceFragmentCompat  {

    Activity mContext;
    MonetCompat monet;
    SharedPreferences prefs;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        MonetCompat.setup(requireContext());
        monet = MonetCompat.getInstance();
        monet.updateMonetColors();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        monet = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) mContext = (Activity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.pair_preferences, rootKey);
        prefs = mContext.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference) {
        switch(preference.getKey()) {
            case "findPhone":
                if(!prefs.getString("UID","").isEmpty()) {
                    NotiListenerService.sendFindTaskNotification(mContext);
                    ToastHelper.show(mContext, "Your request is posted!","OK", ToastHelper.LENGTH_SHORT);
                } else ToastHelper.show(mContext, "Please check your account status and try again!","Dismiss", ToastHelper.LENGTH_SHORT);
                break;

            case "":
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }
}
