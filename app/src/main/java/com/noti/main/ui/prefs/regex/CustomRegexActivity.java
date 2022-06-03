package com.noti.main.ui.prefs.regex;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class CustomRegexActivity extends AppCompatActivity {

    private static final List<Fragment> mFragments = new ArrayList<>();
    public static ActivityResultLauncher<Intent> startAddOptionActivity;
    SharedPreferences regexPrefs;

    @SuppressLint({"NonConstantResourceId", "NotifyDataSetChanged"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_regex);

        regexPrefs = getSharedPreferences("com.noti.main_regex", Context.MODE_PRIVATE);
        startAddOptionActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            try {
                Intent data = result.getData();
                if (data != null && data.hasExtra("index")) {
                    RegexListFragment.adapter.array = new JSONArray(regexPrefs.getString("RegexData", ""));
                    int index = data.getIntExtra("index", -1);
                    if (index > -1)
                        new Handler(getMainLooper()).post(() -> RegexListFragment.adapter.notifyItemChanged(index));
                    else
                        new Handler(getMainLooper()).post(() -> RegexListFragment.adapter.notifyDataSetChanged());
                    Log.d("ddd", index + "");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

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
                        navigationView.getMenu().findItem(R.id.page_1).setChecked(true);
                        actionButton.setImageResource(R.drawable.ic_fluent_add_24_regular);
                        break;

                    case 1:
                        navigationView.getMenu().findItem(R.id.page_2).setChecked(true);
                        actionButton.setImageResource(R.drawable.ic_fluent_question_24_filled);
                        break;
                }
            }
        });

        actionButton.setOnClickListener((v) -> {
            Intent intent;
            if(viewPager.getCurrentItem() == 0) {
                intent = new Intent(this, AddActionActivity.class);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender/wiki/Custom-Regular-expression-Reference"));
            }
            startAddOptionActivity.launch(intent);
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
