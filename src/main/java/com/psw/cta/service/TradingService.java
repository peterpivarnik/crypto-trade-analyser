package com.psw.cta.service;

import static com.binance.api.client.domain.general.SymbolStatus.TRADING;
import static com.binance.api.client.domain.market.CandlestickInterval.DAILY;
import static com.binance.api.client.domain.market.CandlestickInterval.FIFTEEN_MINUTES;
import static com.psw.cta.utils.CommonUtils.calculateMinNumberOfOrders;
import static com.psw.cta.utils.CommonUtils.createTotalAmounts;
import static com.psw.cta.utils.CommonUtils.getOrderComparator;
import static com.psw.cta.utils.CommonUtils.haveBalanceForInitialTrading;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MIN_PRICE_TO_SELL_PERCENTAGE;
import static com.psw.cta.utils.Constants.SYMBOL_BNB_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_TORN_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_WBTC_BTC;
import static com.psw.cta.utils.CryptoBuilder.withCurrentPrice;
import static com.psw.cta.utils.CryptoBuilder.withLeastMaxAverage;
import static com.psw.cta.utils.CryptoBuilder.withVolume;
import static com.psw.cta.utils.OrderUtils.getOrderWrapperPredicate;
import static com.psw.cta.utils.OrderWrapperBuilder.withPrices;
import static com.psw.cta.utils.OrderWrapperBuilder.withWaitingTimes;
import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.TickerStatistics;
import com.binance.api.client.exception.BinanceApiException;
import com.jcabi.manifests.Manifests;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.utils.CryptoBuilder;
import com.psw.cta.utils.OrderWrapperBuilder;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Main service for start trading crypto.
 */
public class TradingService {

  private final InitialTradingService initialTradingService;
  private final RepeatTradingService repeatTradingService;
  private final DiversifyService diversifyService;
  private final BnbService bnbService;
  private final BinanceApiService binanceApiService;
  private final LambdaLogger logger;

  /**
   * Default constructor of {@link TradingService}.
   *
   * @param initialTradingService InitialTradingService
   * @param repeatTradingService  RepeatTradingService
   * @param diversifyService      DiversifyService
   * @param bnbService            BnbService
   * @param binanceApiService     BinanceApiService
   * @param logger                Logger
   */
  public TradingService(InitialTradingService initialTradingService,
                        RepeatTradingService repeatTradingService,
                        DiversifyService diversifyService,
                        BnbService bnbService,
                        BinanceApiService binanceApiService,
                        LambdaLogger logger) {
    this.initialTradingService = initialTradingService;
    this.repeatTradingService = repeatTradingService;
    this.diversifyService = diversifyService;
    this.bnbService = bnbService;
    this.binanceApiService = binanceApiService;
    this.logger = logger;
  }

  /**
   * Initial trading.
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
    int minOpenOrders = calculateMinNumberOfOrders(totalAmount, myBtcBalance);
    logger.log("Min open orders: " + minOpenOrders);

    ExchangeInfo exchangeInfo = binanceApiService.getExchangeInfo();
    long uniqueOpenOrdersSize = openOrders.parallelStream()
                                          .map(Order::getSymbol)
                                          .distinct()
                                          .count();
    logger.log("Unique open orders: " + uniqueOpenOrdersSize);
    BigDecimal actualBalance = binanceApiService.getMyActualBalance();
    logger.log("actualBalance: " + actualBalance);

    if (canHaveMoreOrders(minOpenOrders, uniqueOpenOrdersSize)) {
      expandOrders(openOrders, myBtcBalance, totalAmounts, exchangeInfo, actualBalance);
    } else {
      repeatTrading(openOrders, myBtcBalance, totalAmounts, exchangeInfo, actualBalance);
    }
    diversifyOrderWithLowestOrderPricePercentage(openOrders, totalAmounts, myBtcBalance, exchangeInfo, actualBalance);
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
    }
    if (ordersAndBtcAmountDifference.compareTo(ZERO) < 0) {
      throw new BinanceApiException("New amount lower than before trading! Old amount : "
                                    + ordersAndBtcAmount
                                    + ". New amount: "
                                    + newOrdersAndBtcAmount);
    }
    logger.log("Finished trading.");
  }

  private void repeatTrading(List<Order> openOrders,
                             BigDecimal myBtcBalance,
                             Map<String, BigDecimal> totalAmounts,
                             ExchangeInfo exchangeInfo,
                             BigDecimal actualBalance) {
    Function<OrderWrapper, SymbolInfo> symbolFunction =
        orderWrapper -> exchangeInfo.getSymbols()
                                    .parallelStream()
                                    .filter(symbolInfo -> symbolInfo.getSymbol()
                                                                    .equals(orderWrapper.getOrder().getSymbol()))
                                    .findAny()
                                    .orElseThrow();
    Predicate<OrderWrapper> orderWrapperPredicate = getOrderWrapperPredicate(myBtcBalance);
    List<OrderWrapper> wrappers = getOrderWrappers(openOrders,
                                                   myBtcBalance,
                                                   totalAmounts,
                                                   exchangeInfo,
                                                   actualBalance,
                                                   orderWrapperPredicate);
    wrappers.forEach(orderWrapper -> logger.log(orderWrapper.toString()));
    wrappers.forEach(orderWrapper -> repeatTradingService.rebuySingleOrder(symbolFunction.apply(orderWrapper),
                                                                           orderWrapper));
  }

  private List<OrderWrapper> getOrderWrappers(List<Order> openOrders,
                                              BigDecimal myBtcBalance,
                                              Map<String, BigDecimal> totalAmounts,
                                              ExchangeInfo exchangeInfo,
                                              BigDecimal actualBalance,
                                              Predicate<OrderWrapper> predicate) {
    return openOrders.stream()
                     .map(Order::getSymbol)
                     .distinct()
                     .map(symbol -> openOrders.parallelStream()
                                              .filter(order -> order.getSymbol().equals(symbol))
                                              .min(getOrderComparator()))
                     .map(Optional::orElseThrow)
                     .map(OrderWrapperBuilder::build)
                     .map(orderWrapper -> withWaitingTimes(totalAmounts, orderWrapper))
                     .map(orderWrapper -> withPrices(orderWrapper,
                                                     binanceApiService.getOrderBook(orderWrapper.getOrder()
                                                                                                .getSymbol()),
                                                     exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol()),
                                                     myBtcBalance,
                                                     actualBalance))
                     .filter(predicate)
                     .sorted(comparing(OrderWrapper::getOrderPricePercentage))
                     .collect(Collectors.toList());
  }

  private boolean canHaveMoreOrders(long minOpenOrders, long uniqueOpenOrdersSize) {
    return uniqueOpenOrdersSize <= minOpenOrders;
  }

  private void expandOrders(List<Order> openOrders,
                            BigDecimal myBtcBalance,
                            Map<String, BigDecimal> totalAmounts,
                            ExchangeInfo exchangeInfo,
                            BigDecimal actualBalance) {
    diversifyOrderWithHighestBtcAmount(openOrders, myBtcBalance, totalAmounts, exchangeInfo, actualBalance);
    BigDecimal myBalance = binanceApiService.getMyBalance(ASSET_BTC);
    if (haveBalanceForInitialTrading(myBalance)) {
      initTrading(() -> getCryptos(exchangeInfo));
    }
  }

  private void diversifyOrderWithHighestBtcAmount(List<Order> openOrders,
                                                  BigDecimal myBtcBalance,
                                                  Map<String, BigDecimal> totalAmounts,
                                                  ExchangeInfo exchangeInfo,
                                                  BigDecimal actualBalance) {
    getOrderWrappers(openOrders,
                     myBtcBalance,
                     totalAmounts,
                     exchangeInfo,
                     actualBalance,
                     orderWrapper -> orderWrapper.getOrderPricePercentage().compareTo(new BigDecimal("20")) < 0)
        .stream()
        .max(comparing(OrderWrapper::getOrderBtcAmount))
        .ifPresent(orderWrapper -> diversifyService.diversify(orderWrapper,
                                                              () -> getCryptos(exchangeInfo),
                                                              totalAmounts,
                                                              exchangeInfo));
  }

  private List<Crypto> getCryptos(ExchangeInfo exchangeInfo) {
    logger.log("Sleep for 1 minute before get all cryptos");
    sleep(1000 * 60, logger);
    logger.log("Get all cryptos");
    List<TickerStatistics> tickers = binanceApiService.getAll24hTickers();
    List<Crypto> cryptos = exchangeInfo.getSymbols()
                                       .parallelStream()
                                       .map(CryptoBuilder::build)
                                       .filter(crypto -> crypto.getSymbolInfo().getSymbol().endsWith(ASSET_BTC))
                                       .filter(crypto -> !crypto.getSymbolInfo().getSymbol().endsWith(SYMBOL_BNB_BTC))
                                       .filter(crypto -> !crypto.getSymbolInfo().getSymbol().endsWith(SYMBOL_WBTC_BTC))
                                       .filter(crypto -> !crypto.getSymbolInfo().getSymbol().endsWith(SYMBOL_TORN_BTC))
                                       .filter(crypto -> crypto.getSymbolInfo().getStatus() == TRADING)
                                       .map(crypto -> withVolume(crypto, tickers))
                                       .filter(crypto -> crypto.getVolume().compareTo(new BigDecimal("100")) > 0)
                                       .map(crypto -> crypto.setThreeMonthsCandleStickData(
                                           binanceApiService.getCandleStickData(crypto.getSymbolInfo().getSymbol(),
                                                                                DAILY,
                                                                                90)))
                                       .filter(crypto -> crypto.getThreeMonthsCandleStickData().size() >= 90)
                                       .map(crypto -> withCurrentPrice(crypto,
                                                                       binanceApiService.getOrderBook(
                                                                           crypto.getSymbolInfo().getSymbol())))
                                       .filter(crypto -> crypto.getCurrentPrice()
                                                               .compareTo(new BigDecimal("0.000001")) > 0)
                                       .collect(Collectors.toList());
    logger.log("Cryptos count: " + cryptos.size());
    return cryptos;
  }

  private void initTrading(Supplier<List<Crypto>> cryptosSupplier) {
    logger.log("***** ***** Initial trading ***** *****");
    cryptosSupplier.get()
                   .stream()
                   .map(crypto -> withLeastMaxAverage(crypto,
                                                      binanceApiService.getCandleStickData(crypto.getSymbolInfo()
                                                                                                 .getSymbol(),
                                                                                           FIFTEEN_MINUTES,
                                                                                           96)))
                   .filter(crypto -> crypto.getLastThreeHighAverage()
                                           .compareTo(crypto.getPreviousThreeHighAverage()) > 0)
                   .map(CryptoBuilder::withPrices)
                   .filter(crypto -> crypto.getPriceToSellPercentage()
                                           .compareTo(MIN_PRICE_TO_SELL_PERCENTAGE) > 0)
                   .map(CryptoBuilder::withSumDiffPerc)
                   .filter(crypto -> crypto.getSumPercentageDifferences1h()
                                           .compareTo(new BigDecimal("4")) < 0)
                   .filter(crypto -> crypto.getSumPercentageDifferences10h()
                                           .compareTo(new BigDecimal("400")) < 0)
                   .forEach(initialTradingService::buyCrypto);
  }

  private void diversifyOrderWithLowestOrderPricePercentage(List<Order> openOrders,
                                                            Map<String, BigDecimal> totalAmounts,
                                                            BigDecimal myBtcBalance,
                                                            ExchangeInfo exchangeInfo,
                                                            BigDecimal actualBalance) {
    logger.log("Sleep for 1 minute before splitting");
    sleep(1000 * 60, logger);
    List<OrderWrapper> orderWrappers = getOrderWrappers(openOrders,
                                                        myBtcBalance,
                                                        totalAmounts,
                                                        exchangeInfo,
                                                        actualBalance,
                                                        orderWrapper -> true);
    boolean allOlderThanDay = orderWrappers.stream()
                                           .allMatch(orderWrapper -> orderWrapper.getActualWaitingTime()
                                                                                 .compareTo(new BigDecimal("24")) > 0);
    if (allOlderThanDay && !orderWrappers.isEmpty()) {
      OrderWrapper orderToSplit = Collections.min(orderWrappers, comparing(OrderWrapper::getOrderPricePercentage));
      logger.log("***** ***** Diversifying amounts with lowest order price percentage ***** *****");
      diversifyService.diversify(orderToSplit,
                                 () -> getCryptos(exchangeInfo),
                                 totalAmounts,
                                 exchangeInfo);
    }
  }
}