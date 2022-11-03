package com.psw.cta.dto;

import static com.psw.cta.utils.CommonUtils.getQuantity;

import com.binance.api.client.domain.account.Order;
import java.math.BigDecimal;

/**
 * Object holding information about order.
 */
public class OrderWrapper {

  private final Order order;
  private BigDecimal orderBtcAmount;
  private BigDecimal orderPrice;
  private BigDecimal currentPrice;
  private BigDecimal priceToSell;
  private BigDecimal priceToSellPercentage;
  private BigDecimal orderPricePercentage;
  private BigDecimal minWaitingTime = BigDecimal.ZERO;
  private BigDecimal actualWaitingTime = BigDecimal.ZERO;


  public OrderWrapper(Order order) {
    this.order = order;
  }

  public Order getOrder() {
    return order;
  }

  public BigDecimal getOrderBtcAmount() {
    return orderBtcAmount;
  }

  public void setOrderBtcAmount(BigDecimal orderBtcAmount) {
    this.orderBtcAmount = orderBtcAmount;
  }

  public BigDecimal getOrderPrice() {
    return orderPrice;
  }

  public void setOrderPrice(BigDecimal orderPrice) {
    this.orderPrice = orderPrice;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public void setCurrentPrice(BigDecimal currentPrice) {
    this.currentPrice = currentPrice;
  }

  public BigDecimal getPriceToSell() {
    return priceToSell;
  }

  public void setPriceToSell(BigDecimal priceToSell) {
    this.priceToSell = priceToSell;
  }

  public BigDecimal getPriceToSellPercentage() {
    return priceToSellPercentage;
  }

  public void setPriceToSellPercentage(BigDecimal priceToSellPercentage) {
    this.priceToSellPercentage = priceToSellPercentage;
  }

  public BigDecimal getMinWaitingTime() {
    return minWaitingTime;
  }

  public void setMinWaitingTime(BigDecimal minWaitingTime) {
    this.minWaitingTime = minWaitingTime;
  }

  public BigDecimal getActualWaitingTime() {
    return actualWaitingTime;
  }

  public void setActualWaitingTime(BigDecimal actualWaitingTime) {
    this.actualWaitingTime = actualWaitingTime;
  }

  public BigDecimal getOrderPricePercentage() {
    return orderPricePercentage;
  }

  public void setOrderPricePercentage(BigDecimal orderPricePercentage) {
    this.orderPricePercentage = orderPricePercentage;
  }

  @Override
  public String toString() {
    return "OrderWrapper{"
           + "symbol=" + order.getSymbol()
           + ", orderBtcAmount=" + orderBtcAmount
           + ", quantity=" + getQuantity(order)
           + ", currentPrice=" + currentPrice
           + ", orderPrice=" + orderPrice
           + ", priceToSell=" + priceToSell
           + ", orderPricePercentage=" + orderPricePercentage
           + ", priceToSellPercentage=" + priceToSellPercentage
           + ", remainingWaitingTime=" + minWaitingTime.subtract(actualWaitingTime).toPlainString()
           + ", actualWaitingTime=" + actualWaitingTime.toPlainString()
           + '}';
  }
}
