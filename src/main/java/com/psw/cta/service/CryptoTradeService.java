package com.psw.cta.service;

import static com.psw.cta.utils.CommonUtils.calculateMinNumberOfOrders;
import static com.psw.cta.utils.CommonUtils.createTotalAmounts;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.math.BigDecimal.ZERO;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.exception.BinanceApiException;
import com.jcabi.manifests.Manifests;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Main service for start trading crypto.
 */
public class CryptoTradeService {

  private final BnbService bnbService;
  private final BinanceApiService binanceApiService;
  private final LambdaLogger logger;
  private final TradeService tradeService;

  /**
   * Constructor of {@link CryptoTradeService} for local environment.
   *
   * @param apiKey    ApiKey to be used for trading
   * @param apiSecret ApiSecret to be used for trading
   * @param logger    Logger
   */
  public CryptoTradeService(String apiKey,
                            String apiSecret,
                            LambdaLogger logger) {
    this.binanceApiService = new BinanceApiService(apiKey, apiSecret, logger);
    this.bnbService = new BnbService(binanceApiService, logger);
    this.tradeService = new LocalTradeService(binanceApiService, logger);
    this.logger = logger;
  }

  /**
   * Constructor of {@link CryptoTradeService} for AWS environment.
   *
   * @param apiKey         ApiKey to be used for trading
   * @param apiSecret      ApiSecret to be used for trading
   * @param forbiddenPairs Forbidden trading pairs
   * @param logger         Logger
   */
  public CryptoTradeService(String apiKey,
                            String apiSecret,
                            List<String> forbiddenPairs,
                            LambdaLogger logger) {
    this.binanceApiService = new BinanceApiService(apiKey, apiSecret, logger);
    this.bnbService = new BnbService(binanceApiService, logger);
    this.tradeService = new LambdaTradeService(binanceApiService, forbiddenPairs, logger);
    this.logger = logger;
  }

  /**
   * Start trading.
   */
  public void startTrading() {
    logger.log("***** ***** Start of trading ***** *****");
    String implementationVersion = Manifests.read("Implementation-Version");
    logger.log("Crypto trader with version " + implementationVersion + " started.");
    BigDecimal bnbBalance = bnbService.buyBnB();
    List<Order> openOrders = binanceApiService.getOpenOrders();
    logger.log("Number of open orders: " + openOrders.size());
    Map<String, BigDecimal> totalAmounts = createTotalAmounts(openOrders);
    logger.log("totalAmounts: " + totalAmounts);
    BigDecimal ordersAmount = totalAmounts.values()
                                          .stream()
                                          .reduce(ZERO, BigDecimal::add);
    logger.log("ordersAmount: " + ordersAmount);
    BigDecimal myBtcBalance = binanceApiService.getMyBalance(ASSET_BTC);
    BigDecimal ordersAndBtcAmount = ordersAmount.add(myBtcBalance);
    logger.log("ordersAndBtcAmount: " + ordersAndBtcAmount);
    BigDecimal bnbAmount = bnbBalance.multiply(bnbService.getCurrentBnbBtcPrice());
    BigDecimal totalAmount = ordersAndBtcAmount.add(bnbAmount);
    logger.log("totalAmount: " + totalAmount);
    int minOpenOrders = calculateMinNumberOfOrders(myBtcBalance);
    logger.log("Min open orders: " + minOpenOrders);

    ExchangeInfo exchangeInfo = binanceApiService.getExchangeInfo();
    long uniqueOpenOrdersSize = openOrders.parallelStream()
                                          .map(Order::getSymbol)
                                          .distinct()
                                          .count();
    logger.log("Unique open orders: " + uniqueOpenOrdersSize);
    BigDecimal actualBalance = binanceApiService.getMyActualBalance();
    logger.log("actualBalance: " + actualBalance);

    tradeService.trade(openOrders,
                       totalAmounts,
                       myBtcBalance,
                       totalAmount,
                       minOpenOrders,
                       exchangeInfo,
                       uniqueOpenOrdersSize,
                       actualBalance);
    List<Order> newOpenOrders = binanceApiService.getOpenOrders();
    Map<String, BigDecimal> newTotalAmounts = createTotalAmounts(newOpenOrders);
    BigDecimal newOrdersAmount = newTotalAmounts.values()
                                                .stream()
                                                .reduce(ZERO, BigDecimal::add);
    BigDecimal myNewBtcBalance = binanceApiService.getMyBalance(ASSET_BTC);
    BigDecimal newOrdersAndBtcAmount = newOrdersAmount.add(myNewBtcBalance);
    logger.log("newOrdersAndBtcAmount: " + newOrdersAndBtcAmount);
    BigDecimal ordersAndBtcAmountDifference = newOrdersAndBtcAmount.subtract(ordersAndBtcAmount);

    if (ordersAndBtcAmountDifference.compareTo(ZERO) > 0) {
      logger.log("ordersAndBtcAmountDifference: " + ordersAndBtcAmountDifference);
    } else if (ordersAndBtcAmountDifference.compareTo(ZERO) < 0) {
      throw new BinanceApiException("New amount lower than before trading! Old amount : "
                                    + ordersAndBtcAmount
                                    + ". New amount: "
                                    + newOrdersAndBtcAmount);
    }
    logger.log("Finished trading.");
  }


}