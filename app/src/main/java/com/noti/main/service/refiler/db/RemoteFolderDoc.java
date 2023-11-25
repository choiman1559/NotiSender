package com.noti.main.service.refiler.db;

import android.content.SharedPreferences;
import android.util.Log;

import com.noti.main.BuildConfig;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class RemoteFolderDoc implements Serializable {
    Map<String, Object> lists;

    public RemoteFolderDoc(SharedPreferences prefs, File baseFolder) {
        lists = new HashMap<>();
        if(baseFolder.canRead()) {
            lists.put("$isFile", false);
            lists.put("$lastModified", baseFolder.lastModified());

            if(BuildConfig.DEBUG) Log.d("Added File", "Added:" + baseFolder);
            File[] fileList = baseFolder.listFiles();

            if (fileList != null) {
                boolean isIndexSkipped = fileList.length > prefs.getInt("indexMaximumSize", 150);
                if(isIndexSkipped) {
                    lists.put("$isSkipped", true);
                } else {
                    lists.put("$isSkipped", false);
                    for (File files : fileList) {
                        if (files.canRead() && (prefs.getBoolean("indexHiddenFiles", false) || !files.getName().startsWith("."))) {
                            if (files.isDirectory()) {
                                lists.put(files.getName(), new RemoteFolderDoc(prefs, files).getLists());
                            } else {
                                lists.put(files.getName(), new RemoteFileDoc(files).getList());
                            }
                        }
                    }
                }
            }
        }
    }

    public Map<String, Object> getLists() {
        return lists;
    }
}
