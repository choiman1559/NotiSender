package com.noti.main.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.noti.main.R;
import com.noti.main.ui.receive.ExitActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

@SuppressLint("StaticFieldLeak")
public class GetVersionTask extends AsyncTask<Void, Void, JSONArray> {
    private final Activity context;

    public GetVersionTask(Activity context) {
        this.context = context;
    }

    @Override
    protected JSONArray doInBackground(Void... params) {
        String str = "https://api.github.com/repos/choiman1559/NotiSender/releases";
        URLConnection urlConn;
        BufferedReader bufferedReader = null;
        try {
            URL url = new URL(str);
            urlConn = url.openConnection();
            bufferedReader = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));

            StringBuilder stringBuffer = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuffer.append(line);
            }

            return new JSONArray(stringBuffer.toString());
        } catch (Exception ex) {
            Log.e("App", "yourDataTask", ex);
            return null;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onPostExecute(JSONArray response) {
        if (response != null) {
            try {
                JSONObject obj = response.getJSONObject(0);
                String latestVersion = obj.getString("tag_name");
                PackageManager pm = context.getPackageManager();
                String localVersion = pm.getPackageInfo(context.getPackageName(), 0).versionName.split(" ")[0];
                Version v1 = new Version(latestVersion);
                Version v2 = new Version(localVersion);

                if (v1.compareTo(v2) > 0) {
                    new AlertDialog.Builder(context)
                            .setCancelable(true)
                            .setTitle(context.getString(R.string.dialog_update_title))
                            .setMessage("Version : " + latestVersion + "\n\n" + obj.getString("body"))
                            .setNegativeButton("Exit", (d, w) -> ExitActivity.exitApplication(context))
                            .setNeutralButton("No Thanks", (d, w) -> { })
                            .setPositiveButton("Update", (d, w) -> context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender/releases/latest"))))
                            .show();
                }
            } catch (JSONException | PackageManager.NameNotFoundException ex) {
                ex.printStackTrace();
            }
        }
    }
}

class Version implements Comparable<Version> {

    private final String version;

    public final String get() {
        return this.version;
    }

    public Version(String version) {
        if (version == null)
            throw new IllegalArgumentException("Version can not be null");
        if (!version.matches("[0-9]+(\\.[0-9]+)*"))
            throw new IllegalArgumentException("Invalid version format");
        this.version = version;
    }

    @Override
    public int compareTo(Version that) {
        if (that == null)
            return 1;
        String[] thisParts = this.get().split("\\.");
        String[] thatParts = that.get().split("\\.");
        int length = Math.max(thisParts.length, thatParts.length);
        for (int i = 0; i < length; i++) {
            int thisPart = i < thisParts.length ?
                    Integer.parseInt(thisParts[i]) : 0;
            int thatPart = i < thatParts.length ?
                    Integer.parseInt(thatParts[i]) : 0;
            if (thisPart < thatPart)
                return -1;
            if (thisPart > thatPart)
                return 1;
        }
        return 0;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that)
            return true;
        if (that == null)
            return false;
        if (this.getClass() != that.getClass())
            return false;
        return this.compareTo((Version) that) == 0;
    }
}