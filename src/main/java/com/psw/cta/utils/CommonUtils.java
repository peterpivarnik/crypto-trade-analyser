package com.psw.cta.utils;

import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static com.psw.cta.utils.Constants.HUNDRED_PERCENT;
import static com.psw.cta.utils.LeastSquares.getSlope;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.FLOOR;
import static java.math.RoundingMode.UP;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.psw.cta.exception.CryptoTraderException;
import com.psw.cta.service.BinanceApiService;
import com.psw.cta.service.BnbService;
import com.psw.cta.service.DiversifyService;
import com.psw.cta.service.InitialTradingService;
import com.psw.cta.service.RepeatTradingService;
import com.psw.cta.service.TradingService;
import java.math.BigDecimal;
import java.math.MathContext;
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
   * Initialize Trading service.
   *
   * @param apiKey    ApiKey to be used for trading
   * @param apiSecret ApiSecret to be used for trading
   * @param logger    Logger
   * @return Initialized {@link TradingService}
   */
  public static TradingService initializeTradingService(String apiKey,
                                                        String apiSecret,
                                                        LambdaLogger logger) {
    BinanceApiService binanceApiService = new BinanceApiService(apiKey, apiSecret, logger);
    InitialTradingService initialTradingService = new InitialTradingService(binanceApiService,
                                                                            logger);
    RepeatTradingService repeatTradingService = new RepeatTradingService(binanceApiService, logger);
    DiversifyService diversifyService = new DiversifyService(binanceApiService, logger);
    BnbService bnbService = new BnbService(binanceApiService, logger);
    return new TradingService(initialTradingService,
                              repeatTradingService,
                              diversifyService,
                              bnbService,
                              binanceApiService,
                              logger);
  }

  /**
   * Returns order comparator.
   *
   * @return Comparator
   */
  public static Comparator<Order> getOrderComparator() {
    Function<Order, BigDecimal> quantityFunction = CommonUtils::getQuantity;
    Function<Order, BigDecimal>
        btcAmountFunction
        = order -> (getQuantity(order)).multiply(new BigDecimal(order.getPrice()));
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
   * @param filterType           Filter of symbol
   * @param symbolFilterFunction Function to get proper value
   * @return Value from filter according function
   */
  public static BigDecimal getValueFromFilter(SymbolInfo symbolInfo,
                                              FilterType filterType,
                                              Function<SymbolFilter, String> symbolFilterFunction) {
    return symbolInfo.getFilters()
                     .parallelStream()
                     .filter(filter -> filter.getFilterType().equals(filterType))
                     .map(symbolFilterFunction)
                     .map(BigDecimal::new)
                     .findAny()
                     .orElseThrow(() -> new CryptoTraderException("Value from filter "
                                                                  + filterType
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
    BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, filterType, symbolFilterFunction);
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
    return priceCount.divide(slope, 8, CEILING);
  }

  /**
   * Returns average prices for all candlesticks.
   *
   * @param threeMonthsCandleStickData Candlestick data for three months
   * @return Avewrage prices
   */
  public static List<BigDecimal> getAveragePrices(List<Candlestick> threeMonthsCandleStickData) {
    Candlestick maxHighCandlestick = threeMonthsCandleStickData.stream()
                                                               .max(comparing(candle -> new BigDecimal(
                                                                   candle.getHigh())))
                                                               .orElseThrow();
    return threeMonthsCandleStickData.parallelStream()
                                     .filter(candle -> candle.getOpenTime()
                                                       > maxHighCandlestick.getOpenTime())
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
                                    order -> new BigDecimal(order.getPrice()).multiply(getQuantity(order))
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
   * @param myTotalPossibleBalance Total possible balance
   * @param myBtcBalance           BTC balance
   * @return Minimal number of open orders
   */
  public static int calculateMinNumberOfOrders(BigDecimal myTotalPossibleBalance,
                                               BigDecimal myBtcBalance) {
    BigDecimal minFromPossibleBalance = myTotalPossibleBalance.multiply(new BigDecimal("5"));
    BigDecimal minFromActualBtcBalance = myBtcBalance.multiply(new BigDecimal("50"));
    return minFromActualBtcBalance.max(minFromPossibleBalance).intValue();
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
   * Calculates percentage price.
   *
   * @param lowestPrice  Price
   * @param highestPrice Base price
   * @return Price percentage
   */
  public static BigDecimal calculatePricePercentage(BigDecimal lowestPrice,
                                                    BigDecimal highestPrice) {
    BigDecimal percentage = lowestPrice.multiply(HUNDRED_PERCENT).divide(highestPrice, 8, UP);
    return HUNDRED_PERCENT.subtract(percentage);
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