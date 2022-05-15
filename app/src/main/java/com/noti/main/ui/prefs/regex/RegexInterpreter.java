package com.noti.main.ui.prefs.regex;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.webkit.WebView;

public class RegexInterpreter {
    public interface RegexCallback {
        void onPostExecute(boolean result);
    }

    public static class DataType {
        String TITLE;
        String CONTENT;
        String PACKAGE_NAME;
        String APP_NAME;
        String DEVICE_NAME;
        String DATE;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void evalRegex(Activity mContext, String express, DataType data, RegexCallback callback) {
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
}
