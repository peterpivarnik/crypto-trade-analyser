package com.psw.cta.processor.trade;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Service to cancel trade.
 */
public class CancelProcessor {

  private final BinanceService binanceService;
  private final LambdaLogger logger;

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
    orderWrappers.stream()
                 .max(Comparator.comparing(OrderWrapper::getCurrentBtcAmount))
                 .ifPresent(orderWrapper -> cancelAndSell(orderWrapper, exchangeInfo));
  }

  private void cancelAndSell(OrderWrapper orderToCancel, ExchangeInfo exchangeInfo) {
    logger.log("***** ***** Cancel biggest order due all orders having negative remaining waiting time ***** *****");
    // 1. cancel existing order
    binanceService.cancelOrder(orderToCancel);

    // 2. sell cancelled order
    BigDecimal currentQuantity = orderToCancel.getQuantity();
    SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
    binanceService.sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);
  }
}
