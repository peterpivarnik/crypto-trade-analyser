package com.psw.cta.utils;

import static com.psw.cta.utils.Constants.HALF_OF_MAX_ORDER_BTC_AMOUNT;
import static com.psw.cta.utils.Constants.HUNDRED_PERCENT;
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
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.LocalDateTime;

public class OrderUtils {

    public static BigDecimal calculateOrderPrice(Order order) {
        return new BigDecimal(order.getPrice());
    }

    public static BigDecimal calculateOrderBtcAmount(Order order, BigDecimal orderPrice) {
        BigDecimal orderAltAmount = getQuantityFromOrder(order);
        return orderAltAmount.multiply(orderPrice);
    }

    public static BigDecimal calculatePriceToSell(BigDecimal orderPrice, BigDecimal currentPrice, BigDecimal orderBtcAmount) {
        BigDecimal priceToSellWithoutProfit = calculatePriceToSellWithoutProfit(orderPrice, currentPrice);
        if (orderBtcAmount.compareTo(HALF_OF_MAX_ORDER_BTC_AMOUNT) < 0) {
            BigDecimal profit = orderPrice.subtract(priceToSellWithoutProfit);
            BigDecimal realProfit = profit.divide(TWO, 8, UP);
            return priceToSellWithoutProfit.add(realProfit);
        } else {
            return priceToSellWithoutProfit.multiply(new BigDecimal("1.005")).divide(ONE, 8, UP);
        }
    }

    private static BigDecimal calculatePriceToSellWithoutProfit(BigDecimal orderPrice, BigDecimal currentPrice) {
        BigDecimal subtract = orderPrice.subtract(currentPrice);
        BigDecimal divide = subtract.divide(TWO, 8, UP);
        return currentPrice.add(divide);
    }

    public static BigDecimal calculatePriceToSellPercentage(BigDecimal priceToSell, BigDecimal orderPrice) {
        return calculatePercentagePrice(priceToSell, orderPrice);
    }

    public static BigDecimal calculateOrderPricePercentage(BigDecimal currentPrice, BigDecimal orderPrice) {
        return calculatePercentagePrice(currentPrice, orderPrice);
    }

    private static BigDecimal calculatePercentagePrice(BigDecimal lowestPrice, BigDecimal highiestPrice) {
        BigDecimal percentage = lowestPrice.multiply(HUNDRED_PERCENT)
                                           .divide(highiestPrice, 8, UP);
        return HUNDRED_PERCENT.subtract(percentage);
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

    public static BigDecimal getQuantityFromOrder(Order order) {
        BigDecimal originalQuantity = new BigDecimal(order.getOrigQty());
        BigDecimal executedQuantity = new BigDecimal(order.getExecutedQty());
        return originalQuantity.subtract(executedQuantity);
    }

    private OrderUtils() {
    }
}
