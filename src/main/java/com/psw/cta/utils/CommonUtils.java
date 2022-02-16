package com.psw.cta.utils;

import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.Function;

public class CommonUtils {

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
}
