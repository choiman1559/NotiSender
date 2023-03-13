package com.noti.main.ui.prefs.custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

public class RegexInterpreter {
    public interface RegexCallback {
        void onPostExecute(Object result);
    }

    public static class DataType {
        public String TITLE;
        public String CONTENT;
        public String PACKAGE_NAME;
        public String APP_NAME;
        public String DEVICE_NAME;
        public String DATE;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void evalRegex(Context mContext, String express, DataType data, RegexCallback callback) {
        WebView webView = new WebView(mContext);
        webView.getSettings().setJavaScriptEnabled(true);

        String newString = express
                .replace("$TITLE", data.TITLE)
                .replace("$CONTENT", data.CONTENT)
                .replace("$PACKAGE_NAME", data.PACKAGE_NAME)
                .replace("$APP_NAME", data.APP_NAME)
                .replace("$DEVICE_NAME", data.DEVICE_NAME)
                .replace("$DATE", data.DATE);

        webView.evaluateJavascript("(function(){return (" + newString + ");})();function matchExpr(e,n){return new RegExp(n).test(e)}", s -> callback.onPostExecute(s.equals("true") || s.equals("1")));
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void evalRegexWithArray(Context mContext, String[] expressArray, DataType[] dataArray, RegexCallback callback) {
        new Handler(Looper.getMainLooper()).post(() -> {
            WebView webView = new WebView(mContext);
            webView.getSettings().setJavaScriptEnabled(true);
            StringBuilder parsedExpress = new StringBuilder();

            for(int i = 0;i < expressArray.length;i++) {
                String express = expressArray[i];
                DataType data = dataArray[i];

                String newString = express
                        .replace("$TITLE", data.TITLE)
                        .replace("$CONTENT", data.CONTENT)
                        .replace("$PACKAGE_NAME", data.PACKAGE_NAME)
                        .replace("$APP_NAME", data.APP_NAME)
                        .replace("$DEVICE_NAME", data.DEVICE_NAME)
                        .replace("$DATE", data.DATE);
                parsedExpress.append("case ").append(newString).append(":return ").append(i).append(";");
            }

            webView.evaluateJavascript("(function(){switch(!0){" + parsedExpress + "default:return-1}})();function matchExpr(t,e){return new RegExp(e).test(t)}", callback::onPostExecute);
        });
    }
}
