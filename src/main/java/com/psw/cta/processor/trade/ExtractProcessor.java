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
import java.util.Optional;

/**
 * Service to extract order in two orders.
 */
public class ExtractProcessor {

    private final BinanceService binanceService;
    private final LambdaLogger logger;

    /**
     * Default constructor.
     *
     * @param binanceService service providing functionality for {@link BinanceApi}
     * @param logger         logger
     */
    public ExtractProcessor(BinanceService binanceService, LambdaLogger logger) {
        this.binanceService = binanceService;
        this.logger = logger;
    }


    /**
     * Extracts two orders from more orders.
     *
     * @param orderWrappers all orders
     * @param myBtcBalance  my actual balance in BTC
     * @param exchangeInfo  exchange info
     */
    public void extractOrders(List<OrderWrapper> orderWrappers,
                              BigDecimal myBtcBalance,
                              ExchangeInfo exchangeInfo) {
        logger.log("***** ***** Extracting orders ***** *****");
        orderWrappers.stream()
                     .filter(ExtractProcessor::canBeExtracted)
                     .sorted(Comparator.comparing(OrderWrapper::getOrderPricePercentage))
                     .limit(getNumberOfOrdersToExtract(myBtcBalance))
                     .forEach(order -> extractOrder(order, exchangeInfo));
    }

    /**
     * Checks whether the order can be extracted when more orders are being extracted.
     *
     * @param orderWrapper order to evaluate
     * @return true if order is big enough and its order price percentage is low enough
     */
    public static boolean canBeExtracted(OrderWrapper orderWrapper) {
        return orderWrapper.getOrderBtcAmount().compareTo(new BigDecimal("0.0005")) > 0
               && orderWrapper.getOrderPricePercentage().compareTo(new BigDecimal("20")) < 0;
    }

    private int getNumberOfOrdersToExtract(BigDecimal myBtcBalance) {
        return myBtcBalance.multiply(new BigDecimal("500"))
                           .intValue();
    }

    /**
     * Extracts two orders from one order.
     *
     * @param orderWrappers all orders
     * @param exchangeInfo  exchange info
     */
    public void extractOnlyFirstOrder(List<OrderWrapper> orderWrappers, ExchangeInfo exchangeInfo) {
        logger.log("***** ***** Extracting first order ***** *****");
        getFirstOrderToExtract(orderWrappers).ifPresent(order -> extractOrder(order, exchangeInfo));
    }

    /**
     * Checks whether the first order can be extracted.
     *
     * @param orderWrappers all orders
     * @return true if order with the lowest order price percentage is big enough
     */
    public static boolean canExtractFirstOrder(List<OrderWrapper> orderWrappers) {
        return getFirstOrderToExtract(orderWrappers).isPresent();
    }

    private static Optional<OrderWrapper> getFirstOrderToExtract(List<OrderWrapper> orderWrappers) {
        return orderWrappers.stream()
                            .min(Comparator.comparing(OrderWrapper::getOrderPricePercentage))
                            .filter(orderWrapper -> orderWrapper.getOrderBtcAmount()
                                                                .compareTo(new BigDecimal("0.0003")) > 0);
    }

    private void extractOrder(OrderWrapper orderToExtract, ExchangeInfo exchangeInfo) {
        binanceService.cancelOrder(orderToExtract);
        SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(orderToExtract.getOrder().getSymbol());
        binanceService.extractTwoSellOrders(symbolInfo, orderToExtract);
    }
}
