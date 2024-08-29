package com.noti.main.ui.prefs;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;

import com.android.volley.Response;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import com.noti.main.Application;
import com.noti.main.R;
import com.noti.main.service.backend.PacketConst;
import com.noti.main.service.backend.PacketRequester;
import com.noti.main.service.backend.ResultPacket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

@SuppressLint("SetTextI18n")
public class ServerPingActivity extends AppCompatActivity {

    AppCompatImageView pingStateEmoji;
    ProgressBar pingStateProgress;
    TextView pingStateTitle;
    TextView pingStateDescription;

    long startTime;
    Response.Listener<JSONObject> successListener = response -> {
        try {
            pingStateProgress.setVisibility(View.GONE);
            pingStateEmoji.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_fluent_accessibility_checkmark_24_regular));
            pingStateTitle.setText("Server Calibration and Test Done.");
            String cause = """
                    Time taken: %d (ms)
                    Server Version: %s
                    """;
            setPingStateDescription(String.format(Locale.getDefault(), cause, (System.currentTimeMillis() - startTime), ResultPacket.parseFrom(response.toString()).getExtraData()));
        } catch (IOException e) {
            setError(e);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ping_server);
        getWindow().setStatusBarColor(getResources().getColor(R.color.ui_bg_toolbar, null));
        ((MaterialToolbar)findViewById(R.id.toolbar)).setNavigationOnClickListener((v) -> this.finish());

        pingStateEmoji = findViewById(R.id.pingStateEmoji);
        pingStateProgress = findViewById(R.id.pingStateProgress);
        pingStateTitle = findViewById(R.id.pingStateTitle);
        pingStateDescription = findViewById(R.id.pingStateDescription);
        pingStateDescription.setVisibility(View.GONE);

        if (getCurrentDns().equals(PacketConst.API_DOMAIN)) {
            performDefaultDnsTest();
        } else {
            pingStateEmoji.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_fluent_plug_connected_24_regular));
            pingStateTitle.setText("Performing current DNS ping test...");
            performSavedDnsPing();
        }
    }

    void performSavedDnsPing() {
        startTime = System.currentTimeMillis();
        try {
            JSONObject serverBody = new JSONObject();
            PacketRequester.addToRequestQueue(this, PacketConst.SERVICE_TYPE_PING_SERVER, serverBody, successListener, error -> {
                if (error.networkResponse == null) {
                    setError(error);
                } else {
                    performDefaultDnsTest();
                }
            });
        } catch (JSONException e) {
            setError(e);
        }
    }

    void performDefaultDnsTest() {
        startTime = System.currentTimeMillis();
        setDnsToPreference(PacketConst.API_DOMAIN);

        pingStateEmoji.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_fluent_plug_connected_settings_24_regular));
        pingStateTitle.setText("Performing built-in DNS ping test...");

        try {
            JSONObject serverBody = new JSONObject();
            PacketRequester.addToRequestQueue(this, PacketConst.SERVICE_TYPE_PING_SERVER, serverBody, successListener, error -> {
                if (error.networkResponse == null) {
                    setError(error);
                } else {
                    pingStateEmoji.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_fluent_database_search_24_regular));
                    pingStateTitle.setText("Getting preferred DNS from Database...");
                    Log.d("ErrorCode", "Dns: " + getCurrentDns() + " Code: " + error.networkResponse);

                    OnCompleteListener<QuerySnapshot> onDBCompleteListener = task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            pingStateEmoji.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_fluent_plug_connected_24_regular));
                            pingStateTitle.setText("Performing external DNS ping test...");

                            QuerySnapshot document = task.getResult();
                            String recommendedDns = document.getDocuments().get(0).getString("availableDns");
                            performExternalDnsPing(recommendedDns);
                        } else {
                            setError(Objects.requireNonNullElse(task.getException(), new IllegalStateException("Error occurred while trying to query DB")));
                        }
                    };

                    FirebaseFirestore mFirebaseFireStore = FirebaseFirestore.getInstance();
                    mFirebaseFireStore.collection("ApiKey")
                            .get()
                            .addOnCompleteListener(onDBCompleteListener);
                }
            });
        } catch (Exception e) {
            setError(e);
        }
    }

    void performExternalDnsPing(String dnsAddress) {
        setDnsToPreference(dnsAddress);
        startTime = System.currentTimeMillis();

        try {
            JSONObject serverBody = new JSONObject();
            PacketRequester.addToRequestQueue(this, PacketConst.SERVICE_TYPE_PING_SERVER, serverBody, successListener, error -> {
                        setError(error);
                        if (error.networkResponse != null) {
                            String cause = """
                                    Time taken: %d (ms)
                                    Error code: %s
                                    """;
                            setPingStateDescription(String.format(Locale.getDefault(), cause, (System.currentTimeMillis() - startTime), error.networkResponse.statusCode));
                            setDnsToPreference(PacketConst.API_DOMAIN);
                        }
                    }
            );
        } catch (JSONException e) {
            setError(e);
        }
    }

    String getCurrentDns() {
        return getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE)
                .getString(PacketConst.API_PREFS_DOMAIN_KEY, PacketConst.API_DOMAIN);
    }

    void setDnsToPreference(String dns) {
        getSharedPreferences(Application.PREFS_NAME, MODE_PRIVATE).edit()
                .putString(PacketConst.API_PREFS_DOMAIN_KEY, dns).apply();
    }

    void setError(Exception error) {
        pingStateProgress.setVisibility(View.GONE);
        pingStateEmoji.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_fluent_globe_error_24_regular));
        pingStateTitle.setText("Server calibration failed.");

        String cause = """
                Time taken: %d (ms)
                Exception: %s
                """;
        String message = Objects.requireNonNullElse(error.getMessage(), "No message");
        setPingStateDescription(String.format(Locale.getDefault(), cause, (System.currentTimeMillis() - startTime), message));
    }

    void setPingStateDescription(String description) {
        pingStateDescription.setVisibility(View.VISIBLE);
        pingStateDescription.setText(description);
    }
}
