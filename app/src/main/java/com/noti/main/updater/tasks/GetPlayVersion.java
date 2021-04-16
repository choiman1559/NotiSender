package com.noti.main.updater.tasks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.noti.main.R;
import com.noti.main.ui.receive.ExitActivity;
import com.noti.main.updater.UpdaterActivity;
import com.noti.main.utils.AsyncTask;

import org.jsoup.Jsoup;

@SuppressLint("StaticFieldLeak")
public class GetPlayVersion extends AsyncTask<Void, String, String> {
    Activity context;
    String packageName;
    String currentVersion = null;

    public GetPlayVersion(Activity context) {
       this.context = context;
    }

    @Override
    protected String doInBackground(Void... voids) {

        String newVersion;
        try {
            packageName = context.getPackageName();
            currentVersion = context.getPackageManager().getPackageInfo(packageName, 0).versionName;
            newVersion = Jsoup.connect("https://play.google.com/store/apps/details?id=" + packageName + "&hl=en")
                    .timeout(30000)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .get()
                    .select("div.hAyfc:nth-child(4) > span:nth-child(2) > div:nth-child(1) > span:nth-child(1)")
                    .first()
                    .ownText();
            return newVersion;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPostExecute(String onlineVersion) {
        super.onPostExecute(onlineVersion);
        if (onlineVersion != null && !onlineVersion.isEmpty()) {
            Version[] Versions = {new Version(currentVersion.split(" ")[0]),new Version(onlineVersion.split(" ")[0])};
            if(Versions[1].compareTo(Versions[0]) > 0) {
                new AlertDialog.Builder(context)
                        .setCancelable(false)
                        .setTitle(context.getString(R.string.dialog_update_title))
                        .setMessage("new version " + Versions[1].get() + " exists!\nAre you want to update?")
                        .setNegativeButton("Exit", (d, w) -> ExitActivity.exitApplication(context))
                        .setNeutralButton("No Thanks", (d, w) -> {
                            UpdaterActivity.startMainActivity(context);
                            context.finish();
                        })
                        .setPositiveButton("Update", (d, w) -> {
                            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
                            ExitActivity.exitApplication(context);
                        })
                        .show();
            } else {
                UpdaterActivity.startMainActivity(context);
                context.finish();
            }
        }
        Log.d("update", "Current version " + currentVersion + " playstore version " + onlineVersion);
    }
}
