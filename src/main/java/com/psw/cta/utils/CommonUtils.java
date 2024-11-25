package com.psw.cta.utils;

import static com.psw.cta.dto.binance.FilterType.LOT_SIZE;
import static com.psw.cta.dto.binance.FilterType.PRICE_FILTER;
import static com.psw.cta.utils.LeastSquares.getSlope;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.FLOOR;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.FilterType;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.OrderBook;
import com.psw.cta.dto.binance.OrderBookEntry;
import com.psw.cta.dto.binance.SymbolFilter;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.exception.CryptoTraderException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Common utils to be used everywhere.
 */
public class CommonUtils {

  /**
   * Returns order comparator.
   *
   * @return Comparator
   */
  public static Comparator<Order> getOrderComparator() {
    Function<Order, BigDecimal> quantityFunction = CommonUtils::getQuantity;
    Function<Order, BigDecimal>
        btcAmountFunction = order -> (getQuantity(order)).multiply(new BigDecimal(order.getPrice()));
    Function<Order, BigDecimal> timeFunction = order -> new BigDecimal(order.getTime());
    return comparing(quantityFunction).reversed()
                                      .thenComparing(comparing(btcAmountFunction).reversed())
                                      .thenComparing(timeFunction);
  }

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
   * Returns priceCount to slope.
   *
   * @param averagePrices Average prices
   * @return PriceCount to slope
   */
  public static BigDecimal getPriceCountToSlope(List<BigDecimal> averagePrices) {
    BigDecimal priceCount = new BigDecimal(averagePrices.size(), new MathContext(8));
    double leastSquaresSlope = getSlope(averagePrices);
    if (Double.isNaN(leastSquaresSlope)) {
      leastSquaresSlope = 0.00000001;
    }
    BigDecimal slope = new BigDecimal(String.valueOf(leastSquaresSlope), new MathContext(8));
    if (ZERO.compareTo(slope) == 0) {
      slope = new BigDecimal("0.00000001");
    }
    return priceCount.divide(slope, 8, CEILING);
  }

  /**
   * Returns average prices for all candlesticks.
   *
   * @param threeMonthsCandleStickData Candlestick data for three months
   * @return Avewrage prices
   */
  public static List<BigDecimal> getAveragePrices(List<Candlestick> threeMonthsCandleStickData) {
    return threeMonthsCandleStickData.parallelStream()
                                     .map(CommonUtils::getAveragePrice)
                                     .collect(Collectors.toList());
  }

  /**
   * Returns average price for candlestick.
   *
   * @param candle Kline/Candlestick bars for a symbol
   * @return average prices
   */
  public static BigDecimal getAveragePrice(Candlestick candle) {
    BigDecimal open = new BigDecimal(candle.getOpen());
    BigDecimal close = new BigDecimal(candle.getClose());
    BigDecimal high = new BigDecimal(candle.getHigh());
    BigDecimal low = new BigDecimal(candle.getLow());
    return open.add(close).add(high).add(low).divide(new BigDecimal("4"), 8, CEILING);
  }

  /**
   * Returns map with total amounts from all orders.
   *
   * @param openOrders All open orders
   * @return Total amounts
   */
  public static Map<String, BigDecimal> createTotalAmounts(List<Order> openOrders) {
    return openOrders.stream()
                     .collect(toMap(Order::getSymbol,
                                    order -> new BigDecimal(order.getPrice())
                                        .multiply(getQuantity(order))
                                        .setScale(8, FLOOR),
                                    BigDecimal::add))
                     .entrySet()
                     .stream()
                     .sorted((c1, c2) -> c2.getValue().compareTo(c1.getValue()))
                     .collect(toMap(Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (e1, e2) -> e1,
                                    LinkedHashMap::new));
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
   * Calculates minimal number of open orders.
   *
   * @param myBtcBalance BTC balance
   * @return Minimal number of open orders
   */
  public static int calculateMinNumberOfOrders(BigDecimal myBtcBalance) {
    return myBtcBalance.multiply(new BigDecimal("50")).intValue();
  }

  /**
   * Return current price from the exchange.
   *
   * @param orderBook Order book of a symbol in Binance.
   * @return Current exchange price
   */
  public static BigDecimal getCurrentPrice(OrderBook orderBook) {
    return orderBook.getAsks()
                    .parallelStream()
                    .map(OrderBookEntry::getPrice)
                    .map(BigDecimal::new)
                    .min(Comparator.naturalOrder())
                    .orElseThrow(() -> new CryptoTraderException("No price found!"));
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

  /**
   * Splits forbidden pairs from environment variables to list of pairs.
   *
   * @param forbiddenPairs String representation of forbidden pairs
   * @return List of forbidden pairs
   */
  public static List<String> splitForbiddenPairs(String forbiddenPairs) {
    return Arrays.asList(forbiddenPairs.split(","));
  }

  private CommonUtils() {
  }
}