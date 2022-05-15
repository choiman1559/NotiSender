package com.noti.main.ui.prefs.regex;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.noti.main.R;

import java.util.ArrayList;
import java.util.List;

public class CustomRegexActivity extends AppCompatActivity {

    private static final List<Fragment> mFragments = new ArrayList<>();

    @SuppressLint("NonConstantResourceId")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_regex);

        mFragments.add(new RegexListFragment());
        mFragments.add(new PlaygroundFragment());

        BottomNavigationView navigationView = findViewById(R.id.bottom_navigation);
        ViewPager2 viewPager = findViewById(R.id.viewpager);
        FloatingActionButton actionButton = findViewById(R.id.Button_Action);

        FragmentStateAdapter adapter = new TabsPagerAdapter(getSupportFragmentManager(), getLifecycle());
        adapter.saveState();

        viewPager.setSaveEnabled(true);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch(position) {
                    case 0:
                        actionButton.setImageResource(R.drawable.ic_fluent_add_24_regular);
                        break;

                    case 1:
                        actionButton.setImageResource(R.drawable.ic_fluent_question_24_filled);
                        break;
                }
            }
        });

        navigationView.setOnItemSelectedListener(item -> {
            switch(item.getItemId()) {
                case R.id.page_1:
                    viewPager.setCurrentItem(0);
                    break;

                case R.id.page_2:
                    viewPager.setCurrentItem(1);
                    break;
            }
            return true;
        });

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

        @Override
        public long getItemId(int position) {
            return mFragments.get(position).hashCode();
        }
    }
}
