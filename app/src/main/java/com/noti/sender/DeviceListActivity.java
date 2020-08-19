package com.noti.sender;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.JsonObject;

import java.util.List;

@VisibleForTesting
public class DeviceListActivity extends Activity {
    private ListView deviceListView;
    private List<JsonObject> deviceShowInfo;
    private ProgressBar pg;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        deviceListView = findViewById(R.id.device_list);
        pg = findViewById(R.id.pg);

        ThreadProxy.getInstance().execute(new Runnable() {
            private ShowDeviceAdapter showPackageAdapter;

            @Override
            public void run() {
                //deviceShowInfo = PackageShowInfo.getPackageShowInfo(getApplicationContext());
                showPackageAdapter = new ShowDeviceAdapter();
                runOnUiThread(() -> {
                    deviceListView.setAdapter(showPackageAdapter);
                    pg.setVisibility(View.GONE);
                });
            }
        });
    }

    class ShowDeviceAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return deviceShowInfo.size();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {

            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final Holder holder;
            if (convertView == null) {
                convertView = View.inflate(DeviceListActivity.this, R.layout.item_select_package, null);
                holder = new Holder(convertView, position);
                convertView.setTag(holder);
            } else {
                holder = (Holder) convertView.getTag();
                holder.holderPosition = position;
            }
            return convertView;
        }

        class Holder {
            TextView device_name;
            TextView device_id;
            View baseView;
            int holderPosition;

            Holder(View view, int position) {
                baseView = view;
                device_name = view.findViewById(R.id.device_name);
                device_id = view.findViewById(R.id.device_id);
                this.holderPosition = position;
            }
        }
    }
}
