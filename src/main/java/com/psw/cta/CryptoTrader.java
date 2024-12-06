package com.psw.cta;

import static com.psw.cta.utils.CommonUtils.calculateMinNumberOfOrders;
import static com.psw.cta.utils.CommonUtils.createTotalAmounts;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.OrderWrapperBuilder.withPrices;
import static com.psw.cta.utils.OrderWrapperBuilder.withWaitingTimes;
import static java.lang.Boolean.FALSE;
import static java.lang.String.format;
import static java.math.BigDecimal.ZERO;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.jcabi.manifests.Manifests;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.processor.LambdaTradeProcessor;
import com.psw.cta.processor.LocalTradeProcessor;
import com.psw.cta.processor.MainTradeProcessor;
import com.psw.cta.processor.trade.BnbTradeProcessor;
import com.psw.cta.service.BinanceService;
import com.psw.cta.utils.OrderWrapperBuilder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main service for start trading crypto.
 */
public class CryptoTrader {

  private final BnbTradeProcessor bnbTradeProcessor;
  private final BinanceService binanceService;
  private final LambdaLogger logger;
  private final MainTradeProcessor tradeFacade;

  /**
   * Constructor of {@link CryptoTrader} for local environment.
   *
   * @param apiKey    ApiKey to be used for trading
   * @param apiSecret ApiSecret to be used for trading
   * @param logger    Logger
   */
  public CryptoTrader(String apiKey,
                      String apiSecret,
                      LambdaLogger logger) {
    this.binanceService = new BinanceService(apiKey, apiSecret, logger);
    this.bnbTradeProcessor = new BnbTradeProcessor(binanceService, logger);
    this.tradeFacade = new LocalTradeProcessor(binanceService, logger);
    this.logger = logger;
  }

  /**
   * Constructor of {@link CryptoTrader} for AWS environment.
   *
   * @param apiKey         ApiKey to be used for trading
   * @param apiSecret      ApiSecret to be used for trading
   * @param forbiddenPairs Forbidden trading pairs
   * @param logger         Logger
   */
  public CryptoTrader(String apiKey,
                      String apiSecret,
                      List<String> forbiddenPairs,
                      LambdaLogger logger) {
    this.binanceService = new BinanceService(apiKey, apiSecret, logger);
    this.bnbTradeProcessor = new BnbTradeProcessor(binanceService, logger);
    this.tradeFacade = new LambdaTradeProcessor(binanceService, forbiddenPairs, logger);
    this.logger = logger;
  }

  /**
   * Start trading.
   */
  public void startTrading() {
    logger.log("***** ***** Start of trading ***** *****");
    String implementationVersion = Manifests.read("Implementation-Version");
    logger.log("Crypto trader with version " + implementationVersion + " started.");
    List<Order> openOrders = binanceService.getOpenOrders();
    logger.log("Number of open orders: " + openOrders.size());
    Map<String, BigDecimal> totalAmounts = createTotalAmounts(openOrders);
    logTotalAmounts(totalAmounts);
    BigDecimal ordersAmount = totalAmounts.values()
                                          .stream()
                                          .reduce(ZERO, BigDecimal::add);
    logger.log("ordersAmount: " + ordersAmount);
    BigDecimal myBtcBalance = binanceService.getMyBalance(ASSET_BTC);
    BigDecimal ordersAndBtcAmount = ordersAmount.add(myBtcBalance);
    logger.log("ordersAndBtcAmount: " + ordersAndBtcAmount.stripTrailingZeros());
    ExchangeInfo exchangeInfo = binanceService.getExchangeInfo();
    BigDecimal bnbBalance = bnbTradeProcessor.buyBnB(exchangeInfo);
    BigDecimal bnbAmount = bnbBalance.multiply(bnbTradeProcessor.getCurrentBnbBtcPrice());
    BigDecimal totalAmount = ordersAndBtcAmount.add(bnbAmount);
    logger.log("totalAmount: " + totalAmount.stripTrailingZeros());
    int minOpenOrders = calculateMinNumberOfOrders(myBtcBalance);
    logger.log("Min open orders: " + minOpenOrders);

    long uniqueOpenOrdersSize = openOrders.parallelStream()
                                          .map(Order::getSymbol)
                                          .distinct()
                                          .count();
    logger.log("Unique open orders: " + uniqueOpenOrdersSize);
    BigDecimal actualBalance = binanceService.getMyActualBalance();
    logger.log("actualBalance: " + actualBalance.stripTrailingZeros());

    tradeFacade.trade(openOrders,
                      totalAmounts,
                      myBtcBalance,
                      totalAmount,
                      minOpenOrders,
                      exchangeInfo,
                      uniqueOpenOrdersSize,
                      actualBalance);
    List<Order> newOpenOrders = binanceService.getOpenOrders();
    Map<String, BigDecimal> newTotalAmounts = createTotalAmounts(newOpenOrders);
    logTotalAmounts(newTotalAmounts);
    Boolean tradeCancelled = cancelTrade(newTotalAmounts, exchangeInfo, newOpenOrders);
    checkOldAndNewAmount(ordersAndBtcAmount, newOpenOrders, tradeCancelled);
    logger.log("Finished trading.");
  }

  private void logTotalAmounts(Map<String, BigDecimal> totalAmounts) {
    logger.log("totalAmounts: ");
    List<Map.Entry<String, BigDecimal>> entryList = totalAmounts.entrySet()
                                                                .stream()
                                                                .toList();
    int numberOfTens = entryList.size() / 10;
    List<List<Map.Entry<String, BigDecimal>>> listOfLists = new ArrayList<>();
    for (int i = 0; i < numberOfTens; i++) {
      listOfLists.add(entryList.subList(i * 10, (i + 1) * 10));
    }
    listOfLists.add(entryList.subList(numberOfTens * 10, entryList.size()));
    listOfLists.forEach(subList -> {
      String sublistString = subList.stream()
                              .map(stringBigDecimalEntry -> format("%10s=%-11s",
                                                                   stringBigDecimalEntry.getKey(),
                                                                   stringBigDecimalEntry.getValue()))
                              .collect(Collectors.joining());
      logger.log(sublistString);
    });
  }

  private Boolean cancelTrade(Map<String, BigDecimal> totalAmounts,
                              ExchangeInfo exchangeInfo,
                              List<Order> newOpenOrders) {
    BigDecimal myBtcBalance = binanceService.getMyBalance(ASSET_BTC);
    BigDecimal myActualBalance = binanceService.getMyActualBalance();
    List<OrderWrapper> wrappers =
        newOpenOrders.stream()
                     .map(OrderWrapperBuilder::build)
                     .map(orderWrapper -> withPrices(
                         orderWrapper,
                         binanceService.getOrderBook(orderWrapper.getOrder().getSymbol(), 20),
                         exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol()),
                         myBtcBalance,
                         myActualBalance))
                     .map(orderWrapper -> withWaitingTimes(totalAmounts, orderWrapper))
                     .toList();
    boolean allRemainWaitingTimeLessThanZero = wrappers.stream()
                                                       .map(OrderWrapper::getRemainWaitingTime)
                                                       .allMatch(time -> time.compareTo(ZERO) < 0);
    Boolean tradeCancelled = FALSE;
    if (allRemainWaitingTimeLessThanZero) {
      tradeCancelled = wrappers.stream()
                               .max(Comparator.comparing(OrderWrapper::getCurrentBtcAmount))
                               .map(orderWrapper -> tradeFacade.cancelTrade(orderWrapper, exchangeInfo))
                               .orElse(false);
    }
    return tradeCancelled;
  }

  private void checkOldAndNewAmount(BigDecimal ordersAndBtcAmount, List<Order> newOpenOrders, Boolean tradeCancelled) {
    Map<String, BigDecimal> newTotalAmounts = createTotalAmounts(newOpenOrders);
    BigDecimal newOrdersAmount = newTotalAmounts.values()
                                                .stream()
                                                .reduce(ZERO, BigDecimal::add);
    BigDecimal myNewBtcBalance = binanceService.getMyBalance(ASSET_BTC);
    BigDecimal newOrdersAndBtcAmount = newOrdersAmount.add(myNewBtcBalance);
    logger.log("newOrdersAndBtcAmount: " + newOrdersAndBtcAmount);
    BigDecimal ordersAndBtcAmountDifference = newOrdersAndBtcAmount.subtract(ordersAndBtcAmount);

    if (ordersAndBtcAmountDifference.compareTo(ZERO) > 0) {
      logger.log("ordersAndBtcAmountDifference: " + ordersAndBtcAmountDifference);
    } else if (ordersAndBtcAmountDifference.compareTo(ZERO) < 0 && FALSE.equals(tradeCancelled)) {
      throw new BinanceApiException("New amount lower than before trading! Old amount : "
                                    + ordersAndBtcAmount
                                    + ". New amount: "
                                    + newOrdersAndBtcAmount
                                    + ". Difference: "
                                    + ordersAndBtcAmountDifference);
    }
  }
}