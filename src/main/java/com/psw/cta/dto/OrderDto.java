package com.psw.cta.dto;

import com.binance.api.client.domain.account.Order;
import java.math.BigDecimal;

public class OrderDto {

    private final Order order;
    private BigDecimal orderBtcAmount;
    private BigDecimal orderPrice;
    private BigDecimal currentPrice;
    private BigDecimal priceToSell;
    private BigDecimal priceToSellPercentage;
    private BigDecimal priceToSellWithoutProfit;
    private BigDecimal minWaitingTime = BigDecimal.ZERO;
    private BigDecimal actualWaitingTime = BigDecimal.ZERO;


    public OrderDto(Order order) {
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }

    public BigDecimal getOrderBtcAmount() {
        return orderBtcAmount;
    }

    public void setOrderBtcAmount(BigDecimal orderBtcAmount) {
        this.orderBtcAmount = orderBtcAmount;
    }

    public BigDecimal getOrderPrice() {
        return orderPrice;
    }

    public void setOrderPrice(BigDecimal orderPrice) {
        this.orderPrice = orderPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getPriceToSell() {
        return priceToSell;
    }

    public void setPriceToSell(BigDecimal priceToSell) {
        this.priceToSell = priceToSell;
    }

    public BigDecimal getPriceToSellPercentage() {
        return priceToSellPercentage;
    }

    public void setPriceToSellPercentage(BigDecimal priceToSellPercentage) {
        this.priceToSellPercentage = priceToSellPercentage;
    }

    public void setPriceToSellWithoutProfit(BigDecimal priceToSellWithoutProfit) {
        this.priceToSellWithoutProfit = priceToSellWithoutProfit;
    }

    public BigDecimal getMinWaitingTime() {
        return minWaitingTime;
    }

    public void setMinWaitingTime(BigDecimal minWaitingTime) {
        this.minWaitingTime = minWaitingTime;
    }

    public BigDecimal getActualWaitingTime() {
        return actualWaitingTime;
    }

    public void setActualWaitingTime(BigDecimal actualWaitingTime) {
        this.actualWaitingTime = actualWaitingTime;
    }

    @Override public String toString() {
        return "OrderDto{" +
               "order=" + order +
               ", orderBtcAmount=" + orderBtcAmount +
               ", orderPrice=" + orderPrice +
               ", currentPrice=" + currentPrice +
               ", priceToSell=" + priceToSell +
               ", priceToSellPercentage=" + priceToSellPercentage +
               ", priceToSellWithoutProfit=" + priceToSellWithoutProfit +
               ", minWaitingTime=" + minWaitingTime +
               ", actualWaitingTime=" + actualWaitingTime.toPlainString() +
               ", remainingWaitingTime=" + minWaitingTime.subtract(actualWaitingTime).toPlainString() +
               '}';
    }
}
