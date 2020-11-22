package com.noti.sender;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONArray;
import org.json.JSONException;

public class HistoryActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ImageView BackButton = findViewById(R.id.back);
        TabLayout TabLayout = findViewById(R.id.tabs);
        ViewPager2 viewPager = findViewById(R.id.viewpager);
        FragmentStateAdapter adapter = new TabsPagerAdapter(this,getSupportFragmentManager(),getLifecycle());

        viewPager.setAdapter(adapter);
        new TabLayoutMediator(TabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Sent");
                    break;

                case 1:
                    tab.setText("Received");
                    break;

                default:
                    tab.setText("Error!");
                    break;
            }
        }).attach();

        BackButton.setOnClickListener(v -> finish());
    }

    public static class TabsPagerAdapter extends FragmentStateAdapter {
        private final Activity mContext;

        public TabsPagerAdapter(Activity context, FragmentManager fm,Lifecycle lifecycle) {
            super(fm,lifecycle);
            mContext = context;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return new HistoryFragment(mContext,position);
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    public static class HistoryFragment extends Fragment {

        Activity mContext;
        int mode;

        HistoryFragment(Activity context, int mode) {
            this.mContext = context;
            this.mode = mode;
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            SharedPreferences prefs = mContext.getSharedPreferences("com.noti.sender_preferences",MODE_PRIVATE);
            RecyclerView listView = view.findViewById(R.id.listView);
            LinearLayout layout = view.findViewById(R.id.noneLayout);
            HistoryViewAdapter adapter;

            String list_data = prefs.getString(mode == 0 ? "sendLogs" : "receivedLogs","");
            if(!list_data.equals("")) {
                listView.setVisibility(View.VISIBLE);
                layout.setVisibility(View.GONE);
                try {
                    adapter = new HistoryViewAdapter(new JSONArray(list_data),mode,mContext);
                    listView.setAdapter(adapter);
                    listView.setLayoutManager(new LinearLayoutManagerWrapper(mContext));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                listView.setVisibility(View.GONE);
                layout.setVisibility(View.VISIBLE);
            }
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

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return  inflater.inflate(R.layout.fragment_history, container, false);
        }
    }
}
