package com.psw.cta.facade;

import static com.psw.cta.utils.CommonUtils.getOrderComparator;
import static com.psw.cta.utils.OrderWrapperBuilder.withPrices;
import static com.psw.cta.utils.OrderWrapperBuilder.withWaitingTimes;

import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.service.BinanceService;
import com.psw.cta.utils.OrderWrapperBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Trade service.
 */
public abstract class TradeFacade {

  protected final BinanceService binanceService;

  protected TradeFacade(BinanceService binanceService) {
    this.binanceService = binanceService;
  }

  public abstract void trade(List<Order> openOrders,
                             Map<String, BigDecimal> totalAmounts,
                             BigDecimal myBtcBalance,
                             BigDecimal totalAmount,
                             int minOpenOrders,
                             ExchangeInfo exchangeInfo,
                             long uniqueOpenOrdersSize,
                             BigDecimal actualBalance);

  protected Stream<OrderWrapper> getOrderWrapperStream(List<Order> openOrders,
                                                       Map<String, BigDecimal> totalAmounts,
                                                       BigDecimal myBtcBalance,
                                                       ExchangeInfo exchangeInfo,
                                                       BigDecimal actualBalance) {
    return openOrders.stream()
                     .map(Order::getSymbol)
                     .distinct()
                     .map(symbol -> openOrders.parallelStream()
                                              .filter(order -> order.getSymbol().equals(symbol))
                                              .min(getOrderComparator()))
                     .map(Optional::orElseThrow)
                     .map(OrderWrapperBuilder::build)
                     .map(orderWrapper -> withWaitingTimes(totalAmounts, orderWrapper))
                     .map(orderWrapper -> withPrices(orderWrapper,
                                                     binanceService.getOrderBook(orderWrapper.getOrder().getSymbol(),
                                                                                 20),
                                                     exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol()),
                                                     myBtcBalance,
                                                     actualBalance));
  }

  public abstract Boolean cancelTrade(OrderWrapper orderWrapper, ExchangeInfo exchangeInfo);
}
