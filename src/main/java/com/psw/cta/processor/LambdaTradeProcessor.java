package com.psw.cta.processor;

import static com.psw.cta.dto.binance.CandlestickInterval.DAILY;
import static com.psw.cta.dto.binance.CandlestickInterval.FIFTEEN_MINUTES;
import static com.psw.cta.dto.binance.SymbolStatus.TRADING;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.haveBalanceForInitialTrading;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MIN_PRICE_TO_SELL_PERCENTAGE;
import static com.psw.cta.utils.Constants.MIN_PROFIT_PERCENTAGE;
import static com.psw.cta.utils.Constants.SYMBOL_BNB_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_WBTC_BTC;
import static com.psw.cta.utils.OrderUtils.getOrderWrapperPredicate;
import static java.lang.Boolean.TRUE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.dto.binance.TickerStatistics;
import com.psw.cta.processor.trade.AcquireProcessor;
import com.psw.cta.processor.trade.RepeatTradingProcessor;
import com.psw.cta.processor.trade.SplitProcessor;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Trade service for AWS lambda.
 */
public class LambdaTradeProcessor extends MainTradeProcessor {

  private final LambdaLogger logger;
  private final RepeatTradingProcessor repeatTradingProcessor;
  private final SplitProcessor splitProcessor;
  private final AcquireProcessor acquireProcessor;
  private final List<String> allForbiddenPairs;

  /**
   * Default constructor.
   *
   * @param binanceService Service providing functionality for Binance API
   * @param forbiddenPairs forbidden pairs
   * @param logger         logger
   */
  public LambdaTradeProcessor(BinanceService binanceService, List<String> forbiddenPairs, LambdaLogger logger) {
    super(binanceService);
    this.acquireProcessor = new AcquireProcessor(binanceService, logger);
    this.repeatTradingProcessor = new RepeatTradingProcessor(binanceService, logger);
    this.splitProcessor = new SplitProcessor(binanceService, logger);
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
  @Override
  public void trade(List<Order> openOrders,
                    Map<String, BigDecimal> totalAmounts,
                    BigDecimal myBtcBalance,
                    BigDecimal totalAmount,
                    int minOpenOrders,
                    ExchangeInfo exchangeInfo,
                    long uniqueOpenOrdersSize,
                    BigDecimal actualBalance) {
    Set<Order> ordersToSplit = openOrders.stream()
                                         .filter(order -> allForbiddenPairs.contains(order.getSymbol()))
                                         .collect(Collectors.toSet());
    BigDecimal ordersAmount = totalAmounts.values()
                                          .stream()
                                          .reduce(ZERO, BigDecimal::add);
    List<OrderWrapper> orderWrappers = getOrderWrappers(openOrders,
                                                        myBtcBalance,
                                                        totalAmounts,
                                                        exchangeInfo,
                                                        actualBalance,
                                                        orderWrapper -> true);
    boolean allOlderThanDay = orderWrappers.stream()
                                           .allMatch(orderWrapper -> orderWrapper.getActualWaitingTime()
                                                                                 .compareTo(new BigDecimal("24")) > 0);
    List<OrderWrapper> filteredOrderWrappers =
        orderWrappers.stream()
                     .filter(orderWrapper -> orderWrapper.getOrderBtcAmount()
                                                         .compareTo(new BigDecimal("0.001")) > 0)
                     .toList();
    if (!ordersToSplit.isEmpty()) {
      logger.log("***** ***** Splitting cancelled trades ***** *****");
      ordersToSplit
          .forEach(order -> splitCancelledOrder(order, myBtcBalance, actualBalance, totalAmounts, exchangeInfo));
    } else if (myBtcBalance.compareTo(ordersAmount.multiply(new BigDecimal("3"))) > 0) {
      logger.log("***** ***** Rebuy all orders ***** *****");
      rebuyOrders(openOrders,
                  myBtcBalance,
                  totalAmounts,
                  exchangeInfo,
                  actualBalance);
    } else if (uniqueOpenOrdersSizeIsLessThanHundredTotalAmounts(uniqueOpenOrdersSize, totalAmount)
               && allOlderThanDay
               && !filteredOrderWrappers.isEmpty()) {
      logger.log("***** ***** Splitting amounts with lowest order price percentage ***** *****");
      sleep(1000 * 60, logger);
      OrderWrapper orderToSplit = Collections.min(filteredOrderWrappers,
                                                  comparing(OrderWrapper::getOrderPricePercentage));
      List<Crypto> cryptos = getCryptos(exchangeInfo);
      splitProcessor.split(orderToSplit, cryptos, totalAmounts, exchangeInfo);
    } else if (shouldSplit(myBtcBalance, actualBalance, totalAmount, uniqueOpenOrdersSize)) {
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
    } else if (canHaveMoreOrders(minOpenOrders, uniqueOpenOrdersSize)) {
      logger.log("***** ***** Splitting order with highest btc amount and init trading ***** *****");
      Predicate<OrderWrapper> orderWrapperPredicate =
          orderWrapper -> orderWrapper.getOrderPricePercentage().compareTo(new BigDecimal("20")) < 0;
      splitOrderWithHighestBtcAmount(openOrders,
                                     myBtcBalance,
                                     totalAmounts,
                                     exchangeInfo,
                                     actualBalance,
                                     orderWrapperPredicate);
      BigDecimal myBalance = binanceService.getMyBalance(ASSET_BTC);
      if (haveBalanceForInitialTrading(myBalance)) {
        initTrading(() -> getCryptos(exchangeInfo));
      }
    } else {
      logger.log("***** ***** Rebuy orders ***** *****");
      rebuyOrdersWithPredicateCheck(openOrders,
                                    myBtcBalance,
                                    totalAmounts,
                                    exchangeInfo,
                                    actualBalance);
    }
  }

  private void splitCancelledOrder(Order orderToSplit,
                                   BigDecimal myBtcBalance,
                                   BigDecimal actualBalance,
                                   Map<String, BigDecimal> totalAmounts,
                                   ExchangeInfo exchangeInfo) {
    OrderWrapper orderToCancel = getOrderWrappers(singletonList(orderToSplit),
                                                  myBtcBalance,
                                                  totalAmounts,
                                                  exchangeInfo,
                                                  actualBalance,
                                                  orderWrapper -> true)
        .getFirst();
    List<Crypto> cryptos = getCryptos(exchangeInfo);
    splitProcessor.split(orderToCancel, cryptos, totalAmounts, exchangeInfo);
  }

  private void rebuyOrdersWithPredicateCheck(List<Order> openOrders,
                                             BigDecimal myBtcBalance,
                                             Map<String, BigDecimal> totalAmounts,
                                             ExchangeInfo exchangeInfo,
                                             BigDecimal actualBalance) {
    Predicate<OrderWrapper> orderWrapperPredicate = getOrderWrapperPredicate(myBtcBalance);
    getOrderWrappers(openOrders, myBtcBalance, totalAmounts, exchangeInfo, actualBalance, orderWrapperPredicate)
        .forEach(orderWrapper -> {
          BigDecimal newBtcBalance = binanceService.getMyBalance(ASSET_BTC);
          Predicate<OrderWrapper> predicate = getOrderWrapperPredicate(newBtcBalance);
          if (!predicate.test(orderWrapper)) {
            logger.log("Conditions to rebuy crypto not valid.");
            return;
          }
          SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol());
          repeatTradingProcessor.rebuySingleOrder(symbolInfo, orderWrapper);
        });
  }

  private void rebuyOrders(List<Order> openOrders,
                           BigDecimal myBtcBalance,
                           Map<String, BigDecimal> totalAmounts,
                           ExchangeInfo exchangeInfo,
                           BigDecimal actualBalance) {
    Predicate<OrderWrapper> orderWrapperPredicate = orderWrapper -> orderWrapper.getOrderPricePercentage()
                                                                                .subtract(orderWrapper.getPriceToSellPercentage())
                                                                                .compareTo(MIN_PROFIT_PERCENTAGE) > 0;
    getOrderWrappers(openOrders, myBtcBalance, totalAmounts, exchangeInfo, actualBalance, orderWrapperPredicate)
        .forEach(orderWrapper -> {
          SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol());
          repeatTradingProcessor.rebuySingleOrder(symbolInfo, orderWrapper);
        });
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

  private boolean shouldSplit(BigDecimal myBtcBalance,
                              BigDecimal actualBalance,
                              BigDecimal totalAmount,
                              long uniqueOpenOrdersSize) {
    return actualBtcBalanceMoreThanHalfOfActualBalance(myBtcBalance, actualBalance)
           && uniqueOpenOrdersSizeIsLessThanHundredTotalAmounts(uniqueOpenOrdersSize, totalAmount);
  }

  private boolean actualBtcBalanceMoreThanHalfOfActualBalance(BigDecimal myBtcBalance, BigDecimal actualBalance) {
    return myBtcBalance.compareTo(actualBalance.divide(new BigDecimal("2"), 8, CEILING)) > 0;
  }

  private boolean uniqueOpenOrdersSizeIsLessThanHundredTotalAmounts(long uniqueOpenOrdersSize, BigDecimal totalAmount) {
    return new BigDecimal(uniqueOpenOrdersSize).compareTo(totalAmount.multiply(new BigDecimal("100"))) < 0;
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
        .ifPresent(orderWrapper -> splitProcessor.split(orderWrapper,
                                                        getCryptos(exchangeInfo),
                                                        totalAmounts,
                                                        exchangeInfo));
  }

  private List<Crypto> getCryptos(ExchangeInfo exchangeInfo) {
    sleep(1000 * 60, logger);
    logger.log("Get all cryptos");
    List<TickerStatistics> tickers = binanceService.getAll24hTickers();
    List<Crypto> cryptos = exchangeInfo.getSymbols()
                                       .parallelStream()
                                       .map(Crypto::new)
                                       .filter(crypto -> crypto.getSymbolInfo().getSymbol().endsWith(ASSET_BTC))
                                       .filter(crypto -> !allForbiddenPairs.contains(crypto.getSymbolInfo()
                                                                                           .getSymbol()))
                                       .filter(crypto -> crypto.getSymbolInfo().getStatus() == TRADING)
                                       .map(crypto -> crypto.calculateVolume(tickers))
                                       .filter(crypto -> crypto.getVolume().compareTo(new BigDecimal("100")) > 0)
                                       .map(crypto -> crypto.setThreeMonthsCandleStickData(
                                           binanceService.getCandleStickData(crypto.getSymbolInfo().getSymbol(),
                                                                             DAILY,
                                                                             90L,
                                                                             DAYS)))
                                       .filter(crypto -> crypto.getThreeMonthsCandleStickData().size() >= 90)
                                       .map(crypto -> crypto.calculateCurrentPrice(binanceService.getOrderBook(
                                           crypto.getSymbolInfo().getSymbol(),
                                           20)))
                                       .filter(crypto -> crypto.getCurrentPrice()
                                                               .compareTo(new BigDecimal("0.000001")) > 0)
                                       .collect(Collectors.toList());
    logger.log("Cryptos count: " + cryptos.size());
    return cryptos;
  }

  private void initTrading(Supplier<List<Crypto>> supplier) {
    logger.log("***** ***** Initial trading ***** *****");
    supplier.get()
            .stream()
            .map(crypto -> {
              List<Candlestick> candleStickData = binanceService.getCandleStickData(crypto.getSymbolInfo().getSymbol(),
                                                                                    FIFTEEN_MINUTES,
                                                                                    96L * 15L,
                                                                                    MINUTES);
              return crypto.calculateDataFromCandlesticks(candleStickData);
            })
            .filter(crypto -> crypto.getLastThreeHighAverage().compareTo(crypto.getPreviousThreeHighAverage()) > 0)
            .filter(crypto -> crypto.getPriceToSellPercentage().compareTo(MIN_PRICE_TO_SELL_PERCENTAGE) > 0)
            .filter(crypto -> crypto.getSumPercentageDifferences1h().compareTo(new BigDecimal("4")) < 0)
            .filter(crypto -> crypto.getSumPercentageDifferences10h().compareTo(new BigDecimal("400")) < 0)
            .forEach(acquireProcessor::acquireCrypto);
  }

  @Override
  public Boolean cancelTrade(OrderWrapper orderToCancel, ExchangeInfo exchangeInfo) {
    logger.log("***** ***** Cancel biggest order due all orders having negative remaining waiting time ***** *****");
    // 1. cancel existing order
    splitProcessor.cancelRequest(orderToCancel);

    // 2. sell cancelled order
    BigDecimal currentQuantity = getQuantity(orderToCancel.getOrder());
    SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
    splitProcessor.sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);
    return TRUE;
  }
}
