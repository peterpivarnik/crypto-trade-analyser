package com.psw.cta.dto;

import static com.psw.cta.utils.Constants.HUNDRED_PERCENT;
import static com.psw.cta.utils.Constants.TWO;
import static com.psw.cta.utils.LeastSquares.getRegression;
import static java.lang.Math.sqrt;
import static java.lang.String.format;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.valueOf;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.UP;
import static java.util.Comparator.comparing;

import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.Order;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.math3.stat.regression.SimpleRegression;

/**
 * Object holding information about order.
 */
public class OrderWrapper {

  private static final BigDecimal HALF_OF_MAX_PROFIT = new BigDecimal("0.15");
  private static final BigDecimal HALF_OF_MIN_PROFIT = new BigDecimal("0.0025");

  private final Order order;
  private final BigDecimal orderPrice;
  private final BigDecimal orderBtcAmount;
  private final BigDecimal currentBtcAmount;
  private final BigDecimal currentPrice;
  private final BigDecimal priceToSell;
  private final BigDecimal priceToSellPercentage;
  private final BigDecimal orderPricePercentage;
  private final BigDecimal minWaitingTime;
  private final BigDecimal actualWaitingTime;

  public OrderWrapper(Order order,
                      BigDecimal currentPrice,
                      BigDecimal myBtcBalance,
                      BigDecimal actualBalance,
                      Map<String, BigDecimal> totalAmounts,
                      List<Candlestick> candleStickData,
                      BigDecimal actualWaitingTime) {
    this.order = order;
    this.orderPrice = new BigDecimal(this.order.getPrice());
    this.orderBtcAmount = calculateOrderBtcAmount(this.orderPrice);
    this.currentPrice = currentPrice;
    this.priceToSell = calculatePriceToSell(this.orderPrice,
                                            this.currentPrice,
                                            this.orderBtcAmount,
                                            myBtcBalance,
                                            actualBalance);
    this.currentBtcAmount = getQuantity()
        .multiply(this.currentPrice)
        .setScale(8, CEILING);
    this.priceToSellPercentage = calculatePricePercentage(this.currentPrice, this.priceToSell);
    this.orderPricePercentage = calculatePricePercentage(this.currentPrice, this.orderPrice);
    this.minWaitingTime = getMinWaitingTime(totalAmounts,
                                            this.orderBtcAmount,
                                            this.orderPricePercentage,
                                            candleStickData);
    this.actualWaitingTime = actualWaitingTime;
  }

  private BigDecimal calculateOrderBtcAmount(BigDecimal orderPrice) {
    BigDecimal orderAltAmount = getQuantity();
    return orderAltAmount.multiply(orderPrice).setScale(8, CEILING);
  }

  private BigDecimal calculatePriceToSell(BigDecimal orderPrice,
                                          BigDecimal currentPrice,
                                          BigDecimal orderBtcAmount,
                                          BigDecimal myBtcBalance,
                                          BigDecimal actualBalance) {
    BigDecimal loss = orderPrice.subtract(currentPrice);
    BigDecimal halfLoss = loss.divide(TWO, 8, UP);
    BigDecimal priceToSellWithoutProfit = currentPrice.add(halfLoss);
    BigDecimal profitCoefficient = getProfitCoefficient(orderBtcAmount,
                                                        myBtcBalance,
                                                        actualBalance);
    BigDecimal realProfit = halfLoss.multiply(profitCoefficient);
    BigDecimal priceToSell = priceToSellWithoutProfit.add(realProfit);
    return priceToSell.setScale(8, CEILING);
  }

  private BigDecimal getProfitCoefficient(BigDecimal orderBtcAmount,
                                          BigDecimal myBtcBalance,
                                          BigDecimal actualBalance) {
    BigDecimal btcBalanceToTotalBalanceRatio = myBtcBalance.divide(actualBalance, 8, CEILING);
    BigDecimal maxBtcAmountToReduceProfit = myBtcBalance.divide(new BigDecimal("2"), 8, CEILING);
    SimpleRegression regression = getRegression(0.0001,
                                                HALF_OF_MAX_PROFIT.doubleValue(),
                                                maxBtcAmountToReduceProfit.doubleValue(),
                                                HALF_OF_MIN_PROFIT.doubleValue());
    BigDecimal btcAmountProfitPart = calculateProfitPart(orderBtcAmount,
                                                         valueOf(regression.getSlope()),
                                                         valueOf(regression.getIntercept()));
    BigDecimal ratioProfitPart = calculateProfitPart(btcBalanceToTotalBalanceRatio,
                                                     new BigDecimal("0.1638"),
                                                     new BigDecimal("-0.0138"));
    BigDecimal profitPercentage = btcAmountProfitPart.add(ratioProfitPart);
    return profitPercentage.max(new BigDecimal("0.005"));
  }

  private BigDecimal calculateProfitPart(BigDecimal x, BigDecimal a, BigDecimal b) {
    BigDecimal halfOfProfit = calculateLineEquation(x, a, b);
    return correctForMinAndMaxValues(halfOfProfit);
  }

  private BigDecimal calculateLineEquation(BigDecimal x, BigDecimal a, BigDecimal b) {
    return (x.multiply(a)).add(b);
  }

  private BigDecimal correctForMinAndMaxValues(BigDecimal btcAmountPartBase) {
    BigDecimal maxValue = btcAmountPartBase.min(HALF_OF_MAX_PROFIT);
    return maxValue.max(HALF_OF_MIN_PROFIT);
  }

  private BigDecimal getMinWaitingTime(Map<String, BigDecimal> totalAmounts,
                                       BigDecimal orderBtcAmount,
                                       BigDecimal orderPricePercentage,
                                       List<Candlestick> candleStickData) {
    BigDecimal oldMinWaitingTime = calculateOldMinWaitingTime(totalAmounts.get(this.order.getSymbol()),
                                                              orderBtcAmount,
                                                              orderPricePercentage);
    BigDecimal min = candleStickData.stream()
                                    .map(Candlestick::getLow)
                                    .map(BigDecimal::new)
                                    .min(comparing(Function.identity()))
                                    .orElse(BigDecimal.ZERO);
    BigDecimal max = candleStickData.stream()
                                    .map(Candlestick::getHigh)
                                    .map(BigDecimal::new)
                                    .max(comparing(Function.identity()))
                                    .orElse(this.currentPrice);
    BigDecimal minPricePercentage = calculatePricePercentage(max, min).negate();
    return oldMinWaitingTime.add(oldMinWaitingTime.multiply(minPricePercentage.divide(new BigDecimal("50"), 8, UP)))
                            .abs(new MathContext(5));
  }

  private BigDecimal calculateOldMinWaitingTime(BigDecimal totalSymbolAmount,
                                                BigDecimal orderBtcAmount,
                                                BigDecimal orderPricePercentage) {
    BigDecimal totalWaitingTime = getTimeFromAmount(totalSymbolAmount);
    BigDecimal orderWaitingTime = getTimeFromAmount(orderBtcAmount);
    BigDecimal waitingTime = totalWaitingTime.add(orderWaitingTime);
    BigDecimal timeVariable = getTimeVariable(orderPricePercentage);
    return waitingTime.multiply(timeVariable).round(MathContext.DECIMAL32);
  }

  private BigDecimal getTimeFromAmount(BigDecimal totalAmount) {
    double totalTime = 100 * sqrt(totalAmount.doubleValue());
    return new BigDecimal(String.valueOf(totalTime), new MathContext(3));
  }

  /**
   * Returns quantity of open order.
   *
   * @return Order quantity
   */
  public BigDecimal getQuantity() {
    return new BigDecimal(this.order.getOrigQty()).subtract(new BigDecimal(this.order.getExecutedQty()));
  }

  /*
   * Returns result of f(x)=-0.0008 * x * x + 0.15 * x + 0.5
   */
  private BigDecimal getTimeVariable(BigDecimal orderPricePercentage) {
    BigDecimal a = new BigDecimal("-0.0008");
    BigDecimal b = new BigDecimal("0.15");
    BigDecimal c = new BigDecimal("0.5");
    BigDecimal firstElement = a.multiply(orderPricePercentage).multiply(orderPricePercentage);
    BigDecimal secondElement = b.multiply(orderPricePercentage);
    return firstElement.add(secondElement.add(c));
  }

  private BigDecimal calculatePricePercentage(BigDecimal lowestPrice,
                                              BigDecimal highestPrice) {
    BigDecimal percentage = lowestPrice.multiply(HUNDRED_PERCENT).divide(highestPrice, 8, UP);
    return HUNDRED_PERCENT.subtract(percentage);
  }

  public Order getOrder() {
    return order;
  }

  public BigDecimal getOrderBtcAmount() {
    return orderBtcAmount;
  }

  public BigDecimal getCurrentBtcAmount() {
    return currentBtcAmount;
  }

  public BigDecimal getOrderPrice() {
    return orderPrice;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public BigDecimal getPriceToSell() {
    return priceToSell;
  }

  public BigDecimal getPriceToSellPercentage() {
    return priceToSellPercentage;
  }

  public BigDecimal getMinWaitingTime() {
    return minWaitingTime;
  }

  public BigDecimal getActualWaitingTime() {
    return actualWaitingTime;
  }

  public BigDecimal getOrderPricePercentage() {
    return orderPricePercentage;
  }

  public BigDecimal getRemainWaitingTime() {
    return minWaitingTime.subtract(actualWaitingTime);
  }

  @Override
  public String toString() {
    return "OrderWrapper{"
           + format("symbol=%-12s", order.getSymbol() + ",")
           + format("orderBtcAmount=%-12s", orderBtcAmount.stripTrailingZeros().toPlainString() + ",")
           + format("neededBtcBalance=%-12s", getNeededBtcBalance() + ",")
           + format("currentBtcAmount=%-12s", currentBtcAmount.stripTrailingZeros().toPlainString() + ",")
           + format("quantity=%-9s", getQuantity().stripTrailingZeros().toPlainString() + ",")
           + format("currentPrice=%-12s", currentPrice.stripTrailingZeros().toPlainString() + ",")
           + format("orderPrice=%-12s", orderPrice.stripTrailingZeros().toPlainString() + ",")
           + format("priceToSell=%-12s", priceToSell.stripTrailingZeros().toPlainString() + ",")
           + format("orderPricePercentage=%-13s", orderPricePercentage.stripTrailingZeros().toPlainString() + ",")
           + format("priceToSellPercentage=%-13s", priceToSellPercentage.stripTrailingZeros().toPlainString() + ",")
           + format("remainWaitingTime=%-9s", getRemainWaitingTime().stripTrailingZeros().toPlainString() + ",")
           + format("actualWaitingTime=%-1s", actualWaitingTime.stripTrailingZeros().toPlainString() + "}");
  }

  private String getNeededBtcBalance() {
    BigDecimal neededBtcBalance;
    if (orderPricePercentage.compareTo(TEN) < 0) {
      neededBtcBalance = orderBtcAmount;
    } else {
      BigDecimal multiplicand = orderPricePercentage.divide(TEN, 8, UP)
                                                    .add(ONE);
      neededBtcBalance = orderBtcAmount.multiply(multiplicand);
    }
    return neededBtcBalance.setScale(8, CEILING)
                           .stripTrailingZeros()
                           .toPlainString();
  }
}
