package com.noti.main.service.refiler.db;

import com.noti.main.service.refiler.ReFileConst;

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

    @SuppressWarnings("unused")
    public long getLastModified() {
        return lastModified;
    }

    public boolean isFile() {
        return isFile;
    }

    public Map<String, Object> getList() {
        Map<String, Object> map = new HashMap<>();
        map.put(ReFileConst.DATA_TYPE_LAST_MODIFIED, lastModified);
        map.put(ReFileConst.DATA_TYPE_IS_FILE, isFile);
        map.put(ReFileConst.DATA_TYPE_SIZE, size);
        return map;
    }
}
