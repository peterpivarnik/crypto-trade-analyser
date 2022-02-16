package com.psw.cta.utils;

import static java.lang.Math.sqrt;
import static java.math.BigDecimal.ONE;
import static java.math.RoundingMode.UP;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.psw.cta.dto.OrderDto;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

public class OrderUtils {

    public static BigDecimal calculateOrderPrice(Order order) {
        return new BigDecimal(order.getPrice());
    }

    public static BigDecimal calculateOrderBtcAmount(Order order, BigDecimal orderPrice) {
        BigDecimal orderAltAmount = new BigDecimal(order.getOrigQty());
        return orderAltAmount.multiply(orderPrice);
    }

    public static BigDecimal calculateCurrentPrice(OrderBook depth20) {
        return depth20.getAsks()
                      .parallelStream()
                      .map(OrderBookEntry::getPrice)
                      .map(BigDecimal::new)
                      .min(Comparator.naturalOrder())
                      .orElseThrow(RuntimeException::new);
    }

    public static BigDecimal calculatePriceToSellWithoutProfit(BigDecimal orderPrice, BigDecimal currentPrice) {
        BigDecimal subtract = orderPrice.subtract(currentPrice);
        BigDecimal divide = subtract.divide(new BigDecimal("2"), 8, UP);
        return currentPrice.add(divide);
    }

    public static BigDecimal calculatePriceToSell(BigDecimal orderPrice, BigDecimal priceToSellWithoutProfit, BigDecimal orderBtcAmount) {
        if (orderBtcAmount.compareTo(new BigDecimal("0.01")) < 0) {
            BigDecimal subtract = orderPrice.subtract(priceToSellWithoutProfit);
            BigDecimal divide = subtract.divide(new BigDecimal("2"), 8, UP);
            return priceToSellWithoutProfit.add(divide);
        } else {
            return priceToSellWithoutProfit.multiply(new BigDecimal("1.005")).divide(ONE, 8, UP);
        }
    }

    public static BigDecimal calculatePriceToSellPercentage(BigDecimal priceToSell, BigDecimal orderPrice) {
        BigDecimal percentage = priceToSell.multiply(new BigDecimal("100")).divide(orderPrice, 8, UP);
        return new BigDecimal("100").subtract(percentage);
    }

    public static BigDecimal calculateOrderPricePercentage(BigDecimal currentPrice, BigDecimal orderPrice) {
        BigDecimal percentage = currentPrice.multiply(new BigDecimal("100")).divide(orderPrice, 8, UP);
        return new BigDecimal("100").subtract(percentage);
    }

    public static BigDecimal calculateMinWaitingTime(BigDecimal totalSymbolAmount, BigDecimal orderBtcAmount) {
        BigDecimal totalWaitingTime = getTimeFromAmount(totalSymbolAmount);
        BigDecimal orderWaitingTime = getTimeFromAmount(orderBtcAmount);
        BigDecimal waitingTime = totalWaitingTime.add(orderWaitingTime);
        return waitingTime.multiply(new BigDecimal("2"));
    }

    private static BigDecimal getTimeFromAmount(BigDecimal totalAmount) {
        double totalTime = 100 * sqrt(totalAmount.doubleValue());
        return new BigDecimal(totalTime, new MathContext(3));
    }

    public static BigDecimal calculateActualWaitingTime(Order order) {
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(order.getTime()), ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(date, now);
        double actualWaitingTimeDouble = (double) duration.get(ChronoUnit.SECONDS) / (double) 3600;
        return new BigDecimal(actualWaitingTimeDouble, new MathContext(5));
    }

    public static BigDecimal getQuantityFromOrder(OrderDto orderToCancel) {
        BigDecimal originalQuantity = new BigDecimal(orderToCancel.getOrder().getOrigQty());
        BigDecimal executedQuantity = new BigDecimal(orderToCancel.getOrder().getExecutedQty());
        return originalQuantity.subtract(executedQuantity);
    }
}
