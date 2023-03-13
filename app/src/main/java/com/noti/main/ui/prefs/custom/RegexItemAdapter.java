package com.noti.main.ui.prefs.custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.noti.main.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RegexItemAdapter extends RecyclerView.Adapter<RegexItemAdapter.RegexItemHolder> implements ItemTouchCallback.ItemTouchAdapter {

    final ItemTouchCallback.OnStartDragListener mDragStartListener;
    SharedPreferences regexPrefs;
    AppCompatActivity mContext;
    JSONArray array;

    public RegexItemAdapter(AppCompatActivity mContext, JSONArray array, ItemTouchCallback.OnStartDragListener listener) {
        this.mContext = mContext;
        this.array = array;
        this.regexPrefs = mContext.getSharedPreferences("com.noti.main_regex", Context.MODE_PRIVATE);
        this.mDragStartListener = listener;
    }

    @NonNull
    @Override
    public RegexItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.cardview_regex_item, parent, false);
        return new RegexItemHolder(view);
    }

    @SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
    @Override
    public void onBindViewHolder(@NonNull RegexItemHolder holder, int position) {
        try {
            JSONObject object = array.getJSONObject(position);
            holder.Title.setText(object.getString("label"));
            holder.Enabled.setChecked(object.has("enabled") && object.getBoolean("enabled"));

            boolean hasRingTine = object.has("ringtone");
            boolean hasBitmap = object.has("bitmap");
            if(hasRingTine || hasBitmap) {
                String description = "Change Notification ";
                if(hasRingTine && hasBitmap) description += "Ringtone, Icon";
                else if(hasRingTine) description += "Ringtone";
                else description += "Icon";

                holder.TaskDescription.setText(description);
            }
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

        holder.Parent.setOnLongClickListener(view -> {
            mDragStartListener.onStartDrag(holder);
            return true;
        });

        holder.Parent.setOnClickListener((v) -> {
            Intent intent = new Intent(mContext, AddActionActivity.class);
            intent.putExtra("index", position);
            CustomActivity.startAddOptionActivity.launch(intent);
        });
    }

    @Override
    public int getItemCount() {
        return array.length();
    }

    @Override
    public void onItemMove(int from, int target) {
        try {
            if (from != target) {
                JSONObject object = array.getJSONObject(from);
                array.remove(from);

                for (int i = array.length(); i > target; i--) {
                    array.put(i, array.get(i - 1));
                }
                array.put(target, object);
                regexPrefs.edit().putString("RegexData", array.toString()).apply();
                new Handler().post(() -> notifyItemMoved(from, target));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onItemDismiss(int position) {
        array.remove(position);
        regexPrefs.edit().putString("RegexData", array.toString()).apply();
        new Handler().post(() -> notifyItemRemoved(position));
    }

    public static class RegexItemHolder extends RecyclerView.ViewHolder implements ItemTouchCallback.ItemTouchEvent {
        TextView Title;
        TextView TaskDescription;
        SwitchMaterial Enabled;
        MaterialCardView Parent;

        public RegexItemHolder(@NonNull View itemView) {
            super(itemView);
            Title = itemView.findViewById(R.id.Title);
            Enabled = itemView.findViewById(R.id.Enabled);
            Parent = itemView.findViewById(R.id.Parent);
            TaskDescription = itemView.findViewById(R.id.taskDescription);
        }

        @Override
        public void onItemSelected() {
        }

        @Override
        public void onItemClear() {
            itemView.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}