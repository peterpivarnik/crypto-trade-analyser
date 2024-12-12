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
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.UP;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
   * @param exchangeInfo         exchange trading rules and symbol information
   * @param actualBalance        actual balance
   * @param uniqueOpenOrdersSize number of unique open orders
   * @param totalAmount          current total amount
   * @param minOpenOrders        minimum of open orders
   */
  @Override
  public void trade(List<Order> openOrders,
                    Map<String, BigDecimal> totalAmounts,
                    BigDecimal myBtcBalance,
                    ExchangeInfo exchangeInfo,
                    BigDecimal actualBalance,
                    long uniqueOpenOrdersSize,
                    BigDecimal totalAmount,
                    int minOpenOrders) {
    Set<String> orderSymbolsToSplit = getOrderSymbolsToSplit(openOrders);
    BigDecimal ordersAmount = totalAmounts.values()
                                          .stream()
                                          .reduce(ZERO, BigDecimal::add);
    List<OrderWrapper> orderWrappers = getOrderWrapperStream(openOrders,
                                                             totalAmounts,
                                                             myBtcBalance,
                                                             exchangeInfo,
                                                             actualBalance)
        .collect(Collectors.toList());
    if (!orderSymbolsToSplit.isEmpty()) {
      logger.log("***** ***** Splitting cancelled trades ***** *****");
      orderSymbolsToSplit.forEach(symbol -> splitCancelledOrder(orderWrappers, symbol, exchangeInfo, totalAmounts));
    } else if (shouldRebuyAllOrders(myBtcBalance, ordersAmount)) {
      logger.log("***** ***** Rebuy all orders ***** *****");
      rebuyAllOrders(orderWrappers, exchangeInfo);
    } else if (shouldSplitOrderWithLowestOrderPrice(uniqueOpenOrdersSize, totalAmount, orderWrappers)) {
      logger.log("***** ***** Splitting order with lowest order price percentage ***** *****");
      orderWrappers.stream()
                   .filter(orderWrapper -> orderWrapper.getOrderBtcAmount()
                                                       .compareTo(new BigDecimal("0.001")) > 0)
                   .min(Comparator.comparing(OrderWrapper::getOrderPricePercentage))
                   .ifPresent(orderToSplit -> splitProcessor.split(orderToSplit,
                                                                   getCryptos(exchangeInfo),
                                                                   totalAmounts,
                                                                   exchangeInfo));
    } else if (shouldSplitOrderForQuickerSelling(myBtcBalance, actualBalance, uniqueOpenOrdersSize, totalAmount)) {
      logger.log("***** ***** Splitting trade for quicker selling ***** *****");
      Predicate<OrderWrapper> orderWrapperPredicate =
          orderWrapper -> orderWrapper.getOrderPricePercentage().compareTo(new BigDecimal("5")) < 0
                          && orderWrapper.getOrderBtcAmount().compareTo(new BigDecimal("0.001")) > 0;
      splitOrderWithHighestBtcAmount(orderWrappers, orderWrapperPredicate, exchangeInfo, totalAmounts);
    } else if (shouldSplitHighestOrderAndBuy(uniqueOpenOrdersSize, minOpenOrders)) {
      logger.log("***** ***** Splitting order with highest btc amount and init trading ***** *****");
      Predicate<OrderWrapper> orderWrapperPredicate =
          orderWrapper -> orderWrapper.getOrderPricePercentage().compareTo(new BigDecimal("20")) < 0;
      splitOrderWithHighestBtcAmount(orderWrappers, orderWrapperPredicate, exchangeInfo, totalAmounts);
      BigDecimal myBalance = binanceService.getMyBalance(ASSET_BTC);
      if (haveBalanceForInitialTrading(myBalance)) {
        initTrading(() -> getCryptos(exchangeInfo));
      }
    } else if (shouldCancelTrade(orderWrappers)) {
      cancelTrade(orderWrappers, exchangeInfo);
    } else {
      logger.log("***** ***** Rebuy orders ***** *****");
      rebuyOrders(orderWrappers, myBtcBalance, exchangeInfo);
    }
  }

  private Set<String> getOrderSymbolsToSplit(List<Order> openOrders) {
    return openOrders.stream()
                     .map(Order::getSymbol)
                     .filter(allForbiddenPairs::contains)
                     .collect(Collectors.toSet());
  }

  private void splitCancelledOrder(List<OrderWrapper> orderWrappers,
                                   String orderToSplit,
                                   ExchangeInfo exchangeInfo,
                                   Map<String, BigDecimal> totalAmounts) {
    OrderWrapper orderToCancel = orderWrappers.stream()
                                              .filter(orderWrapper -> orderWrapper.getOrder()
                                                                                  .getSymbol()
                                                                                  .equals(orderToSplit))
                                              .toList()
                                              .getFirst();
    List<Crypto> cryptos = getCryptos(exchangeInfo);
    splitProcessor.split(orderToCancel, cryptos, totalAmounts, exchangeInfo);
  }

  private boolean shouldRebuyAllOrders(BigDecimal myBtcBalance, BigDecimal ordersAmount) {
    return myBtcBalance.compareTo(ordersAmount.multiply(new BigDecimal("3"))) > 0;
  }

  private void rebuyAllOrders(List<OrderWrapper> orderWrappers, ExchangeInfo exchangeInfo) {
    Predicate<OrderWrapper> orderWrapperPredicate =
        orderWrapper -> orderWrapper.getOrderPricePercentage()
                                    .subtract(orderWrapper.getPriceToSellPercentage())
                                    .compareTo(MIN_PROFIT_PERCENTAGE) > 0;
    orderWrappers.stream()
                 .filter(orderWrapperPredicate)
                 .forEach(orderWrapper -> {
                   SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol());
                   repeatTradingProcessor.rebuySingleOrder(symbolInfo, orderWrapper);
                 });
  }

  private boolean shouldSplitOrderWithLowestOrderPrice(long uniqueOpenOrdersSize,
                                                       BigDecimal totalAmount,
                                                       List<OrderWrapper> orderWrappers) {
    return uniqueOpenOrdersSizeIsLessThanHundredTotalAmounts(uniqueOpenOrdersSize, totalAmount)
           && allOlderThanDay(orderWrappers);
  }

  private boolean allOlderThanDay(List<OrderWrapper> orderWrappers) {
    return orderWrappers.stream()
                        .allMatch(orderWrapper -> orderWrapper.getActualWaitingTime()
                                                              .compareTo(new BigDecimal("24")) > 0);
  }

  private boolean shouldSplitOrderForQuickerSelling(BigDecimal myBtcBalance,
                                                    BigDecimal actualBalance,
                                                    long uniqueOpenOrdersSize,
                                                    BigDecimal totalAmount) {
    return actualBtcBalanceMoreThanHalfOfActualBalance(myBtcBalance, actualBalance)
           && uniqueOpenOrdersSizeIsLessThanHundredTotalAmounts(uniqueOpenOrdersSize, totalAmount);
  }

  private boolean actualBtcBalanceMoreThanHalfOfActualBalance(BigDecimal myBtcBalance, BigDecimal actualBalance) {
    return myBtcBalance.compareTo(actualBalance.divide(new BigDecimal("2"), 8, CEILING)) > 0;
  }

  private boolean uniqueOpenOrdersSizeIsLessThanHundredTotalAmounts(long uniqueOpenOrdersSize, BigDecimal totalAmount) {
    return new BigDecimal(uniqueOpenOrdersSize).compareTo(totalAmount.multiply(new BigDecimal("100"))) < 0;
  }

  private boolean shouldSplitHighestOrderAndBuy(long uniqueOpenOrdersSize, long minOpenOrders) {
    return uniqueOpenOrdersSize <= minOpenOrders;
  }

  private void splitOrderWithHighestBtcAmount(List<OrderWrapper> orderWrappers,
                                              Predicate<OrderWrapper> orderWrapperPredicate,
                                              ExchangeInfo exchangeInfo,
                                              Map<String, BigDecimal> totalAmounts) {
    orderWrappers.stream()
                 .filter(orderWrapperPredicate)
                 .max(comparing(OrderWrapper::getOrderBtcAmount))
                 .ifPresent(orderWrapper -> splitProcessor.split(orderWrapper,
                                                                 getCryptos(exchangeInfo),
                                                                 totalAmounts,
                                                                 exchangeInfo));
  }

  private void rebuyOrders(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance, ExchangeInfo exchangeInfo) {
    orderWrappers.stream()
                 .filter(orderWrapper -> shouldBeRebought(orderWrapper, service -> myBtcBalance))
                 .forEach(orderWrapper -> {
                   if (!shouldBeRebought(orderWrapper, service -> service.getMyBalance(ASSET_BTC))) {
                     logger.log("Conditions to rebuy crypto not valid.");
                     return;
                   }
                   SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol());
                   repeatTradingProcessor.rebuySingleOrder(symbolInfo, orderWrapper);
                 });
  }

  private boolean shouldBeRebought(OrderWrapper orderWrapper, Function<BinanceService, BigDecimal> function) {
    return hasMinProfit(orderWrapper)
           && isRemainingTimeGreaterZero(orderWrapper)
           && hasEnoughBtcAmount(orderWrapper, function);
  }

  private boolean hasMinProfit(OrderWrapper orderWrapper) {
    return orderWrapper.getOrderPricePercentage()
                       .subtract(orderWrapper.getPriceToSellPercentage())
                       .compareTo(MIN_PROFIT_PERCENTAGE) > 0;
  }

  private boolean isRemainingTimeGreaterZero(OrderWrapper orderWrapper) {
    return orderWrapper.getActualWaitingTime()
                       .compareTo(orderWrapper.getMinWaitingTime()) > 0;
  }

  private boolean hasEnoughBtcAmount(OrderWrapper orderWrapper, Function<BinanceService, BigDecimal> function) {
    BigDecimal myBtcBalance = function.apply(binanceService);
    return isOrderPricePercentageLessThan10AndHasEnoughAmount(orderWrapper, myBtcBalance)
           || isOrderPricePercentageMoreThan10AndHasEnoughMultipliedAmount(orderWrapper, myBtcBalance);
  }

  private boolean isOrderPricePercentageLessThan10AndHasEnoughAmount(OrderWrapper orderWrapper,
                                                                     BigDecimal myBtcBalance) {
    return isOrderPricePercentageLessThan10(orderWrapper) && hasMultipliedAmount(orderWrapper, ONE, myBtcBalance);
  }

  private boolean isOrderPricePercentageMoreThan10AndHasEnoughMultipliedAmount(OrderWrapper orderWrapper,
                                                                               BigDecimal myBtcBalance) {
    BigDecimal multiplicator = orderWrapper.getOrderPricePercentage()
                                           .divide(TEN, 8, UP)
                                           .add(ONE);
    return !isOrderPricePercentageLessThan10(orderWrapper)
           && hasMultipliedAmount(orderWrapper, multiplicator, myBtcBalance);
  }

  private boolean isOrderPricePercentageLessThan10(OrderWrapper orderWrapper) {
    return orderWrapper.getOrderPricePercentage().compareTo(TEN) < 0;
  }

  private boolean hasMultipliedAmount(OrderWrapper orderWrapper, BigDecimal multiplicator, BigDecimal myBtcBalance) {
    return orderWrapper.getOrderBtcAmount()
                       .multiply(multiplicator)
                       .compareTo(myBtcBalance) < 0;
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

  private boolean shouldCancelTrade(List<OrderWrapper> orderWrappers) {
    return orderWrappers.stream()
                        .map(OrderWrapper::getRemainWaitingTime)
                        .allMatch(time -> time.compareTo(ZERO) < 0);
  }

  private void cancelTrade(List<OrderWrapper> orderWrappers, ExchangeInfo exchangeInfo) {
    orderWrappers.stream()
                 .max(Comparator.comparing(OrderWrapper::getCurrentBtcAmount))
                 .ifPresent(orderWrapper -> cancelAndSell(orderWrapper, exchangeInfo));
  }

  private void cancelAndSell(OrderWrapper orderToCancel, ExchangeInfo exchangeInfo) {
    logger.log("***** ***** Cancel biggest order due all orders having negative remaining waiting time ***** *****");
    // 1. cancel existing order
    splitProcessor.cancelRequest(orderToCancel);

    // 2. sell cancelled order
    BigDecimal currentQuantity = getQuantity(orderToCancel.getOrder());
    SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
    splitProcessor.sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);
  }
}
