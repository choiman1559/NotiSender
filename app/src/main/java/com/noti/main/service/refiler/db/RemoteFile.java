package com.noti.main.service.refiler.db;

import com.noti.main.service.refiler.ReFileConst;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RemoteFile implements Comparable<RemoteFile>, Serializable {
    long size;
    long lastModified;
    boolean isFile;
    boolean isIndexSkipped;
    String path;
    RemoteFile parent;
    List<RemoteFile> list;

    public RemoteFile(JSONObject jsonObject) throws JSONException {
        path = "/storage/emulated";
        list = new ArrayList<>();

        for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
            String key = it.next();
            Object obj = jsonObject.get(key);
            switch (key) {
                case ReFileConst.DATA_TYPE_LAST_MODIFIED -> lastModified = (Long) obj;
                case ReFileConst.DATA_TYPE_INTERNAL_STORAGE -> list.add(new RemoteFile(this ,(JSONObject) obj, path + "/0"));
                default -> list.add(new RemoteFile(this, (JSONObject) obj, path + "/" + key));
            }
        }
    }

    protected RemoteFile(RemoteFile parent, JSONObject jsonObject, String basePath) throws JSONException {
        path = basePath;
        list = new ArrayList<>();
        this.parent = parent;

        if (jsonObject.getBoolean(ReFileConst.DATA_TYPE_IS_FILE)) {
            isFile = true;
            size = jsonObject.getLong(ReFileConst.DATA_TYPE_SIZE);
            lastModified = jsonObject.getLong(ReFileConst.DATA_TYPE_LAST_MODIFIED);
        } else {
            isFile = false;
            lastModified = jsonObject.getLong(ReFileConst.DATA_TYPE_LAST_MODIFIED);
            isIndexSkipped = jsonObject.getBoolean(ReFileConst.DATA_TYPE_IS_SKIPPED);

            ArrayList<String> metaKeys = new ArrayList<>();
            metaKeys.add(ReFileConst.DATA_TYPE_IS_FILE);
            metaKeys.add(ReFileConst.DATA_TYPE_LAST_MODIFIED);
            metaKeys.add(ReFileConst.DATA_TYPE_IS_SKIPPED);

            for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                String key = it.next();
                if(!metaKeys.contains(key)) {
                    Object obj = jsonObject.get(key);
                    list.add(new RemoteFile(this, (JSONObject) obj, basePath + "/" + key));
                }
            }
        }
    }

    public boolean isFile() {
        return isFile;
    }

    public boolean isIndexSkipped() {
        return isIndexSkipped;
    }

    public List<RemoteFile> getList() {
        return list;
    }

    public long getLastModified() {
        return lastModified;
    }

    public long getSize() {
        return size;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        String[] pathArrays = path.split("/");
        return pathArrays[pathArrays.length - 1];
    }

    public RemoteFile getParent() {
        return parent;
    }

    @Override
    public int compareTo(RemoteFile o) {
        return o.getName().compareTo(this.getName());
    }

    public RemoteFile getSerializeOptimized() {
        RemoteFile tmp = this;
        list = new ArrayList<>();
        parent = null;

        return tmp;
    }
}
