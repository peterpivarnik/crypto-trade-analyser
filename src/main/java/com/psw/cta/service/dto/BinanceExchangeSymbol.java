package com.psw.cta.service.dto;

import com.google.gson.JsonObject;
import com.psw.cta.exception.CryptoTradeAnalyserException;

public class BinanceExchangeSymbol {

    private BinanceSymbol symbol;
    private String status;

    BinanceExchangeSymbol(JsonObject obj) throws CryptoTradeAnalyserException {
        symbol = BinanceSymbol.valueOf(obj.get("symbol").getAsString());
        status = obj.get("status").getAsString();
    }

    public BinanceSymbol getSymbol() {
        return symbol;
    }

    public String getStatus() {
        return status;
    }
}
