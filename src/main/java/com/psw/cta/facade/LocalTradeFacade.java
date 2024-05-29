package com.psw.cta.facade;

import static java.lang.Boolean.FALSE;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
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
public class LocalTradeFacade extends TradeFacade {

  private final LambdaLogger logger;

  public LocalTradeFacade(BinanceService binanceApiService, LambdaLogger logger) {
    super(binanceApiService);
    this.logger = logger;
  }

  @Override
  public void trade(List<Order> openOrders,
                    Map<String, BigDecimal> totalAmounts,
                    BigDecimal myBtcBalance,
                    BigDecimal totalAmount,
                    int minOpenOrders,
                    ExchangeInfo exchangeInfo,
                    long uniqueOpenOrdersSize,
                    BigDecimal actualBalance) {

    getOrderWrapperStream(openOrders, totalAmounts, myBtcBalance, exchangeInfo, actualBalance)
        .sorted(comparing(OrderWrapper::getOrderPricePercentage))
        .forEach(orderWrapper -> logger.log(orderWrapper.toString()));
  }

  @Override
  public Boolean cancelTrade(OrderWrapper orderWrapper, ExchangeInfo exchangeInfo) {
    return FALSE;
  }
}
