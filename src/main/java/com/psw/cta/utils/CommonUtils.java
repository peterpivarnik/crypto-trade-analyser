package com.psw.cta.utils;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.binance.FilterType;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.SymbolFilter;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.exception.CryptoTraderException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import static com.psw.cta.dto.binance.FilterType.LOT_SIZE;
import static com.psw.cta.dto.binance.FilterType.PRICE_FILTER;

/**
 * Common utils to be used everywhere.
 */
public class CommonUtils {

  /**
   * Sleep program for provided amount of milliseconds.
   *
   * @param millis Milliseconds to sleep
   * @param logger Logger to log the exception
   */
  @SuppressWarnings("java:S2142")
  public static void sleep(int millis, LambdaLogger logger) {
    logger.log("Sleeping for " + millis / 1000 + " seconds");
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      logger.log("Error during sleeping");
    }
  }

  /**
   * Returns value from provided filter according provided function.
   *
   * @param symbolInfo           Symbol information
   * @param symbolFilterFunction Function to get proper value
   * @param filterTypes          Filters of symbol
   * @return Value from filter according function
   */
  public static BigDecimal getValueFromFilter(SymbolInfo symbolInfo,
                                              Function<SymbolFilter, String> symbolFilterFunction,
                                              FilterType... filterTypes) {
    List<FilterType> filterTypesList = Arrays.asList(filterTypes);
    return symbolInfo.getFilters()
                     .parallelStream()
                     .filter(filter -> filterTypesList.contains(filter.getFilterType()))
                     .map(symbolFilterFunction)
                     .map(BigDecimal::new)
                     .findAny()
                     .orElseThrow(() -> new CryptoTraderException("Value from filters "
                                                                      + Arrays.toString(filterTypes)
                                                                      + " not found"));
  }

  /**
   * Round amount.
   *
   * @param symbolInfo Symbol information
   * @param amount     Amount to round
   * @return Rounded amount
   */
  public static BigDecimal roundAmount(SymbolInfo symbolInfo, BigDecimal amount) {
    return round(symbolInfo,
                 amount,
                 LOT_SIZE,
                 SymbolFilter::getMinQty,
                 (roundedValue, valueFromFilter) -> roundedValue);
  }

  /**
   * Round Price.
   *
   * @param symbolInfo Symbol information
   * @param price      Price to round
   * @return Rounded price
   */
  public static BigDecimal roundPrice(SymbolInfo symbolInfo, BigDecimal price) {
    return round(symbolInfo,
                 price,
                 PRICE_FILTER,
                 SymbolFilter::getTickSize,
                 (roundedValue, valueFromFilter) -> roundedValue);
  }

  /**
   * Round price up.
   *
   * @param symbolInfo Symbol information
   * @param price      Price to round
   * @return Rounded price
   */
  public static BigDecimal roundPriceUp(SymbolInfo symbolInfo, BigDecimal price) {
    return round(symbolInfo,
                 price,
                 PRICE_FILTER,
                 SymbolFilter::getTickSize,
                 BigDecimal::add);
  }

  private static BigDecimal round(SymbolInfo symbolInfo,
                                  BigDecimal amountToRound,
                                  FilterType filterType,
                                  Function<SymbolFilter, String> symbolFilterFunction,
                                  BinaryOperator<BigDecimal> roundUpFunction) {
    BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, symbolFilterFunction, filterType);
    BigDecimal remainder = amountToRound.remainder(valueFromFilter);
    BigDecimal roundedValue = amountToRound.subtract(remainder);
    return roundUpFunction.apply(roundedValue, valueFromFilter);
  }

  /**
   * Returns quantity of open order.
   *
   * @param order Trade order information
   * @return Order quantity
   */
  public static BigDecimal getQuantity(Order order) {
    return new BigDecimal(order.getOrigQty()).subtract(new BigDecimal(order.getExecutedQty()));
  }

  /**
   * Returns whether BTC balance is higher than minimal balance for trading.
   *
   * @param myBtcBalance Actual BTC balance
   * @return Flag whether BTC balance is higher than minimal balance
   */
  public static boolean haveBalanceForInitialTrading(BigDecimal myBtcBalance) {
    return myBtcBalance.compareTo(new BigDecimal("0.0002")) > 0;
  }

  /**
   * Returns minimum BTC amount to buy.
   *
   * @param btcAmountToSpend              Total BTC amount
   * @param minAddition                   Minimum addition
   * @param minValueFromMinNotionalFilter Minimum value from filter
   * @return Min BTC amount to buy
   */
  public static BigDecimal getMinBtcAmount(BigDecimal btcAmountToSpend,
                                           BigDecimal minAddition,
                                           BigDecimal minValueFromMinNotionalFilter) {
    if (btcAmountToSpend.compareTo(minValueFromMinNotionalFilter) < 0) {
      return getMinBtcAmount(btcAmountToSpend.add(minAddition),
                             minAddition,
                             minValueFromMinNotionalFilter);
    }
    return btcAmountToSpend.add(minAddition);
  }

  private CommonUtils() {
  }
}