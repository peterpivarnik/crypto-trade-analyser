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
import java.util.Map;
import java.util.function.Function;

public class OrderDto {

    private final Order order;
    private BigDecimal orderBtcAmount;
    private BigDecimal orderPrice;
    private BigDecimal currentPrice;
    private BigDecimal priceToSell;
    private BigDecimal priceToSellPercentage;
    private BigDecimal priceToSellWithoutProfit;
    private BigDecimal actualProfit;
    private BigDecimal minWaitingTime;
    private BigDecimal actualWaitingTime;

    public OrderDto(Order order) {
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }

    public BigDecimal getOrderBtcAmount() {
        return orderBtcAmount;
    }

    public BigDecimal getPriceToSell() {
        return priceToSell;
    }

    public BigDecimal getPriceToSellPercentage() {
        return priceToSellPercentage;
    }

    public BigDecimal getOrderPrice() {
        return orderPrice;
    }

    public BigDecimal getActualProfit() {
        return actualProfit;
    }

    public BigDecimal getMinWaitingTime() {
        return minWaitingTime;
    }

    public BigDecimal getActualWaitingTime() {
        return actualWaitingTime;
    }

    public void calculateOrderBtcAmount() {
        BigDecimal orderAltAmount = new BigDecimal(order.getOrigQty());
        this.orderPrice = new BigDecimal(order.getPrice());
        this.orderBtcAmount = orderAltAmount.multiply(orderPrice);
    }

    public void calculateCurrentPrice(OrderBook depth20) {
        this.currentPrice = depth20.getAsks()
                                   .parallelStream()
                                   .map(OrderBookEntry::getPrice)
                                   .map(BigDecimal::new)
                                   .min(Comparator.naturalOrder())
                                   .orElseThrow(RuntimeException::new);
    }

    public void calculatePriceToSellWithoutProfit() {
        BigDecimal subtract = this.orderPrice.subtract(currentPrice);
        BigDecimal divide = subtract.divide(new BigDecimal("2"), 8, UP);
        this.priceToSellWithoutProfit = currentPrice.add(divide);
    }

    public void calculatePriceToSell() {
        BigDecimal subtract = this.orderPrice.subtract(priceToSellWithoutProfit);
        BigDecimal divide = subtract.divide(new BigDecimal("2"), 8, UP);
        this.priceToSell = priceToSellWithoutProfit.add(divide);
    }

    public void calculatePriceToSellPercentage() {
        BigDecimal percentage = this.priceToSell.multiply(new BigDecimal("100")).divide(this.orderPrice, 8, UP);
        this.priceToSellPercentage = new BigDecimal("100").subtract(percentage);
    }

    public void calculateMinWaitingTime(Function<String, BigDecimal> totalAmountFunction, Map<String, BigDecimal> totalAmounts) {
        String symbol = this.order.getSymbol();
        BigDecimal totalBtcAmount = totalAmounts.merge(symbol,
                                                       totalAmountFunction.apply(symbol),
                                                       (v1, v2) -> v2);
        BigDecimal totalWaitingTime = getTimeFromAmount(totalBtcAmount);
        BigDecimal orderWaitingTime = getTimeFromAmount(orderBtcAmount);
        this.minWaitingTime = totalWaitingTime.add(orderWaitingTime);
    }

    private BigDecimal getTimeFromAmount(BigDecimal totalAmount) {
        double totalTime = 100 * sqrt(totalAmount.doubleValue());
        return new BigDecimal(totalTime, new MathContext(3));
    }

    public void calculateActualWaitingTime() {
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(order.getTime()), ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(date, now);
        actualWaitingTime = new BigDecimal(duration.get(ChronoUnit.SECONDS) / 3600);
    }

    public void calculateActualProfit() {
        BigDecimal profit = priceToSell.subtract(priceToSellWithoutProfit);
        actualProfit = profit.multiply(new BigDecimal(order.getOrigQty()));
    }

    public String print() {
        return "OrderDto{" +
               "order=" + order +
               ", orderBtcAmount=" + orderBtcAmount +
               ", orderPrice=" + orderPrice +
               ", currentPrice=" + currentPrice +
               ", priceToSell=" + priceToSell +
               ", priceToSellPercentage=" + priceToSellPercentage +
               ", priceToSellWithoutProfit=" + priceToSellWithoutProfit +
               ", actualProfit=" + actualProfit +
               ", minWaitingTime=" + minWaitingTime +
               ", actualWaitingTime=" + actualWaitingTime +
               '}';
    }
}
