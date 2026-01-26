package com.psw.cta.processor.trade;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Service to cancel trade.
 */
public class CancelProcessor {

    private final BinanceService binanceService;
    private final LambdaLogger logger;

    /**
     * Default constructor.
     *
     * @param binanceService service for {@link BinanceApi}
     * @param logger         logger
     */
    public CancelProcessor(BinanceService binanceService, LambdaLogger logger) {
        this.binanceService = binanceService;
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
     * Cancel trade for symbols.
     *
     * @param symbolsToCancel symbols to be canceled
     * @param orderWrappers   all orders
     * @param exchangeInfo    exchange info
     */
    public void cancelTrade(Set<String> symbolsToCancel,
                            List<OrderWrapper> orderWrappers,
                            ExchangeInfo exchangeInfo) {
        logger.log("***** ***** Cancel orders for symbols: " + symbolsToCancel + " ***** *****");
        orderWrappers.stream()
                     .filter(orderWrapper -> symbolsToCancel.contains(orderWrapper.getOrder().getSymbol()))
                     .forEach(orderWrapper -> cancelAndSell(orderWrapper, exchangeInfo));

    }

    private void cancelAndSell(OrderWrapper orderToCancel, ExchangeInfo exchangeInfo) {
        // 1. cancel existing order
        binanceService.cancelOrder(orderToCancel);

        // 2. sell cancelled order
        BigDecimal currentQuantity = orderToCancel.getQuantity();
        SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
        binanceService.sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);
    }
}
