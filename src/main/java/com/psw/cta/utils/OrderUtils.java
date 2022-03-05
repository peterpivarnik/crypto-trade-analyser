package com.psw.cta.utils;

import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.roundPrice;
import static com.psw.cta.utils.Constants.MAX_BTC_FOR_EIGHTH_PRICE_TO_SELL;
import static com.psw.cta.utils.Constants.MAX_BTC_FOR_QUARTER_PRICE_TO_SELL;
import static com.psw.cta.utils.Constants.TIME_CONSTANT;
import static com.psw.cta.utils.Constants.TWO;
import static java.lang.Math.sqrt;
import static java.math.BigDecimal.ONE;
import static java.math.RoundingMode.UP;
import static java.time.Duration.between;
import static java.time.Instant.ofEpochMilli;
import static java.time.LocalDateTime.now;
import static java.time.LocalDateTime.ofInstant;
import static java.time.ZoneId.systemDefault;
import static java.time.temporal.ChronoUnit.SECONDS;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.LocalDateTime;

public class OrderUtils {

    public static BigDecimal calculateOrderPrice(Order order) {
        return new BigDecimal(order.getPrice());
    }

    public static BigDecimal calculateOrderBtcAmount(Order order, BigDecimal orderPrice) {
        BigDecimal orderAltAmount = getQuantity(order);
        return orderAltAmount.multiply(orderPrice);
    }

    public static BigDecimal calculatePriceToSell(BigDecimal orderPrice, BigDecimal currentPrice, BigDecimal orderBtcAmount, SymbolInfo symbolInfo) {
        BigDecimal tickSize = getValueFromFilter(symbolInfo, PRICE_FILTER, SymbolFilter::getTickSize);
        if (orderPrice.compareTo(currentPrice) == 0 || orderPrice.subtract(tickSize).compareTo(currentPrice) == 0) {
            return currentPrice;
        }
        BigDecimal priceToSellWithoutProfit = getPriceToSell(orderPrice, currentPrice, TWO);
        BigDecimal priceToSell;
        if (orderBtcAmount.compareTo(MAX_BTC_FOR_QUARTER_PRICE_TO_SELL) < 0) {
            priceToSell = getPriceToSell(orderPrice, priceToSellWithoutProfit, TWO);
        } else if (orderBtcAmount.compareTo(MAX_BTC_FOR_QUARTER_PRICE_TO_SELL) >= 0
                   && orderBtcAmount.compareTo(MAX_BTC_FOR_EIGHTH_PRICE_TO_SELL) < 0) {
            priceToSell = getPriceToSell(orderPrice, priceToSellWithoutProfit, new BigDecimal("4"));
        } else {
            priceToSell = priceToSellWithoutProfit.multiply(new BigDecimal("1.005")).divide(ONE, 8, UP);
        }

        if (priceToSell.compareTo(orderPrice) >= 0) {
            priceToSell = orderPrice.subtract(tickSize);
        }
        return roundPrice(symbolInfo, priceToSell);
    }

    private static BigDecimal getPriceToSell(BigDecimal bigPrice, BigDecimal smallPrice, BigDecimal divisor) {
        BigDecimal profit = bigPrice.subtract(smallPrice);
        BigDecimal realProfit = profit.divide(divisor, 8, UP);
        return smallPrice.add(realProfit);
    }

    public static BigDecimal calculateMinWaitingTime(BigDecimal totalSymbolAmount, BigDecimal orderBtcAmount) {
        BigDecimal totalWaitingTime = getTimeFromAmount(totalSymbolAmount);
        BigDecimal orderWaitingTime = getTimeFromAmount(orderBtcAmount);
        BigDecimal waitingTime = totalWaitingTime.add(orderWaitingTime);
        return waitingTime.multiply(TIME_CONSTANT);
    }

    private static BigDecimal getTimeFromAmount(BigDecimal totalAmount) {
        double totalTime = 100 * sqrt(totalAmount.doubleValue());
        return new BigDecimal(totalTime, new MathContext(3));
    }

    public static BigDecimal calculateActualWaitingTime(Order order) {
        LocalDateTime date = ofInstant(ofEpochMilli(order.getTime()), systemDefault());
        LocalDateTime now = now();
        Duration duration = between(date, now);
        double actualWaitingTimeDouble = (double) duration.get(SECONDS) / (double) 3600;
        return new BigDecimal(actualWaitingTimeDouble, new MathContext(5));
    }

    private OrderUtils() {
    }
}
