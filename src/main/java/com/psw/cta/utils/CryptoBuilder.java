package com.psw.cta.utils;

import static com.psw.cta.utils.CommonUtils.calculatePricePercentage;
import static com.psw.cta.utils.CommonUtils.getAveragePrices;
import static com.psw.cta.utils.CommonUtils.getCurrentPrice;
import static com.psw.cta.utils.CommonUtils.getPriceCountToSlope;
import static com.psw.cta.utils.CryptoUtils.calculateLastThreeHighAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePreviousThreeHighAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePriceToSell;
import static com.psw.cta.utils.CryptoUtils.calculateSumPercentageDifferences10h;
import static com.psw.cta.utils.CryptoUtils.calculateSumPercentageDifferences1h;
import static com.psw.cta.utils.CryptoUtils.getVolume;

import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.TickerStatistics;
import com.psw.cta.dto.Crypto;
import java.math.BigDecimal;
import java.util.List;

public class CryptoBuilder {

    public static Crypto build(SymbolInfo symbolInfo) {
        return new Crypto(symbolInfo);
    }

    public static Crypto withSlopeData(Crypto crypto) {
        List<BigDecimal> averagePrices = getAveragePrices(crypto.getThreeMonthsCandleStickData());
        BigDecimal priceCountToSlope = getPriceCountToSlope(averagePrices);
        crypto.setPriceCountToSlope(priceCountToSlope);
        crypto.setNumberOfCandles(new BigDecimal(averagePrices.size()));
        return crypto;
    }

    public static Crypto withLeastMaxAverage(Crypto crypto, List<Candlestick> candleStickData) {
        BigDecimal lastThreeHighAverage = calculateLastThreeHighAverage(candleStickData);
        BigDecimal previousThreeHighAverage = calculatePreviousThreeHighAverage(candleStickData);
        crypto.setFifteenMinutesCandleStickData(candleStickData);
        crypto.setLastThreeHighAverage(lastThreeHighAverage);
        crypto.setPreviousThreeHighAverage(previousThreeHighAverage);
        return crypto;
    }

    public static Crypto withPrices(Crypto crypto) {
        List<Candlestick> fifteenMinutesCandleStickData = crypto.getFifteenMinutesCandleStickData();
        BigDecimal currentPrice = crypto.getCurrentPrice();
        BigDecimal priceToSell = calculatePriceToSell(fifteenMinutesCandleStickData, currentPrice);
        BigDecimal priceToSellPercentage = calculatePricePercentage(currentPrice, priceToSell);
        crypto.setPriceToSell(priceToSell);
        crypto.setPriceToSellPercentage(priceToSellPercentage);
        return crypto;
    }

    public static Crypto withSumDiffPerc(Crypto crypto) {
        List<Candlestick> fifteenMinutesCandleStickData = crypto.getFifteenMinutesCandleStickData();
        BigDecimal currentPrice = crypto.getCurrentPrice();
        BigDecimal sumPercentageDifferences1h = calculateSumPercentageDifferences1h(fifteenMinutesCandleStickData, currentPrice);
        BigDecimal calculateSumPercentageDifferences10h = calculateSumPercentageDifferences10h(fifteenMinutesCandleStickData, currentPrice);
        crypto.setSumPercentageDifferences1h(sumPercentageDifferences1h);
        crypto.setSumPercentageDifferences10h(calculateSumPercentageDifferences10h);
        return crypto;
    }

    public static Crypto withVolume(Crypto crypto, List<TickerStatistics> tickers) {
        BigDecimal volume = getVolume(crypto, tickers);
        crypto.setVolume(volume);
        return crypto;
    }

    public static Crypto withCurrentPrice(Crypto crypto, OrderBook orderBook) {
        BigDecimal currentPrice = getCurrentPrice(orderBook);
        crypto.setOrderBook(orderBook);
        crypto.setCurrentPrice(currentPrice);
        return crypto;
    }
}