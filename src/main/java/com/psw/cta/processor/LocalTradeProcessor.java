package com.psw.cta.processor;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Trade service for local environment.
 */
public class LocalTradeProcessor extends MainTradeProcessor {

    private final LambdaLogger logger;

    /**
     * Default constructor.
     *
     * @param binanceService service for {@link BinanceApi}
     * @param logger         logger
     */
    public LocalTradeProcessor(BinanceService binanceService, LambdaLogger logger) {
        super(binanceService);
        this.logger = logger;
    }

    @Override
    public void trade(List<Order> openOrders,
                      Map<String, BigDecimal> totalAmounts,
                      BigDecimal myBtcBalance,
                      BigDecimal actualBalance,
                      ExchangeInfo exchangeInfo,
                      long uniqueOpenOrdersSize,
                      BigDecimal totalAmount,
                      int minOpenOrders) {

        BigDecimal neededBtcAmount = getOrderWrapperStream(openOrders, myBtcBalance, actualBalance, totalAmounts)
            .peek(orderWrapper -> logger.log(orderWrapper.toString()))
            .map(OrderWrapper::getNeededBtcAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        logger.log("Needed btc amount from all orders: " + neededBtcAmount);
    }
}
