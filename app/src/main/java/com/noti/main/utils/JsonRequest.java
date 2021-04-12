package com.noti.main.utils;

import android.annotation.SuppressLint;
import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class JsonRequest {
    @SuppressLint("StaticFieldLeak")
    private static JsonRequest instance;
    private RequestQueue requestQueue;
    private final Context ctx;

    private JsonRequest(Context context) {
        ctx = context;
        requestQueue = getRequestQueue();
    }

    public static synchronized JsonRequest getInstance(Context context) {
        if (instance == null) {
            instance = new JsonRequest(context);
        }
        return instance;
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(ctx.getApplicationContext());
        }
        return requestQueue;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }
}