package com.psw.cta.utils;

import static java.math.RoundingMode.UP;

import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.binance.api.client.domain.market.TickerStatistics;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public class CryptoUtils {

    public static TickerStatistics calculateTicker24hr(List<TickerStatistics> tickers, String symbol) {
        return tickers.parallelStream()
                      .filter(ticker -> ticker.getSymbol().equals(symbol))
                      .findAny()
                      .orElseThrow(() -> new RuntimeException("Dto with symbol: " + symbol + "not found"));
    }

    public static BigDecimal calculateVolume(TickerStatistics ticker24hr) {
        return new BigDecimal(ticker24hr.getVolume());
    }

    public static BigDecimal calculateCurrentPrice(OrderBook depth20) {
        return depth20.getAsks()
                      .parallelStream()
                      .map(OrderBookEntry::getPrice)
                      .map(BigDecimal::new)
                      .min(Comparator.naturalOrder())
                      .orElseThrow(RuntimeException::new);
    }

    public static BigDecimal calculateLastThreeMaxAverage(List<Candlestick> fifteenMinutesCandleStickData) {
        int skipSize = fifteenMinutesCandleStickData.size() - 3;
        return fifteenMinutesCandleStickData.stream()
                                            .skip(skipSize)
                                            .map(Candlestick::getHigh)
                                            .map(BigDecimal::new)
                                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                                            .divide(new BigDecimal("3"), 8, UP);
    }

    public static  BigDecimal calculateSumDiffsPercent(List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        return calculateSumDiffsPerc(4, fifteenMinutesCandleStickData, currentPrice);
    }

    public static BigDecimal calculateSumDiffsPercent10h(List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        return calculateSumDiffsPerc(40, fifteenMinutesCandleStickData, currentPrice);
    }

    private static BigDecimal calculateSumDiffsPerc(int numberOfDataToKeep, List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        int size = fifteenMinutesCandleStickData.size();
        if (size - numberOfDataToKeep < 0) {
            return BigDecimal.ZERO;
        }
        return calculateSumDiffsPercentage(size - numberOfDataToKeep, fifteenMinutesCandleStickData, currentPrice);
    }

    private static BigDecimal calculateSumDiffsPercentage(int size, List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        return fifteenMinutesCandleStickData.stream()
                                            .skip(size)
                                            .map(data -> getPercentualDifference(data, currentPrice))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal getPercentualDifference(Candlestick data, BigDecimal currentPrice) {
        BigDecimal absoluteValue = getAverageValue(data);
        BigDecimal relativeValue = absoluteValue.multiply(new BigDecimal("100"))
                                                .divide(currentPrice, 8, UP);
        return relativeValue.subtract(new BigDecimal("100")).abs();
    }

    private static BigDecimal getAverageValue(Candlestick data) {
        return new BigDecimal(data.getOpen())
            .add(new BigDecimal(data.getClose()))
            .add(new BigDecimal(data.getHigh()))
            .add(new BigDecimal(data.getLow()))
            .divide(new BigDecimal("4"), 8, UP);
    }


    public static BigDecimal calculatePriceToSell(List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        int size = fifteenMinutesCandleStickData.size();
        if (size - 4 < 0) {
            return BigDecimal.ZERO;
        }
        return fifteenMinutesCandleStickData
            .stream()
            .skip(size - 4)
            .map(Candlestick::getHigh)
            .map(BigDecimal::new)
            .max(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO)
            .subtract(currentPrice)
            .divide(new BigDecimal("2"), 8, UP)
            .add(currentPrice);
    }

    public static BigDecimal calculatePriceToSellPercentage(BigDecimal priceToSell, BigDecimal currentPrice) {
        return priceToSell.multiply(new BigDecimal("100"))
                          .divide(currentPrice, 8, UP)
                          .subtract(new BigDecimal("100"));
    }

    public static BigDecimal calculateWeight(OrderBook depth20, BigDecimal priceToSell, BigDecimal currentPrice, BigDecimal volume, BigDecimal priceToSellPercentage) {
        BigDecimal ratio;
        final BigDecimal sum = depth20.getAsks().parallelStream()
                                      .filter(data -> (new BigDecimal(data.getPrice()).compareTo(priceToSell) < 0))
                                      .map(data -> (new BigDecimal(data.getPrice()).multiply(new BigDecimal(data.getQty()))))
                                      .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) == 0 && priceToSell.compareTo(currentPrice) > 0) {
            ratio = new BigDecimal(Double.MAX_VALUE);
        } else if (sum.compareTo(BigDecimal.ZERO) == 0) {
            ratio = BigDecimal.ZERO;
        } else {
            ratio = volume.divide(sum, 8, UP);
        }
        return priceToSellPercentage.multiply(ratio);
    }

    public static BigDecimal calculatePreviousThreeMaxAverage(List<Candlestick> fifteenMinutesCandleStickData) {
        int skipSize = fifteenMinutesCandleStickData.size() - 6;
        return fifteenMinutesCandleStickData.stream()
                                            .skip(skipSize)
                                            .limit(3)
                                            .map(Candlestick::getHigh)
                                            .map(BigDecimal::new)
                                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                                            .divide(new BigDecimal("3"), 8, UP);
    }
}
