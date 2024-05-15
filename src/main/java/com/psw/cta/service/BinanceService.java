package com.psw.cta.service;

import static com.psw.cta.utils.BinanceApiConstants.API_BASE_URL;
import static com.psw.cta.utils.BinanceApiConstants.DEFAULT_RECEIVING_WINDOW;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.binance.Account;
import com.psw.cta.dto.binance.CancelOrderRequest;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.CandlestickInterval;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.NewOrder;
import com.psw.cta.dto.binance.NewOrderResponse;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.OrderBook;
import com.psw.cta.dto.binance.OrderRequest;
import com.psw.cta.dto.binance.OrderStatusRequest;
import com.psw.cta.dto.binance.TickerPrice;
import com.psw.cta.dto.binance.TickerStatistics;
import com.psw.cta.dto.binance.Trade;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.security.AuthenticationInterceptor;
import java.io.IOException;
import java.util.List;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Implementation of Binance's REST API using Retrofit with synchronous/blocking method calls.
 */
public class BinanceService {

  private final BinanceApi binanceApi;

  /**
   * Default constructor.
   *
   * @param apiKey Api key
   * @param secret Api secret
   */
  public BinanceService(String apiKey, String secret) {
    binanceApi = new Retrofit.Builder().baseUrl(API_BASE_URL)
                                       .client(getOkHttpClient(apiKey, secret))
                                       .addConverterFactory(JacksonConverterFactory.create())
                                       .build()
                                       .create(BinanceApi.class);
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
   * Kline/candlestick bars for a symbol. Klines are uniquely identified by their open time.
   *
   * @param symbol    symbol to aggregate (mandatory)
   * @param interval  candlestick interval (mandatory)
   * @param limit     Default 500; max 1000 (optional)
   * @param startTime Timestamp in ms to get candlestick bars from INCLUSIVE (optional).
   * @param endTime   Timestamp in ms to get candlestick bars until INCLUSIVE (optional).
   * @return a candlestick bar for the given symbol and interval
   */
  public List<Candlestick> getCandlestickBars(String symbol,
                                              CandlestickInterval interval,
                                              Integer limit,
                                              Long startTime,
                                              Long endTime) {
    return executeCall(binanceApi.getCandlestickBars(symbol,
                                                     interval.getIntervalId(),
                                                     limit,
                                                     startTime,
                                                     endTime));
  }

  /**
   * Get 24 hour price change statistics for all symbols.
   */
  public List<TickerStatistics> getAll24HrPriceStatistics() {
    return executeCall(binanceApi.getAll24HrPriceStatistics());
  }

  /**
   * Get latest price for <code>symbol</code>.
   *
   * @param symbol ticker symbol (e.g. ETHBTC)
   */
  public TickerPrice getPrice(String symbol) {
    return executeCall(binanceApi.getLatestPrice(symbol));
  }

  /**
   * Send in a new order.
   *
   * @param order the new order to submit.
   * @return a response containing details about the newly placed order.
   */
  public NewOrderResponse newOrder(NewOrder order) {
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

  /**
   * Check an order's status.
   *
   * @param orderStatusRequest order status request options/filters
   * @return an order
   */
  public Order getOrderStatus(OrderStatusRequest orderStatusRequest) {
    return executeCall(binanceApi.getOrderStatus(orderStatusRequest.getSymbol(),
                                                 orderStatusRequest.getOrderId(),
                                                 orderStatusRequest.getOrigClientOrderId(),
                                                 orderStatusRequest.getRecvWindow(),
                                                 orderStatusRequest.getTimestamp()));
  }

  /**
   * Cancel an active order.
   *
   * @param cancelOrderRequest order status request parameters
   */
  public void cancelOrder(CancelOrderRequest cancelOrderRequest) {
    executeCall(binanceApi.cancelOrder(cancelOrderRequest.getSymbol(),
                                       cancelOrderRequest.getOrderId(),
                                       cancelOrderRequest.getOrigClientOrderId(),
                                       cancelOrderRequest.getNewClientOrderId(),
                                       cancelOrderRequest.getRecvWindow(),
                                       cancelOrderRequest.getTimestamp()));
  }

  /**
   * Get all open orders on a symbol.
   *
   * @param orderRequest order request parameters
   * @return a list of all account open orders on a symbol.
   */
  public List<Order> getOpenOrders(OrderRequest orderRequest) {
    return executeCall(binanceApi.getOpenOrders(orderRequest.getSymbol(),
                                                orderRequest.getRecvWindow(),
                                                orderRequest.getTimestamp()));
  }

  /**
   * Get current account information.
   */
  public Account getAccount(Long recvWindow, Long timestamp) {
    return executeCall(binanceApi.getAccount(recvWindow, timestamp));
  }

  /**
   * Get current account information using default parameters.
   */
  public Account getAccount() {
    return getAccount(DEFAULT_RECEIVING_WINDOW, System.currentTimeMillis());
  }

  /**
   * Get trades for a specific account and symbol.
   *
   * @param symbol  symbol to get trades from
   * @param orderId id of the order
   * @return a list of trades
   */
  public List<Trade> getMyTrades(String symbol, String orderId) {
    return executeCall(binanceApi.getMyTrades(symbol,
                                              orderId,
                                              null,
                                              null,
                                              DEFAULT_RECEIVING_WINDOW,
                                              System.currentTimeMillis()));
  }

  private <T> T executeCall(Call<T> call) {
    Response<T> response;
    try {
      response = call.execute();
    } catch (IOException e) {
      throw new BinanceApiException(e);
    }
    if (response.isSuccessful()) {
      return response.body();
    } else {
      throw new BinanceApiException(response.toString());
    }
  }
}
