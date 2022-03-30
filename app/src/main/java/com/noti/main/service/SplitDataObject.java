package com.noti.main.service;

import java.util.Map;
import java.util.Objects;

public class SplitDataObject {
    String[] data;
    String unique_id;
    long expireTimeMill;
    int length;

    public SplitDataObject(Map<String, String> map) {
        if("split_data".equals(map.get("type"))) {
            String[] indexInfo = Objects.requireNonNull(map.get("split_index")).split("/");
            int currentIndex = Integer.parseInt(indexInfo[0]);
            length = Integer.parseInt(indexInfo[1]);
            data = new String[length];

            //expireTimeMill = Long.parseLong(Objects.requireNonNull(map.get("expire_time")));
            unique_id = map.get("split_unique");
            data[currentIndex] = map.get("split_data");
        }
    }

    public SplitDataObject addData(Map<String, String> map) {
        data[Integer.parseInt(Objects.requireNonNull(map.get("split_index")).split("/")[0])] = map.get("split_data");
        return this;
    }

    public int getSize() {
        int i = 0;
        for(String obj : data) {
            if(obj != null && !obj.isEmpty()) i++;
        }
        return i;
    }

    public String getFullData() {
        StringBuilder string = new StringBuilder();
        for(String str : data) {
            string.append(str);
        }

        return string.toString();
    }
}
