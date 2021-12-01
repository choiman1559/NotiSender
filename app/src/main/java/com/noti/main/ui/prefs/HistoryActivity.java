package com.noti.main.ui.prefs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.adapter.FragmentViewHolder;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.noti.main.R;
import com.noti.main.utils.ThreadProxy;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private static final List<Fragment> mFragments = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        mFragments.add(HistoryFragment.newInstance(0));
        mFragments.add(HistoryFragment.newInstance(1));

        TabLayout TabLayout = findViewById(R.id.tabs);
        ViewPager2 viewPager = findViewById(R.id.viewpager);
        FragmentStateAdapter adapter = new TabsPagerAdapter(getSupportFragmentManager(), getLifecycle());
        adapter.saveState();

        viewPager.setSaveEnabled(true);
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

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }

    public static class TabsPagerAdapter extends FragmentStateAdapter {

        public TabsPagerAdapter(FragmentManager fm, Lifecycle lifecycle) {
            super(fm, lifecycle);
        }

        @Override
        public boolean containsItem(long itemId) {
            return (long) mFragments.hashCode() == itemId || (long) mFragments.get(0).hashCode() == itemId || (long) mFragments.get(1).hashCode() == itemId;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return mFragments.get(position);
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    public static class HistoryFragment extends Fragment {

        Activity mContext;
        int mode;

        HistoryFragment() {

        }

        public static HistoryFragment newInstance(int mode) {
            HistoryFragment historyFragment = new HistoryFragment();
            historyFragment.mode = mode;
            return historyFragment;
        }

        @Override
        public void onAttach(@NonNull Context context) {
            super.onAttach(context);
            if (context instanceof Activity) mContext = (Activity) context;
            else throw new RuntimeException("Can't get Activity instanceof Context!");
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            SharedPreferences prefs = mContext.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
            ProgressBar progress = mContext.findViewById(R.id.progress);
            RecyclerView listView = view.findViewById(R.id.listView);
            LinearLayout layout = view.findViewById(R.id.noneLayout);

            String list_data = prefs.getString(mode == 0 ? "sendLogs" : "receivedLogs", "");
            if (!list_data.equals("")) {
                progress.setVisibility(View.VISIBLE);
                listView.setVisibility(View.GONE);
                layout.setVisibility(View.GONE);
                ThreadProxy.getInstance().execute(() -> {
                    try {
                        HistoryViewAdapter adapter = new HistoryViewAdapter(new JSONArray(list_data), mode, mContext);
                        mContext.runOnUiThread(() -> {
                            listView.setAdapter(adapter);
                            listView.setLayoutManager(new LinearLayoutManagerWrapper(mContext));
                            listView.setVisibility(View.VISIBLE);
                            progress.setVisibility(View.GONE);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } else {
                listView.setVisibility(View.GONE);
                progress.setVisibility(View.GONE);
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
            return inflater.inflate(R.layout.fragment_history, container, false);
        }
    }
}
