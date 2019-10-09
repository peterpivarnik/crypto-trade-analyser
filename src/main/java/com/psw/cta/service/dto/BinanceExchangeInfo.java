package com.psw.cta.service.dto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.psw.cta.exception.CryptoTradeAnalyserException;

import java.util.LinkedList;
import java.util.List;

public class BinanceExchangeInfo {

    private List<BinanceExchangeSymbol> symbols = new LinkedList<>();

    public BinanceExchangeInfo(JsonObject obj) throws CryptoTradeAnalyserException {
        if (obj.has("symbols") && obj.get("symbols").isJsonArray()) {
            JsonArray arrSymbols = obj.get("symbols").getAsJsonArray();
            symbols.clear();
            for (JsonElement entry : arrSymbols) {
                JsonObject jsonObject = entry.getAsJsonObject();
                if (!jsonObject.has("symbol")) continue;
                String sym = jsonObject.get("symbol").getAsString();
                if (sym.equals("123456")) continue; // some special symbol that doesn't fit

                BinanceExchangeSymbol symbol = new BinanceExchangeSymbol(jsonObject);
                symbols.add(symbol);
            }
        }
    }

    public List<BinanceExchangeSymbol> getSymbols() {
        return symbols;
    }
}
