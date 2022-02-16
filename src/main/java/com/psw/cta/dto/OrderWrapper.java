package com.psw.cta.dto;

import com.binance.api.client.domain.account.Order;
import java.math.BigDecimal;

public class OrderWrapper {

    private final Order order;
    private BigDecimal orderBtcAmount;
    private BigDecimal orderPrice;
    private BigDecimal currentPrice;
    private BigDecimal priceToSell;
    private BigDecimal priceToSellPercentage;
    private BigDecimal orderPricePercentage;
    private BigDecimal priceToSellWithoutProfit;
    private BigDecimal minWaitingTime = BigDecimal.ZERO;
    private BigDecimal actualWaitingTime = BigDecimal.ZERO;


    public OrderWrapper(Order order) {
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

    public BigDecimal getOrderPricePercentage() {
        return orderPricePercentage;
    }

    public void setOrderPricePercentage(BigDecimal orderPricePercentage) {
        this.orderPricePercentage = orderPricePercentage;
    }

    public BigDecimal getPriceToSellWithoutProfit() {
        return priceToSellWithoutProfit;
    }

    @Override public String toString() {
        return "OrderWrapper{" +
               "order=" + order +
               ", orderBtcAmount=" + orderBtcAmount +
               ", orderPrice=" + orderPrice +
               ", currentPrice=" + currentPrice +
               ", priceToSell=" + priceToSell +
               ", priceToSellPercentage=" + priceToSellPercentage +
               ", orderPricePercentage=" + orderPricePercentage +
               ", minWaitingTime=" + minWaitingTime +
               ", actualWaitingTime=" + actualWaitingTime +
               ", remainingWaitingTime=" + minWaitingTime.subtract(actualWaitingTime).toPlainString() +
               '}';
    }
}
