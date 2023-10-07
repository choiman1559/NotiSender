package com.noti.main.utils.network;

import android.annotation.SuppressLint;
import android.content.Context;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import java.util.ArrayList;

public class JsonRequest {
    @SuppressLint("StaticFieldLeak")
    private static JsonRequest instance;
    private final ArrayList<RequestQueue> requestQueue;
    private final Context ctx;

    private JsonRequest(Context context) {
        ctx = context;
        requestQueue = new ArrayList<>();
        requestQueue.add(getRequestQueue());
    }

    public static synchronized JsonRequest getInstance(Context context) {
        if (instance == null) {
            instance = new JsonRequest(context);
        }
        return instance;
    }

    public RequestQueue getRequestQueue() {
        return getRequestQueue(0);
    }

    public RequestQueue getRequestQueue(int index) {
        RequestQueue requestObj = null;
        if(requestQueue.size() > index) {
            requestObj = requestQueue.get(index);
        }

        if(requestObj == null) {
            requestObj = getActualRequestQueue();
        }

        if(requestQueue.size() > index) {
            requestQueue.set(index, requestObj);
        } else requestQueue.add(requestObj);

        return requestObj;
    }

    public RequestQueue getActualRequestQueue() {
        return Volley.newRequestQueue(ctx.getApplicationContext());
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }

    public <T> void addToRequestQueue(Request<T> req, int index) {
        getRequestQueue(index).add(req);
    }
}