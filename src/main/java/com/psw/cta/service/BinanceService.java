package com.psw.cta.service;

import static com.psw.cta.dto.binance.CandlestickInterval.DAILY;
import static com.psw.cta.dto.binance.CandlestickInterval.FIVE_MINUTES;
import static com.psw.cta.dto.binance.CandlestickInterval.HOURLY;
import static com.psw.cta.dto.binance.CandlestickInterval.WEEKLY;
import static com.psw.cta.dto.binance.FilterType.LOT_SIZE;
import static com.psw.cta.dto.binance.FilterType.MIN_NOTIONAL;
import static com.psw.cta.dto.binance.FilterType.NOTIONAL;
import static com.psw.cta.dto.binance.FilterType.PRICE_FILTER;
import static com.psw.cta.dto.binance.NewOrderResponseType.RESULT;
import static com.psw.cta.dto.binance.OrderSide.BUY;
import static com.psw.cta.dto.binance.OrderSide.SELL;
import static com.psw.cta.dto.binance.OrderType.LIMIT;
import static com.psw.cta.dto.binance.OrderType.MARKET;
import static com.psw.cta.dto.binance.TimeInForce.GTC;
import static com.psw.cta.utils.BinanceApiConstants.API_BASE_URL;
import static com.psw.cta.utils.BinanceApiConstants.DEFAULT_RECEIVING_WINDOW;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.lang.System.currentTimeMillis;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.UP;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.Account;
import com.psw.cta.dto.binance.AssetBalance;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.CandlestickInterval;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.FilterType;
import com.psw.cta.dto.binance.NewOrderResponse;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.OrderBook;
import com.psw.cta.dto.binance.OrderBookEntry;
import com.psw.cta.dto.binance.OrderSide;
import com.psw.cta.dto.binance.OrderType;
import com.psw.cta.dto.binance.SymbolFilter;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.dto.binance.TickerStatistics;
import com.psw.cta.dto.binance.TimeInForce;
import com.psw.cta.dto.binance.Trade;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.exception.CryptoTraderException;
import com.psw.cta.security.AuthenticationInterceptor;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.tuple.Pair;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Implementation of Binance's REST API using Retrofit with synchronous/blocking method calls.
 */
public class BinanceService {

  private final BinanceApi binanceApi;
  private final LambdaLogger logger;

  /**
   * Default constructor.
   *
   * @param apiKey Api key
   * @param secret Api secret
   */
  public BinanceService(String apiKey, String secret, LambdaLogger logger) {
    this.binanceApi = new Retrofit.Builder().baseUrl(API_BASE_URL)
                                            .client(getOkHttpClient(apiKey, secret))
                                            .addConverterFactory(JacksonConverterFactory.create())
                                            .build()
                                            .create(BinanceApi.class);
    this.logger = logger;
  }

  private OkHttpClient getOkHttpClient(String apiKey, String secret) {
    return new OkHttpClient.Builder()
        .dispatcher(getDispatcher())
        .pingInterval(20, SECONDS)
        .addInterceptor(new AuthenticationInterceptor(apiKey, secret))
        .build();
  }

  private Dispatcher getDispatcher() {
    Dispatcher dispatcher = new Dispatcher();
    dispatcher.setMaxRequestsPerHost(500);
    dispatcher.setMaxRequests(500);
    return dispatcher;
  }

  /**
   * Returns exchange info.
   *
   * @return Current exchange trading rules and symbol information
   */
  public ExchangeInfo getExchangeInfo() {
    logger.log("Get exchange info.");
    return executeCall(binanceApi.getExchangeInfo());
  }

  /**
   * Returns current price for provided symbol.
   *
   * @param symbol Order symbol
   * @return current price
   */
  public BigDecimal getCurrentPrice(String symbol) {
    return getOrderBook(symbol, 1)
        .getAsks()
        .stream()
        .map(OrderBookEntry::getPrice)
        .map(BigDecimal::new)
        .findFirst()
        .orElseThrow(() -> new CryptoTraderException("No price found!"));
  }

  /**
   * Returns Kline/Candlestick bars for a symbol and provided number of units.
   *
   * @param symbol            Order symbol
   * @param interval          Interval for Candlestick
   * @param numberOfTimeUnits number of time intervals
   * @param chronoUnit        the unit of the amount to subtract
   * @return List of candlestick data
   */
  public List<Candlestick> getCandleStickData(String symbol,
                                              CandlestickInterval interval,
                                              long numberOfTimeUnits,
                                              ChronoUnit chronoUnit) {
    Instant endTime = Instant.now();
    Instant startTime = endTime.minus(numberOfTimeUnits, chronoUnit);
    return executeCall(binanceApi.getCandlestickBars(symbol,
                                                     interval.getIntervalId(),
                                                     null,
                                                     startTime.toEpochMilli(),
                                                     endTime.toEpochMilli()));
  }

  /**
   * Returns candlestick data for provided order and actualWaitingTime.
   *
   * @param order                order
   * @param actualWaitingTime    order's actual waiting time
   * @param orderPricePercentage percentual price of order
   * @return candlesticks
   */
  public List<Candlestick> getCandlesticks(Order order, BigDecimal actualWaitingTime, BigDecimal orderPricePercentage) {
    if (actualWaitingTime.compareTo(ONE) < 0) {
      return getCandlestickList(order, actualWaitingTime, orderPricePercentage, new BigDecimal("5"), FIVE_MINUTES);
    } else if (actualWaitingTime.compareTo(new BigDecimal("24")) < 0) {
      return getCandlestickList(order, actualWaitingTime, orderPricePercentage, new BigDecimal("1"), HOURLY);
    } else if (actualWaitingTime.compareTo(new BigDecimal("720")) < 0) {
      return getCandlestickList(order, actualWaitingTime, orderPricePercentage, new BigDecimal("24"), DAILY);
    } else {
      return getCandlestickList(order, actualWaitingTime, orderPricePercentage, new BigDecimal("168"), WEEKLY);
    }
  }

  private List<Candlestick> getCandlestickList(Order order,
                                               BigDecimal actualWaitingTime,
                                               BigDecimal orderPricePercentage,
                                               BigDecimal divisor,
                                               CandlestickInterval candlestickInterval) {
    int totalTimeUnits = getTotalTimeUnits(actualWaitingTime, orderPricePercentage, divisor);
    return getCandlestickBars(order.getSymbol(), candlestickInterval, totalTimeUnits);
  }

  private int getTotalTimeUnits(BigDecimal actualWaitingTime, BigDecimal orderPricePercentage, BigDecimal divisor) {
    BigDecimal baseTimeUnits = actualWaitingTime.divide(divisor, 8, UP).max(ONE);
    BigDecimal timeUnitsAddition = baseTimeUnits.multiply(orderPricePercentage).divide(new BigDecimal("100"), 8, UP);
    return baseTimeUnits.add(timeUnitsAddition)
                        .setScale(0, UP)
                        .intValue();
  }

  private List<Candlestick> getCandlestickBars(String symbol, CandlestickInterval interval, Integer limit) {
    return executeCall(binanceApi.getCandlestickBars(symbol, interval.getIntervalId(), limit, null, null));
  }

  /**
   * Get 24 hour price change statistics for all symbols.
   */
  public List<TickerStatistics> getAll24hTickers() {
    logger.log("Get 24 h Tickers");
    sleep(1000 * 60, logger);
    return executeCall(binanceApi.getAll24HrPriceStatistics());
  }

  /**
   * Buy order with provided quantity.
   *
   * @param symbolInfo Symbol information
   * @param quantity   quantity to buy
   * @return order response
   */
  public NewOrderResponse buyWithQuantity(SymbolInfo symbolInfo, BigDecimal quantity) {
    logger.log("Buy " + symbolInfo.getSymbol() + " with quantity=" + quantity);
    BigDecimal currentPrice = getCurrentPrice(symbolInfo.getSymbol());
    logger.log("currentPrice: " + currentPrice);
    return buy(symbolInfo, quantity, currentPrice);
  }

  /**
   * Buy order and return quantity of response order.
   *
   * @param symbolInfo Symbol information
   * @param btcAmount  BTC amount to spend
   * @return bought quantity
   */
  public BigDecimal buyWithBtcs(SymbolInfo symbolInfo, BigDecimal btcAmount) {
    logger.log("Buy " + symbolInfo.getSymbol() + " with btc amount=" + btcAmount);
    BigDecimal currentPrice = getCurrentPrice(symbolInfo.getSymbol());
    logger.log("currentPrice: " + currentPrice);
    BigDecimal myQuantity = btcAmount.divide(currentPrice, 8, CEILING);
    NewOrderResponse newOrder = buy(symbolInfo, myQuantity, currentPrice);
    return new BigDecimal(newOrder.getExecutedQty());
  }

  private NewOrderResponse buy(SymbolInfo symbolInfo, BigDecimal quantity, BigDecimal currentPrice) {
    BigDecimal minQuantityToBuy = getMinQuantityToBuy(symbolInfo, quantity, currentPrice);
    logger.log("minQuantityToBuy: " + minQuantityToBuy);
    BigDecimal roundedQuantity = roundQuantity(symbolInfo, minQuantityToBuy);
    logger.log("roundedQuantity: " + roundedQuantity);
    return createNewBuyMarketOrder(symbolInfo, roundedQuantity);
  }

  private BigDecimal getMinQuantityToBuy(SymbolInfo symbolInfo, BigDecimal quantity, BigDecimal currentPrice) {
    BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo,
                                                                  SymbolFilter::getMinNotional,
                                                                  MIN_NOTIONAL,
                                                                  NOTIONAL);
    logger.log("minValueFromMinNotionalFilter: " + minValueFromMinNotionalFilter);
    BigDecimal stepSizeFromLotSizeFilter = getValueFromFilter(symbolInfo, SymbolFilter::getStepSize, LOT_SIZE);
    logger.log("stepSizeFromLotSizeFilter: " + stepSizeFromLotSizeFilter);
    if (quantity.multiply(currentPrice).compareTo(minValueFromMinNotionalFilter) < 0) {
      BigDecimal minQuantity = minValueFromMinNotionalFilter.divide(currentPrice, 8, CEILING);
      return minQuantity.add(stepSizeFromLotSizeFilter)
                        .add(stepSizeFromLotSizeFilter);
    }
    return quantity;
  }

  private NewOrderResponse createNewBuyMarketOrder(SymbolInfo symbolInfo, BigDecimal roundedQuantity) {
    return createNewOrder(symbolInfo.getSymbol(),
                          BUY,
                          MARKET,
                          null,
                          roundedQuantity.toPlainString(),
                          null);
  }

  /**
   * Sell available balance.
   *
   * @param symbolInfo Symbol information
   * @param quantity   Quantity to sell
   */
  public void sellAvailableBalance(SymbolInfo symbolInfo, BigDecimal quantity) {
    logger.log("Sell available balance for " + symbolInfo.getSymbol() + ", quantity=" + quantity);
    String asset = getAssetFromSymbolInfo(symbolInfo);
    BigDecimal myBalance = waitUntilHaveBalance(asset, quantity);
    BigDecimal roundedBidQuantity = roundQuantity(symbolInfo, myBalance);
    createNewSellMarketOrder(symbolInfo.getSymbol(), roundedBidQuantity.toPlainString());
  }

  private void createNewSellMarketOrder(String symbol, String quantity) {
    createNewOrder(symbol, SELL, MARKET, null, quantity, null);
  }

  /**
   * Place sell order with new price.
   *
   * @param symbolInfo  Symbol information
   * @param priceToSell Price of new sell order
   * @param quantity    Quantity of new sell order
   */
  public void placeSellOrder(SymbolInfo symbolInfo, BigDecimal priceToSell, BigDecimal quantity) {
    logger.log("Place sell order: " + symbolInfo.getSymbol() + ", priceToSell=" + priceToSell);
    String asset = getAssetFromSymbolInfo(symbolInfo);
    BigDecimal balance = waitUntilHaveBalance(asset, quantity);
    createSellLimitOrder(symbolInfo, priceToSell, balance);
  }

  /**
   * Extract two orders out of the provided one.
   *
   * @param symbolInfo     symbol information
   * @param orderToExtract order to be extracted
   */
  public void extractTwoSellOrders(SymbolInfo symbolInfo, OrderWrapper orderToExtract) {
    logger.log("Extract order: " + symbolInfo.getSymbol());
    String asset = getAssetFromSymbolInfo(symbolInfo);
    BigDecimal balance = waitUntilHaveBalance(asset, orderToExtract.getQuantity());
    logger.log("balance: " + balance);
    BigDecimal orderPrice = orderToExtract.getOrderPrice();
    logger.log("orderPrice: " + orderPrice);
    BigDecimal minValueFromLotSizeFilter = getValueFromFilter(symbolInfo, SymbolFilter::getMinQty, LOT_SIZE);
    logger.log("minValueFromLotSizeFilter: " + minValueFromLotSizeFilter);
    BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo,
                                                                  SymbolFilter::getMinNotional,
                                                                  MIN_NOTIONAL,
                                                                  NOTIONAL);
    logger.log("minValueFromMinNotionalFilter: " + minValueFromMinNotionalFilter);
    BigDecimal extractedBalance = getExtractedBalance(minValueFromMinNotionalFilter,
                                                      orderPrice,
                                                      minValueFromLotSizeFilter);
    logger.log("extractedBalance: " + extractedBalance);

    createSellLimitOrder(symbolInfo, orderToExtract.getOrderPrice(), extractedBalance);
    BigDecimal minPriceTickSize = getValueFromFilter(symbolInfo, SymbolFilter::getTickSize, PRICE_FILTER);
    createSellLimitOrder(symbolInfo,
                         orderToExtract.getOrderPrice().add(minPriceTickSize),
                         balance.subtract(extractedBalance));
  }

  private String getAssetFromSymbolInfo(SymbolInfo symbolInfo) {
    return symbolInfo.getSymbol().substring(0, symbolInfo.getSymbol().length() - 3);
  }

  private BigDecimal waitUntilHaveBalance(String asset, BigDecimal quantity) {
    BigDecimal myBalance = getMyBalance(asset);
    if (myBalance.compareTo(quantity) >= 0) {
      return myBalance;
    } else {
      sleep(500, logger);
      return waitUntilHaveBalance(asset, quantity);
    }
  }

  private BigDecimal getExtractedBalance(BigDecimal minValueFromMinNotionalFilter,
                                         BigDecimal orderPrice,
                                         BigDecimal minValueFromLotSizeFilter) {
    BigDecimal notRoundedValue = minValueFromMinNotionalFilter.divide(orderPrice, 8, CEILING);
    BigDecimal remainder = notRoundedValue.remainder(minValueFromLotSizeFilter);
    return notRoundedValue.subtract(remainder)
                          .add(minValueFromLotSizeFilter)
                          .add(minValueFromLotSizeFilter);
  }

  private void createSellLimitOrder(SymbolInfo symbolInfo, BigDecimal priceToSell, BigDecimal balance) {
    BigDecimal minBalance = getMinBalance(balance, priceToSell, symbolInfo);
    logger.log("minBalance: " + minBalance);
    BigDecimal roundedBidQuantity = roundQuantity(symbolInfo, minBalance);
    BigDecimal roundedPriceToSell = roundPrice(symbolInfo, priceToSell);
    createNewSellLimitOrder(symbolInfo.getSymbol(),
                            roundedBidQuantity.toPlainString(),
                            roundedPriceToSell.toPlainString());
  }

  private BigDecimal getMinBalance(BigDecimal balance, BigDecimal priceToSell, SymbolInfo symbolInfo) {
    BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo,
                                                                  SymbolFilter::getMinNotional,
                                                                  MIN_NOTIONAL,
                                                                  NOTIONAL);
    BigDecimal btcAmount = balance.multiply(priceToSell);
    if (btcAmount.compareTo(minValueFromMinNotionalFilter) < 0) {
      logger.log("Calling recursively: balance: " + balance);
      return getMinBalance(balance.multiply(new BigDecimal("2")), priceToSell, symbolInfo);
    }
    return balance;
  }

  private BigDecimal roundQuantity(SymbolInfo symbolInfo, BigDecimal quantity) {
    return round(symbolInfo,
                 quantity,
                 LOT_SIZE,
                 SymbolFilter::getStepSize,
                 (roundedValue, valueFromFilter) -> roundedValue);
  }

  private BigDecimal roundPrice(SymbolInfo symbolInfo, BigDecimal price) {
    return round(symbolInfo,
                 price,
                 PRICE_FILTER,
                 SymbolFilter::getTickSize,
                 BigDecimal::add);
  }

  private BigDecimal round(SymbolInfo symbolInfo,
                           BigDecimal amountToRound,
                           FilterType filterType,
                           Function<SymbolFilter, String> symbolFilterFunction,
                           BinaryOperator<BigDecimal> roundUpFunction) {
    BigDecimal valueFromFilter = getValueFromFilter(symbolInfo, symbolFilterFunction, filterType);
    BigDecimal remainder = amountToRound.remainder(valueFromFilter);
    BigDecimal roundedValue = amountToRound.subtract(remainder);
    return roundUpFunction.apply(roundedValue, valueFromFilter);
  }

  private BigDecimal getValueFromFilter(SymbolInfo symbolInfo,
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

  private void createNewSellLimitOrder(String symbol, String quantity, String price) {
    createNewOrder(symbol, SELL, LIMIT, GTC, quantity, price);
  }

  private NewOrderResponse createNewOrder(String symbol,
                                          OrderSide orderSide,
                                          OrderType orderType,
                                          TimeInForce timeInForce,
                                          String quantity,
                                          String price) {
    NewOrderResponse response = executeCall(binanceApi.newOrder(symbol,
                                                                orderSide,
                                                                orderType,
                                                                timeInForce,
                                                                quantity,
                                                                price,
                                                                RESULT,
                                                                DEFAULT_RECEIVING_WINDOW,
                                                                currentTimeMillis()));
    logger.log("response: " + response);
    return response;
  }

  /**
   * Check an order's status.
   */
  public void checkOrderStatus(String symbol, Long orderId) {
    logger.log("Check order status for " + symbol + ", orderId=" + orderId);
    executeCall(binanceApi.getOrderStatus(symbol,
                                          orderId,
                                          DEFAULT_RECEIVING_WINDOW,
                                          currentTimeMillis()));
  }

  /**
   * Cancel an active order.
   *
   * @param orderWrapper order status request parameters
   */
  public void cancelOrder(OrderWrapper orderWrapper) {
    logger.log("Cancel request for " + orderWrapper);
    executeCall(binanceApi.cancelOrder(orderWrapper.getOrder().getSymbol(),
                                       orderWrapper.getOrder().getClientOrderId(),
                                       DEFAULT_RECEIVING_WINDOW,
                                       currentTimeMillis()));
  }

  /**
   * Get all open orders on a symbol.
   *
   * @return a list of all account open orders on a symbol.
   */
  public List<Order> getOpenOrders() {
    logger.log("Get open orders");
    return executeCall(binanceApi.getOpenOrders(DEFAULT_RECEIVING_WINDOW, currentTimeMillis()));
  }

  /**
   * Returns actual balance per asset.
   *
   * @param asset Asset of the crypto
   * @return Actual balance
   */
  public BigDecimal getMyBalance(String asset) {
    logger.log("Get my balance for " + asset);
    BigDecimal myBalance = getAccount()
        .getBalances()
        .parallelStream()
        .filter(balance -> balance.getAsset().equals(asset))
        .map(AssetBalance::getFree)
        .map(BigDecimal::new)
        .findFirst()
        .orElse(ZERO);
    logger.log("My balance in currency: " + asset + ", is: " + myBalance);
    return myBalance;
  }

  /**
   * Returns actual balance.
   *
   * @return Actual balance
   */
  public BigDecimal getMyActualBalance() {
    logger.log("Get my actual balance");
    return getAccount()
        .getBalances()
        .parallelStream()
        .filter(assetBalance -> !assetBalance.getAsset().equals("NFT"))
        .map(this::mapToAssetAndBalance)
        .filter(pair -> pair.getLeft().compareTo(ZERO) > 0)
        .map(this::mapToBtcBalance)
        .reduce(ZERO, BigDecimal::add);
  }

  private Account getAccount() {
    return executeCall(binanceApi.getAccount(DEFAULT_RECEIVING_WINDOW, currentTimeMillis()));
  }

  private Pair<BigDecimal, String> mapToAssetAndBalance(AssetBalance assetBalance) {
    BigDecimal balance = new BigDecimal(assetBalance.getFree()).add(new BigDecimal(assetBalance.getLocked()));
    return Pair.of(balance, assetBalance.getAsset());
  }

  private BigDecimal mapToBtcBalance(Pair<BigDecimal, String> pair) {
    if (pair.getRight().equals(ASSET_BTC)) {
      return pair.getLeft();
    } else {
      try {
        return getOrderBook(pair.getRight() + ASSET_BTC, 20)
            .getBids()
            .parallelStream()
            .map(OrderBookEntry::getPrice)
            .map(BigDecimal::new)
            .max(BigDecimal::compareTo)
            .orElse(ZERO)
            .multiply(pair.getLeft());
      } catch (BinanceApiException e) {
        return ZERO;
      }
    }
  }

  private OrderBook getOrderBook(String symbol, Integer limit) {
    return executeCall(binanceApi.getOrderBook(symbol, limit));
  }

  /**
   * Returns new price to sell. If crypto was bought with different prices, then new price to sell must be calculated.
   * If crypto with the same price was bought, then no change is done to the price to sell.
   *
   * @param symbolInfo    symbol information
   * @param orderResponse response from new order creation
   * @param orderWrapper  wrapper holding trade information
   * @return new price to sell
   */
  public BigDecimal getNewPriceToSell(SymbolInfo symbolInfo,
                                      NewOrderResponse orderResponse,
                                      OrderWrapper orderWrapper) {
    List<Trade> myTrades = getMyTrades(symbolInfo.getSymbol(), orderResponse.getOrderId());
    BigDecimal boughtQuantity = new BigDecimal(orderResponse.getExecutedQty());
    logger.log("boughtQuantity: " + boughtQuantity);
    BigDecimal boughtBtcs = getBoughtBtcsFromTrades(myTrades);
    logger.log("boughtBtcs: " + boughtBtcs);
    BigDecimal boughtPrice = boughtBtcs.divide(boughtQuantity, 8, CEILING);
    logger.log("boughtPrice: " + boughtPrice);
    BigDecimal priceDifference = boughtPrice.subtract(orderWrapper.getCurrentPrice());
    logger.log("priceDifference: " + priceDifference);
    BigDecimal newPriceToSell = orderWrapper.getPriceToSell().add(priceDifference);
    logger.log("newPriceToSell: " + newPriceToSell);
    return newPriceToSell;
  }

  private List<Trade> getMyTrades(String symbol, Long orderId) {
    logger.log("Get my trades for " + symbol + ", orderId=" + orderId);
    sleep(5 * 1000, logger);
    return executeCall(binanceApi.getMyTrades(symbol, orderId + "", currentTimeMillis()));
  }

  private void sleep(int millis, LambdaLogger logger) {
    logger.log("Sleeping for " + millis / 1000 + " seconds");
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      logger.log("Error during sleeping");
    }
  }

  private <T> T executeCall(Call<T> call) {
    Response<T> response;
    try {
      response = call.execute();
    } catch (IOException e) {
      logger.log("Exception during execution of request: " + e);
      throw new BinanceApiException(e);
    }
    if (response.isSuccessful()) {
      return response.body();
    } else {
      logger.log("Call failed: " + response);
      logger.log("Call failed with code=" + response.code() + ", and status=" + response.message());
      if (response.body() != null) {
        logger.log("Call failed with body  " + response.body());
      }
      try (ResponseBody errorBody = response.errorBody()) {
        if (errorBody != null) {
          try {
            logger.log("Call failed with errorBody  " + errorBody.string());
          } catch (IOException e) {
            logger.log("Exception during parsing response: " + e);
            throw new BinanceApiException(e);
          }
        }
      }
      throw new BinanceApiException(response.toString());
    }
  }

  private BigDecimal getBoughtBtcsFromTrades(List<Trade> myTrades) {
    return myTrades.stream()
                   .map(trade -> new BigDecimal(trade.getQty()).multiply(new BigDecimal(trade.getPrice())))
                   .reduce(ZERO, BigDecimal::add);
  }
}
