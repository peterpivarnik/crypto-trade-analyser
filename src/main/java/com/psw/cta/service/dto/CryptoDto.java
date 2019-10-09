package com.psw.cta.service.dto;

import com.google.gson.internal.LinkedTreeMap;

import java.math.BigDecimal;
import java.util.List;

public class CryptoDto {

    public CryptoDto(BinanceExchangeSymbol binanceExchangeSymbol) {
        this.binanceExchangeSymbol = binanceExchangeSymbol;
    }

    private List<BinanceCandlestick> fifteenMinutesCandleStickData;
    private List<BinanceCandlestick> threeMonthsCandleStickData;
    private LinkedTreeMap<String, Object> ticker24hr;
    private LinkedTreeMap<String, Object> depth20;
    private BinanceExchangeSymbol binanceExchangeSymbol;

    private BigDecimal currentPrice;
    private BigDecimal volume;
    private BigDecimal sumDiffsPerc;
    private BigDecimal sumDiffsPerc10h;
    private BigDecimal priceToSell;
    private BigDecimal priceToSellPercentage;
    private BigDecimal weight;

    public List<BinanceCandlestick> getFifteenMinutesCandleStickData() {
        return fifteenMinutesCandleStickData;
    }

    public void setFifteenMinutesCandleStickData(List<BinanceCandlestick> fifteenMinutesCandleStickData) {
        this.fifteenMinutesCandleStickData = fifteenMinutesCandleStickData;
    }

    public List<BinanceCandlestick> getThreeMonthsCandleStickData() {
        return threeMonthsCandleStickData;
    }

    public void setThreeMonthsCandleStickData(List<BinanceCandlestick> threeMonthsCandleStickData) {
        this.threeMonthsCandleStickData = threeMonthsCandleStickData;
    }

    public LinkedTreeMap<String, Object> getTicker24hr() {
        return ticker24hr;
    }

    public void setTicker24hr(LinkedTreeMap<String, Object> ticker24hr) {
        this.ticker24hr = ticker24hr;
    }

    public LinkedTreeMap<String, Object> getDepth20() {
        return depth20;
    }

    public void setDepth20(LinkedTreeMap<String, Object> depth20) {
        this.depth20 = depth20;
    }

    public BinanceExchangeSymbol getBinanceExchangeSymbol() {
        return binanceExchangeSymbol;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public BigDecimal getSumDiffsPerc() {
        return sumDiffsPerc;
    }

    public void setSumDiffsPerc(BigDecimal sumDiffsPerc) {
        this.sumDiffsPerc = sumDiffsPerc;
    }

    public BigDecimal getSumDiffsPerc10h() {
        return sumDiffsPerc10h;
    }

    public void setSumDiffsPerc10h(BigDecimal sumDiffsPerc10h) {
        this.sumDiffsPerc10h = sumDiffsPerc10h;
    }

    public BigDecimal getPriceToSell() {
        return priceToSell;
    }

    public void setPriceToSell(BigDecimal priceToSell) {
        this.priceToSell = priceToSell;
    }

    public BigDecimal getPriceToSellPercentage() {
        return priceToSellPercentage;
    }

    public void setPriceToSellPercentage(BigDecimal priceToSellPercentage) {
        this.priceToSellPercentage = priceToSellPercentage;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    @Override
    public String toString() {
        return "CryptoDto{" +
               "fifteenMinutesCandleStickData=" + fifteenMinutesCandleStickData +
               ", threeMonthsCandleStickData=" + threeMonthsCandleStickData +
               ", ticker24hr=" + ticker24hr +
               ", depth20=" + depth20 +
               ", binanceExchangeSymbol=" + binanceExchangeSymbol +
               ", currentPrice=" + currentPrice +
               ", volume=" + volume +
               ", sumDiffsPerc=" + sumDiffsPerc +
               ", sumDiffsPerc10h=" + sumDiffsPerc10h +
               ", priceToSell=" + priceToSell +
               ", priceToSellPercentage=" + priceToSellPercentage +
               ", weight=" + weight +
               '}';
    }
}
