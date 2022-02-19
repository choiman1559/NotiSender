package com.noti.main.service.pair;

public class PairDeviceInfo {
    private final String Device_name;
    private final String Device_id;
    @PairDeviceStatus.Status
    private final int Device_status;

    public PairDeviceInfo(String Device_name, String Device_id, @PairDeviceStatus.Status int Device_status) {
        this.Device_id = Device_id;
        this.Device_name = Device_name;
        this.Device_status = Device_status;
    }

    public String getDevice_id() {
        return Device_id;
    }

    public String getDevice_name() {
        return Device_name;
    }

    public int getDevice_status() {
        return Device_status;
    }
}
