package com.psw.cta.processor;

import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.service.BinanceService;
import com.psw.cta.utils.CommonUtils;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.psw.cta.utils.CommonUtils.getQuantity;
import static java.util.Comparator.comparing;

/**
 * Trade service.
 */
public abstract class MainTradeProcessor {

  protected final BinanceService binanceService;

  protected MainTradeProcessor(BinanceService binanceService) {
    this.binanceService = binanceService;
  }

  public abstract void trade(List<Order> openOrders,
                             Map<String, BigDecimal> totalAmounts,
                             ExchangeInfo exchangeInfo,
                             BigDecimal myBtcBalance,
                             BigDecimal actualBalance,
                             long uniqueOpenOrdersSize,
                             BigDecimal totalAmount,
                             int minOpenOrders);

  protected Stream<OrderWrapper> getOrderWrapperStream(List<Order> openOrders,
                                                       ExchangeInfo exchangeInfo,
                                                       BigDecimal myBtcBalance,
                                                       BigDecimal actualBalance,
                                                       Map<String, BigDecimal> totalAmounts) {
    return openOrders.stream()
                     .map(Order::getSymbol)
                     .distinct()
                     .map(symbol -> openOrders.parallelStream()
                                              .filter(order -> order.getSymbol().equals(symbol))
                                              .min(getOrderComparator()))
                     .map(Optional::orElseThrow)
                     .map(order -> createOrderWrapper(order, exchangeInfo, myBtcBalance, actualBalance, totalAmounts));
  }

  private Comparator<Order> getOrderComparator() {
    Function<Order, BigDecimal> quantityFunction = CommonUtils::getQuantity;
    Function<Order, BigDecimal>
        btcAmountFunction = order -> (getQuantity(order)).multiply(new BigDecimal(order.getPrice()));
    Function<Order, BigDecimal> timeFunction = order -> new BigDecimal(order.getTime());
    return comparing(quantityFunction).reversed()
                                      .thenComparing(comparing(btcAmountFunction).reversed())
                                      .thenComparing(timeFunction);
  }

  private OrderWrapper createOrderWrapper(Order order,
                                          ExchangeInfo exchangeInfo,
                                          BigDecimal myBtcBalance,
                                          BigDecimal actualBalance,
                                          Map<String, BigDecimal> totalAmounts) {
    BigDecimal currentPrice = binanceService.getCurrentPrice(order.getSymbol());
    SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(order.getSymbol());
    return new OrderWrapper(order, currentPrice, symbolInfo, myBtcBalance, actualBalance, totalAmounts);
  }
}
