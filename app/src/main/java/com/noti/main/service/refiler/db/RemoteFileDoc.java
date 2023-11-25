package com.noti.main.service.refiler.db;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class RemoteFileDoc implements Serializable {
    long size;
    long lastModified;
    boolean isFile;

    public RemoteFileDoc(File baseFile) {
        this.lastModified = baseFile.lastModified();
        this.size = baseFile.length();
        this.isFile = true;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isFile() {
        return isFile;
    }

    public Map<String, Object> getList() {
        Map<String, Object> map = new HashMap<>();
        map.put("$lastModified", lastModified);
        map.put("$isFile", isFile);
        map.put("$size", size);
        return map;
    }
}
