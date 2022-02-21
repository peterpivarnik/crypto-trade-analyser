package com.psw.cta.service;

import static com.binance.api.client.domain.general.FilterType.MAX_NUM_ORDERS;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MAX_ORDER_BTC_AMOUNT;
import static com.psw.cta.utils.OrderUtils.getQuantityFromOrder;
import static java.util.Collections.emptyList;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
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

    public synchronized List<Crypto> repeatTrade(SymbolInfo symbolInfo,
                                                 OrderWrapper orderWrapper,
                                                 BigDecimal currentNumberOfOpenOrdersBySymbol,
                                                 Supplier<List<Crypto>> cryptosSupplier,
                                                 Map<String, BigDecimal> totalAmounts,
                                                 ExchangeInfo exchangeInfo) {
        logger.log("Rebuying: symbol=" + symbolInfo.getSymbol());
        logger.log("currentNumberOfOpenOrdersBySymbol=" + currentNumberOfOpenOrdersBySymbol);
        BigDecimal maxSymbolOpenOrders = getValueFromFilter(symbolInfo, MAX_NUM_ORDERS, SymbolFilter::getMaxNumOrders);
        if ((orderWrapper.getOrderBtcAmount().compareTo(MAX_ORDER_BTC_AMOUNT) > 0) && currentNumberOfOpenOrdersBySymbol.compareTo(maxSymbolOpenOrders) < 0) {
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
        BigDecimal quantityToSell = getQuantityFromOrder(orderWrapper.getOrder());
        BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
        binanceApiService.placeSellOrder(symbolInfo, orderWrapper.getPriceToSell(), completeQuantityToSell);
    }
}
