package com.noti.plugin.data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class NetPacket implements Serializable {
    @Serial
    private static final long serialVersionUID = 1234L;
    String[] keyList;
    String [] valueList;
    int length;

    private NetPacket(int length) {
        this.length = length;
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

    public static NetPacket parseFrom(Map<String, String> map) {
        NetPacket netPacket = new NetPacket(map.size());

        int i = 0;
        for(Object key : map.keySet()) {
            netPacket.keyList[i] = key.toString();
            netPacket.valueList[i] = map.get(key.toString());
            i += 1;
        }

        return netPacket;
    }
}
