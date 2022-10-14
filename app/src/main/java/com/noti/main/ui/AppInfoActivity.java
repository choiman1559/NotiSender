package com.noti.main.ui;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.noti.main.R;
import com.noti.main.utils.DetectAppSource;

import java.util.Objects;

public class AppInfoActivity extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(Build.VERSION.SDK_INT > 25 ? R.layout.activity_appinfo : R.layout.activity_appinfo_v25);

        if(Build.VERSION.SDK_INT <= 25) {
            TextView OSS = findViewById(R.id.oss);
            OSS.setText(R.string.ossl);
        }

        TextView version = findViewById(R.id.version);
        try {
            PackageManager pm =  getPackageManager();
            String LV = pm.getPackageInfo(getPackageName(), 0).versionName;
            int AppSource = DetectAppSource.detectSource(this);
            String Source;

            switch (AppSource) {
                case 1:
                    Source = "Debug build";
                    break;

                case 2:
                    Source = "Github";
                    break;

                case 3:
                    Source = "Play Store";
                    break;

                default:
                    Source = "Other";
                    break;
            }

            String Value = "Version : " + LV + "\n";
            Value += "Downloaded from : " + Source + "\n\n";
            Value += "this app is free-software under the GNU LGPL 3.0 license.";
            version.setText(Value);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Button GoToGit = findViewById(R.id.gotogit);
        GoToGit.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender"))));

        Button PrivacyPolicy = findViewById(R.id.privacy);
        PrivacyPolicy.setOnClickListener(v -> {
            RelativeLayout layout = (RelativeLayout) LayoutInflater.from(this).inflate(R.layout.dialog_privacy, null,false);
            WebView webView = layout.findViewById(R.id.webView);
            webView.loadUrl("file:///android_asset/privacy_policy.html");

            Button acceptButton = layout.findViewById(R.id.acceptButton);
            acceptButton.setText("Close");
            Button denyButton = layout.findViewById(R.id.denyButton);
            denyButton.setVisibility(View.INVISIBLE);

            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.MaterialAlertDialog_Material3));
            dialog.setTitle("Privacy Policy");
            dialog.setMessage(Html.fromHtml("You have already accepted the <a href=\"https://github.com/choiman1559/NotiSender/blob/master/PrivacyPolicy\">Privacy Policy</a>.<br> However, You can review this policy any time."));
            dialog.setView(layout);
            dialog.setIcon(R.drawable.ic_fluent_inprivate_account_24_regular);

            AlertDialog alertDialog = dialog.show();
            ((TextView) Objects.requireNonNull(alertDialog.findViewById(android.R.id.message))).setMovementMethod(LinkMovementMethod.getInstance());

            acceptButton.setOnClickListener((view) -> alertDialog.dismiss());
        });

        Button DataCollection = findViewById(R.id.data_collection);
        DataCollection.setOnClickListener(v -> {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(this, R.style.MaterialAlertDialog_Material3));
            dialog.setTitle("Data Collect Agreement");
            dialog.setMessage("Noti Sender reads the user's SMS information for synchronization between devices, even when the app is closed or not in use.");
            dialog.setIcon(R.drawable.ic_fluent_database_search_24_regular);
            dialog.setPositiveButton("OK", (dialog1, which) -> { });
            dialog.show();
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener((v) -> this.finish());
    }
}
