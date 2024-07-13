package com.noti.main.service.backend;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.noti.main.Application;
import com.noti.main.utils.network.JsonRequest;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PacketRequester {
    public static void addToRequestQueue(Context context, String serviceType, JSONObject packetBody,
                                         Response.Listener<org. json. JSONObject> listener, @Nullable Response.ErrorListener errorListener) {

        SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
        final String URI = getApiAddress(serviceType, prefs.getBoolean("useDebugBackend", false));
        boolean notUseAuthentication = prefs.getBoolean("notUseAuthentication", false);

        if(notUseAuthentication) {
            postPacket(context, null, URI, packetBody, listener, errorListener);
        } else {
            FirebaseUser mUser = FirebaseAuth.getInstance().getCurrentUser();
            if(mUser != null) {
                mUser.getIdToken(true)
                        .addOnFailureListener(task -> Objects.requireNonNull(task.getCause()).printStackTrace())
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                String idToken = task.getResult().getToken();
                                postPacket(context, idToken, URI, packetBody, listener, errorListener);
                            } else {
                                Objects.requireNonNull(task.getException()).printStackTrace();
                            }
                        });
            }
        }
    }

    private static void postPacket(Context context, @Nullable String idToken, String URI, JSONObject packetBody,
                                   Response.Listener<org. json. JSONObject> listener, @Nullable Response.ErrorListener errorListener) {
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, URI, packetBody, listener, errorListener) {
            @Override
            public Map<String, String> getHeaders() {
                return PacketRequester.getHeaders(idToken);
            }
        };
        JsonRequest.getInstance(context).addToRequestQueue(jsonObjectRequest);
    }

    public static String getApiAddress(String serviceType, boolean isDebug) {
        return String.format(PacketConst.API_ROUTE_SCHEMA, PacketConst.API_DOMAIN,
                isDebug ? PacketConst.API_DEBUG_ROUTE : PacketConst.API_PUBLIC_ROUTE, serviceType);
    }

    public static Map<String, String> getHeaders(@Nullable String idToken) {
        Map<String, String> params = new HashMap<>();
        params.put("Content-Type", PacketConst.contentType);

        if(idToken != null) {
            params.put("Authorization", "Bearer " + idToken);
        }
        return params;
    }
}
