package com.psw.cta.processor.trade;

import static java.math.BigDecimal.ONE;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Service to extract order in two orders.
 */
public class ExtractProcessor {

  private final BinanceService binanceService;
  private final LambdaLogger logger;

  public ExtractProcessor(BinanceService binanceService, LambdaLogger logger) {
    this.binanceService = binanceService;
    this.logger = logger;
  }


  /**
   * Extracts two orders from order with lowest order price percentage.
   *
   * @param orderWrappers all orders
   * @param myBtcBalance  my actual balance in BTC
   * @param exchangeInfo  exchange info
   */
  public void extractOrderWithLowestOrderPrice(List<OrderWrapper> orderWrappers,
                                               BigDecimal myBtcBalance,
                                               ExchangeInfo exchangeInfo) {
    logger.log("***** ***** Extracting orders ***** *****");
    orderWrappers.stream()
                 .filter(orderWrapper -> orderWrapper.getOrderBtcAmount().compareTo(new BigDecimal("0.0005")) > 0)
                 .filter(orderWrapper -> orderWrapper.getOrderPricePercentage().compareTo(new BigDecimal("20")) < 0)
                 .sorted(Comparator.comparing(OrderWrapper::getOrderPricePercentage))
                 .limit(getNumberOfOrdersToExtract(myBtcBalance))
                 .forEach(order -> extractOrder(order, exchangeInfo));
  }

  private int getNumberOfOrdersToExtract(BigDecimal myBtcBalance) {
    return myBtcBalance.multiply(new BigDecimal("1000"))
                       .max(ONE)
                       .intValue();
  }

  private void extractOrder(OrderWrapper orderToExtract, ExchangeInfo exchangeInfo) {
    binanceService.cancelOrder(orderToExtract);
    SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(orderToExtract.getOrder().getSymbol());
    binanceService.extractTwoSellOrders(symbolInfo, orderToExtract);
  }
}
