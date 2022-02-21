package com.psw.cta.utils;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.UP;

import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.TickerStatistics;
import com.psw.cta.dto.Crypto;
import com.psw.cta.exception.CryptoTraderException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public class CryptoUtils {

    public static BigDecimal getVolume(Crypto crypto, List<TickerStatistics> tickers) {
        String symbol = crypto.getSymbolInfo().getSymbol();
        return tickers.parallelStream()
                      .filter(ticker -> ticker.getSymbol().equals(symbol))
                      .map(TickerStatistics::getVolume)
                      .map(BigDecimal::new)
                      .findAny()
                      .orElseThrow(() -> new CryptoTraderException("Ticker with symbol: " + symbol + " not found."));
    }

    public static BigDecimal calculateLastThreeMaxAverage(List<Candlestick> fifteenMinutesCandleStickData) {
        int skipSize = fifteenMinutesCandleStickData.size() - 3;
        return fifteenMinutesCandleStickData.stream()
                                            .skip(skipSize)
                                            .map(Candlestick::getHigh)
                                            .map(BigDecimal::new)
                                            .reduce(ZERO, BigDecimal::add)
                                            .divide(new BigDecimal("3"), 8, UP);
    }

    public static BigDecimal calculatePreviousThreeMaxAverage(List<Candlestick> fifteenMinutesCandleStickData) {
        int skipSize = fifteenMinutesCandleStickData.size() - 6;
        return fifteenMinutesCandleStickData.stream()
                                            .skip(skipSize)
                                            .limit(3)
                                            .map(Candlestick::getHigh)
                                            .map(BigDecimal::new)
                                            .reduce(ZERO, BigDecimal::add)
                                            .divide(new BigDecimal("3"), 8, UP);
    }

    public static BigDecimal calculateSumDiffsPercent(List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        return calculateSumDiffsPerc(4, fifteenMinutesCandleStickData, currentPrice);
    }

    public static BigDecimal calculateSumDiffsPercent10h(List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        return calculateSumDiffsPerc(40, fifteenMinutesCandleStickData, currentPrice);
    }

    private static BigDecimal calculateSumDiffsPerc(int numberOfDataToKeep, List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        int size = fifteenMinutesCandleStickData.size();
        if (size - numberOfDataToKeep < 0) {
            return ZERO;
        }
        return calculateSumDiffsPercentage(size - numberOfDataToKeep, fifteenMinutesCandleStickData, currentPrice);
    }

    private static BigDecimal calculateSumDiffsPercentage(int size, List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        return fifteenMinutesCandleStickData.stream()
                                            .skip(size)
                                            .map(data -> getPercentualDifference(data, currentPrice))
                                            .reduce(ZERO, BigDecimal::add);
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
            return ZERO;
        }
        return fifteenMinutesCandleStickData
            .stream()
            .skip(size - 4)
            .map(Candlestick::getHigh)
            .map(BigDecimal::new)
            .max(Comparator.naturalOrder())
            .orElse(ZERO)
            .subtract(currentPrice)
            .divide(new BigDecimal("2"), 8, UP)
            .add(currentPrice);
    }

    public static BigDecimal calculatePriceToSellPercentage(BigDecimal priceToSell, BigDecimal currentPrice) {
        return priceToSell.multiply(new BigDecimal("100"))
                          .divide(currentPrice, 8, UP)
                          .subtract(new BigDecimal("100"));
    }
}