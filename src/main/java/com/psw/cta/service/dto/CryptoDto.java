package com.psw.cta.service.dto;

import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.binance.api.client.domain.market.TickerStatistics;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private SymbolInfo symbolInfo;

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

    public void setDepth20(OrderBook depth20) {
        this.depth20 = depth20;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public BigDecimal getSumDiffsPerc() {
        return sumDiffsPerc;
    }

    public BigDecimal getSumDiffsPerc10h() {
        return sumDiffsPerc10h;
    }

    public BigDecimal getPriceToSell() {
        return priceToSell;
    }

    public BigDecimal getPriceToSellPercentage() {
        return priceToSellPercentage;
    }

    public SymbolInfo getSymbolInfo() {
        return symbolInfo;
    }

    public BigDecimal getLastThreeMaxAverage() {
        return lastThreeMaxAverage;
    }

    public BigDecimal getPreviousThreeMaxAverage() {
        return previousThreeMaxAverage;
    }

    public void setPreviousThreeMaxAverage(BigDecimal previousThreeMaxAverage) {
        this.previousThreeMaxAverage = previousThreeMaxAverage;
    }

    public void calculateTicker24hr(List<TickerStatistics> tickers) {
        final String symbol = this.symbolInfo.getSymbol();
        this.ticker24hr = tickers.parallelStream()
            .filter(ticker -> ticker.getSymbol().equals(symbol))
            .findAny()
            .orElseThrow(() -> new RuntimeException("Dto with symbol: " + symbol + "not found"));
    }

    public void calculateVolume() {
        this.volume = new BigDecimal(this.ticker24hr.getVolume());
    }

    public void calculateCurrentPrice() {
        this.currentPrice = this.depth20.getAsks()
            .parallelStream()
            .map(OrderBookEntry::getPrice)
            .map(BigDecimal::new)
            .min(Comparator.naturalOrder())
            .orElseThrow(RuntimeException::new);
    }

    public void calculateLastThreeMaxAverage() {
        int skipSize = this.fifteenMinutesCandleStickData.size() - 3;
        this.lastThreeMaxAverage = this.fifteenMinutesCandleStickData.stream()
            .skip(skipSize)
            .map(Candlestick::getHigh)
            .map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal("3"), 8, RoundingMode.UP);
    }

    public void calculateSumDiffsPercent() {
        this.sumDiffsPerc = calculateSumDiffsPerc(4);
    }

    public void calculateSumDiffsPercent10h() {
        this.sumDiffsPerc10h = calculateSumDiffsPerc(40);
    }

    private BigDecimal calculateSumDiffsPerc(int numberOfDataToKeep) {
        int size = this.fifteenMinutesCandleStickData.size();
        if (size - numberOfDataToKeep < 0) {
            return BigDecimal.ZERO;
        }
        return calculateSumDiffsPercentage(size - numberOfDataToKeep);
    }

    private BigDecimal calculateSumDiffsPercentage(int size) {
        return this.fifteenMinutesCandleStickData.stream()
            .skip(size)
            .map(data -> getPercentualDifference(data, this.currentPrice))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getPercentualDifference(Candlestick data, BigDecimal currentPrice) {
        BigDecimal absoluteValue = getAverageValue(data);
        BigDecimal relativeValue = absoluteValue.multiply(new BigDecimal("100"))
            .divide(currentPrice, 8, BigDecimal.ROUND_UP);
        return relativeValue.subtract(new BigDecimal("100")).abs();
    }

    private BigDecimal getAverageValue(Candlestick data) {
        return new BigDecimal(data.getOpen())
            .add(new BigDecimal(data.getClose()))
            .add(new BigDecimal(data.getHigh()))
            .add(new BigDecimal(data.getLow()))
            .divide(new BigDecimal("4"), 8, BigDecimal.ROUND_UP);
    }


    public void calculatePriceToSell() {
        int size = this.fifteenMinutesCandleStickData.size();
        if (size - 4 < 0) {
            this.priceToSell = BigDecimal.ZERO;
        }
        this.priceToSell = this.fifteenMinutesCandleStickData
            .stream()
            .skip(size - 4)
            .map(Candlestick::getHigh)
            .map(BigDecimal::new)
            .max(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO)
            .subtract(this.currentPrice)
            .divide(new BigDecimal("2"), 8, BigDecimal.ROUND_UP)
            .add(this.currentPrice);
    }

    public void calculatePriceToSellPercentage() {
        BigDecimal priceToSell = this.priceToSell;
        BigDecimal currentPrice = this.currentPrice;
        this.priceToSellPercentage = priceToSell.multiply(new BigDecimal("100"))
            .divide(currentPrice, 8, BigDecimal.ROUND_UP)
            .subtract(new BigDecimal("100"));
    }

    public void calculateWeight() {
        BigDecimal priceToSell = this.priceToSell;
        BigDecimal priceToSellPercentage = this.priceToSellPercentage;
        BigDecimal ratio;
        List<OrderBookEntry> asks = this.depth20.getAsks();
        final BigDecimal sum = asks.parallelStream()
            .filter(data -> (new BigDecimal(data.getPrice()).compareTo(priceToSell) < 0))
            .map(data -> (new BigDecimal(data.getPrice()).multiply(new BigDecimal(data.getQty()))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) == 0 && priceToSell.compareTo(this.currentPrice) > 0) {
            ratio = new BigDecimal(Double.MAX_VALUE);
        } else if (sum.compareTo(BigDecimal.ZERO) == 0) {
            ratio = BigDecimal.ZERO;
        } else {
            ratio = this.volume.divide(sum, 8, BigDecimal.ROUND_UP);
        }
        this.weight = priceToSellPercentage.multiply(ratio);
    }

    public void calculatePreviousThreeMaxAverage() {
        int skipSize = this.fifteenMinutesCandleStickData.size() - 6;
        this.previousThreeMaxAverage = this.fifteenMinutesCandleStickData.stream()
            .skip(skipSize)
            .limit(3)
            .map(Candlestick::getHigh)
            .map(BigDecimal::new)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(new BigDecimal("3"), 8, RoundingMode.UP);
    }

    @Override
    public String toString() {
        return "CryptoDto{" +
            "fifteenMinutesCandleStickData=" + fifteenMinutesCandleStickData +
            ", threeMonthsCandleStickData=" + threeMonthsCandleStickData +
            ", ticker24hr=" + ticker24hr +
            ", depth20=" + depth20 +
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
