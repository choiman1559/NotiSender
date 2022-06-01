package com.noti.main.ui.prefs.regex;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.noti.main.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RegexItemAdapter extends RecyclerView.Adapter<RegexItemAdapter.RegexItemHolder> {

    SharedPreferences regexPrefs;
    Activity mContext;
    JSONArray array;

    public RegexItemAdapter(Activity mContext, JSONArray array) {
        this.mContext = mContext;
        this.array = array;
        this.regexPrefs = mContext.getSharedPreferences("com.noti.main_regex", Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public RegexItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.cardview_regex_item, parent, false);
        return new RegexItemHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RegexItemHolder holder, int position) {
        try {
            JSONObject object = array.getJSONObject(position);
            holder.Title.setText(object.getString("label"));
            holder.Enabled.setChecked(object.has("enabled") && object.getBoolean("enabled"));
        } catch (JSONException e) {
            e.printStackTrace();
            holder.Enabled.setEnabled(false);
            holder.Title.setText("Error!");
        }

        holder.Enabled.setOnCheckedChangeListener((compoundButton, b) -> {
            try {
                JSONObject object = array.getJSONObject(position);
                object.put("enabled", b);
                array.put(position, object);
                regexPrefs.edit().putString("RegexData", array.toString()).apply();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        holder.Enabled.setOnLongClickListener(view -> {
            return true;
        });

        holder.Parent.setOnClickListener((v) -> {
            Intent intent = new Intent(mContext, AddActionActivity.class);
            intent.putExtra("index", position);
            mContext.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return array.length();
    }

    public static class RegexItemHolder extends RecyclerView.ViewHolder {
        TextView Title;
        SwitchMaterial Enabled;
        MaterialCardView Parent;

        public RegexItemHolder(@NonNull View itemView) {
            super(itemView);
            Title = itemView.findViewById(R.id.Title);
            Enabled = itemView.findViewById(R.id.Enabled);
            Parent = itemView.findViewById(R.id.Parent);
        }
    }
}