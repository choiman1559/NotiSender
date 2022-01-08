package com.noti.main.updater.tasks;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import com.noti.main.R;
import com.noti.main.ui.receive.ExitActivity;
import com.noti.main.updater.UpdaterActivity;
import com.noti.main.utils.AsyncTask;

@SuppressLint("StaticFieldLeak")
public class GetPlayVersion extends AsyncTask<Void, String, String> {
    Activity context;
    String packageName;
    String currentVersion = null;

    public GetPlayVersion(Activity context) {
       this.context = context;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        try {
            currentVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName.split(" ")[0];
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected String doInBackground(Void... voids) {
        SharedPreferences prefs = context.getSharedPreferences("com.noti.main_preferences", MODE_PRIVATE);
        String latestVersion = prefs.getString("Latest_Version_Play", "");
        if(latestVersion.equals("")) UpdaterActivity.startMainActivity(context);
        return latestVersion;
    }

    @Override
    protected void onPostExecute(String onlineVersion) {
        super.onPostExecute(onlineVersion);
        if (onlineVersion != null && !onlineVersion.isEmpty()) {
            try {
                Version[] Versions = {new Version(currentVersion.split(" ")[0]), new Version(onlineVersion.split(" ")[0])};
                if (Versions[1].compareTo(Versions[0]) > 0) {
                    new MaterialAlertDialogBuilder(new ContextThemeWrapper(context, R.style.MaterialAlertDialog_Material3))
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
            } catch (Exception e) {
                UpdaterActivity.isErrorOccurred = true;
                Snackbar.make(context, context.findViewById(R.id.layout), "There is a error in checking for updates.",Snackbar.LENGTH_INDEFINITE)
                        .setAction("Skip", v -> UpdaterActivity.startMainActivity(context)).show();
            }
        }
        Log.d("update", "Current version " + currentVersion + " playstore version " + onlineVersion);
    }
}
