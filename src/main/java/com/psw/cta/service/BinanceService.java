package com.psw.cta.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.psw.cta.exception.CryptoTradeAnalyserException;
import com.psw.cta.service.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Slf4j
@Service
public class BinanceService {

    private String baseUrl = "https://www.binance.com/api/";

    JsonArray ticker24hr() throws CryptoTradeAnalyserException {
        return (new BinanceRequest(baseUrl + "v1/ticker/24hr")).read().asJsonArray();
    }

    BinanceExchangeInfo exchangeInfo() throws CryptoTradeAnalyserException {
        JsonObject jsonObject = (new BinanceRequest(baseUrl + "v1/exchangeInfo")).read().asJsonObject();
        return new BinanceExchangeInfo(jsonObject);
    }

    JsonObject depth(BinanceSymbol symbol, int limit) throws CryptoTradeAnalyserException {
        BinanceRequest binanceRequest =
                new BinanceRequest(baseUrl + "v1/depth?symbol=" + symbol.get() + "&limit=" + limit);
        return binanceRequest.read().asJsonObject();
    }

    List<BinanceCandlestick> klines(BinanceSymbol symbol, BinanceInterval interval, int limit)
            throws CryptoTradeAnalyserException {

        String requestUrl = baseUrl +
                            "v1/klines?symbol=" +
                            symbol.get() +
                            "&interval=" +
                            interval.getValue() +
                            "&limit=" +
                            limit;
        JsonArray jsonElements = (new BinanceRequest(requestUrl)).read().asJsonArray();
        List<BinanceCandlestick> list = new LinkedList<>();
        for (JsonElement e : jsonElements) list.add(new BinanceCandlestick(e.getAsJsonArray()));
        return list;
    }
}


