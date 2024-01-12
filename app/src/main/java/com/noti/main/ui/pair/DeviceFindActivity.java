package com.noti.main.ui.pair;

import static com.noti.main.service.NotiListenerService.getUniqueID;
import static com.noti.main.service.NotiListenerService.sendNotification;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.noti.main.Application;
import com.noti.main.R;

import org.json.JSONException;
import org.json.JSONObject;

public class DeviceFindActivity extends AppCompatActivity implements OnMapReadyCallback {

    String deviceId;
    String deviceName;
    private GoogleMap mGoogleMap;

    public static OnLocationResponseListener mOnLocationResponseListener;
    public interface OnLocationResponseListener {
        void onLocationResponse(boolean isSuccess, Double latitude, Double longitude);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair_find);

        Intent intent = getIntent();
        deviceId = intent.getStringExtra("device_id");
        deviceName = intent.getStringExtra("device_name");

        new Handler().postDelayed(() -> {
            SupportMapFragment findDeviceMapview = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.findDeviceMapview);
            if(findDeviceMapview != null) {
                findDeviceMapview.getMapAsync(this);
            }
        }, 1000);

        LinearLayout findProgressBar = findViewById(R.id.findProgressBar);
        Button reloadLocationButton = findViewById(R.id.reloadLocationButton);
        Button playSoundButton = findViewById(R.id.playSoundButton);

        playSoundButton.setOnClickListener(v -> requestFind(deviceId, deviceName, true, false));
        reloadLocationButton.setOnClickListener(v -> {
            findProgressBar.setVisibility(View.VISIBLE);
            requestFind(deviceId, deviceName, false, true);
        });

        mOnLocationResponseListener = (isSuccess, latitude, longitude) -> runOnUiThread(() -> {
            if(isSuccess) {
                mGoogleMap.clear();

                LatLng deviceLocation = new LatLng(latitude, longitude);
                MarkerOptions markerOptions = new MarkerOptions();
                markerOptions.position(deviceLocation);
                markerOptions.title("Your Device's Location");
                markerOptions.snippet(deviceName);

                mGoogleMap.addMarker(markerOptions);
                mGoogleMap.moveCamera(CameraUpdateFactory.newLatLng(deviceLocation));
                mGoogleMap.animateCamera(CameraUpdateFactory.zoomTo(15));
                findProgressBar.setVisibility(View.GONE);
            } else {
                MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.Theme_App_Palette_Dialog));
                dialog.setTitle("Unable to get location information");
                dialog.setMessage("Click \"Reload Location\" to try obtaining location information again.");
                dialog.setIcon(R.drawable.ic_info_outline_black_24dp);
                dialog.setPositiveButton("Okay", (dialog1, which) -> { });
                dialog.show();
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mOnLocationResponseListener = null;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mGoogleMap = googleMap;
        mGoogleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        requestFind(deviceId, deviceName, false, true);
    }

    private void requestFind(String deviceId, String deviceName, boolean playSound, boolean locationRequired) {
        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getUniqueID();
        String TOPIC = "/topics/" + getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).getString("UID", "");

        JSONObject notificationHead = new JSONObject();
        JSONObject notificationBody = new JSONObject();
        try {
            notificationBody.put("type", "pair|find");
            notificationBody.put("findType", "findRequest");
            notificationBody.put("playSound", playSound);
            notificationBody.put("locationRequest", locationRequired);

            notificationBody.put("device_name", DEVICE_NAME);
            notificationBody.put("device_id", DEVICE_ID);
            notificationBody.put("send_device_name", deviceName);
            notificationBody.put("send_device_id", deviceId);
            notificationBody.put("date", Application.getDateString());

            notificationHead.put("to", TOPIC);
            notificationHead.put("data", notificationBody);
        } catch (JSONException e) {
            Log.e("Noti", "onCreate: " + e.getMessage());
        }
        sendNotification(notificationHead, getPackageName(), this);
    }

    @SuppressLint("MissingPermission")
    public static void responseLocation(Context context, String deviceId, String deviceName) {
        String DEVICE_NAME = Build.MANUFACTURER + " " + Build.MODEL;
        String DEVICE_ID = getUniqueID();
        String TOPIC = "/topics/" + context.getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).getString("UID", "");

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                .addOnCompleteListener(task -> {
                    JSONObject notificationHead = new JSONObject();
                    JSONObject notificationBody = new JSONObject();
                    try {
                        notificationBody.put("type", "pair|find");
                        notificationBody.put("findType", "locationResponse");

                        if(task.isSuccessful()) {
                            Location location = task.getResult();
                            if(location != null) {
                                notificationBody.put("isSuccess", true);
                                notificationBody.put("latitude", location.getLatitude());
                                notificationBody.put("longitude", location.getLongitude());
                            } else {
                                notificationBody.put("isSuccess", false);
                            }
                        } else {
                            notificationBody.put("isSuccess", false);
                        }

                        notificationBody.put("device_name", DEVICE_NAME);
                        notificationBody.put("device_id", DEVICE_ID);
                        notificationBody.put("send_device_name", deviceName);
                        notificationBody.put("send_device_id", deviceId);
                        notificationBody.put("date", Application.getDateString());

                        notificationHead.put("to", TOPIC);
                        notificationHead.put("data", notificationBody);
                    } catch (JSONException e) {
                        Log.e("Noti", "onCreate: " + e.getMessage());
                    }
                    sendNotification(notificationHead, context.getPackageName(), context);
                });
    }
}
