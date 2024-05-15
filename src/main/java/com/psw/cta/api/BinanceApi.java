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
import com.psw.cta.dto.binance.TickerPrice;
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

  @GET("/api/v1/exchangeInfo")
  Call<ExchangeInfo> getExchangeInfo();

  @GET("/api/v3/depth")
  Call<OrderBook> getOrderBook(@Query("symbol") String symbol, @Query("limit") Integer limit);

  @GET("/api/v3/klines")
  Call<List<Candlestick>> getCandlestickBars(@Query("symbol") String symbol,
                                             @Query("interval") String interval,
                                             @Query("limit") Integer limit,
                                             @Query("startTime") Long startTime,
                                             @Query("endTime") Long endTime);

  @GET("/api/v3/ticker/24hr")
  Call<List<TickerStatistics>> getAll24HrPriceStatistics();

  @GET("/api/v3/ticker/price")
  Call<TickerPrice> getLatestPrice(@Query("symbol") String symbol);

  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @POST("/api/v3/order")
  Call<NewOrderResponse> newOrder(@Query("symbol") String symbol,
                                  @Query("side") OrderSide side,
                                  @Query("type") OrderType type,
                                  @Query("timeInForce") TimeInForce timeInForce,
                                  @Query("quantity") String quantity,
                                  @Query("price") String price,
                                  @Query("newClientOrderId") String newClientOrderId,
                                  @Query("stopPrice") String stopPrice,
                                  @Query("icebergQty") String icebergQty,
                                  @Query("newOrderRespType") NewOrderResponseType newOrderRespType,
                                  @Query("recvWindow") Long recvWindow,
                                  @Query("timestamp") Long timestamp);

  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @GET("/api/v3/order")
  Call<Order> getOrderStatus(@Query("symbol") String symbol,
                             @Query("orderId") Long orderId,
                             @Query("origClientOrderId") String origClientOrderId,
                             @Query("recvWindow") Long recvWindow,
                             @Query("timestamp") Long timestamp);

  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @DELETE("/api/v3/order")
  Call<CancelOrderResponse> cancelOrder(@Query("symbol") String symbol,
                                        @Query("orderId") Long orderId,
                                        @Query("origClientOrderId") String origClientOrderId,
                                        @Query("newClientOrderId") String newClientOrderId,
                                        @Query("recvWindow") Long recvWindow,
                                        @Query("timestamp") Long timestamp);

  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @GET("/api/v3/openOrders")
  Call<List<Order>> getOpenOrders(@Query("symbol") String symbol,
                                  @Query("recvWindow") Long recvWindow,
                                  @Query("timestamp") Long timestamp);

  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @GET("/api/v3/account")
  Call<Account> getAccount(@Query("recvWindow") Long recvWindow,
                           @Query("timestamp") Long timestamp);

  @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
  @GET("/api/v3/myTrades")
  Call<List<Trade>> getMyTrades(@Query("symbol") String symbol,
                                @Query("orderId") String orderId,
                                @Query("limit") Integer limit,
                                @Query("fromId") Long fromId,
                                @Query("recvWindow") Long recvWindow,
                                @Query("timestamp") Long timestamp);
}
