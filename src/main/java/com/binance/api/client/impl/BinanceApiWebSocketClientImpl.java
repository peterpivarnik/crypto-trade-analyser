package com.binance.api.client.impl;

import static com.binance.api.client.constant.BinanceApiConstants.WS_API_BASE_URL;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.event.AggTradeEvent;
import com.binance.api.client.domain.event.AllMarketTickersEvent;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.event.DepthEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.Closeable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

/**
 * Binance API WebSocket client implementation using OkHttp.
 */
public class BinanceApiWebSocketClientImpl implements BinanceApiWebSocketClient, Closeable {

  private final OkHttpClient client;

  public BinanceApiWebSocketClientImpl(OkHttpClient client) {
    this.client = client;
  }

  @Override
  public Closeable onDepthEvent(String symbols, BinanceApiCallback<DepthEvent> callback) {
    final String channel = Arrays.stream(symbols.split(","))
                                 .map(String::trim)
                                 .map(s -> String.format("%s@depth", s))
                                 .collect(Collectors.joining("/"));
    return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, DepthEvent.class));
  }

  @Override
  public Closeable onCandlestickEvent(String symbols,
                                      CandlestickInterval interval,
                                      BinanceApiCallback<CandlestickEvent> callback) {
    final String channel = Arrays.stream(symbols.split(","))
                                 .map(String::trim)
                                 .map(s -> String.format("%s@kline_%s",
                                                         s,
                                                         interval.getIntervalId()))
                                 .collect(Collectors.joining("/"));
    return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, CandlestickEvent.class));
  }

  /**
   * Method called on Agg trade event.
   *
   * @param symbols  market (one or coma-separated) symbol(s) to subscribe to
   * @param callback the callback to call on new events
   * @return Closeable
   */
  public Closeable onAggTradeEvent(String symbols, BinanceApiCallback<AggTradeEvent> callback) {
    final String channel = Arrays.stream(symbols.split(","))
                                 .map(String::trim)
                                 .map(s -> String.format("%s@aggTrade", s))
                                 .collect(Collectors.joining("/"));
    return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, AggTradeEvent.class));
  }

  public Closeable onUserDataUpdateEvent(String listenKey, BinanceApiCallback<UserDataUpdateEvent> callback) {
    return createNewWebSocket(listenKey, new BinanceApiWebSocketListener<>(callback, UserDataUpdateEvent.class));
  }

  /**
   * Method called on all market tickers event.
   *
   * @param callback the callback to call on new events
   * @return Closeable
   */
  public Closeable onAllMarketTickersEvent(BinanceApiCallback<List<AllMarketTickersEvent>> callback) {
    final String channel = "!ticker@arr";
    return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, new TypeReference<>() {
    }));
  }

  @Deprecated
  @Override
  public void close() {
  }

  private Closeable createNewWebSocket(String channel, BinanceApiWebSocketListener<?> listener) {
    String streamingUrl = String.format("%s/%s", WS_API_BASE_URL, channel);
    Request request = new Request.Builder().url(streamingUrl).build();
    final WebSocket webSocket = client.newWebSocket(request, listener);
    return () -> {
      final int code = 1000;
      listener.onClosing(webSocket, code, "");
      webSocket.close(code, null);
      listener.onClosed(webSocket, code, "");
    };
  }
}
