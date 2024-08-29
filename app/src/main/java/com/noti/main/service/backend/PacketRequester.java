package com.noti.main.service.backend;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.noti.main.Application;
import com.noti.main.service.NotiListenerService;
import com.noti.main.utils.network.JsonRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PacketRequester {
    public static void addToRequestQueue(Context context, String serviceType, JSONObject packetBody,
                                         Response.Listener<org. json. JSONObject> listener, @Nullable Response.ErrorListener errorListener) throws JSONException {

        SharedPreferences prefs = context.getSharedPreferences(Application.PREFS_NAME, Context.MODE_PRIVATE);
        final String URI = getDefaultApiAddress(prefs, serviceType, prefs.getBoolean("useDebugBackend", false));
        boolean notUseAuthentication = prefs.getBoolean("notUseAuthentication", false);

        packetBody.put(PacketConst.KEY_UID, prefs.getString("UID", ""));
        packetBody.put(PacketConst.KEY_DEVICE_ID, NotiListenerService.getUniqueID());
        packetBody.put(PacketConst.KEY_DEVICE_NAME, NotiListenerService.getDeviceName());

        if(notUseAuthentication) {
            postPacket(context, null, URI, packetBody, listener, errorListener);
        } else {
            FirebaseUser mUser = FirebaseAuth.getInstance().getCurrentUser();
            if(mUser != null) {
                mUser.getIdToken(true)
                        .addOnFailureListener(task -> {
                            if(errorListener != null) {
                                errorListener.onErrorResponse(new VolleyError("Firebase Authentication Failed"));
                            }
                        })
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                String idToken = task.getResult().getToken();
                                postPacket(context, idToken, URI, packetBody, listener, errorListener);
                            } else {
                                Exception exception = task.getException();
                                if(exception != null && errorListener != null) {
                                    errorListener.onErrorResponse(new VolleyError(exception.getMessage()));
                                }
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

    public static String getDefaultApiAddress(SharedPreferences prefs, String serviceType, boolean isDebug) {
        String apiDomain = prefs.getString(PacketConst.API_PREFS_DOMAIN_KEY, PacketConst.API_DOMAIN);
        return String.format(PacketConst.API_ROUTE_SCHEMA, apiDomain,
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
