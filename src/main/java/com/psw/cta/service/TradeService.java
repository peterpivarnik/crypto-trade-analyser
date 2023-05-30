package com.psw.cta.service;

import static com.psw.cta.utils.CommonUtils.getOrderComparator;
import static com.psw.cta.utils.OrderWrapperBuilder.withPrices;
import static com.psw.cta.utils.OrderWrapperBuilder.withWaitingTimes;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.utils.OrderWrapperBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Trade service.
 */
public abstract class TradeService {

  protected final BinanceApiService binanceApiService;

  protected TradeService(BinanceApiService binanceApiService) {
    this.binanceApiService = binanceApiService;
  }

  abstract void trade(List<Order> openOrders,
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
                                                     binanceApiService.getOrderBook(orderWrapper.getOrder()
                                                                                                .getSymbol()),
                                                     exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol()),
                                                     myBtcBalance,
                                                     actualBalance));
  }

  public abstract Boolean cancelTrade(OrderWrapper orderWrapper, ExchangeInfo exchangeInfo);
}
