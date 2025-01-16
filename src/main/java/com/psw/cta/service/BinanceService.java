package com.psw.cta.service;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.Account;
import com.psw.cta.dto.binance.AssetBalance;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.CandlestickInterval;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.NewOrder;
import com.psw.cta.dto.binance.NewOrderResponse;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.OrderBook;
import com.psw.cta.dto.binance.OrderBookEntry;
import com.psw.cta.dto.binance.OrderSide;
import com.psw.cta.dto.binance.SymbolFilter;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.dto.binance.TickerStatistics;
import com.psw.cta.dto.binance.Trade;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.exception.CryptoTraderException;
import com.psw.cta.security.AuthenticationInterceptor;
import com.psw.cta.utils.CommonUtils;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.tuple.Pair;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BiFunction;

import static com.psw.cta.dto.binance.FilterType.MIN_NOTIONAL;
import static com.psw.cta.dto.binance.FilterType.NOTIONAL;
import static com.psw.cta.dto.binance.OrderSide.BUY;
import static com.psw.cta.dto.binance.OrderSide.SELL;
import static com.psw.cta.dto.binance.OrderType.LIMIT;
import static com.psw.cta.dto.binance.OrderType.MARKET;
import static com.psw.cta.dto.binance.TimeInForce.GTC;
import static com.psw.cta.utils.BinanceApiConstants.API_BASE_URL;
import static com.psw.cta.utils.BinanceApiConstants.DEFAULT_RECEIVING_WINDOW;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.roundAmount;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.lang.System.currentTimeMillis;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.SECONDS;

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
   * Get order book of a symbol.
   *
   * @param symbol ticker symbol (e.g. ETHBTC)
   * @param limit  depth of the order book (max 100)
   */
  public OrderBook getOrderBook(String symbol, Integer limit) {
    return executeCall(binanceApi.getOrderBook(symbol, limit));
  }

  /**
   * Returns current price for provided symbol
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
   * Returns order book entry with min price.
   *
   * @param symbol Order symbol
   * @return OrderBookEntry with min price
   */
  public OrderBookEntry getMinOrderBookEntry(String symbol) {
    logger.log("Get min order book entry for " + symbol);
    return getOrderBook(symbol, 20)
        .getAsks()
        .parallelStream()
        .min(comparing(OrderBookEntry::getPrice))
        .orElseThrow(RuntimeException::new);
  }


  /**
   * Returns Kline/Candlestick bars for a symbol.
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
   * Get 24 hour price change statistics for all symbols.
   */
  public List<TickerStatistics> getAll24hTickers() {
    logger.log("Get 24 h Tickers");
    return executeCall(binanceApi.getAll24HrPriceStatistics());
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
    BigDecimal roundedBidQuantity = roundAmount(symbolInfo, myBalance);
    createNewOrder(symbolInfo.getSymbol(), SELL, roundedBidQuantity);
  }

  /**
   * Place sell order with new price.
   *
   * @param symbolInfo  Symbol information
   * @param priceToSell Price of new sell order
   * @param quantity    Quantity of new sell order
   * @param roundPrice  Function for rounding price
   */
  public void placeSellOrder(SymbolInfo symbolInfo,
                             BigDecimal priceToSell,
                             BigDecimal quantity,
                             BiFunction<SymbolInfo, BigDecimal, BigDecimal> roundPrice) {
    logger.log("Place sell order: " + symbolInfo.getSymbol() + ", priceToSell=" + priceToSell);
    String asset = getAssetFromSymbolInfo(symbolInfo);
    BigDecimal balance = waitUntilHaveBalance(asset, quantity);
    BigDecimal roundedBidQuantity = roundAmount(symbolInfo, balance);
    BigDecimal roundedPriceToSell = roundPrice.apply(symbolInfo, priceToSell);
    NewOrder sellOrder = new NewOrder(symbolInfo.getSymbol(),
                                      SELL,
                                      LIMIT,
                                      GTC,
                                      roundedBidQuantity.toPlainString(),
                                      roundedPriceToSell.toPlainString());
    createNewOrder(sellOrder);
  }

  /**
   * Creates new sell/buy order.
   *
   * @param symbol           Symbol information
   * @param orderSide        Buy or sell
   * @param roundedMyQuatity Quantity of new order
   * @return New Order response
   */
  public NewOrderResponse createNewOrder(String symbol, OrderSide orderSide, BigDecimal roundedMyQuatity) {
    logger.log("Create new order for " + symbol + ", and side=" + orderSide + ", and quantity=" + roundedMyQuatity);
    NewOrder buyOrder = new NewOrder(symbol,
                                     orderSide,
                                     MARKET,
                                     null,
                                     roundedMyQuatity.toPlainString());
    return createNewOrder(buyOrder);
  }

  private NewOrderResponse createNewOrder(NewOrder order) {
    logger.log("My new order: " + order);
    return executeCall(binanceApi.newOrder(order.getSymbol(),
                                           order.getSide(),
                                           order.getType(),
                                           order.getTimeInForce(),
                                           order.getQuantity(),
                                           order.getPrice(),
                                           order.getNewClientOrderId(),
                                           order.getStopPrice(),
                                           order.getIcebergQty(),
                                           order.getNewOrderRespType(),
                                           order.getRecvWindow(),
                                           order.getTimestamp()));
  }


  private String getAssetFromSymbolInfo(SymbolInfo symbolInfo) {
    return symbolInfo.getSymbol().substring(0, symbolInfo.getSymbol().length() - 3);
  }

  private BigDecimal waitUntilHaveBalance(String asset, BigDecimal quantity) {
    BigDecimal myBalance = getMyBalance(asset);
    if (myBalance.compareTo(quantity) >= 0) {
      return myBalance;
    } else {
      CommonUtils.sleep(500, logger);
      return waitUntilHaveBalance(asset, quantity);
    }
  }

  /**
   * Check an order's status.
   */
  public void checkOrderStatus(String symbol, Long orderId) {
    logger.log("Check order status for " + symbol + ", orderId=" + orderId);
    executeCall(binanceApi.getOrderStatus(symbol,
                                          orderId,
                                          null,
                                          DEFAULT_RECEIVING_WINDOW,
                                          currentTimeMillis()));
  }

  /**
   * Cancel an active order.
   *
   * @param orderWrapper order status request parameters
   */
  public void cancelRequest(OrderWrapper orderWrapper) {
    logger.log("Cancel request for " + orderWrapper);
    executeCall(binanceApi.cancelOrder(orderWrapper.getOrder().getSymbol(),
                                       null,
                                       orderWrapper.getOrder().getClientOrderId(),
                                       null,
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
    return executeCall(binanceApi.getOpenOrders(null,
                                                DEFAULT_RECEIVING_WINDOW,
                                                currentTimeMillis()));
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

  /**
   * Buy order.
   *
   * @param symbolInfo Symbol information
   * @param btcAmount  BTC amount
   * @param price      price of new order
   * @return bought quantity
   */
  public Pair<Long, BigDecimal> buy(SymbolInfo symbolInfo, BigDecimal btcAmount, BigDecimal price) {
    logger.log("Buy " + symbolInfo.getSymbol() + ", btcAmount=" + btcAmount + ", price=" + price);
    BigDecimal myQuantity = btcAmount.divide(price, 8, CEILING);
    BigDecimal minNotionalFromMinNotionalFilter = getValueFromFilter(symbolInfo,
                                                                     SymbolFilter::getMinNotional,
                                                                     MIN_NOTIONAL,
                                                                     NOTIONAL);
    BigDecimal myQuantityToBuy = myQuantity.max(minNotionalFromMinNotionalFilter);
    BigDecimal roundedQuantity = roundAmount(symbolInfo, myQuantityToBuy);
    NewOrderResponse newOrder = createNewOrder(symbolInfo.getSymbol(), BUY, roundedQuantity);
    logger.log("response: " + newOrder);
    return Pair.of(newOrder.getOrderId(), roundedQuantity);
  }

  /**
   * Get trades for a specific account and symbol.
   *
   * @param symbol  symbol to get trades from
   * @param orderId id of the order
   * @return a list of trades
   */
  public List<Trade> getMyTrades(String symbol, Long orderId) {
    logger.log("Get my trades for " + symbol + ", orderId=" + orderId);
    sleep(5 * 1000, logger);
    return executeCall(binanceApi.getMyTrades(symbol,
                                              orderId + "",
                                              null,
                                              null,
                                              null,
                                              null,
                                              currentTimeMillis()));
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
}
