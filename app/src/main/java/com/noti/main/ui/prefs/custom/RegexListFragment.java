package com.noti.main.ui.prefs.custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.noti.main.R;

import org.json.JSONArray;
import org.json.JSONException;

public class RegexListFragment extends Fragment {

    AppCompatActivity mContext;
    static SharedPreferences regexPrefs;
    private ItemTouchHelper mItemTouchHelper;

    @SuppressLint("StaticFieldLeak")
    static RegexItemAdapter adapter;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) mContext = (AppCompatActivity) context;
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
        regexPrefs = mContext.getSharedPreferences("com.noti.main_regex", Context.MODE_PRIVATE);
        ProgressBar progress = mContext.findViewById(R.id.progress);
        RecyclerView listView = view.findViewById(R.id.listView);
        LinearLayoutCompat itemNotAvailableLayout = view.findViewById(R.id.itemNotAvailableLayout);

        progress.setVisibility(View.GONE);
        mContext.runOnUiThread(() -> {
            JSONArray array = new JSONArray();
            try {
                String data = regexPrefs.getString("RegexData", "");
                if(!data.isEmpty()) {
                    array = new JSONArray(data);
                    if(array.length() > 0) itemNotAvailableLayout.setVisibility(View.GONE);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            adapter = new RegexItemAdapter(mContext, array, viewHolder -> mItemTouchHelper.startDrag(viewHolder));
            listView.setAdapter(adapter);
            listView.setLayoutManager(new LinearLayoutManagerWrapper(mContext));

            ItemTouchHelper.Callback callback = new ItemTouchCallback(adapter);
            mItemTouchHelper = new ItemTouchHelper(callback);
            mItemTouchHelper.attachToRecyclerView(listView);
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
