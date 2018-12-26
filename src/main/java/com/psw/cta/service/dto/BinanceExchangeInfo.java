package com.psw.cta.service.dto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.psw.cta.exception.CryptoTradeAnalyserException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;

@Data
@Slf4j
public class BinanceExchangeInfo {
    String timezone = null;
    Long serverTime = 0L;
    List<BinanceRateLimit> rateLimits = new LinkedList<>();
    List<JsonObject> exchangeFilters = new LinkedList<>(); // missing proper documentation on that yet
    List<BinanceExchangeSymbol> symbols = new LinkedList<>();

    public BinanceExchangeInfo(JsonObject obj) throws CryptoTradeAnalyserException {
        if (obj.has("timezone")) {
            timezone = obj.get("timezone").getAsString();
        }
        if (obj.has("serverTime")) {
            serverTime = obj.get("serverTime").getAsLong();
        }
        if (obj.has("rateLimits") && obj.get("rateLimits").isJsonArray()) {
            JsonArray arrRateLimits = obj.get("rateLimits").getAsJsonArray();
            rateLimits.clear();
            for (JsonElement entry: arrRateLimits) {
                BinanceRateLimit limit = new BinanceRateLimit(entry.getAsJsonObject());
                rateLimits.add(limit);
            }
        }
        if (obj.has("exchangeFilters") && obj.get("exchangeFilters").isJsonArray()) {
            JsonArray arrExchangeFilters = obj.get("exchangeFilters").getAsJsonArray();
            exchangeFilters.clear();
            for (JsonElement entry: arrExchangeFilters) {
                exchangeFilters.add(entry.getAsJsonObject());
            }
        }
        if (obj.has("symbols") && obj.get("symbols").isJsonArray()) {
            JsonArray arrSymbols = obj.get("symbols").getAsJsonArray();
            symbols.clear();
            for (JsonElement entry: arrSymbols) {
                JsonObject jsonObject = entry.getAsJsonObject();
                if (!jsonObject.has("symbol")) continue;
                String sym = jsonObject.get("symbol").getAsString();
                if (sym.equals("123456")) continue; // some special symbol that doesn't fit

                BinanceExchangeSymbol symbol = new BinanceExchangeSymbol(jsonObject);
                symbols.add(symbol);
            }
        }
    }
}
