package com.psw.cta.service.dto;

import com.google.gson.JsonObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class BinanceRateLimit {

    String rateLimitType;
    String interval;
    Long limit;

    public BinanceRateLimit(JsonObject obj) {
        if (obj.has("rateLimitType") && obj.get("rateLimitType").isJsonPrimitive()) {
            rateLimitType = obj.get("rateLimitType").getAsString();
        }
        if (obj.has("interval") && obj.get("interval").isJsonPrimitive()) {
            interval = obj.get("interval").getAsString();
        }
        if (obj.has("limit") && obj.get("limit").isJsonPrimitive()) {
            limit = obj.get("limit").getAsLong();
        }
    }
}