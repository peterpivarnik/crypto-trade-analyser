package com.psw.cta.service.dto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.psw.cta.exception.CryptoTradeAnalyserException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Data
public class BinanceExchangeSymbol {

    BinanceSymbol symbol;
    String status;
    String baseAsset;
    Long baseAssetPrecision;
    String quoteAsset;
    Long quotePrecision;
    List<BinanceOrderType> orderTypes = new LinkedList<>();
    boolean icebergAllowed;
    HashMap<String, JsonObject> filters = new HashMap<>();

    public BinanceExchangeSymbol(JsonObject obj) throws CryptoTradeAnalyserException {
        // log.debug("Reading Symbol {}, {}", obj.get("symbol").getAsString(), obj.toString());

        symbol = BinanceSymbol.valueOf(obj.get("symbol").getAsString());
        status = obj.get("status").getAsString();

        baseAsset = obj.get("baseAsset").getAsString();
        baseAssetPrecision = obj.get("baseAssetPrecision").getAsLong();
        quoteAsset = obj.get("quoteAsset").getAsString();
        quotePrecision = obj.get("quotePrecision").getAsLong();
        icebergAllowed = obj.get("icebergAllowed").getAsBoolean();

        if (obj.has("orderTypes") && obj.get("orderTypes").isJsonArray()) {
            JsonArray arrOrderTypes = obj.get("orderTypes").getAsJsonArray();
            orderTypes.clear();
            for (JsonElement entry : arrOrderTypes) {
                orderTypes.add(BinanceOrderType.valueOf(entry.getAsString()));
            }
        }

        if (obj.has("filters") && obj.get("filters").isJsonArray()) {
            JsonArray arrFilters = obj.get("filters").getAsJsonArray();
            filters.clear();
            for (JsonElement entry : arrFilters) {
                JsonObject item = entry.getAsJsonObject();
                String key = item.get("filterType").getAsString();
                filters.put(key, item);
            }
        }
    }

    public JsonObject getPriceFilter() {
        return filters.get("PRICE_FILTER");
    }

    public JsonObject getLotSize() {
        return filters.get("LOT_SIZE");
    }

    public JsonObject getMinNotional() {
        return filters.get("MIN_NOTIONAL");
    }

    public BigDecimal getMinNotionalValue() {
        if (filters.containsKey("MIN_NOTIONAL")) {
            JsonObject obj = this.getMinNotional();
            if (obj.has("minNotional")) {
                return obj.get("minNotional").getAsBigDecimal();
            }
        }
        return BigDecimal.ZERO;
    }

}
