package com.noti.main.ui.prefs.regex;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.noti.main.R;

import org.json.JSONArray;

public class RegexItemAdapter extends RecyclerView.Adapter<RegexItemAdapter.RegexItemHolder> {

    Activity mContext;
    JSONArray array;

    public RegexItemAdapter(Activity mContext, JSONArray array) {
        this.mContext = mContext;
        this.array = array;
    }

    @NonNull
    @Override
    public RegexItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.cardview_regex_item, parent, false);
        return new RegexItemHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RegexItemHolder holder, int position) {

    }

    @Override
    public int getItemCount() {
        return array.length();
    }

    public static class RegexItemHolder extends RecyclerView.ViewHolder {
        public RegexItemHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}