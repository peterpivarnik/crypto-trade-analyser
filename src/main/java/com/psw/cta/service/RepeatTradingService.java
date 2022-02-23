package com.psw.cta.service;

import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.Constants.ASSET_BTC;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.dto.OrderWrapper;
import java.math.BigDecimal;

public class RepeatTradingService {

    private final BinanceApiService binanceApiService;
    private final LambdaLogger logger;

    public RepeatTradingService(BinanceApiService binanceApiService, LambdaLogger logger) {
        this.binanceApiService = binanceApiService;
        this.logger = logger;
    }

    public synchronized void rebuySingleOrder(SymbolInfo symbolInfo, OrderWrapper orderWrapper) {
        logger.log("***** ***** Repeat trading ***** *****");
        logger.log("OrderWrapper: " + orderWrapper);
        BigDecimal mybtcBalance = binanceApiService.getMyBalance(ASSET_BTC);
        if (mybtcBalance.compareTo(orderWrapper.getOrderBtcAmount()) < 0) {
            logger.log("BTC balance too low, skip rebuy of crypto.");
            return ;
        }
        // 1. cancel existing order
        binanceApiService.cancelRequest(orderWrapper);
        // 2. buy
        BigDecimal orderBtcAmount = orderWrapper.getOrderBtcAmount();
        BigDecimal orderPrice = orderWrapper.getOrderPrice();
        binanceApiService.buy(symbolInfo, orderBtcAmount, orderPrice);

        // 3. create new order
        BigDecimal quantityToSell = getQuantity(orderWrapper.getOrder());
        BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
        binanceApiService.placeSellOrder(symbolInfo, orderWrapper.getPriceToSell(), completeQuantityToSell);
    }
}
