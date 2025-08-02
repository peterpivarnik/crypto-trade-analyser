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
 * Trade service for AWS lambda that processes cryptocurrency trading operations.
 * Handles splitting, acquiring, repeating, extracting and canceling trade orders
 * based on various market conditions and trading rules.
 */
public class LambdaTradeProcessor extends MainTradeProcessor {

  private final LambdaLogger logger;
  private final CryptoProcessor cryptoProcessor;
  private final SplitProcessor splitProcessor;
  private final AcquireProcessor acquireProcessor;
  private final RepeatTradingProcessor repeatTradingProcessor;
  private final ExtractProcessor extractProcessor;
  private final CancelProcessor cancelProcessor;
  private final List<String> allForbiddenPairs;

  /**
   * Constructs a new LambdaTradeProcessor with the specified dependencies.
   *
   * @param binanceService         service for executing Binance API operations
   * @param cryptoProcessor        processor for handling cryptocurrency operations
   * @param splitProcessor         processor for splitting trade orders
   * @param acquireProcessor       processor for acquiring new cryptocurrencies
   * @param repeatTradingProcessor processor for handling repeated trading operations
   * @param extractProcessor       processor for extracting trade orders
   * @param cancelProcessor        processor for canceling trade orders
   * @param forbiddenPairs         list of trading pairs that are forbidden for trading
   * @param logger                 lambda logger for logging operations
   */
  public LambdaTradeProcessor(BinanceService binanceService,
                              CryptoProcessor cryptoProcessor,
                              SplitProcessor splitProcessor,
                              AcquireProcessor acquireProcessor,
                              RepeatTradingProcessor repeatTradingProcessor,
                              ExtractProcessor extractProcessor,
                              CancelProcessor cancelProcessor,
                              List<String> forbiddenPairs,
                              LambdaLogger logger) {
    super(binanceService);
    this.cryptoProcessor = cryptoProcessor;
    this.splitProcessor = splitProcessor;
    this.acquireProcessor = acquireProcessor;
    this.repeatTradingProcessor = repeatTradingProcessor;
    this.extractProcessor = extractProcessor;
    this.cancelProcessor = cancelProcessor;
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
    } else if (shouldSplitOrderWithLowestOrderPrice(uniqueOpenOrdersSize, totalAmount, orderWrappers)) {
      List<Crypto> cryptos = cryptoProcessor.getCryptos(exchangeInfo, allForbiddenPairs);
      splitProcessor.splitOrderWithLowestOrderPrice(orderWrappers, exchangeInfo, totalAmounts, cryptos);
    } else if (shouldSplitHighestOrderAndBuy(uniqueOpenOrdersSize, minOpenOrders)) {
      List<Crypto> cryptos = cryptoProcessor.getCryptos(exchangeInfo, allForbiddenPairs);
      splitProcessor.splitHighestOrder(orderWrappers, exchangeInfo, totalAmounts, cryptos);
      BigDecimal myBalance = binanceService.getMyBalance(ASSET_BTC);
      if (acquireProcessor.haveBalanceForInitialTrading(myBalance)) {
        cryptos = cryptoProcessor.getCryptos(exchangeInfo, allForbiddenPairs);
        acquireProcessor.initTrading(cryptos);
      }
    } else if (shouldRebuyAllOrders(myBtcBalance, ordersAmount)) {
      repeatTradingProcessor.rebuyAllOrders(orderWrappers, exchangeInfo);
    } else if (shouldRebuyAnyOrder(orderWrappers, myBtcBalance)) {
      repeatTradingProcessor.rebuyOrders(orderWrappers, myBtcBalance, exchangeInfo);
    } else if (shouldExtractMoreOrders(orderWrappers, myBtcBalance)) {
      extractProcessor.extractOrders(orderWrappers, myBtcBalance, exchangeInfo);
    } else if (shouldExtractOneOrder(orderWrappers, myBtcBalance)) {
      extractProcessor.extractOnlyFirstOrder(orderWrappers, exchangeInfo);
    } else if (shouldSplitOrderForQuickerSelling(myBtcBalance, actualBalance, uniqueOpenOrdersSize, totalAmount)) {
      List<Crypto> cryptos = cryptoProcessor.getCryptos(exchangeInfo, allForbiddenPairs);
      splitProcessor.splitOrdersForQuickerSelling(orderWrappers, exchangeInfo, totalAmounts, cryptos);
    } else if (shouldCancelTrade(orderWrappers, myBtcBalance)) {
      cancelProcessor.cancelTrade(orderWrappers, exchangeInfo);
    }
  }

  private Set<String> getOrderSymbolsToSplit(List<Order> openOrders) {
    return openOrders.stream()
                     .map(Order::getSymbol)
                     .filter(allForbiddenPairs::contains)
                     .collect(Collectors.toSet());
  }

  private boolean shouldSplitOrderWithLowestOrderPrice(long uniqueOpenOrdersSize,
                                                       BigDecimal totalAmount,
                                                       List<OrderWrapper> orderWrappers) {
    return uniqueOpenOrdersSizeIsLessThanHundredTotalAmounts(uniqueOpenOrdersSize, totalAmount)
           && allOlderThanDay(orderWrappers);
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

  private boolean shouldRebuyAllOrders(BigDecimal myBtcBalance, BigDecimal ordersAmount) {
    return myBtcBalance.compareTo(ordersAmount.multiply(new BigDecimal("3"))) > 0;
  }

  private boolean shouldRebuyAnyOrder(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
    return orderWrappers.stream()
                        .anyMatch(orderWrapper -> repeatTradingProcessor.shouldBeRebought(orderWrapper, myBtcBalance));
  }

  private boolean shouldExtractMoreOrders(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
    return allOlderThanDay(orderWrappers) && haveEnoughBtcToExtractMoreOrders(myBtcBalance);
  }

  private boolean shouldExtractOneOrder(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
    return allOlderThanDay(orderWrappers) && !haveEnoughBtcToExtractMoreOrders(myBtcBalance);
  }

  private boolean haveEnoughBtcToExtractMoreOrders(BigDecimal myBtcBalance) {
    return myBtcBalance.compareTo(new BigDecimal("0.003")) > 0;
  }

  private boolean shouldCancelTrade(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
    return allOlderThanDay(orderWrappers)
           && allRemainWaitingTimeLessThanZero(orderWrappers)
           && allNeededBtcAmountBiggerThanMyBtcBalance(orderWrappers, myBtcBalance);
  }

  private boolean allOlderThanDay(List<OrderWrapper> orderWrappers) {
    return orderWrappers.stream()
                        .allMatch(orderWrapper -> orderWrapper.getActualWaitingTime()
                                                              .compareTo(new BigDecimal("24")) > 0);
  }

  private boolean allRemainWaitingTimeLessThanZero(List<OrderWrapper> orderWrappers) {
    return orderWrappers.stream()
                        .map(OrderWrapper::getRemainWaitingTime)
                        .allMatch(time -> time.compareTo(ZERO) < 0);
  }

  private boolean allNeededBtcAmountBiggerThanMyBtcBalance(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
    return orderWrappers.stream()
                        .map(OrderWrapper::getNeededBtcAmount)
                        .allMatch(orderBtcAmount -> orderBtcAmount.compareTo(myBtcBalance) > 0);
  }
}
