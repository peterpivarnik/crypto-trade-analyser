package com.psw.cta.utils;

import static com.psw.cta.utils.CommonUtils.calculatePricePercentage;
import static com.psw.cta.utils.CommonUtils.getCurrentPrice;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.OrderUtils.calculateActualWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateMinWaitingTime;
import static com.psw.cta.utils.OrderUtils.calculateOrderBtcAmount;
import static com.psw.cta.utils.OrderUtils.calculateOrderPrice;
import static com.psw.cta.utils.OrderUtils.calculatePriceToSell;
import static java.math.RoundingMode.CEILING;

import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.OrderBook;
import com.psw.cta.dto.binance.SymbolInfo;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Buider to build {@link OrderWrapper}.
 */
public class OrderWrapperBuilder {

  /**
   * Builds {@link OrderWrapper}.
   *
   * @param order Order to build {@link OrderWrapper}
   * @return {@link OrderWrapper}
   */
  public static OrderWrapper build(Order order) {
    BigDecimal orderPrice = calculateOrderPrice(order);
    BigDecimal orderBtcAmount = calculateOrderBtcAmount(order, orderPrice);
    OrderWrapper orderWrapper = new OrderWrapper(order);
    orderWrapper.setOrderPrice(orderPrice);
    orderWrapper.setOrderBtcAmount(orderBtcAmount);
    return orderWrapper;
  }

  /**
   * Add waiting times to {@link OrderWrapper}.
   *
   * @param totalAmounts Total amounts from all orders
   * @param orderWrapper {@link OrderWrapper} to update
   * @return Updated {@link OrderWrapper}
   */
  public static OrderWrapper withWaitingTimes(Map<String, BigDecimal> totalAmounts, OrderWrapper orderWrapper) {
    BigDecimal minWaitingTime = calculateMinWaitingTime(totalAmounts.get(orderWrapper.getOrder().getSymbol()),
                                                        orderWrapper.getOrderBtcAmount());
    BigDecimal actualWaitingTime = calculateActualWaitingTime(orderWrapper.getOrder());
    orderWrapper.setMinWaitingTime(minWaitingTime);
    orderWrapper.setActualWaitingTime(actualWaitingTime);
    return orderWrapper;
  }

  /**
   * Add prices to {@link OrderWrapper}.
   *
   * @param orderWrapper  {@link OrderWrapper} to update
   * @param orderBook     Order book of a symbol
   * @param symbolInfo    Symbol information
   * @param myBtcBalance  Actual balance in BTC
   * @param actualBalance Total actual balance
   * @return updated {@link OrderWrapper}
   */
  public static OrderWrapper withPrices(OrderWrapper orderWrapper,
                                        OrderBook orderBook,
                                        SymbolInfo symbolInfo,
                                        BigDecimal myBtcBalance,
                                        BigDecimal actualBalance) {
    BigDecimal orderPrice = orderWrapper.getOrderPrice();
    BigDecimal currentPrice = getCurrentPrice(orderBook);
    BigDecimal priceToSell = calculatePriceToSell(orderPrice,
                                                  currentPrice,
                                                  orderWrapper.getOrderBtcAmount(),
                                                  symbolInfo,
                                                  myBtcBalance,
                                                  actualBalance);
    BigDecimal priceToSellPercentage = calculatePricePercentage(currentPrice, priceToSell);
    BigDecimal orderPricePercentage = calculatePricePercentage(currentPrice, orderPrice);
    BigDecimal currentBtcAmount = getQuantity(orderWrapper.getOrder())
        .multiply(currentPrice)
        .setScale(8, CEILING);
    orderWrapper.setCurrentPrice(currentPrice);
    orderWrapper.setPriceToSell(priceToSell);
    orderWrapper.setPriceToSellPercentage(priceToSellPercentage);
    orderWrapper.setOrderPricePercentage(orderPricePercentage);
    orderWrapper.setCurrentBtcAmount(currentBtcAmount);
    return orderWrapper;
  }

  private OrderWrapperBuilder() {
  }
}
