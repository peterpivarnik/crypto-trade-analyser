package com.psw.cta.utils;

import static com.psw.cta.utils.CommonUtils.getAveragePrice;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.UP;
import static java.util.Comparator.naturalOrder;

import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.TickerStatistics;
import com.psw.cta.dto.Crypto;
import com.psw.cta.exception.CryptoTraderException;
import java.math.BigDecimal;
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

    public static BigDecimal calculateLastThreeHighAverage(List<Candlestick> fifteenMinutesCandleStickData) {
        return calculateHighAverage(fifteenMinutesCandleStickData, 3);
    }

    public static BigDecimal calculatePreviousThreeHighAverage(List<Candlestick> fifteenMinutesCandleStickData) {
        return calculateHighAverage(fifteenMinutesCandleStickData, 6);
    }

    private static BigDecimal calculateHighAverage(List<Candlestick> fifteenMinutesCandleStickData, int notSkipped) {
        int skipSize = fifteenMinutesCandleStickData.size() - notSkipped;
        return fifteenMinutesCandleStickData.stream()
                                            .skip(skipSize)
                                            .limit(3)
                                            .map(Candlestick::getHigh)
                                            .map(BigDecimal::new)
                                            .reduce(ZERO, BigDecimal::add)
                                            .divide(new BigDecimal("3"), 8, UP);
    }

    public static BigDecimal calculateSumPercentageDifferences1h(List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        return calculateSumPercentageDifferences(4, fifteenMinutesCandleStickData, currentPrice);
    }

    public static BigDecimal calculateSumPercentageDifferences10h(List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        return calculateSumPercentageDifferences(40, fifteenMinutesCandleStickData, currentPrice);
    }

    private static BigDecimal calculateSumPercentageDifferences(int numberOfDataToKeep, List<Candlestick> fifteenMinutesCandleStickData,
                                                                BigDecimal currentPrice) {
        int size = fifteenMinutesCandleStickData.size();
        if (size - numberOfDataToKeep < 0) {
            return ZERO;
        }
        return sumPercentageDifferences(size - numberOfDataToKeep, fifteenMinutesCandleStickData, currentPrice);
    }

    private static BigDecimal sumPercentageDifferences(int size, List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        return fifteenMinutesCandleStickData.stream()
                                            .skip(size)
                                            .map(data -> getPercentageDifference(data, currentPrice))
                                            .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal getPercentageDifference(Candlestick data, BigDecimal currentPrice) {
        BigDecimal averagePrice = getAveragePrice(data);
        BigDecimal relativeValue = averagePrice.multiply(new BigDecimal("100"))
                                               .divide(currentPrice, 8, UP);
        return relativeValue.subtract(new BigDecimal("100")).abs();
    }

    public static BigDecimal calculatePriceToSell(List<Candlestick> fifteenMinutesCandleStickData, BigDecimal currentPrice) {
        int size = fifteenMinutesCandleStickData.size();
        if (size - 4 < 0) {
            return currentPrice;
        }
        return fifteenMinutesCandleStickData
            .stream()
            .skip(size - 4)
            .map(Candlestick::getHigh)
            .map(BigDecimal::new)
            .max(naturalOrder())
            .orElse(currentPrice)
            .subtract(currentPrice)
            .divide(new BigDecimal("2"), 8, UP)
            .add(currentPrice);
    }
}