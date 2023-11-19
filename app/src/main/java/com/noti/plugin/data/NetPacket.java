package com.noti.plugin.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class NetPacket implements Serializable {
    String[] keyList;
    String [] valueList;
    int length;

    private NetPacket(int length) {
        keyList = new String[length];
        valueList = new String[length];
    }

    public Map<String, String> build() {
        Map<String, String> newMap = new HashMap<>();
        for(int i = 0; i < length; i++) {
            newMap.put(keyList[i], valueList[i]);
        }

        return newMap;
    }
}
