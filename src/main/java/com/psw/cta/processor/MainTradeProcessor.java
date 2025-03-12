package com.psw.cta.processor;

import static com.psw.cta.dto.OrderWrapper.calculatePricePercentage;
import static java.time.Duration.between;
import static java.time.Instant.ofEpochMilli;
import static java.time.LocalDateTime.now;
import static java.time.LocalDateTime.ofInstant;
import static java.time.ZoneId.systemDefault;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Comparator.comparing;

import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

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
                             BigDecimal myBtcBalance,
                             BigDecimal actualBalance,
                             ExchangeInfo exchangeInfo,
                             long uniqueOpenOrdersSize,
                             BigDecimal totalAmount,
                             int minOpenOrders);

  protected Stream<OrderWrapper> getOrderWrapperStream(List<Order> openOrders,
                                                       BigDecimal myBtcBalance,
                                                       BigDecimal actualBalance,
                                                       Map<String, BigDecimal> totalAmounts) {
    return openOrders.stream()
                     .map(Order::getSymbol)
                     .distinct()
                     .map(symbol -> openOrders.parallelStream()
                                              .filter(order -> order.getSymbol().equals(symbol))
                                              .min(comparing(order -> new BigDecimal(order.getPrice()))))
                     .map(Optional::orElseThrow)
                     .map(order -> createOrderWrapper(order, myBtcBalance, actualBalance, totalAmounts))
                     .sorted(comparing(OrderWrapper::getOrderPricePercentage));
  }

  private OrderWrapper createOrderWrapper(Order order,
                                          BigDecimal myBtcBalance,
                                          BigDecimal actualBalance,
                                          Map<String, BigDecimal> totalAmounts) {
    BigDecimal currentPrice = binanceService.getCurrentPrice(order.getSymbol());
    BigDecimal actualWaitingTime = calculateActualWaitingTime(order);
    BigDecimal orderPrice = new BigDecimal(order.getPrice());
    BigDecimal orderPricePercentage = calculatePricePercentage(currentPrice, orderPrice);
    List<Candlestick> candleStickData = binanceService.getCandlesticks(order, actualWaitingTime, orderPricePercentage);
    return new OrderWrapper(order,
                            orderPrice,
                            currentPrice,
                            myBtcBalance,
                            actualBalance,
                            orderPricePercentage,
                            totalAmounts,
                            candleStickData,
                            actualWaitingTime);
  }

  private BigDecimal calculateActualWaitingTime(Order order) {
    LocalDateTime date = ofInstant(ofEpochMilli(order.getTime()), systemDefault());
    LocalDateTime now = now();
    Duration duration = between(date, now);
    double actualWaitingTimeDouble = (double) duration.get(SECONDS) / (double) 3600;
    return new BigDecimal(String.valueOf(actualWaitingTimeDouble), new MathContext(5));
  }
}
