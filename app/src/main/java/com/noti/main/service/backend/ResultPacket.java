package com.noti.main.service.backend;

import java.io.IOException;
import java.util.Objects;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;
import me.pushy.sdk.lib.jackson.databind.ObjectMapper;

@SuppressWarnings("unused")
public class ResultPacket {
    @JsonProperty
    private String status;
    @JsonProperty
    private String errorCause;
    @JsonProperty
    private String extraData;

    public static ResultPacket parseFrom(String serializedMessage) throws IOException {
        return new ObjectMapper().readValue(serializedMessage, ResultPacket.class);
    }

    public boolean isResultOk() {
        return Objects.equals(this.status, PacketConst.STATUS_OK);
    }

    public String getErrorCause() {
        return this.errorCause;
    }

    public String getExtraData() {
        return this.extraData;
    }
}