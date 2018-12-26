package com.psw.cta.service.dto;


import com.google.gson.JsonArray;
import com.psw.cta.exception.CryptoTradeAnalyserException;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BinanceCandlestick {

    public Long openTime = null;
    public BigDecimal open = null;
    public BigDecimal high = null;
    public BigDecimal low = null;
    public BigDecimal close = null;
    public BigDecimal volume = null;
    public Long closeTime = null;
    public BigDecimal quoteAssetVolume = null;
    public Long numberOfTrades = null;
    public BigDecimal takerBuyBaseAssetVolume = null;
    public BigDecimal takerBuyQuoteAssetVolume = null;

    public BinanceCandlestick(JsonArray jsonArray) throws CryptoTradeAnalyserException {
        if (jsonArray.size() < 11) {
            throw new CryptoTradeAnalyserException("Error reading candlestick, 11 parameters expected, "
                                                   + jsonArray.size() + " found");
        }
        setOpenTime(jsonArray.get(0).getAsLong());
        setOpen(jsonArray.get(1).getAsBigDecimal());
        setHigh(jsonArray.get(2).getAsBigDecimal());
        setLow(jsonArray.get(3).getAsBigDecimal());
        setClose(jsonArray.get(4).getAsBigDecimal());
        setVolume(jsonArray.get(5).getAsBigDecimal());
        setCloseTime(jsonArray.get(6).getAsLong());
        setQuoteAssetVolume(jsonArray.get(7).getAsBigDecimal());
        setNumberOfTrades(jsonArray.get(8).getAsLong());
        setTakerBuyBaseAssetVolume(jsonArray.get(9).getAsBigDecimal());
        setTakerBuyQuoteAssetVolume(jsonArray.get(10).getAsBigDecimal());
    }
}
