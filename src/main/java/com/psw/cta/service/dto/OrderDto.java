package com.psw.cta.service.dto;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class OrderDto {

    private final Order order;
    private BigDecimal sumCurrentPriceToSell;
    private BigDecimal averageCurrentPriceToSell;
    private BigDecimal sumAmounts;
    private BigDecimal currentPrice;
    private BigDecimal maxOriginalPriceToSell;
    private BigDecimal priceToSell;
    private BigDecimal percentualDecrease;
    private BigDecimal currentPriceToSellPercentage;
    private BigDecimal idealRatio;
    private BigDecimal priceToSellWithoutProfit;
    private BigDecimal totalBtcAmountToRebuy;

    public OrderDto(Order order) {
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getPriceToSell() {
        return priceToSell;
    }

    public BigDecimal getPercentualDecrease() {
        return percentualDecrease;
    }

    public BigDecimal getIdealRatio() {
        return idealRatio;
    }

    public BigDecimal getPriceToSellWithoutProfit() {
        return priceToSellWithoutProfit;
    }

    public BigDecimal getSumAmounts() {
        return sumAmounts;
    }

    public BigDecimal getMaxOriginalPriceToSell() {
        return maxOriginalPriceToSell;
    }

    public BigDecimal getTotalBtcAmountToRebuy() {
        return totalBtcAmountToRebuy;
    }

    public void calculateSumAmounts(List<Order> openOrders) {
        this.sumAmounts = getSum(openOrders, Order::getOrigQty);
    }

    public void calculateAverageCurrentPrice(List<Order> openOrders) {
        String symbol = this.order.getSymbol();
        long count = openOrders.stream()
                .filter(order -> order.getSymbol().equals(symbol))
                .count();
        this.averageCurrentPriceToSell = getSum(openOrders, Order::getPrice)
                .divide(new BigDecimal(count), 8, BigDecimal.ROUND_UP);
    }

    private BigDecimal getSum(List<Order> openOrders, Function<Order, String> function) {
        String symbol = this.order.getSymbol();
        return openOrders.stream()
                .filter(order -> order.getSymbol().equals(symbol))
                .map(function)
                .map(BigDecimal::new)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void calculateSumCurrentPrice(List<Order> openOrders) {
        this.sumCurrentPriceToSell = getSum(openOrders, Order::getPrice);
    }

    public void calculateMaxOriginalPriceToSell(List<Order> openOrders) {
        String symbol = this.order.getSymbol();
        this.maxOriginalPriceToSell = openOrders.stream()
                .filter(order -> order.getSymbol().equals(symbol))
                .map(Order::getPrice)
                .map(BigDecimal::new)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    public void calculateCurrentPrice(OrderBook depth20) {
        this.currentPrice = depth20.getAsks()
                .parallelStream()
                .map(OrderBookEntry::getPrice)
                .map(BigDecimal::new)
                .min(Comparator.naturalOrder())
                .orElseThrow(RuntimeException::new);
    }

    public void calculatePriceToSell() {
        BigDecimal currentPriceForSell = this.currentPrice.multiply(new BigDecimal("1.01"));
        BigDecimal btcAmountFromOrders = this.sumAmounts.multiply(this.maxOriginalPriceToSell).min(new BigDecimal("0.05"));
        BigDecimal amountAlterToInvest = btcAmountFromOrders.divide(currentPriceForSell, 8, BigDecimal.ROUND_UP);
        BigDecimal totalAlterAmount = amountAlterToInvest.add(this.sumAmounts);
        this.totalBtcAmountToRebuy = btcAmountFromOrders.multiply(new BigDecimal("2"));
        this.priceToSellWithoutProfit = totalBtcAmountToRebuy.divide(totalAlterAmount, 8, BigDecimal.ROUND_UP);
        BigDecimal differenceBetweenMaxAndWithoutProfit = this.maxOriginalPriceToSell.subtract(priceToSellWithoutProfit);
        BigDecimal profit = differenceBetweenMaxAndWithoutProfit.divide(new BigDecimal("2"), 8, BigDecimal.ROUND_UP);
        this.priceToSell = priceToSellWithoutProfit.add(profit);
    }

    public void calculatePercentualDecreaseBetweenPricesToSell() {
        this.percentualDecrease = this.priceToSell.multiply(new BigDecimal("100")).divide(this.priceToSellWithoutProfit, 8, BigDecimal.ROUND_UP)
                .subtract(new BigDecimal("100"));
    }

    public void calculateCurrentPriceToSellPercentage() {
        BigDecimal percentage = this.priceToSellWithoutProfit.multiply(new BigDecimal("100")).divide(this.currentPrice, 8, BigDecimal.ROUND_UP);
        this.currentPriceToSellPercentage = percentage.subtract(new BigDecimal("100"));
    }

    public void calculateIdealRatio() {
        if (this.currentPriceToSellPercentage.compareTo(BigDecimal.ZERO) != 0) {
            this.idealRatio = this.percentualDecrease.divide(currentPriceToSellPercentage, 8, BigDecimal.ROUND_UP);
        } else {
            this.idealRatio = BigDecimal.ZERO;
        }
    }

    public String print() {
        return "OrderDto{" +
                "order=" + order +
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
