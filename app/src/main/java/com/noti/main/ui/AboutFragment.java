package com.noti.main.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.noti.main.R;
import com.noti.main.utils.DetectAppSource;

import java.util.Objects;

public class AboutFragment extends Fragment {
    Activity mContext;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) mContext = (Activity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout AppVersion = view.findViewById(R.id.AppVersion);
        LinearLayout GithubRepository = view.findViewById(R.id.GithubRepository);
        LinearLayout OpenSource = view.findViewById(R.id.OpenSource);
        LinearLayout PrivacyPolicy = view.findViewById(R.id.PrivacyPolicy);
        LinearLayout DataCollection = view.findViewById(R.id.DataCollection);
        LinearLayout StoreLink = view.findViewById(R.id.StoreLink);

        TextView VersionTextView = view.findViewById(R.id.VersionTextView);
        TextView DownloadFromTextView = view.findViewById(R.id.DownloadFromTextView);

        try {
            PackageManager pm = mContext.getPackageManager();
            String version = pm.getPackageInfo(mContext.getPackageName(), 0).versionName;
            int AppSource = DetectAppSource.detectSource(mContext);
            String Source = "App installed from ";

            switch (AppSource) {
                case 1:
                    Source += "Debug build";
                    break;

                case 2:
                    Source += "Github";
                    break;

                case 3:
                    Source += "Play Store";
                    break;

                default:
                    Source += "Unknown source";
                    break;
            }

            VersionTextView.setText(version);
            DownloadFromTextView.setText(Source);
        } catch (Exception e) {
            e.printStackTrace();
        }

        AppVersion.setOnClickListener(v -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:com.noti.main"))));
        GithubRepository.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/choiman1559/NotiSender"))));
        StoreLink.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.noti.main")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));

        OpenSource.setOnClickListener(v -> {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Open Source Licenses");
            dialog.setMessage(R.string.ossl);
            dialog.setIcon(R.drawable.ic_fluent_database_search_24_regular);
            dialog.setPositiveButton("OK", (dialog1, which) -> { });
            dialog.show();
        });

        DataCollection.setOnClickListener(v -> {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Data Collect Agreement");
            dialog.setMessage("Noti Sender reads the user's SMS information for synchronization between devices, even when the app is closed or not in use.");
            dialog.setIcon(R.drawable.ic_fluent_database_search_24_regular);
            dialog.setPositiveButton("OK", (dialog1, which) -> { });
            dialog.show();
        });

        PrivacyPolicy.setOnClickListener(v -> {
            RelativeLayout layout = (RelativeLayout) LayoutInflater.from(mContext).inflate(R.layout.dialog_privacy, null,false);
            WebView webView = layout.findViewById(R.id.webView);
            webView.loadUrl("file:///android_asset/privacy_policy.html");

            Button acceptButton = layout.findViewById(R.id.acceptButton);
            acceptButton.setText("Close");
            Button denyButton = layout.findViewById(R.id.denyButton);
            denyButton.setVisibility(View.INVISIBLE);

            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, R.style.Theme_App_Palette_Dialog));
            dialog.setTitle("Privacy Policy");
            dialog.setMessage(Html.fromHtml("You have already accepted the <a href=\"https://github.com/choiman1559/NotiSender/blob/master/PrivacyPolicy\">Privacy Policy</a>.<br> However, You can review this policy any time."));
            dialog.setView(layout);
            dialog.setIcon(R.drawable.ic_fluent_inprivate_account_24_regular);

            AlertDialog alertDialog = dialog.show();
            ((TextView) Objects.requireNonNull(alertDialog.findViewById(android.R.id.message))).setMovementMethod(LinkMovementMethod.getInstance());

            acceptButton.setOnClickListener((v2) -> alertDialog.dismiss());
        });
    }
}
