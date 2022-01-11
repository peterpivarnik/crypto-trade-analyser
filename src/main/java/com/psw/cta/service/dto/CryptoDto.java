package com.psw.cta.service.dto;

import static java.math.RoundingMode.UP;

import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.binance.api.client.domain.market.TickerStatistics;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public class CryptoDto {

    public CryptoDto(SymbolInfo symbolInfo) {
        this.symbolInfo = symbolInfo;
    }

    private List<Candlestick> fifteenMinutesCandleStickData;
    private List<Candlestick> threeMonthsCandleStickData;
    private TickerStatistics ticker24hr;
    private OrderBook depth20;
    private final SymbolInfo symbolInfo;

    private BigDecimal currentPrice;
    private BigDecimal volume;
    private BigDecimal sumDiffsPerc;
    private BigDecimal sumDiffsPerc10h;
    private BigDecimal priceToSell;
    private BigDecimal priceToSellPercentage;
    private BigDecimal weight;
    private BigDecimal lastThreeMaxAverage;
    private BigDecimal previousThreeMaxAverage;

    public List<Candlestick> getFifteenMinutesCandleStickData() {
        return fifteenMinutesCandleStickData;
    }

    public void setFifteenMinutesCandleStickData(List<Candlestick> fifteenMinutesCandleStickData) {
        this.fifteenMinutesCandleStickData = fifteenMinutesCandleStickData;
    }

    public List<Candlestick> getThreeMonthsCandleStickData() {
        return threeMonthsCandleStickData;
    }

    public void setThreeMonthsCandleStickData(List<Candlestick> threeMonthsCandleStickData) {
        this.threeMonthsCandleStickData = threeMonthsCandleStickData;
    }

    public TickerStatistics getTicker24hr() {
        return ticker24hr;
    }

    public void setTicker24hr(TickerStatistics ticker24hr) {
        this.ticker24hr = ticker24hr;
    }

    public OrderBook getDepth20() {
        return depth20;
    }

    public void setDepth20(OrderBook depth20) {
        this.depth20 = depth20;
    }

    public SymbolInfo getSymbolInfo() {
        return symbolInfo;
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

    public BigDecimal getLastThreeMaxAverage() {
        return lastThreeMaxAverage;
    }

    public void setLastThreeMaxAverage(BigDecimal lastThreeMaxAverage) {
        this.lastThreeMaxAverage = lastThreeMaxAverage;
    }

    public BigDecimal getPreviousThreeMaxAverage() {
        return previousThreeMaxAverage;
    }

    public void setPreviousThreeMaxAverage(BigDecimal previousThreeMaxAverage) {
        this.previousThreeMaxAverage = previousThreeMaxAverage;
    }

    @Override
    public String toString() {
        return "CryptoDto{" +
            ", symbolInfo=" + symbolInfo +
            ", currentPrice=" + currentPrice +
            ", volume=" + volume +
            ", sumDiffsPerc=" + sumDiffsPerc +
            ", sumDiffsPerc10h=" + sumDiffsPerc10h +
            ", priceToSell=" + priceToSell +
            ", priceToSellPercentage=" + priceToSellPercentage +
            ", weight=" + weight +
            ", lastThreeMaxAverage=" + lastThreeMaxAverage +
            ", previousThreeMaxAverage=" + previousThreeMaxAverage +
            '}';
    }
}
