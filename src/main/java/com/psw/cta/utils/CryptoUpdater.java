package com.psw.cta.utils;

import static com.psw.cta.utils.CommonUtils.calculateCurrentPrice;
import static com.psw.cta.utils.CommonUtils.getAveragePrices;
import static com.psw.cta.utils.CryptoUtils.calculateLastThreeMaxAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePreviousThreeMaxAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePriceToSell;
import static com.psw.cta.utils.CryptoUtils.calculatePriceToSellPercentage;
import static com.psw.cta.utils.CryptoUtils.calculateSumDiffsPercent;
import static com.psw.cta.utils.CryptoUtils.calculateSumDiffsPercent10h;
import static com.psw.cta.utils.LeastSquares.getSlope;
import static java.math.RoundingMode.CEILING;

import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.TickerStatistics;
import com.psw.cta.dto.Crypto;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

public class CryptoUpdater {

    public static Crypto updateCryptoWithSlopeData(Crypto crypto) {
        List<BigDecimal> averagePrices = getAveragePrices(crypto);
        double leastSquaresSlope = getSlope(averagePrices);
        if (Double.isNaN(leastSquaresSlope)) {
            leastSquaresSlope = 0.0000000001;
        }
        BigDecimal slope = new BigDecimal(leastSquaresSlope, new MathContext(8));
        BigDecimal priceCount = new BigDecimal(averagePrices.size(), new MathContext(8));
        crypto.setSlope(slope);
        crypto.setPriceCount(priceCount);
        crypto.setPriceCountToSlope(priceCount.divide(slope, 8, CEILING));
        return crypto;
    }

    public static Crypto updateCryptoWithLeastMaxAverage(Crypto crypto, List<Candlestick> candleStickData) {
        BigDecimal lastThreeMaxAverage = calculateLastThreeMaxAverage(candleStickData);
        BigDecimal previousThreeMaxAverage = calculatePreviousThreeMaxAverage(candleStickData);
        crypto.setFifteenMinutesCandleStickData(candleStickData);
        crypto.setLastThreeMaxAverage(lastThreeMaxAverage);
        crypto.setPreviousThreeMaxAverage(previousThreeMaxAverage);
        return crypto;
    }

    public static Crypto updateCryptoWithPrices(Crypto crypto) {
        List<Candlestick> fifteenMinutesCandleStickData = crypto.getFifteenMinutesCandleStickData();
        BigDecimal currentPrice = crypto.getCurrentPrice();
        BigDecimal priceToSell = calculatePriceToSell(fifteenMinutesCandleStickData, currentPrice);
        BigDecimal priceToSellPercentage = calculatePriceToSellPercentage(priceToSell, currentPrice);
        crypto.setPriceToSell(priceToSell);
        crypto.setPriceToSellPercentage(priceToSellPercentage);
        return crypto;
    }

    public static Crypto updateCryptoWithSumDiffPerc(Crypto crypto) {
        List<Candlestick> fifteenMinutesCandleStickData = crypto.getFifteenMinutesCandleStickData();
        BigDecimal currentPrice = crypto.getCurrentPrice();
        BigDecimal sumDiffsPerc = calculateSumDiffsPercent(fifteenMinutesCandleStickData, currentPrice);
        BigDecimal sumDiffsPerc10h = calculateSumDiffsPercent10h(fifteenMinutesCandleStickData, currentPrice);
        crypto.setSumDiffsPerc(sumDiffsPerc);
        crypto.setSumDiffsPerc10h(sumDiffsPerc10h);
        return crypto;
    }

    public static Crypto updateCryptoWithVolume(Crypto crypto, List<TickerStatistics> tickers) {
        TickerStatistics ticker24hr = CryptoUtils.calculateTicker24hr(tickers, crypto.getSymbolInfo().getSymbol());
        BigDecimal volume = CryptoUtils.calculateVolume(ticker24hr);
        crypto.setTicker24hr(ticker24hr);
        crypto.setVolume(volume);
        return crypto;
    }

    public static Crypto updateCryptoWithCurrentPrice(Crypto crypto, OrderBook orderBook) {
        BigDecimal currentPrice = calculateCurrentPrice(orderBook);
        crypto.setOrderBook(orderBook);
        crypto.setCurrentPrice(currentPrice);
        return crypto;
    }
}