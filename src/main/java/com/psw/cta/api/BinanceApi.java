package com.psw.cta.api;

import static com.psw.cta.utils.BinanceApiConstants.ENDPOINT_SECURITY_TYPE_SIGNED_HEADER;

import com.psw.cta.dto.binance.Account;
import com.psw.cta.dto.binance.CancelOrderResponse;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.NewOrderResponse;
import com.psw.cta.dto.binance.NewOrderResponseType;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.OrderBook;
import com.psw.cta.dto.binance.OrderSide;
import com.psw.cta.dto.binance.OrderType;
import com.psw.cta.dto.binance.TickerStatistics;
import com.psw.cta.dto.binance.TimeInForce;
import com.psw.cta.dto.binance.Trade;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Binance's REST API URL mappings and endpoint security configuration.
 */
public interface BinanceApi {

  /**
   * Return exchange info.
   *
   * @return exchange info
   */
  @GET("/api/v1/exchangeInfo")
  Call<ExchangeInfo> getExchangeInfo();

  /**
   * Return order book of symbol.
   *
   * @param symbol order symbol
   * @param limit  limit
   * @return order book
   */
  @GET("/api/v3/depth")
  Call<OrderBook> getOrderBook(@Query("symbol") String symbol, @Query("limit") Integer limit);

  /**
   * Get candle sticks.
   *
   * @param symbol    order symbol
   * @param interval  candlestick interval
   * @param limit     number of candlestics
   * @param startTime start time of interval
   * @param endTime   end time of interval
   * @return candle sticks
   */
  @GET("/api/v3/klines")
  Call<List<Candlestick>> getCandlestickBars(@Query("symbol") String symbol,
                                             @Query("interval") String interval,
                                             @Query("limit") Integer limit,
                                             @Query("startTime") Long startTime,
                                             @Query("endTime") Long endTime);

  /**
   * Return price statistics.
   *
   * @return statistics
   */
  @GET("/api/v3/ticker/24hr")
  Call<List<TickerStatistics>> getAll24HrPriceStatistics();

  /**
   * Place new order.
   *
   * @param symbol           orders symbol
   * @param side             orders side (Buy / Sell)
   * @param type             order type
   * @param timeInForce      indicate how long an order will remain active before it is executed or expires
   * @param quantity         order quantity
   * @param price            order price
   * @param newOrderRespType response type
   * @param recvWindow       number of milliseconds after timestamp the request is valid for.
   *                         If recvWindow is not sent, it defaults to 5000.
   * @param timestamp        current timestamp
   * @return response from creation of new order
   */
  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @POST("/api/v3/order")
  Call<NewOrderResponse> newOrder(@Query("symbol") String symbol,
                                  @Query("side") OrderSide side,
                                  @Query("type") OrderType type,
                                  @Query("timeInForce") TimeInForce timeInForce,
                                  @Query("quantity") String quantity,
                                  @Query("price") String price,
                                  @Query("newOrderRespType") NewOrderResponseType newOrderRespType,
                                  @Query("recvWindow") Long recvWindow,
                                  @Query("timestamp") Long timestamp);

  /**
   * Return status of the order.
   *
   * @param symbol     order's symbol
   * @param orderId    id of order
   * @param recvWindow number of milliseconds after timestamp the request is valid for.
   *                   If recvWindow is not sent, it defaults to 5000.
   * @param timestamp  current timestamp
   * @return Order status information
   */
  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @GET("/api/v3/order")
  Call<Order> getOrderStatus(@Query("symbol") String symbol,
                             @Query("orderId") Long orderId,
                             @Query("recvWindow") Long recvWindow,
                             @Query("timestamp") Long timestamp);

  /**
   * Cancel already open order.
   *
   * @param symbol            order's symbol
   * @param origClientOrderId id of order
   * @param recvWindow        number of milliseconds after timestamp the request is valid for.
   *                          If recvWindow is not sent, it defaults to 5000.
   * @param timestamp         current timestamp
   * @return cancel order response
   */
  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @DELETE("/api/v3/order")
  Call<CancelOrderResponse> cancelOrder(@Query("symbol") String symbol,
                                        @Query("origClientOrderId") String origClientOrderId,
                                        @Query("recvWindow") Long recvWindow,
                                        @Query("timestamp") Long timestamp);

  /**
   * Returns list of open orders.
   *
   * @param recvWindow number of milliseconds after timestamp the request is valid for.
   *                   If recvWindow is not sent, it defaults to 5000.
   * @param timestamp  current timestamp
   * @return open orders
   */
  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @GET("/api/v3/openOrders")
  Call<List<Order>> getOpenOrders(@Query("recvWindow") Long recvWindow, @Query("timestamp") Long timestamp);

  /**
   * Returns information about account.
   *
   * @param recvWindow number of milliseconds after timestamp the request is valid for.
   *                   If recvWindow is not sent, it defaults to 5000.
   * @param timestamp  current timestamp
   * @return Account information
   */
  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @GET("/api/v3/account")
  Call<Account> getAccount(@Query("recvWindow") Long recvWindow, @Query("timestamp") Long timestamp);

  /**
   * Return information about trades.
   *
   * @param symbol    trade symbol
   * @param orderId   id of order, must be used in combination with symbol
   * @param timestamp current timestamp
   * @return list of executed trades
   */
  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @GET("/api/v3/myTrades")
  Call<List<Trade>> getMyTrades(@Query("symbol") String symbol,
                                @Query("orderId") String orderId,
                                @Query("timestamp") Long timestamp);
}
