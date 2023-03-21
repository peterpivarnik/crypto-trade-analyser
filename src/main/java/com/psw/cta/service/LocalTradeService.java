package com.psw.cta.service;

import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.psw.cta.dto.OrderWrapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Trade service for local environment.
 */
public class LocalTradeService extends TradeService {

  private final LambdaLogger logger;

  public LocalTradeService(BinanceApiService binanceApiService, LambdaLogger logger) {
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
}
