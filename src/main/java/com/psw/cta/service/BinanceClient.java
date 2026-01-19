package com.psw.cta.service;

import static com.psw.cta.dto.binance.NewOrderResponseType.RESULT;
import static com.psw.cta.utils.BinanceApiConstants.DEFAULT_RECEIVING_WINDOW;
import static java.lang.System.currentTimeMillis;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.binance.Account;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.DelistResponse;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.NewOrderResponse;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.OrderBook;
import com.psw.cta.dto.binance.OrderSide;
import com.psw.cta.dto.binance.OrderType;
import com.psw.cta.dto.binance.TickerStatistics;
import com.psw.cta.dto.binance.TimeInForce;
import com.psw.cta.dto.binance.Trade;
import com.psw.cta.exception.BinanceApiException;
import java.io.IOException;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Client for interacting with Binance API. Provides methods for executing trading operations,
 * retrieving market data, and managing orders on the Binance exchange.
 */
public class BinanceClient {

    private final BinanceApi binanceApi;
    private final LambdaLogger logger;

    /**
     * Creates a new BinanceClient instance with authentication credentials and logging.
     *
     * @param logger     Logger for recording client operations
     * @param binanceApi Retrofit interface for Binance API
     */
    public BinanceClient(LambdaLogger logger, BinanceApi binanceApi) {
        this.logger = logger;
        this.binanceApi = binanceApi;
    }

    /**
     * Retrieves current exchange trading rules and symbol information.
     *
     * @return Exchange information containing trading rules and symbol details
     */
    public ExchangeInfo getExchangeInfo() {
        return executeCall(binanceApi.getExchangeInfo("SPOT"));
    }

    /**
     * Retrieves candlestick/kline data for a symbol within a specified time range.
     *
     * @param symbol     The trading pair symbol
     * @param intervalId Candlestick interval identifier
     * @param startTime  Start time in milliseconds
     * @param endTime    End time in milliseconds
     * @return List of candlestick data
     */
    public List<Candlestick> getCandlestickBars(String symbol, String intervalId, Long startTime, Long endTime) {
        return executeCall(binanceApi.getCandlestickBars(symbol, intervalId, null, startTime, endTime));
    }

    /**
     * Retrieves a limited number of candlestick/kline data for a symbol.
     *
     * @param symbol     The trading pair symbol
     * @param intervalId Candlestick interval identifier
     * @param limit      Maximum number of candlesticks to return
     * @return List of candlestick data
     */
    public List<Candlestick> getCandlestickBars(String symbol, String intervalId, Integer limit) {
        return executeCall(binanceApi.getCandlestickBars(symbol, intervalId, limit, null, null));
    }

    /**
     * Retrieves 24-hour price statistics for all symbols.
     *
     * @return List of 24-hour price statistics for all symbols
     */
    public List<TickerStatistics> getAll24HrPriceStatistics() {
        return executeCall(binanceApi.getAll24HrPriceStatistics());
    }

    /**
     * Places a new order on the exchange.
     *
     * @param symbol      The trading pair symbol
     * @param orderSide   Buy/Sell order side
     * @param orderType   Type of order (LIMIT, MARKET, etc.)
     * @param timeInForce Time the order will be active for
     * @param quantity    Amount of base asset to trade
     * @param price       Price per unit of base asset
     * @return Response containing the order details
     */
    public NewOrderResponse newOrder(String symbol,
                                     OrderSide orderSide,
                                     OrderType orderType,
                                     TimeInForce timeInForce,
                                     String quantity,
                                     String price) {
        return executeCall(binanceApi.newOrder(symbol,
                                               orderSide,
                                               orderType,
                                               timeInForce,
                                               quantity,
                                               price,
                                               RESULT,
                                               DEFAULT_RECEIVING_WINDOW,
                                               currentTimeMillis()));
    }

    /**
     * Checks the status of an order.
     *
     * @param symbol  The trading pair symbol
     * @param orderId ID of the order to check
     */
    public void getOrderStatus(String symbol, Long orderId) {
        executeCall(binanceApi.getOrderStatus(symbol, orderId, DEFAULT_RECEIVING_WINDOW, currentTimeMillis()));
    }

    /**
     * Cancels an active order.
     *
     * @param symbol        The trading pair symbol
     * @param clientOrderId Client order ID to cancel
     */
    public void cancelOrder(String symbol, String clientOrderId) {
        executeCall(binanceApi.cancelOrder(symbol, clientOrderId, DEFAULT_RECEIVING_WINDOW, currentTimeMillis()));
    }

    /**
     * Retrieves all open orders for the account.
     *
     * @return List of currently open orders
     */
    public List<Order> getOpenOrders() {
        return executeCall(binanceApi.getOpenOrders(DEFAULT_RECEIVING_WINDOW, currentTimeMillis()));
    }

    /**
     * Retrieves current account information.
     *
     * @return Account information including balances
     */
    public Account getAccount() {
        return executeCall(binanceApi.getAccount(DEFAULT_RECEIVING_WINDOW, currentTimeMillis()));
    }

    /**
     * Retrieves orderBook for a symbol.
     *
     * @param symbol The trading pair symbol
     * @param limit  Maximum number of orders to return
     * @return OrderBook containing bids and asks
     */
    public OrderBook getOrderBook(String symbol, Integer limit) {
        return executeCall(binanceApi.getOrderBook(symbol, limit));
    }

    /**
     * Retrieves trades for a specific order.
     *
     * @param symbol  The trading pair symbol
     * @param orderId ID of the order to get trades for
     * @return List of trades for the specified order
     */
    public List<Trade> getMyTrades(String symbol, String orderId) {
        return executeCall(binanceApi.getMyTrades(symbol, orderId, currentTimeMillis()));
    }

    /**
     * Retrieves the schedule of tokens to be delisted from the exchange.
     *
     * @return List of delist responses containing delisting schedule information
     */
    public List<DelistResponse> getDelistSchedule() {
        return executeCall(binanceApi.getDelistSchedule());
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