package com.psw.cta.dto;

import static com.psw.cta.utils.CommonUtils.getQuantity;
import static java.lang.String.format;

import com.psw.cta.dto.binance.Order;
import java.math.BigDecimal;

/**
 * Object holding information about order.
 */
public class OrderWrapper {

  private final Order order;
  private BigDecimal orderBtcAmount;
  private BigDecimal currentBtcAmount;
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

  public BigDecimal getCurrentBtcAmount() {
    return currentBtcAmount;
  }

  public void setCurrentBtcAmount(BigDecimal currentBtcAmount) {
    this.currentBtcAmount = currentBtcAmount;
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

  public BigDecimal getRemainWaitingTime() {
    return minWaitingTime.subtract(actualWaitingTime);
  }

  @Override
  public String toString() {
    return "OrderWrapper{"
           + format("symbol=%-12s", order.getSymbol() + ",")
           + format("orderBtcAmount=%-12s", orderBtcAmount.stripTrailingZeros().toPlainString() + ",")
           + format("currentBtcAmount=%-12s", currentBtcAmount.stripTrailingZeros().toPlainString() + ",")
           + format("quantity=%-9s", getQuantity(order).stripTrailingZeros().toPlainString() + ",")
           + format("currentPrice=%-12s", currentPrice.stripTrailingZeros().toPlainString() + ",")
           + format("orderPrice=%-12s", orderPrice.stripTrailingZeros().toPlainString() + ",")
           + format("priceToSell=%-12s", priceToSell.stripTrailingZeros().toPlainString() + ",")
           + format("orderPricePercentage=%-13s", orderPricePercentage.stripTrailingZeros().toPlainString() + ",")
           + format("priceToSellPercentage=%-13s", priceToSellPercentage.stripTrailingZeros().toPlainString() + ",")
           + format("remainWaitingTime=%-12s", getRemainWaitingTime().stripTrailingZeros().toPlainString() + ",")
           + format("actualWaitingTime=%-1s", actualWaitingTime.stripTrailingZeros().toPlainString() + "}");
  }
}
