package com.psw.cta.service;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.adapter.BinanceAdapter;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.SymbolInfo;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Service to cancel trade.
 */
public class CancelService {

    private final BinanceAdapter binanceAdapter;
    private final LambdaLogger logger;

    /**
     * Default constructor.
     *
     * @param binanceAdapter service for {@link BinanceApi}
     * @param logger         logger
     */
    public CancelService(BinanceAdapter binanceAdapter, LambdaLogger logger) {
        this.binanceAdapter = binanceAdapter;
        this.logger = logger;
    }

    /**
     * Cancel trade.
     *
     * @param orderWrappers all orders
     * @param exchangeInfo  exchange info
     */
    public void cancelTrade(List<OrderWrapper> orderWrappers, ExchangeInfo exchangeInfo) {
        logger.log(
            "***** ***** Cancel biggest order due all orders having negative remaining waiting time ***** *****");
        orderWrappers.stream()
                     .max(Comparator.comparing(OrderWrapper::getCurrentBtcAmount))
                     .ifPresent(orderWrapper -> cancelAndSell(orderWrapper, exchangeInfo));
    }

    /**
     * Cancel an existing order and sell the cancelled order's quantity.
     *
     * @param orderToCancel the order to cancel
     * @param exchangeInfo  exchange info containing symbol information
     */
    public void cancelAndSell(OrderWrapper orderToCancel, ExchangeInfo exchangeInfo) {
        // 1. cancel existing order
        binanceAdapter.cancelOrder(orderToCancel);

        // 2. sell cancelled order
        BigDecimal currentQuantity = orderToCancel.getQuantity();
        SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
        binanceAdapter.sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);
    }
}
