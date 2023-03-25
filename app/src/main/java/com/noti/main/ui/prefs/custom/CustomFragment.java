package com.noti.main.ui.prefs.custom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.utils.BillingHelper;
import com.noti.main.utils.ui.ToastHelper;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class CustomFragment extends Fragment {

    private static final List<Fragment> mFragments = new ArrayList<>();
    public static ActivityResultLauncher<Intent> startAddOptionActivity;
    SharedPreferences regexPrefs;
    Activity mContext;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_regex, container, false);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) mContext = (Activity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @SuppressLint({"NonConstantResourceId", "NotifyDataSetChanged"})
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        regexPrefs = mContext.getSharedPreferences("com.noti.main_regex", Context.MODE_PRIVATE);
        startAddOptionActivity = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            try {
                Intent data = result.getData();
                if (data != null && data.hasExtra("index")) {
                    RegexListFragment.adapter.array = new JSONArray(regexPrefs.getString("RegexData", ""));
                    int index = data.getIntExtra("index", -1);
                    if (index > -1)
                        new Handler(mContext.getMainLooper()).post(() -> RegexListFragment.adapter.notifyItemChanged(index));
                    else
                        new Handler(mContext.getMainLooper()).post(() -> RegexListFragment.adapter.notifyDataSetChanged());
                    Log.d("ddd", index + "");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        mFragments.add(new PluginFragment());
        mFragments.add(new RegexListFragment());
        mFragments.add(new PlaygroundFragment());

        BottomNavigationView navigationView = view.findViewById(R.id.bottom_navigation);
        ViewPager2 viewPager = view.findViewById(R.id.viewpager);
        FloatingActionButton actionButton = view.findViewById(R.id.Button_Action);

        FragmentStateAdapter adapter = new TabsPagerAdapter(getChildFragmentManager(), getLifecycle());
        adapter.saveState();

        viewPager.setSaveEnabled(true);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 0:
                        navigationView.getMenu().findItem(R.id.page_0).setChecked(true);
                        actionButton.setImageResource(R.drawable.ic_fluent_developer_board_search_24_regular);
                        break;

                    case 1:
                        navigationView.getMenu().findItem(R.id.page_1).setChecked(true);
                        actionButton.setImageResource(R.drawable.ic_fluent_add_24_regular);
                        break;

                    case 2:
                        navigationView.getMenu().findItem(R.id.page_2).setChecked(true);
                        actionButton.setImageResource(R.drawable.ic_fluent_question_24_filled);
                        break;
                }
            }
        });

        actionButton.setOnClickListener((v) -> {
            Intent intent = null;
            switch (viewPager.getCurrentItem()) {
                case 0:
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender-PluginLibrary"));
                break;

                case 1:
                    try {
                        BillingHelper billingHelper = BillingHelper.getInstance();
                        if (RegexListFragment.adapter.array.length() < 3 || billingHelper.isSubscribedOrDebugBuild()) {
                            intent = new Intent(mContext, AddActionActivity.class);
                        } else {
                            BillingHelper.showSubscribeInfoDialog(mContext, "Without a subscription, you can only add up to 3 objects.");
                        }
                    } catch (IllegalStateException e) {
                        ToastHelper.show(mContext, "Error: Can't get purchase information!", ToastHelper.LENGTH_SHORT);
                    }
                    break;

                case 2:
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender/wiki/Custom-Regular-expression-Reference"));
                    break;
            }
            if (intent != null) startAddOptionActivity.launch(intent);
        });

        navigationView.setOnItemSelectedListener(item -> {
            switch (item.getItemId()) {
                case R.id.page_0:
                    viewPager.setCurrentItem(0);
                    break;

                case R.id.page_1:
                    viewPager.setCurrentItem(1);
                    break;

                case R.id.page_2:
                    viewPager.setCurrentItem(2);
                    break;
            }
            return true;
        });

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        if(Application.isTablet()) toolbar.setNavigationIcon(null);
        toolbar.setNavigationOnClickListener((v) -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });
    }

    public static class TabsPagerAdapter extends FragmentStateAdapter {

        public TabsPagerAdapter(FragmentManager fm, Lifecycle lifecycle) {
            super(fm, lifecycle);
        }

        @Override
        public boolean containsItem(long itemId) {
            return (long) mFragments.hashCode() == itemId || (long) mFragments.get(0).hashCode() == itemId || (long) mFragments.get(1).hashCode() == itemId || (long) mFragments.get(2).hashCode() == itemId;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return mFragments.get(position);
        }

        @Override
        public int getItemCount() {
            return 3;
        }

        @Override
        public long getItemId(int position) {
            return mFragments.get(position).hashCode();
        }
    }
}
