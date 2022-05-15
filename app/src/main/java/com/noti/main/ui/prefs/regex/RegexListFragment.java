package com.noti.main.ui.prefs.regex;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.noti.main.R;

import org.json.JSONArray;
import org.json.JSONException;

public class RegexListFragment extends Fragment {
    Activity mContext;
    SharedPreferences regexPrefs;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) mContext = (Activity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list_regex, container, false);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        regexPrefs = mContext.getSharedPreferences("CustomRegex", Context.MODE_PRIVATE);
        ProgressBar progress = mContext.findViewById(R.id.progress);
        RecyclerView listView = view.findViewById(R.id.listView);

        progress.setVisibility(View.GONE);
        mContext.runOnUiThread(() -> {
            JSONArray array = new JSONArray();
            try {
                String data = regexPrefs.getString("RegexData", "");
                if(!data.isEmpty()) array = new JSONArray(data);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            RegexItemAdapter adapter = new RegexItemAdapter(mContext, array);
            listView.setAdapter(adapter);
            listView.setLayoutManager(new LinearLayoutManagerWrapper(mContext));

            regexPrefs.registerOnSharedPreferenceChangeListener((sharedPreferences, s) -> {
                if(s.equals("RegexData")) {
                    try {
                        adapter.array = new JSONArray(regexPrefs.getString("RegexData", ""));
                        adapter.notifyDataSetChanged();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        });
    }

    public static class LinearLayoutManagerWrapper extends LinearLayoutManager {
        public LinearLayoutManagerWrapper(Context context) {
            super(context);
        }

        @Override
        public boolean supportsPredictiveItemAnimations() {
            return false;
        }
    }
}
