package com.psw.cta.service.dto;

import com.binance.api.client.domain.account.Order;
import java.math.BigDecimal;

public class OrderDto {

    private Order order;
    private BigDecimal sumCurrentPriceToSell;
    private BigDecimal averageCurrentPriceToSell;
    private BigDecimal sumAmounts;
    private BigDecimal currentPrice;
    private BigDecimal maxOriginalPriceToSell;
    private BigDecimal currentPriceDecreasedPercentage;
    private BigDecimal priceToSell;
    private BigDecimal percentualDecrease;
    private BigDecimal currentPriceToSellPercentage;
    private BigDecimal idealRatio;

    public OrderDto(Order order) {
        this.order = order;
    }

    public BigDecimal getSumCurrentPriceToSell() {
        return sumCurrentPriceToSell;
    }

    public void setSumCurrentPriceToSell(BigDecimal sumCurrentPriceToSell) {
        this.sumCurrentPriceToSell = sumCurrentPriceToSell;
    }

    public BigDecimal getAverageCurrentPriceToSell() {
        return averageCurrentPriceToSell;
    }

    public void setAverageCurrentPriceToSell(BigDecimal averageCurrentPriceToSell) {
        this.averageCurrentPriceToSell = averageCurrentPriceToSell;
    }

    public BigDecimal getSumAmounts() {
        return sumAmounts;
    }

    public void setSumAmounts(BigDecimal sumAmounts) {
        this.sumAmounts = sumAmounts;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getCurrentPriceDecreasedPercentage() {
        return currentPriceDecreasedPercentage;
    }

    public void setCurrentPriceDecreasedPercentage(BigDecimal currentPriceDecreasedPercentage) {
        this.currentPriceDecreasedPercentage = currentPriceDecreasedPercentage;
    }

    public BigDecimal getPriceToSell() {
        return priceToSell;
    }

    public void setPriceToSell(BigDecimal priceToSell) {
        this.priceToSell = priceToSell;
    }

    public BigDecimal getPercentualDecrease() {
        return percentualDecrease;
    }

    public void setPercentualDecrease(BigDecimal percentualDecrease) {
        this.percentualDecrease = percentualDecrease;
    }

    public BigDecimal getCurrentPriceToSellPercentage() {
        return currentPriceToSellPercentage;
    }

    public void setCurrentPriceToSellPercentage(BigDecimal currentPriceToSellPercentage) {
        this.currentPriceToSellPercentage = currentPriceToSellPercentage;
    }

    public BigDecimal getIdealRatio() {
        return idealRatio;
    }

    public void setIdealRatio(BigDecimal idealRatio) {
        this.idealRatio = idealRatio;
    }

    public BigDecimal getMaxOriginalPriceToSell() {
        return maxOriginalPriceToSell;
    }

    public void setMaxOriginalPriceToSell(BigDecimal maxOriginalPriceToSell) {
        this.maxOriginalPriceToSell = maxOriginalPriceToSell;
    }

    public String print() {
        return "OrderDto{" +
            "order=" + order +
            ", currentPriceDecreasedPercentage=" + currentPriceDecreasedPercentage +
            ", sumCurrentPriceToSell=" + sumCurrentPriceToSell +
            ", maxOriginalPriceToSell=" + maxOriginalPriceToSell +
            ", sumAmounts=" + sumAmounts +
            ", percentualDecrease=" + percentualDecrease +
            ", currentPriceToSellPercentage=" + currentPriceToSellPercentage +
            ", averageCurrentPriceToSell=" + averageCurrentPriceToSell +
            ", currentPrice=" + currentPrice +
            ", priceToSell=" + priceToSell +
            ", idealRatio=" + idealRatio +
            '}';
    }
}
