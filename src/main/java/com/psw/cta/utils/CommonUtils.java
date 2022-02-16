package com.psw.cta.utils;

import static java.math.RoundingMode.CEILING;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.psw.cta.dto.CryptoDto;
import com.psw.cta.service.BinanceApiService;
import com.psw.cta.service.BnbService;
import com.psw.cta.service.DiversifyService;
import com.psw.cta.service.InitialTradingService;
import com.psw.cta.service.RepeatTradingService;
import com.psw.cta.service.TradingService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
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


    public static BigDecimal roundDown(SymbolInfo symbolInfo, BigDecimal amountToRound, FilterType filterType, Function<SymbolFilter, String> symbolFilterFunction) {
        BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, filterType, symbolFilterFunction);
        BigDecimal remainder = amountToRound.remainder(valueFromFilter);
        return amountToRound.subtract(remainder);
    }

    public static BigDecimal roundUp(SymbolInfo symbolInfo, BigDecimal amountToRound, FilterType filterType, Function<SymbolFilter, String> symbolFilterFunction) {
        BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, filterType, symbolFilterFunction);
        BigDecimal remainder = amountToRound.remainder(valueFromFilter);
        return amountToRound.subtract(remainder).add(valueFromFilter);
    }


    public static List<BigDecimal> getAveragePrices(CryptoDto cryptoDto) {
        Candlestick candlestick = cryptoDto.getThreeMonthsCandleStickData()
                                           .stream()
                                           .max(comparing(candle -> new BigDecimal(candle.getHigh())))
                                           .orElseThrow();
        return cryptoDto.getThreeMonthsCandleStickData()
                        .parallelStream()
                        .filter(candle -> candle.getOpenTime() > candlestick.getOpenTime())
                        .map(CommonUtils::getAveragePrice)
                        .collect(Collectors.toList());
    }

    private static BigDecimal getAveragePrice(Candlestick candle) {
        BigDecimal open = new BigDecimal(candle.getOpen());
        BigDecimal close = new BigDecimal(candle.getClose());
        BigDecimal high = new BigDecimal(candle.getHigh());
        BigDecimal low = new BigDecimal(candle.getLow());
        return open.add(close)
                   .add(high)
                   .add(low)
                   .divide(new BigDecimal("4"), 8, CEILING);
    }
}
