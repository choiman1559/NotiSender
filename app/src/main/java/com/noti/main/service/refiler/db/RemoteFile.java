package com.noti.main.service.refiler.db;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RemoteFile implements Comparable<RemoteFile>{
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
                case "$lastUpdate" -> lastModified = (Long) obj;
                case "Internal Storage" -> list.add(new RemoteFile(this ,(JSONObject) obj, "/storage/emulated/0"));
                default -> list.add(new RemoteFile(this, (JSONObject) obj, "/storage/emulated/" + key));
            }
        }
    }

    protected RemoteFile(RemoteFile parent, JSONObject jsonObject, String basePath) throws JSONException {
        path = basePath;
        list = new ArrayList<>();
        this.parent = parent;

        if (jsonObject.getBoolean("$isFile")) {
            isFile = true;
            size = jsonObject.getLong("$size");
            lastModified = jsonObject.getLong("$lastModified");
        } else {
            isFile = false;
            lastModified = jsonObject.getLong("$lastModified");
            isIndexSkipped = jsonObject.getBoolean("$isSkipped");

            ArrayList<String> metaKeys = new ArrayList<>();
            metaKeys.add("$isFile");
            metaKeys.add("$lastModified");
            metaKeys.add("$isSkipped");

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
}
