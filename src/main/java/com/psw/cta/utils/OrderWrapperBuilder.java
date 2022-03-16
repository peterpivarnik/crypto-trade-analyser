package com.psw.cta.utils;

import static com.psw.cta.utils.CommonUtils.calculatePricePercentage;
import static com.psw.cta.utils.CommonUtils.getCurrentPrice;
import static com.psw.cta.utils.OrderUtils.calculateActualWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateMinWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateOrderBtcAmount;
import static com.psw.cta.utils.OrderUtils.calculateOrderPrice;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSell;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.OrderBook;
import com.psw.cta.dto.OrderWrapper;
import java.math.BigDecimal;
import java.util.Map;

public class OrderWrapperBuilder {

    public static OrderWrapper build(Order order) {
        BigDecimal orderPrice = calculateOrderPrice(order);
        BigDecimal orderBtcAmount = calculateOrderBtcAmount(order, orderPrice);
        OrderWrapper orderWrapper = new OrderWrapper(order);
        orderWrapper.setOrderPrice(orderPrice);
        orderWrapper.setOrderBtcAmount(orderBtcAmount);
        return orderWrapper;
    }

    public static OrderWrapper withWaitingTimes(Map<String, BigDecimal> totalAmounts, OrderWrapper orderWrapper) {
        BigDecimal minWaitingTime = calculateMinWaitingTime(totalAmounts.get(orderWrapper.getOrder().getSymbol()), orderWrapper.getOrderBtcAmount());
        BigDecimal actualWaitingTime = calculateActualWaitingTime(orderWrapper.getOrder());
        orderWrapper.setMinWaitingTime(minWaitingTime);
        orderWrapper.setActualWaitingTime(actualWaitingTime);
        return orderWrapper;
    }

    public static OrderWrapper withPrices(OrderWrapper orderWrapper,
                                          OrderBook orderBook,
                                          SymbolInfo symbolInfo,
                                          BigDecimal btcBalanceToTotalBalanceRatio) {
        BigDecimal orderPrice = orderWrapper.getOrderPrice();
        BigDecimal currentPrice = getCurrentPrice(orderBook);
        BigDecimal priceToSell = calculatePriceToSell(orderPrice, currentPrice, orderWrapper.getOrderBtcAmount(), symbolInfo, btcBalanceToTotalBalanceRatio);
        BigDecimal priceToSellPercentage = calculatePricePercentage(currentPrice, priceToSell);
        BigDecimal orderPricePercentage = calculatePricePercentage(currentPrice, orderPrice);
        orderWrapper.setCurrentPrice(currentPrice);
        orderWrapper.setPriceToSell(priceToSell);
        orderWrapper.setPriceToSellPercentage(priceToSellPercentage);
        orderWrapper.setOrderPricePercentage(orderPricePercentage);
        return orderWrapper;
    }
}
