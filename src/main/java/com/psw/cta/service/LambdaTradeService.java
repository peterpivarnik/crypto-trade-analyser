package com.psw.cta.service;

import static com.binance.api.client.domain.general.SymbolStatus.TRADING;
import static com.binance.api.client.domain.market.CandlestickInterval.DAILY;
import static com.binance.api.client.domain.market.CandlestickInterval.FIFTEEN_MINUTES;
import static com.psw.cta.utils.CommonUtils.haveBalanceForInitialTrading;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MIN_PRICE_TO_SELL_PERCENTAGE;
import static com.psw.cta.utils.Constants.SYMBOL_BNB_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_WBTC_BTC;
import static com.psw.cta.utils.CryptoBuilder.withCurrentPrice;
import static com.psw.cta.utils.CryptoBuilder.withLeastMaxAverage;
import static com.psw.cta.utils.CryptoBuilder.withVolume;
import static com.psw.cta.utils.OrderUtils.getOrderWrapperPredicate;
import static java.math.RoundingMode.CEILING;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.TickerStatistics;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.utils.CryptoBuilder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Trade service for AWS.
 */
public class LambdaTradeService extends TradeService {

  private final LambdaLogger logger;
  private final RepeatTradingService repeatTradingService;
  private final SplitService splitService;
  private final AcquireService acquireService;
  private final List<String> allForbiddenPairs;

  /**
   * Default constructor.
   *
   * @param binanceApiService Service providing functionality for Binance API
   * @param forbiddenPairs    forbidden pairs
   * @param logger            logger
   */
  public LambdaTradeService(BinanceApiService binanceApiService, List<String> forbiddenPairs, LambdaLogger logger) {
    super(binanceApiService);
    this.acquireService = new AcquireService(binanceApiService, logger);
    this.repeatTradingService = new RepeatTradingService(binanceApiService, logger);
    this.splitService = new SplitService(binanceApiService, logger);
    this.logger = logger;
    this.allForbiddenPairs = initializeForbiddenPairs(forbiddenPairs);
  }

  private ArrayList<String> initializeForbiddenPairs(List<String> forbiddenPairs) {
    List<String> defaultForbiddenPairs = Arrays.asList(SYMBOL_BNB_BTC, SYMBOL_WBTC_BTC);
    ArrayList<String> allForbiddenPairs = new ArrayList<>();
    allForbiddenPairs.addAll(defaultForbiddenPairs);
    allForbiddenPairs.addAll(forbiddenPairs);
    logger.log("ForbiddenPairs:");
    allForbiddenPairs.forEach(logger::log);
    return allForbiddenPairs;
  }

  /**
   * Trade crypto orders.
   *
   * @param openOrders           list of currently open orders
   * @param totalAmounts         map of all amounts
   * @param myBtcBalance         current BTC amount
   * @param totalAmount          current total amount
   * @param minOpenOrders        minimum of open orders
   * @param exchangeInfo         exchange trading rules and symbol information
   * @param uniqueOpenOrdersSize number of unique open orders
   * @param actualBalance        actual balance
   */
  public void trade(List<Order> openOrders,
                    Map<String, BigDecimal> totalAmounts,
                    BigDecimal myBtcBalance,
                    BigDecimal totalAmount,
                    int minOpenOrders,
                    ExchangeInfo exchangeInfo,
                    long uniqueOpenOrdersSize,
                    BigDecimal actualBalance) {
    if (canHaveMoreOrders(minOpenOrders, uniqueOpenOrdersSize)) {
      expandOrders(openOrders, myBtcBalance, totalAmounts, exchangeInfo, actualBalance);
    } else {
      repeatTrading(openOrders, myBtcBalance, totalAmounts, exchangeInfo, actualBalance);
    }
    splitOrderWithLowestOrderPricePercentage(openOrders,
                                             totalAmounts,
                                             myBtcBalance,
                                             exchangeInfo,
                                             actualBalance);

    if (shouldSplit(myBtcBalance, actualBalance, totalAmount, uniqueOpenOrdersSize)) {
      logger.log("***** ***** Splitting trade for quicker selling ***** *****");
      Predicate<OrderWrapper> orderWrapperPredicate =
          orderWrapper -> orderWrapper.getOrderPricePercentage().compareTo(new BigDecimal("5")) < 0
                          && orderWrapper.getOrderBtcAmount().compareTo(new BigDecimal("0.001")) > 0;
      splitOrderWithHighestBtcAmount(openOrders,
                                     myBtcBalance,
                                     totalAmounts,
                                     exchangeInfo,
                                     actualBalance,
                                     orderWrapperPredicate);
    }
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
    return getOrderWrapperStream(openOrders, totalAmounts, myBtcBalance, exchangeInfo, actualBalance)
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
    splitOrderWithHighestBtcAmount(openOrders,
                                   myBtcBalance,
                                   totalAmounts,
                                   exchangeInfo,
                                   actualBalance,
                                   orderWrapper -> orderWrapper.getOrderPricePercentage()
                                                               .compareTo(new BigDecimal("20")) < 0);
    BigDecimal myBalance = binanceApiService.getMyBalance(ASSET_BTC);
    if (haveBalanceForInitialTrading(myBalance)) {
      initTrading(() -> getCryptos(exchangeInfo));
    }
  }

  private boolean shouldSplit(BigDecimal myBtcBalance,
                              BigDecimal actualBalance,
                              BigDecimal totalAmount,
                              long uniqueOpenOrdersSize) {
    return myBtcBalance.compareTo(actualBalance.divide(new BigDecimal("2"), 8, CEILING)) > 0
           && new BigDecimal(uniqueOpenOrdersSize).compareTo(totalAmount.multiply(new BigDecimal("100"))) < 0;
  }

  private void splitOrderWithHighestBtcAmount(List<Order> openOrders,
                                              BigDecimal myBtcBalance,
                                              Map<String, BigDecimal> totalAmounts,
                                              ExchangeInfo exchangeInfo,
                                              BigDecimal actualBalance,
                                              Predicate<OrderWrapper> orderWrapperPredicate) {
    getOrderWrappers(openOrders,
                     myBtcBalance,
                     totalAmounts,
                     exchangeInfo,
                     actualBalance,
                     orderWrapperPredicate)
        .stream()
        .max(comparing(OrderWrapper::getOrderBtcAmount))
        .ifPresent(orderWrapper -> splitService.split(orderWrapper,
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
                                       .filter(crypto -> !allForbiddenPairs.contains(crypto.getSymbolInfo()
                                                                                           .getSymbol()))
                                       .filter(crypto -> crypto.getSymbolInfo().getStatus() == TRADING)
                                       .map(crypto -> withVolume(crypto, tickers))
                                       .filter(crypto -> crypto.getVolume().compareTo(new BigDecimal("100")) > 0)
                                       .map(crypto -> crypto.setThreeMonthsCandleStickData(
                                           binanceApiService.getCandleStickData(crypto.getSymbolInfo().getSymbol(),
                                                                                DAILY,
                                                                                90L,
                                                                                DAYS)))
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
                                                                                           96L * 15L,
                                                                                           MINUTES)))
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
                   .forEach(acquireService::acquireCrypto);
  }

  private void splitOrderWithLowestOrderPricePercentage(List<Order> openOrders,
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
      logger.log("***** ***** Splitting amounts with lowest order price percentage ***** *****");
      splitService.split(orderToSplit,
                         () -> getCryptos(exchangeInfo),
                         totalAmounts,
                         exchangeInfo);
    }
  }
}
