package com.psw.cta.service.dto;

import static java.lang.Math.sqrt;
import static java.math.RoundingMode.UP;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class OrderDtoUtil {

    public BigDecimal calculateOrderPrice(Order order) {
        return new BigDecimal(order.getPrice());
    }

    public BigDecimal calculateOrderBtcAmount(Order order, BigDecimal orderPrice) {
        BigDecimal orderAltAmount = new BigDecimal(order.getOrigQty());
        return orderAltAmount.multiply(orderPrice);
    }

    public BigDecimal calculateCurrentPrice(OrderBook depth20) {
        return depth20.getAsks()
                      .parallelStream()
                      .map(OrderBookEntry::getPrice)
                      .map(BigDecimal::new)
                      .min(Comparator.naturalOrder())
                      .orElseThrow(RuntimeException::new);
    }

    public BigDecimal calculatePriceToSellWithoutProfit(BigDecimal orderPrice, BigDecimal currentPrice) {
        BigDecimal subtract = orderPrice.subtract(currentPrice);
        BigDecimal divide = subtract.divide(new BigDecimal("2"), 8, UP);
        return currentPrice.add(divide);
    }

    public BigDecimal calculatePriceToSell(BigDecimal orderPrice, BigDecimal priceToSellWithoutProfit) {
        BigDecimal subtract = orderPrice.subtract(priceToSellWithoutProfit);
        BigDecimal divide = subtract.divide(new BigDecimal("2"), 8, UP);
        return priceToSellWithoutProfit.add(divide);
    }

    public BigDecimal calculatePriceToSellPercentage(BigDecimal priceToSell, BigDecimal orderPrice) {
        BigDecimal percentage = priceToSell.multiply(new BigDecimal("100")).divide(orderPrice, 8, UP);
        return new BigDecimal("100").subtract(percentage);
    }

    public BigDecimal calculateMinWaitingTime(BigDecimal totalSymbolAmount, BigDecimal orderBtcAmount) {
        BigDecimal totalWaitingTime = getTimeFromAmount(totalSymbolAmount);
        BigDecimal orderWaitingTime = getTimeFromAmount(orderBtcAmount);
        return totalWaitingTime.add(orderWaitingTime);
    }

    private BigDecimal getTimeFromAmount(BigDecimal totalAmount) {
        double totalTime = 100 * sqrt(totalAmount.doubleValue());
        return new BigDecimal(totalTime, new MathContext(3));
    }

    public BigDecimal calculateActualWaitingTime(Order order) {
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(order.getTime()), ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(date, now);
        double actualWaitingTimeDouble = (double) duration.get(ChronoUnit.SECONDS) / (double) 3600;
        return new BigDecimal(actualWaitingTimeDouble, new MathContext(5));
    }
}
