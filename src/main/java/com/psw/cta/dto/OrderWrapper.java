package com.psw.cta.dto;

import static com.psw.cta.utils.Constants.HUNDRED_PERCENT;
import static com.psw.cta.utils.Constants.TWO;
import static com.psw.cta.utils.LeastSquares.getRegression;
import static java.lang.Math.sqrt;
import static java.lang.String.format;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.valueOf;
import static java.math.MathContext.DECIMAL32;
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
  private final BigDecimal currentPrice;
  private final BigDecimal orderPrice;
  private final BigDecimal quantity;
  private final BigDecimal orderBtcAmount;
  private final BigDecimal priceToSell;
  private final BigDecimal currentBtcAmount;
  private final BigDecimal orderPricePercentage;
  private final BigDecimal priceToSellPercentage;
  private final BigDecimal neededBtcAmount;
  private final BigDecimal actualWaitingTime;
  private final BigDecimal remainWaitingTime;

  /**
   * Default constructor.
   *
   * @param order                open order info
   * @param orderPrice           price from order
   * @param currentPrice         current market price
   * @param myBtcBalance         my btc balance
   * @param actualBalance        actual balance of all trades and my btc balance
   * @param orderPricePercentage percentage of order price to current price
   * @param totalAmounts         all amounts
   * @param candleStickData      candle sticks
   * @param actualWaitingTime    actual waiting time of order
   */
  public OrderWrapper(Order order,
                      BigDecimal orderPrice,
                      BigDecimal currentPrice,
                      BigDecimal myBtcBalance,
                      BigDecimal actualBalance,
                      BigDecimal orderPricePercentage,
                      Map<String, BigDecimal> totalAmounts,
                      List<Candlestick> candleStickData,
                      BigDecimal actualWaitingTime) {
    this.order = order;
    this.currentPrice = currentPrice;
    this.orderPrice = orderPrice;
    this.quantity = calculateQuantity(this.order);
    this.orderBtcAmount = calculateOrderBtcAmount(this.orderPrice, this.quantity);
    this.priceToSell = calculatePriceToSell(this.orderPrice,
                                            this.currentPrice,
                                            this.orderBtcAmount,
                                            myBtcBalance,
                                            actualBalance);
    this.currentBtcAmount = this.quantity.multiply(this.currentPrice)
                                         .setScale(8, CEILING);
    this.orderPricePercentage = orderPricePercentage;
    this.priceToSellPercentage = calculatePricePercentage(this.currentPrice, this.priceToSell);
    this.neededBtcAmount = calculateNeededBtcAmount(this.orderPricePercentage, this.orderBtcAmount);
    this.actualWaitingTime = actualWaitingTime;
    BigDecimal minWaitingTime = calculateMinWaitingTime(this.order,
                                                        totalAmounts,
                                                        this.orderBtcAmount,
                                                        this.orderPricePercentage,
                                                        candleStickData,
                                                        this.currentPrice);
    this.remainWaitingTime = calculateRemainWaitingTime(minWaitingTime, this.actualWaitingTime);
  }

  /**
   * Calculate percentage between highest and lowest price.
   *
   * @param lowestPrice  lowest price
   * @param highestPrice highest price
   * @return percentage price
   */
  public static BigDecimal calculatePricePercentage(BigDecimal lowestPrice,
                                                    BigDecimal highestPrice) {
    BigDecimal percentage = lowestPrice.multiply(HUNDRED_PERCENT).divide(highestPrice, 8, UP);
    return HUNDRED_PERCENT.subtract(percentage);
  }

  private BigDecimal calculateQuantity(Order order) {
    return new BigDecimal(order.getOrigQty()).subtract(new BigDecimal(order.getExecutedQty()));
  }

  private BigDecimal calculateOrderBtcAmount(BigDecimal orderPrice, BigDecimal quantity) {
    return quantity.multiply(orderPrice)
                   .setScale(8, CEILING);
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

  private BigDecimal calculateNeededBtcAmount(BigDecimal orderPricePercentage, BigDecimal orderBtcAmount) {
    BigDecimal multiplicand = ONE.add(orderPricePercentage.divide(TEN, 8, UP));
    return orderBtcAmount.multiply(multiplicand)
                         .setScale(8, CEILING);
  }

  private BigDecimal calculateMinWaitingTime(Order order,
                                             Map<String, BigDecimal> totalAmounts,
                                             BigDecimal orderBtcAmount,
                                             BigDecimal orderPricePercentage,
                                             List<Candlestick> candleStickData,
                                             BigDecimal currentPrice) {
    String symbol = order.getSymbol();
    BigDecimal oldMinWaitingTime = calculateOldMinWaitingTime(totalAmounts.get(symbol),
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
                                    .orElse(currentPrice);
    BigDecimal minPricePercentage = calculatePricePercentage(max, min).negate();
    return oldMinWaitingTime.add(oldMinWaitingTime.multiply(minPricePercentage.divide(new BigDecimal("50"), 8, UP)))
                            .abs(new MathContext(5));
  }

  private BigDecimal calculateRemainWaitingTime(BigDecimal minWaitingTime, BigDecimal actualWaitingTime) {
    return minWaitingTime.subtract(actualWaitingTime)
                         .round(new MathContext(5));
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

  private BigDecimal calculateOldMinWaitingTime(BigDecimal totalSymbolAmount,
                                                BigDecimal orderBtcAmount,
                                                BigDecimal orderPricePercentage) {
    BigDecimal totalWaitingTime = getTimeFromAmount(totalSymbolAmount);
    BigDecimal orderWaitingTime = getTimeFromAmount(orderBtcAmount);
    BigDecimal waitingTime = totalWaitingTime.add(orderWaitingTime);
    BigDecimal timeVariable = getTimeVariable(orderPricePercentage);
    return waitingTime.multiply(timeVariable)
                      .round(DECIMAL32);
  }

  private BigDecimal calculateProfitPart(BigDecimal x, BigDecimal a, BigDecimal b) {
    return calculateLineEquation(x, a, b)
        .min(HALF_OF_MAX_PROFIT)
        .max(HALF_OF_MIN_PROFIT);
  }

  private BigDecimal getTimeFromAmount(BigDecimal totalAmount) {
    double totalTime = 100 * sqrt(totalAmount.doubleValue());
    return new BigDecimal(String.valueOf(totalTime), new MathContext(3));
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

  private BigDecimal calculateLineEquation(BigDecimal x, BigDecimal a, BigDecimal b) {
    return (x.multiply(a)).add(b);
  }

  public Order getOrder() {
    return order;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public BigDecimal getOrderPrice() {
    return orderPrice;
  }

  public BigDecimal getQuantity() {
    return this.quantity;
  }

  public BigDecimal getOrderBtcAmount() {
    return orderBtcAmount;
  }

  public BigDecimal getPriceToSell() {
    return priceToSell;
  }

  public BigDecimal getCurrentBtcAmount() {
    return currentBtcAmount;
  }

  public BigDecimal getOrderPricePercentage() {
    return orderPricePercentage;
  }

  public BigDecimal getPriceToSellPercentage() {
    return priceToSellPercentage;
  }

  public BigDecimal getNeededBtcAmount() {
    return neededBtcAmount;
  }

  public BigDecimal getActualWaitingTime() {
    return actualWaitingTime;
  }

  public BigDecimal getRemainWaitingTime() {
    return remainWaitingTime;
  }

  @Override
  public String toString() {
    return format("%-11s", order.getSymbol())
           + format("orderAmount=%-11s", bigDecimalToString(orderBtcAmount))
           + format("neededAmount=%-11s", bigDecimalToString(neededBtcAmount))
           + format("currentAmount=%-11s", bigDecimalToString(currentBtcAmount))
           + format("quantity=%-7s", bigDecimalToString(quantity))
           + format("currentPrice=%-11s", bigDecimalToString(currentPrice))
           + format("orderPrice=%-11s", bigDecimalToString(orderPrice))
           + format("priceToSell=%-11s", bigDecimalToString(priceToSell))
           + format("orderPricePerc=%-12s", bigDecimalToString(orderPricePercentage))
           + format("priceToSellPerc=%-12s", bigDecimalToString(priceToSellPercentage))
           + format("remainTime=%-8s", bigDecimalToString(remainWaitingTime))
           + format("actualTime=%-1s", bigDecimalToString(actualWaitingTime));
  }

  private String bigDecimalToString(BigDecimal bigDecimal) {
    return bigDecimal.stripTrailingZeros()
                     .toPlainString();
  }
}
