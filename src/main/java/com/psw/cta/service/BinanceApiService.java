package com.psw.cta.service;

import static com.binance.api.client.domain.OrderSide.BUY;
import static com.binance.api.client.domain.OrderSide.SELL;
import static com.binance.api.client.domain.OrderType.LIMIT;
import static com.binance.api.client.domain.OrderType.MARKET;
import static com.binance.api.client.domain.TimeInForce.GTC;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.binance.api.client.domain.general.FilterType.NOTIONAL;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.roundAmount;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.utils.CommonUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BiFunction;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Service providing functionality for Binance API.
 */
public class BinanceApiService {

  private final BinanceApiRestClient binanceApiRestClient;
  private final LambdaLogger logger;

  /**
   * Default constructor for {@link BinanceApiService}.
   *
   * @param apiKey    API key
   * @param apiSecret API secret
   * @param logger    logger
   */
  public BinanceApiService(String apiKey, String apiSecret, LambdaLogger logger) {
    this.binanceApiRestClient = BinanceApiClientFactory.newInstance(apiKey, apiSecret)
                                                       .newRestClient();
    this.logger = logger;
  }

  /**
   * Returns all open orders.
   *
   * @return Open orders
   */
  public List<Order> getOpenOrders() {
    return binanceApiRestClient.getOpenOrders(new OrderRequest(null));
  }

  /**
   * Returns current exchange trading rules and symbol information.
   *
   * @return Exchange info
   */
  public ExchangeInfo getExchangeInfo() {
    return binanceApiRestClient.getExchangeInfo();
  }

  /**
   * Returns 24 hour price change statistics for all symbols.
   *
   * @return Statistics
   */
  public List<TickerStatistics> getAll24hTickers() {
    return binanceApiRestClient.getAll24HrPriceStatistics();
  }

  /**
   * Returns order book of a symbol in Binance.
   *
   * @param symbol Order symbol
   * @return Order book
   */
  public OrderBook getOrderBook(String symbol) {
    return binanceApiRestClient.getOrderBook(symbol, 20);
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
    return binanceApiRestClient.getCandlestickBars(symbol,
                                                   interval,
                                                   null,
                                                   startTime.toEpochMilli(),
                                                   endTime.toEpochMilli());
  }

  /**
   * Returns order book entry with min price.
   *
   * @param symbol Order symbol
   * @return OrderBookEntry with min price
   */
  public OrderBookEntry getMinOrderBookEntry(String symbol) {
    return binanceApiRestClient.getOrderBook(symbol, 20)
                               .getAsks()
                               .parallelStream()
                               .min(comparing(OrderBookEntry::getPrice))
                               .orElseThrow(RuntimeException::new);
  }

  /**
   * Returns actual balance.
   *
   * @return Actual balance
   */
  public BigDecimal getMyActualBalance() {
    return binanceApiRestClient.getAccount()
                               .getBalances()
                               .parallelStream()
                               .map(this::mapToAssetAndBalance)
                               .filter(pair -> pair.getLeft().compareTo(ZERO) > 0)
                               .map(this::mapToBtcBalance)
                               .reduce(ZERO, BigDecimal::add);
  }

  private Pair<BigDecimal, String> mapToAssetAndBalance(AssetBalance assetBalance) {
    return Pair.of(new BigDecimal(assetBalance.getFree()).add(new BigDecimal(assetBalance.getLocked())),
                   assetBalance.getAsset());
  }

  private BigDecimal mapToBtcBalance(Pair<BigDecimal, String> pair) {
    if (pair.getRight().equals(ASSET_BTC)) {
      return pair.getLeft();
    } else {
      try {
        return getOrderBook(pair.getRight() + ASSET_BTC)
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
   * Returns actual balance per asset.
   *
   * @param asset Asset of the crypto
   * @return Actual balance
   */
  public BigDecimal getMyBalance(String asset) {
    Account account = binanceApiRestClient.getAccount();
    BigDecimal myBalance = account.getBalances()
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
   * Cancel actual order.
   *
   * @param orderToCancel Order to cancel
   */
  public void cancelRequest(OrderWrapper orderToCancel) {
    CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(orderToCancel.getOrder()
                                                                                .getSymbol(),
                                                                   orderToCancel.getOrder()
                                                                                .getClientOrderId());
    logger.log("New cancelOrderRequest: " + cancelOrderRequest);
    binanceApiRestClient.cancelOrder(cancelOrderRequest);
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
    BigDecimal myQuantity = btcAmount.divide(price, 8, CEILING);
    BigDecimal minNotionalFromMinNotionalFilter = getValueFromFilter(symbolInfo,
                                                                     SymbolFilter::getMinNotional,
                                                                     MIN_NOTIONAL,
                                                                     NOTIONAL);
    BigDecimal myQuantityToBuy = myQuantity.max(minNotionalFromMinNotionalFilter);
    BigDecimal roundedQuantity = roundAmount(symbolInfo, myQuantityToBuy);
    NewOrderResponse newOrder = createNewOrder(symbolInfo.getSymbol(), BUY, roundedQuantity);

    return Pair.of(newOrder.getOrderId(), roundedQuantity);
  }

  /**
   * Sell available balance.
   *
   * @param symbolInfo Symbol information
   * @param quantity   Quantity to sell
   */
  public void sellAvailableBalance(SymbolInfo symbolInfo, BigDecimal quantity) {
    logger.log("Sell order: " + symbolInfo.getSymbol() + ", quantity=" + quantity);
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
    NewOrder buyOrder = new NewOrder(symbol,
                                     orderSide,
                                     MARKET,
                                     null,
                                     roundedMyQuatity.toPlainString());
    return createNewOrder(buyOrder);
  }

  private NewOrderResponse createNewOrder(NewOrder newOrder) {
    logger.log("My new order: " + newOrder);
    return binanceApiRestClient.newOrder(newOrder);
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

  public List<Trade> getMyTrades(String symbol, String orderId) {
    return binanceApiRestClient.getMyTrades(symbol, orderId);
  }

  public void checkOrderStatus(String symbol, Long orderId) {
    OrderStatusRequest orderStatusRequest = new OrderStatusRequest(symbol, orderId);
    binanceApiRestClient.getOrderStatus(orderStatusRequest);
  }
}
