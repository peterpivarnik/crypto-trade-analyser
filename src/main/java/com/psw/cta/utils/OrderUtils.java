package com.psw.cta.utils;

import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.roundPrice;
import static com.psw.cta.utils.Constants.MIN_PROFIT_PERCENTAGE;
import static com.psw.cta.utils.Constants.TIME_CONSTANT;
import static com.psw.cta.utils.Constants.TWO;
import static com.psw.cta.utils.LeastSquares.getRegression;
import static java.lang.Math.sqrt;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.valueOf;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.UP;
import static java.time.Duration.between;
import static java.time.Instant.ofEpochMilli;
import static java.time.LocalDateTime.now;
import static java.time.LocalDateTime.ofInstant;
import static java.time.ZoneId.systemDefault;
import static java.time.temporal.ChronoUnit.SECONDS;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.dto.OrderWrapper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Predicate;
import org.apache.commons.math3.stat.regression.SimpleRegression;

/**
 * Utils for order values.
 */
public class OrderUtils {

  private static final BigDecimal HALF_OF_MAX_PROFIT = new BigDecimal("0.15");
  private static final BigDecimal HALF_OF_MIN_PROFIT = new BigDecimal("0.0025");

  private OrderUtils() {
  }

  /**
   * Calculates order price.
   *
   * @param order Order
   * @return Actual order price
   */
  public static BigDecimal calculateOrderPrice(Order order) {
    return new BigDecimal(order.getPrice());
  }

  /**
   * Calculate order amount in BTC.
   *
   * @param order      Order
   * @param orderPrice Price from order
   * @return Order amount
   */
  public static BigDecimal calculateOrderBtcAmount(Order order, BigDecimal orderPrice) {
    BigDecimal orderAltAmount = getQuantity(order);
    return orderAltAmount.multiply(orderPrice);
  }

  /**
   * Calculates new price to sell.
   *
   * @param orderPrice     Actual order price
   * @param currentPrice   Current price from exchange
   * @param orderBtcAmount Total amount in BTC
   * @param symbolInfo     Symbol information
   * @param myBtcBalance   Actual BTC balance
   * @param actualBalance  Actual total balance
   * @return New price to sell
   */
  public static BigDecimal calculatePriceToSell(BigDecimal orderPrice,
                                                BigDecimal currentPrice,
                                                BigDecimal orderBtcAmount,
                                                SymbolInfo symbolInfo,
                                                BigDecimal myBtcBalance,
                                                BigDecimal actualBalance) {
    BigDecimal profitCoefficient = getProfitCoefficient(orderBtcAmount,
                                                        myBtcBalance,
                                                        actualBalance);
    return getNewPriceToSell(orderPrice, currentPrice, symbolInfo, profitCoefficient);
  }

  private static BigDecimal getProfitCoefficient(BigDecimal orderBtcAmount,
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

  private static BigDecimal getNewPriceToSell(BigDecimal orderPrice,
                                              BigDecimal currentPrice,
                                              SymbolInfo symbolInfo,
                                              BigDecimal profitCoefficient) {
    BigDecimal priceToSellWithoutProfit = getPriceToSellWithoutProfit(orderPrice, currentPrice);
    BigDecimal profit = priceToSellWithoutProfit.subtract(currentPrice);
    BigDecimal realProfit = profit.multiply(profitCoefficient);
    BigDecimal priceToSell = priceToSellWithoutProfit.add(realProfit);
    BigDecimal roundedPriceToSell = roundPrice(symbolInfo, priceToSell);
    return roundedPriceToSell.stripTrailingZeros();
  }

  private static BigDecimal calculateProfitPart(BigDecimal x, BigDecimal a, BigDecimal b) {
    BigDecimal halfOfProfit = calculateLineEquation(x, a, b);
    return correctForMinAndMaxValues(halfOfProfit);
  }

  private static BigDecimal calculateLineEquation(BigDecimal x, BigDecimal a, BigDecimal b) {
    return (x.multiply(a)).add(b);
  }

  private static BigDecimal correctForMinAndMaxValues(BigDecimal btcAmountPartBase) {
    BigDecimal maxValue = btcAmountPartBase.min(HALF_OF_MAX_PROFIT);
    return maxValue.max(HALF_OF_MIN_PROFIT);
  }

  private static BigDecimal getPriceToSellWithoutProfit(BigDecimal bigPrice,
                                                        BigDecimal smallPrice) {
    BigDecimal profit = bigPrice.subtract(smallPrice);
    BigDecimal realProfit = profit.divide(TWO, 8, UP);
    return smallPrice.add(realProfit);
  }

  /**
   * Calculate minimal waiting time.
   *
   * @param totalSymbolAmount Total amount from all orders for specific symbol
   * @param orderBtcAmount    Amount from order
   * @return Minimal waiting time
   */
  public static BigDecimal calculateMinWaitingTime(BigDecimal totalSymbolAmount,
                                                   BigDecimal orderBtcAmount) {
    BigDecimal totalWaitingTime = getTimeFromAmount(totalSymbolAmount);
    BigDecimal orderWaitingTime = getTimeFromAmount(orderBtcAmount);
    BigDecimal waitingTime = totalWaitingTime.add(orderWaitingTime);
    return waitingTime.multiply(TIME_CONSTANT);
  }

  private static BigDecimal getTimeFromAmount(BigDecimal totalAmount) {
    double totalTime = 100 * sqrt(totalAmount.doubleValue());
    return new BigDecimal(String.valueOf(totalTime), new MathContext(3));
  }

  /**
   * Calculates actual waiting time.
   *
   * @param order Order for calculation of actual waiting time
   * @return Actual waiting time
   */
  public static BigDecimal calculateActualWaitingTime(Order order) {
    LocalDateTime date = ofInstant(ofEpochMilli(order.getTime()), systemDefault());
    LocalDateTime now = now();
    Duration duration = between(date, now);
    double actualWaitingTimeDouble = (double) duration.get(SECONDS) / (double) 3600;
    return new BigDecimal(String.valueOf(actualWaitingTimeDouble), new MathContext(5));
  }

  /**
   * Returns predicate to filter out either trades with amount lower than my BTC amount,
   * or in case orderPricePercentage higher than 10 filter out those which doubled amount not highier than myBtc amount.
   * This is done due to always keep some amount to rebuy orders with low orderPricePercentage.
   *
   * @param myBtcBalance My actual BTC amount
   * @return Predicate to filter out orders
   */
  public static Predicate<OrderWrapper> getOrderWrapperPredicate(BigDecimal myBtcBalance) {
    return orderWrapper -> {
      boolean orderPricePercentageLessThan10 = orderWrapper.getOrderPricePercentage().compareTo(TEN) < 0;
      boolean haveEnoughAmount = orderWrapper.getOrderBtcAmount().compareTo(myBtcBalance) < 0;
      boolean haveDoubleAmount = orderWrapper.getOrderBtcAmount().multiply(new BigDecimal("2"))
                                             .compareTo(myBtcBalance) < 0;
      boolean hasMinProfit = orderWrapper.getOrderPricePercentage()
                                         .subtract(orderWrapper.getPriceToSellPercentage())
                                         .compareTo(MIN_PROFIT_PERCENTAGE) > 0;
      return ((orderPricePercentageLessThan10 && haveEnoughAmount)
              || (!orderPricePercentageLessThan10 && haveDoubleAmount)) && hasMinProfit;
    };
  }
}
