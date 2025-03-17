package com.psw.cta.processor;

import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_BNB_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_WBTC_BTC;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.processor.trade.AcquireProcessor;
import com.psw.cta.processor.trade.CancelProcessor;
import com.psw.cta.processor.trade.CryptoProcessor;
import com.psw.cta.processor.trade.ExtractProcessor;
import com.psw.cta.processor.trade.RepeatTradingProcessor;
import com.psw.cta.processor.trade.SplitProcessor;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trade service for AWS lambda.
 */
public class LambdaTradeProcessor extends MainTradeProcessor {

  private final CryptoProcessor cryptoProcessor;
  private final SplitProcessor splitProcessor;
  private final RepeatTradingProcessor repeatTradingProcessor;
  private final ExtractProcessor extractProcessor;
  private final AcquireProcessor acquireProcessor;
  private final CancelProcessor cancelProcessor;
  private final List<String> allForbiddenPairs;
  private final LambdaLogger logger;

  /**
   * Default constructor.
   *
   * @param binanceService Service providing functionality for Binance API
   * @param forbiddenPairs forbidden pairs
   * @param logger         logger
   */
  public LambdaTradeProcessor(BinanceService binanceService, List<String> forbiddenPairs, LambdaLogger logger) {
    super(binanceService);
    this.cryptoProcessor = new CryptoProcessor(binanceService, logger);
    this.splitProcessor = new SplitProcessor(binanceService, logger);
    this.repeatTradingProcessor = new RepeatTradingProcessor(binanceService, logger);
    this.extractProcessor = new ExtractProcessor(binanceService, logger);
    this.acquireProcessor = new AcquireProcessor(binanceService, logger);
    this.cancelProcessor = new CancelProcessor(binanceService, logger);
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
   * @param actualBalance        actual balance
   * @param exchangeInfo         exchange trading rules and symbol information
   * @param uniqueOpenOrdersSize number of unique open orders
   * @param totalAmount          current total amount
   * @param minOpenOrders        minimum of open orders
   */
  @Override
  public void trade(List<Order> openOrders,
                    Map<String, BigDecimal> totalAmounts,
                    BigDecimal myBtcBalance,
                    BigDecimal actualBalance,
                    ExchangeInfo exchangeInfo,
                    long uniqueOpenOrdersSize,
                    BigDecimal totalAmount,
                    int minOpenOrders) {
    Set<String> orderSymbolsToSplit = getOrderSymbolsToSplit(openOrders);
    BigDecimal ordersAmount = totalAmounts.values()
                                          .stream()
                                          .reduce(ZERO, BigDecimal::add);
    List<OrderWrapper> orderWrappers = getOrderWrapperStream(openOrders, myBtcBalance, actualBalance, totalAmounts)
        .collect(Collectors.toList());
    if (!orderSymbolsToSplit.isEmpty()) {
      logger.log("***** ***** Splitting cancelled trades ***** *****");
      orderSymbolsToSplit.forEach(symbol -> {
        List<Crypto> cryptos = cryptoProcessor.getCryptos(exchangeInfo,
                                                          allForbiddenPairs);
        splitProcessor.splitCancelledOrder(orderWrappers,
                                           symbol,
                                           exchangeInfo,
                                           totalAmounts,
                                           cryptos);
      });
    } else if (shouldRebuyAllOrders(myBtcBalance, ordersAmount)) {
      repeatTradingProcessor.rebuyAllOrders(orderWrappers, exchangeInfo);
    } else if (shouldSplitOrderWithLowestOrderPrice(uniqueOpenOrdersSize, totalAmount, orderWrappers)) {
      List<Crypto> cryptos = cryptoProcessor.getCryptos(exchangeInfo, allForbiddenPairs);
      splitProcessor.splitOrderWithLowestOrderPrice(orderWrappers, exchangeInfo, totalAmounts, cryptos);//
    } else if (shouldExtractOrderWithLowestOrderPrice(orderWrappers)) {
      extractProcessor.extractOrderWithLowestOrderPrice(orderWrappers, myBtcBalance, exchangeInfo);
    } else if (shouldSplitOrderForQuickerSelling(myBtcBalance, actualBalance, uniqueOpenOrdersSize, totalAmount)) {
      List<Crypto> cryptos = cryptoProcessor.getCryptos(exchangeInfo, allForbiddenPairs);
      splitProcessor.splitOrdersForQuickerSelling(orderWrappers, exchangeInfo, totalAmounts, cryptos);
    } else if (shouldSplitHighestOrderAndBuy(uniqueOpenOrdersSize, minOpenOrders)) {
      List<Crypto> cryptos = cryptoProcessor.getCryptos(exchangeInfo, allForbiddenPairs);
      splitProcessor.splitHighestOrder(orderWrappers, exchangeInfo, totalAmounts, cryptos);
      BigDecimal myBalance = binanceService.getMyBalance(ASSET_BTC);
      if (acquireProcessor.haveBalanceForInitialTrading(myBalance)) {
        cryptos = cryptoProcessor.getCryptos(exchangeInfo, allForbiddenPairs);
        acquireProcessor.initTrading(cryptos);
      }
    } else if (shouldCancelTrade(orderWrappers, myBtcBalance)) {
      cancelProcessor.cancelTrade(orderWrappers, exchangeInfo);
    } else {
      repeatTradingProcessor.rebuyOrders(orderWrappers, myBtcBalance, exchangeInfo);
    }
  }

  private Set<String> getOrderSymbolsToSplit(List<Order> openOrders) {
    return openOrders.stream()
                     .map(Order::getSymbol)
                     .filter(allForbiddenPairs::contains)
                     .collect(Collectors.toSet());
  }

  private boolean shouldRebuyAllOrders(BigDecimal myBtcBalance, BigDecimal ordersAmount) {
    return myBtcBalance.compareTo(ordersAmount.multiply(new BigDecimal("3"))) > 0;
  }


  private boolean shouldSplitOrderWithLowestOrderPrice(long uniqueOpenOrdersSize,
                                                       BigDecimal totalAmount,
                                                       List<OrderWrapper> orderWrappers) {
    return uniqueOpenOrdersSizeIsLessThanHundredTotalAmounts(uniqueOpenOrdersSize, totalAmount)
           && allOlderThanDay(orderWrappers);
  }

  private boolean shouldExtractOrderWithLowestOrderPrice(List<OrderWrapper> orderWrappers) {
    return allOlderThanDay(orderWrappers);
  }

  private boolean shouldSplitOrderForQuickerSelling(BigDecimal myBtcBalance,
                                                    BigDecimal actualBalance,
                                                    long uniqueOpenOrdersSize,
                                                    BigDecimal totalAmount) {
    return actualBtcBalanceMoreThanHalfOfActualBalance(myBtcBalance, actualBalance)
           && uniqueOpenOrdersSizeIsLessThanHundredTotalAmounts(uniqueOpenOrdersSize, totalAmount);
  }

  private boolean shouldSplitHighestOrderAndBuy(long uniqueOpenOrdersSize, long minOpenOrders) {
    return uniqueOpenOrdersSize <= minOpenOrders;
  }

  private boolean shouldCancelTrade(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
    return allRemainWaitingTimeLessThanZero(orderWrappers)
           && allOrderBtcAmountBiggerThanMyBtcBalance(orderWrappers, myBtcBalance);
  }

  private boolean uniqueOpenOrdersSizeIsLessThanHundredTotalAmounts(long uniqueOpenOrdersSize, BigDecimal totalAmount) {
    return new BigDecimal(uniqueOpenOrdersSize).compareTo(totalAmount.multiply(new BigDecimal("100"))) < 0;
  }

  private boolean allOlderThanDay(List<OrderWrapper> orderWrappers) {
    return orderWrappers.stream()
                        .allMatch(orderWrapper -> orderWrapper.getActualWaitingTime()
                                                              .compareTo(new BigDecimal("24")) > 0);
  }

  private boolean actualBtcBalanceMoreThanHalfOfActualBalance(BigDecimal myBtcBalance, BigDecimal actualBalance) {
    return myBtcBalance.compareTo(actualBalance.divide(new BigDecimal("2"), 8, CEILING)) > 0;
  }

  private boolean allRemainWaitingTimeLessThanZero(List<OrderWrapper> orderWrappers) {
    return orderWrappers.stream()
                        .map(OrderWrapper::getRemainWaitingTime)
                        .allMatch(time -> time.compareTo(ZERO) < 0);
  }

  private boolean allOrderBtcAmountBiggerThanMyBtcBalance(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
    return orderWrappers.stream()
                        .map(OrderWrapper::getOrderBtcAmount)
                        .allMatch(orderBtcAmount -> orderBtcAmount.compareTo(myBtcBalance) > 0);
  }
}
