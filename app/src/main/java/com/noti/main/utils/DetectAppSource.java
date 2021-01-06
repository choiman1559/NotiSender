package com.noti.main.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Log;

import com.noti.main.BuildConfig;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DetectAppSource {
    public static int detectSource(Context context) {
        if(BuildConfig.DEBUG) return 1; //When build is debug, you can pass checking SHA1 hash logic
        switch (getSHA1Hash(context)) {
            case /* Debug build key */ "36:47:5f:49:ce:2d:bc:cb:b8:59:30:e3:86:17:85:6c:78:cf:86:53":
                return 1; //You can change this value freely while you're testing

            case /* Release key for Github */ "d5:5c:2e:6a:58:4c:3d:4f:4a:3a:08:cd:1c:7e:6a:eb:ee:ea:46:10":
                return 2;

            case /* Release key for Play Store */ "3b:84:65:62:d4:f5:21:87:95:e3:f8:7a:0a:87:52:8e:cd:26:1a:f0":
                return 3;

            default:
                return -1; //When error occurred
        }
    }

    @SuppressLint("PackageManagerGetSignatures")
    public static String getSHA1Hash(Context context) {
        try {
            final PackageInfo info = context.getPackageManager()
                    .getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                final MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());

                final byte[] digest = md.digest();
                final StringBuilder toRet = new StringBuilder();
                for (int i = 0; i < digest.length; i++) {
                    if (i != 0) toRet.append(":");
                    int b = digest[i] & 0xff;
                    String hex = Integer.toHexString(b);
                    if (hex.length() == 1) toRet.append("0");
                    toRet.append(hex);
                }
                return toRet.toString();
            }
        } catch (PackageManager.NameNotFoundException e1) {
            Log.e("name not found", e1.toString());
        } catch (NoSuchAlgorithmException e) {
            Log.e("no such an algorithm", e.toString());
        } catch (Exception e) {
            Log.e("exception", e.toString());
        }
        return "";
    }
}
