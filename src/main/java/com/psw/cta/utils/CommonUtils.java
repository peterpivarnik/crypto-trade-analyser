package com.psw.cta.utils;

import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static com.psw.cta.utils.Constants.HUNDRED_PERCENT;
import static com.psw.cta.utils.LeastSquares.getSlope;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.UP;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.psw.cta.exception.CryptoTraderException;
import com.psw.cta.service.BinanceApiService;
import com.psw.cta.service.BnbService;
import com.psw.cta.service.DiversifyService;
import com.psw.cta.service.InitialTradingService;
import com.psw.cta.service.RepeatTradingService;
import com.psw.cta.service.TradingService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CommonUtils {

    public static TradingService initializeTradingService(String apiKey, String apiSecret, LambdaLogger logger) {
        BinanceApiService binanceApiService = new BinanceApiService(apiKey, apiSecret, logger);
        InitialTradingService initialTradingService = new InitialTradingService(binanceApiService, logger);
        RepeatTradingService repeatTradingService = new RepeatTradingService(binanceApiService, logger);
        DiversifyService diversifyService = new DiversifyService(binanceApiService, logger);
        BnbService bnbService = new BnbService(binanceApiService, logger);
        return new TradingService(initialTradingService, repeatTradingService, diversifyService, bnbService, binanceApiService, logger);
    }

    public static Comparator<Order> getOrderComparator() {
        Function<Order, BigDecimal> quantityFunction = CommonUtils::getQuantity;
        Function<Order, BigDecimal> btcAmountFunction = order -> (getQuantity(order)).multiply(new BigDecimal(order.getPrice()));
        Function<Order, BigDecimal> timeFunction = order -> new BigDecimal(order.getTime());
        return comparing(quantityFunction)
            .reversed()
            .thenComparing(comparing(btcAmountFunction).reversed())
            .thenComparing(timeFunction);
    }

    public static void sleep(int millis, LambdaLogger logger) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            logger.log("Error during sleeping");
        }
    }

    public static BigDecimal getValueFromFilter(SymbolInfo symbolInfo, FilterType filterType, Function<SymbolFilter, String> symbolFilterFunction) {
        return symbolInfo.getFilters()
                         .parallelStream()
                         .filter(filter -> filter.getFilterType().equals(filterType))
                         .map(symbolFilterFunction)
                         .map(BigDecimal::new)
                         .findAny()
                         .orElseThrow(() -> new CryptoTraderException("Value from filter " + filterType + " not found"));
    }

    public static BigDecimal roundAmount(SymbolInfo symbolInfo, BigDecimal amount) {
        return round(symbolInfo, amount, LOT_SIZE, SymbolFilter::getMinQty);
    }

    public static BigDecimal roundPrice(SymbolInfo symbolInfo, BigDecimal price) {
        return round(symbolInfo, price, PRICE_FILTER, SymbolFilter::getTickSize);
    }

    private static BigDecimal round(SymbolInfo symbolInfo,
                                    BigDecimal amountToRound,
                                    FilterType filterType,
                                    Function<SymbolFilter, String> symbolFilterFunction) {
        BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, filterType, symbolFilterFunction);
        BigDecimal remainder = amountToRound.remainder(valueFromFilter);
        return amountToRound.subtract(remainder);
    }

    public static BigDecimal getPriceCountToSlope(List<BigDecimal> averagePrices) {
        BigDecimal priceCount = new BigDecimal(averagePrices.size(), new MathContext(8));
        double leastSquaresSlope = getSlope(averagePrices);
        if (Double.isNaN(leastSquaresSlope)) {
            leastSquaresSlope = 0.00000001;
        }
        BigDecimal slope = new BigDecimal(leastSquaresSlope, new MathContext(8));
        return priceCount.divide(slope, 8, CEILING);
    }

    public static List<BigDecimal> getAveragePrices(List<Candlestick> threeMonthsCandleStickData) {
        Candlestick maxHighCandlestick = threeMonthsCandleStickData.stream()
                                                            .max(comparing(candle -> new BigDecimal(candle.getHigh())))
                                                            .orElseThrow();
        return threeMonthsCandleStickData.parallelStream()
                                         .filter(candle -> candle.getOpenTime() > maxHighCandlestick.getOpenTime())
                                         .map(CommonUtils::getAveragePrice)
                                         .collect(Collectors.toList());
    }

    public static BigDecimal getAveragePrice(Candlestick candle) {
        BigDecimal open = new BigDecimal(candle.getOpen());
        BigDecimal close = new BigDecimal(candle.getClose());
        BigDecimal high = new BigDecimal(candle.getHigh());
        BigDecimal low = new BigDecimal(candle.getLow());
        return open.add(close)
                   .add(high)
                   .add(low)
                   .divide(new BigDecimal("4"), 8, CEILING);
    }

    public static Map<String, BigDecimal> createTotalAmounts(List<Order> openOrders) {
        return openOrders.stream()
                         .collect(toMap(Order::getSymbol,
                                        order -> new BigDecimal(order.getPrice()).multiply(getQuantity(order)),
                                        BigDecimal::add))
                         .entrySet()
                         .stream()
                         .sorted(Map.Entry.comparingByValue())
                         .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public static BigDecimal getQuantity(Order order) {
        return new BigDecimal(order.getOrigQty()).subtract(new BigDecimal(order.getExecutedQty()));
    }

    public static int calculateMinNumberOfOrders(BigDecimal myTotalPossibleBalance, BigDecimal myBtcBalance) {
        BigDecimal minFromPossibleBalance = myTotalPossibleBalance.multiply(new BigDecimal("5"));
        BigDecimal minFromActualBtcBalance = myBtcBalance.multiply(new BigDecimal("50"));
        return minFromActualBtcBalance.max(minFromPossibleBalance).intValue();
    }

    public static BigDecimal getCurrentPrice(OrderBook orderBook) {
        return orderBook.getAsks()
                        .parallelStream()
                        .map(OrderBookEntry::getPrice)
                        .map(BigDecimal::new)
                        .min(Comparator.naturalOrder())
                        .orElseThrow(() -> new CryptoTraderException("No price found!"));
    }

    public static BigDecimal calculatePricePercentage(BigDecimal lowestPrice, BigDecimal highestPrice) {
        BigDecimal percentage = lowestPrice.multiply(HUNDRED_PERCENT)
                                           .divide(highestPrice, 8, UP);
        return HUNDRED_PERCENT.subtract(percentage);
    }

    public static boolean haveBalanceForInitialTrading(BigDecimal myBtcBalance) {
        return myBtcBalance.compareTo(new BigDecimal("0.0002")) > 0;
    }

    public static BigDecimal getMinBtcAmount(BigDecimal btcAmountToSpend, BigDecimal minAddition, BigDecimal minValueFromMinNotionalFilter) {
        if (btcAmountToSpend.compareTo(minValueFromMinNotionalFilter) < 0) {
            return getMinBtcAmount(btcAmountToSpend.add(minAddition), minAddition, minValueFromMinNotionalFilter);
        }
        return btcAmountToSpend.add(minAddition);
    }
}