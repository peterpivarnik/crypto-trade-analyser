package com.psw.cta.service;

import static com.psw.cta.dto.binance.FilterType.MIN_NOTIONAL;
import static com.psw.cta.dto.binance.FilterType.NOTIONAL;
import static com.psw.cta.dto.binance.OrderSide.BUY;
import static com.psw.cta.dto.binance.OrderSide.SELL;
import static com.psw.cta.dto.binance.OrderType.LIMIT;
import static com.psw.cta.dto.binance.OrderType.MARKET;
import static com.psw.cta.dto.binance.TimeInForce.GTC;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.roundAmount;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.Account;
import com.psw.cta.dto.binance.AssetBalance;
import com.psw.cta.dto.binance.CancelOrderRequest;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.CandlestickInterval;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.NewOrder;
import com.psw.cta.dto.binance.NewOrderResponse;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.OrderBook;
import com.psw.cta.dto.binance.OrderBookEntry;
import com.psw.cta.dto.binance.OrderRequest;
import com.psw.cta.dto.binance.OrderSide;
import com.psw.cta.dto.binance.OrderStatusRequest;
import com.psw.cta.dto.binance.SymbolFilter;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.dto.binance.TickerStatistics;
import com.psw.cta.dto.binance.Trade;
import com.psw.cta.exception.BinanceApiException;
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

  private final BinanceService binanceService;
  private final LambdaLogger logger;

  /**
   * Default constructor for {@link BinanceApiService}.
   *
   * @param apiKey    API key
   * @param apiSecret API secret
   * @param logger    logger
   */
  public BinanceApiService(String apiKey, String apiSecret, LambdaLogger logger) {
    this.binanceService = new BinanceService(apiKey, apiSecret);
    this.logger = logger;
  }

  /**
   * Returns all open orders.
   *
   * @return Open orders
   */
  public List<Order> getOpenOrders() {
    return binanceService.getOpenOrders(new OrderRequest(null));
  }

  /**
   * Returns current exchange trading rules and symbol information.
   *
   * @return Exchange info
   */
  public ExchangeInfo getExchangeInfo() {
    return binanceService.getExchangeInfo();
  }

  /**
   * Returns 24 hour price change statistics for all symbols.
   *
   * @return Statistics
   */
  public List<TickerStatistics> getAll24hTickers() {
    return binanceService.getAll24HrPriceStatistics();
  }

  /**
   * Returns order book of a symbol in Binance.
   *
   * @param symbol Order symbol
   * @return Order book
   */
  public OrderBook getOrderBook(String symbol) {
    return binanceService.getOrderBook(symbol, 20);
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
    return binanceService.getCandlestickBars(symbol,
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
    return binanceService.getOrderBook(symbol, 20)
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
    return binanceService.getAccount()
                         .getBalances()
                         .parallelStream()
                         .filter(assetBalance -> !assetBalance.getAsset().equals("NFT"))
                         .map(this::mapToAssetAndBalance)
                         .filter(pair -> pair.getLeft().compareTo(ZERO) > 0)
                         .map(this::mapToBtcBalance)
                         .reduce(ZERO, BigDecimal::add);
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
    Account account = binanceService.getAccount();
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
    binanceService.cancelOrder(cancelOrderRequest);
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
    return binanceService.newOrder(newOrder);
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
    return binanceService.getMyTrades(symbol, orderId);
  }

  public void checkOrderStatus(String symbol, Long orderId) {
    OrderStatusRequest orderStatusRequest = new OrderStatusRequest(symbol, orderId);
    binanceService.getOrderStatus(orderStatusRequest);
  }
}
