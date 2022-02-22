package com.psw.cta.utils;

import static com.psw.cta.utils.Constants.HUNDRED_PERCENT;
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
import com.psw.cta.dto.Crypto;
import com.psw.cta.exception.CryptoTraderException;
import com.psw.cta.service.BinanceApiService;
import com.psw.cta.service.BnbService;
import com.psw.cta.service.DiversifyService;
import com.psw.cta.service.InitialTradingService;
import com.psw.cta.service.RepeatTradingService;
import com.psw.cta.service.TradingService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CommonUtils {

    public static TradingService initializeTradingService(String apiKey, String apiSecret, LambdaLogger logger) {
        BinanceApiService binanceApiService = new BinanceApiService(apiKey, apiSecret, logger);
        BnbService bnbService = new BnbService(binanceApiService, logger);
        InitialTradingService initialTradingService = new InitialTradingService(binanceApiService, logger);
        DiversifyService diversifyService = new DiversifyService(binanceApiService, logger);
        RepeatTradingService repeatTradingService = new RepeatTradingService(diversifyService, binanceApiService, logger);
        return new TradingService(initialTradingService, repeatTradingService, bnbService, binanceApiService, logger);
    }

    public static Comparator<Order> getOrderComparator() {
        Function<Order, BigDecimal> quantityFunction = order -> new BigDecimal(order.getOrigQty()).subtract(new BigDecimal(order.getExecutedQty()));
        Function<Order, BigDecimal> btcAmountFunction =
            order -> (new BigDecimal(order.getOrigQty()).subtract(new BigDecimal(order.getExecutedQty()))).multiply(new BigDecimal(order.getPrice()));
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
                         .orElseThrow(RuntimeException::new);
    }

    public static BigDecimal round(SymbolInfo symbolInfo, BigDecimal amountToRound, FilterType filterType,
                                   Function<SymbolFilter, String> symbolFilterFunction) {
        BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, filterType, symbolFilterFunction);
        BigDecimal remainder = amountToRound.remainder(valueFromFilter);
        return amountToRound.subtract(remainder);
    }

    public static List<BigDecimal> getAveragePrices(Crypto crypto) {
        Candlestick candlestick = crypto.getThreeMonthsCandleStickData()
                                        .stream()
                                        .max(comparing(candle -> new BigDecimal(candle.getHigh())))
                                        .orElseThrow();
        return crypto.getThreeMonthsCandleStickData()
                     .parallelStream()
                     .filter(candle -> candle.getOpenTime() > candlestick.getOpenTime())
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
                                        order -> new BigDecimal(order.getPrice()).multiply(new BigDecimal(order.getOrigQty())),
                                        BigDecimal::add))
                         .entrySet()
                         .stream()
                         .sorted(Map.Entry.comparingByValue())
                         .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public static int calculateMinNumberOfOrders(BigDecimal myTotalPossibleBalance, BigDecimal myBtcBalance) {
        BigDecimal minFromPossibleBalance = myTotalPossibleBalance.multiply(new BigDecimal("5"));
        BigDecimal minFromActualBtcBalance = myBtcBalance.multiply(new BigDecimal("50"));
        return minFromActualBtcBalance.max(minFromPossibleBalance).intValue();
    }

    public static BigDecimal calculateCurrentPrice(OrderBook orderBook) {
        return orderBook.getAsks()
                        .parallelStream()
                        .map(OrderBookEntry::getPrice)
                        .map(BigDecimal::new)
                        .min(Comparator.naturalOrder())
                        .orElseThrow(() -> new CryptoTraderException("No price found!"));
    }

    public static boolean haveBalanceForInitialTrading(BigDecimal myBtcBalance) {
        return myBtcBalance.compareTo(new BigDecimal("0.0002")) > 0;
    }


    public static BigDecimal calculatePricePercentage(BigDecimal lowestPrice, BigDecimal highestPrice) {
        BigDecimal percentage = lowestPrice.multiply(HUNDRED_PERCENT)
                                           .divide(highestPrice, 8, UP);
        return HUNDRED_PERCENT.subtract(percentage);
    }
}