package com.psw.cta.service.dto;


import com.google.gson.JsonArray;
import com.psw.cta.exception.CryptoTradeAnalyserException;

import java.math.BigDecimal;

public class BinanceCandlestick {

    private Long openTime = null;
    private BigDecimal open = null;
    private BigDecimal high = null;
    private BigDecimal low = null;
    private BigDecimal close = null;
    private BigDecimal volume = null;
    private Long closeTime = null;
    private BigDecimal quoteAssetVolume = null;
    private Long numberOfTrades = null;
    private BigDecimal takerBuyBaseAssetVolume = null;
    private BigDecimal takerBuyQuoteAssetVolume = null;

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

    public Long getOpenTime() {
        return openTime;
    }

    public void setOpenTime(Long openTime) {
        this.openTime = openTime;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public Long getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(Long closeTime) {
        this.closeTime = closeTime;
    }

    public BigDecimal getQuoteAssetVolume() {
        return quoteAssetVolume;
    }

    public void setQuoteAssetVolume(BigDecimal quoteAssetVolume) {
        this.quoteAssetVolume = quoteAssetVolume;
    }

    public Long getNumberOfTrades() {
        return numberOfTrades;
    }

    public void setNumberOfTrades(Long numberOfTrades) {
        this.numberOfTrades = numberOfTrades;
    }

    public BigDecimal getTakerBuyBaseAssetVolume() {
        return takerBuyBaseAssetVolume;
    }

    public void setTakerBuyBaseAssetVolume(BigDecimal takerBuyBaseAssetVolume) {
        this.takerBuyBaseAssetVolume = takerBuyBaseAssetVolume;
    }

    public BigDecimal getTakerBuyQuoteAssetVolume() {
        return takerBuyQuoteAssetVolume;
    }

    public void setTakerBuyQuoteAssetVolume(BigDecimal takerBuyQuoteAssetVolume) {
        this.takerBuyQuoteAssetVolume = takerBuyQuoteAssetVolume;
    }
}
