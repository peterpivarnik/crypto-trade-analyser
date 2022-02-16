package com.psw.cta.service;

import static com.binance.api.client.domain.general.FilterType.MAX_NUM_ORDERS;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.OrderUtils.calculateActualWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateMinWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateOrderBtcAmount;
import static com.psw.cta.utils.OrderUtils.calculateOrderPrice;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSell;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSellWithoutProfit;
import static com.psw.cta.utils.OrderUtils.getQuantityFromOrder;
import static java.util.Collections.emptyList;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.utils.OrderUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class RepeatTradingService {

    private final DiversifyService diversifyService;
    private final BinanceApiService binanceApiService;
    private final LambdaLogger logger;

    public RepeatTradingService(DiversifyService diversifyService, BinanceApiService binanceApiService, LambdaLogger logger) {
        this.diversifyService = diversifyService;
        this.binanceApiService = binanceApiService;
        this.logger = logger;
    }

    public OrderWrapper createOrderWrapper(Order order) {
        BigDecimal orderPrice = calculateOrderPrice(order);
        BigDecimal orderBtcAmount = calculateOrderBtcAmount(order, orderPrice);
        OrderWrapper orderWrapper = new OrderWrapper(order);
        orderWrapper.setOrderPrice(orderPrice);
        orderWrapper.setOrderBtcAmount(orderBtcAmount);
        return orderWrapper;
    }

    public OrderWrapper updateOrderWrapperWithWaitingTimes(Map<String, BigDecimal> totalAmounts, OrderWrapper orderWrapper) {
        BigDecimal minWaitingTime = calculateMinWaitingTime(totalAmounts.get(orderWrapper.getOrder().getSymbol()), orderWrapper.getOrderBtcAmount());
        BigDecimal actualWaitingTime = calculateActualWaitingTime(orderWrapper.getOrder());
        orderWrapper.setMinWaitingTime(minWaitingTime);
        orderWrapper.setActualWaitingTime(actualWaitingTime);
        return orderWrapper;
    }

    public OrderWrapper updateOrderWrapperWithPrices(OrderWrapper orderWrapper) {
        BigDecimal orderPrice = orderWrapper.getOrderPrice();
        BigDecimal currentPrice = OrderUtils.calculateCurrentPrice(binanceApiService.getDepth(orderWrapper.getOrder().getSymbol()));
        BigDecimal priceToSellWithoutProfit = calculatePriceToSellWithoutProfit(orderPrice, currentPrice);
        BigDecimal priceToSell = calculatePriceToSell(orderPrice, priceToSellWithoutProfit, orderWrapper.getOrderBtcAmount());
        BigDecimal priceToSellPercentage = OrderUtils.calculatePriceToSellPercentage(currentPrice, priceToSell);
        BigDecimal orderPricePercentage = OrderUtils.calculateOrderPricePercentage(currentPrice, orderPrice);
        orderWrapper.setCurrentPrice(currentPrice);
        orderWrapper.setPriceToSellWithoutProfit(priceToSellWithoutProfit);
        orderWrapper.setPriceToSell(priceToSell);
        orderWrapper.setPriceToSellPercentage(priceToSellPercentage);
        orderWrapper.setOrderPricePercentage(orderPricePercentage);
        return orderWrapper;
    }

    public synchronized List<Crypto> repeatTrade(SymbolInfo symbolInfo,
                                                 OrderWrapper orderWrapper,
                                                 BigDecimal currentNumberOfOpenOrdersBySymbol,
                                                 Supplier<List<Crypto>> cryptosSupplier,
                                                 Map<String, BigDecimal> totalAmounts,
                                                 ExchangeInfo exchangeInfo) {
        logger.log("Rebuying: symbol=" + symbolInfo.getSymbol());
        logger.log("currentNumberOfOpenOrdersBySymbol=" + currentNumberOfOpenOrdersBySymbol);
        BigDecimal maxSymbolOpenOrders = getValueFromFilter(symbolInfo, MAX_NUM_ORDERS, SymbolFilter::getMaxNumOrders);
        if ((orderWrapper.getOrderBtcAmount().compareTo(new BigDecimal("0.02")) > 0) && currentNumberOfOpenOrdersBySymbol.compareTo(maxSymbolOpenOrders) < 0) {
            return diversifyService.diversify(orderWrapper, cryptosSupplier, totalAmounts, exchangeInfo);
        } else {
            rebuySingleOrder(symbolInfo, orderWrapper);
            return emptyList();
        }
    }

    private void rebuySingleOrder(SymbolInfo symbolInfo, OrderWrapper orderWrapper) {
        logger.log("OrderWrapper: " + orderWrapper);
        BigDecimal mybtcBalance = binanceApiService.getMyBalance(ASSET_BTC);
        if (mybtcBalance.compareTo(orderWrapper.getOrderBtcAmount()) < 0) {
            logger.log("BTC balance too low, skip rebuy of crypto.");
            return;
        }
        // 1. cancel existing order
        binanceApiService.cancelRequest(orderWrapper);
        // 2. buy
        BigDecimal orderBtcAmount = orderWrapper.getOrderBtcAmount();
        BigDecimal orderPrice = orderWrapper.getOrderPrice();
        binanceApiService.buy(symbolInfo, orderBtcAmount, orderPrice);

        // 3. create new order
        BigDecimal quantityToSell = getQuantityFromOrder(orderWrapper);
        BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
        binanceApiService.placeSellOrder(symbolInfo, orderWrapper.getPriceToSell(), completeQuantityToSell);
    }
}
