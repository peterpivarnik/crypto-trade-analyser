package com.psw.cta.processor;

import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MIN_ORDER_PRICE_PERCENTAGE_TO_SPLIT;
import static java.math.BigDecimal.ZERO;

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
import java.util.HashSet;
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

    private static final BigDecimal MIN_BTC_BALANCE_TO_EXTRACT = new BigDecimal("0.0002");
    private static final BigDecimal MIN_BTC_BALANCE_TO_EXTRACT_MORE_ORDERS = new BigDecimal("0.003");
    private static final BigDecimal MIN_ORDER_PRICE_PERCENTAGE_TO_REBUY_HIGH_LOSS = new BigDecimal("30");
    private static final BigDecimal MIN_ORDER_PRICE_PERCENTAGE_TO_SELL_FORBIDDEN_ORDER = new BigDecimal("45");
    private static final BigDecimal MIN_ORDER_PRICE_PERCENTAGE_OF_ALL_ORDERS_TO_SPLIT = new BigDecimal("20");
    private static final BigDecimal MIN_BTC_TO_ORDERS_RATIO_TO_REBUY_ALL = new BigDecimal("3");
    private static final BigDecimal HOURS_IN_DAY = new BigDecimal("24");

    private final LambdaLogger logger;
    private final CryptoProcessor cryptoProcessor;
    private final SplitProcessor splitProcessor;
    private final AcquireProcessor acquireProcessor;
    private final RepeatTradingProcessor repeatTradingProcessor;
    private final ExtractProcessor extractProcessor;
    private final CancelProcessor cancelProcessor;
    private final ForbiddenPairsProvider forbiddenPairsProvider;
    private List<String> allForbiddenPairs;
    private List<Crypto> loadedCryptos;

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
     * @param forbiddenPairsProvider provider of trading pairs that are forbidden for trading
     * @param logger                 lambda logger for logging operations
     */
    public LambdaTradeProcessor(BinanceService binanceService,
                                CryptoProcessor cryptoProcessor,
                                SplitProcessor splitProcessor,
                                AcquireProcessor acquireProcessor,
                                RepeatTradingProcessor repeatTradingProcessor,
                                ExtractProcessor extractProcessor,
                                CancelProcessor cancelProcessor,
                                ForbiddenPairsProvider forbiddenPairsProvider,
                                LambdaLogger logger) {
        super(binanceService);
        this.cryptoProcessor = cryptoProcessor;
        this.splitProcessor = splitProcessor;
        this.acquireProcessor = acquireProcessor;
        this.repeatTradingProcessor = repeatTradingProcessor;
        this.extractProcessor = extractProcessor;
        this.cancelProcessor = cancelProcessor;
        this.logger = logger;
        this.forbiddenPairsProvider = forbiddenPairsProvider;
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
     * @param minOpenOrders        minimum of open orders
     */
    @Override
    public void trade(List<Order> openOrders,
                      Map<String, BigDecimal> totalAmounts,
                      BigDecimal myBtcBalance,
                      BigDecimal actualBalance,
                      ExchangeInfo exchangeInfo,
                      long uniqueOpenOrdersSize,
                      int minOpenOrders) {
        loadedCryptos = null;
        allForbiddenPairs = forbiddenPairsProvider.getAllForbiddenPairs();
        Set<String> forbiddenOrderSymbols = getForbiddenOrderSymbols(openOrders);
        BigDecimal ordersAmount = totalAmounts.values()
                                              .stream()
                                              .reduce(ZERO, BigDecimal::add);
        List<OrderWrapper> orderWrappers = getOrderWrapperStream(openOrders,
                                                                 myBtcBalance,
                                                                 actualBalance,
                                                                 totalAmounts)
            .toList();
        if (!forbiddenOrderSymbols.isEmpty()) {
            logger.log("***** ***** Handling first forbidden order ***** *****");
            forbiddenOrderSymbols.stream()
                                 .findFirst()
                                 .ifPresent(symbol -> handleForbiddenOrder(totalAmounts,
                                                                           exchangeInfo,
                                                                           symbol,
                                                                           orderWrappers));
            return;
        }
        Set<String> existingSymbols = new HashSet<>(totalAmounts.keySet());
        if (shouldAcquireNewCryptos(uniqueOpenOrdersSize, minOpenOrders)) {
            List<String> acquiredSymbols = acquireNewCryptos(exchangeInfo, existingSymbols);
            if (!acquiredSymbols.isEmpty()) {
                return;
            }
            logger.log("No new crypto acquired, continuing with other trades");
        }
        if (shouldRebuyAllOrders(myBtcBalance, ordersAmount)) {
            repeatTradingProcessor.rebuyAllOrders(orderWrappers, exchangeInfo);
        } else if (shouldRebuyOrderWithHighPricePercentage(orderWrappers, myBtcBalance)) {
            orderWrappers.stream()
                         .filter(ow -> canRebuyOrderWithHighLoss(ow, myBtcBalance))
                         .findFirst()
                         .ifPresent(ow -> repeatTradingProcessor.rebuySingleOrder(
                             ow,
                             (service, wrapper) -> service.getMyBalance(ASSET_BTC)
                                                          .compareTo(wrapper.getNeededBtcAmount()) > 0,
                             exchangeInfo));
        } else if (shouldRebuyAnyOrder(orderWrappers, myBtcBalance)) {
            repeatTradingProcessor.rebuyOrders(orderWrappers, myBtcBalance, exchangeInfo);
        } else if (shouldSplitOrderWithLowestOrderPrice(orderWrappers)) {
            splitProcessor.splitOrderWithLowestOrderPrice(orderWrappers,
                                                          exchangeInfo,
                                                          getCryptos(exchangeInfo),
                                                          existingSymbols);
        } else if (shouldExtractMoreOrders(orderWrappers, myBtcBalance)) {
            extractProcessor.extractOrders(orderWrappers, myBtcBalance, exchangeInfo);
        } else if (shouldExtractOneOrder(orderWrappers, myBtcBalance)) {
            extractProcessor.extractOnlyFirstOrder(orderWrappers, exchangeInfo);
        } else if (haveOrdersToSplitByOrderPricePercentage(orderWrappers)) {
            splitProcessor.splitOrdersWithOrderPricePercGreaterThen10(orderWrappers,
                                                                      exchangeInfo,
                                                                      getCryptos(exchangeInfo),
                                                                      existingSymbols);
        } else if (shouldCancelTrade(orderWrappers, myBtcBalance)) {
            cancelProcessor.cancelTrade(orderWrappers, exchangeInfo);
        }
    }

    private List<String> acquireNewCryptos(ExchangeInfo exchangeInfo, Set<String> existingSymbols) {
        BigDecimal myBalance = binanceService.getMyBalance(ASSET_BTC);
        if (!acquireProcessor.haveBalanceForInitialTrading(myBalance)) {
            return List.of();
        }
        List<String> acquiredSymbols = acquireProcessor.initTrading(getCryptos(exchangeInfo), existingSymbols);
        existingSymbols.addAll(acquiredSymbols);
        return acquiredSymbols;
    }

    private List<Crypto> getCryptos(ExchangeInfo exchangeInfo) {
        if (loadedCryptos == null) {
            loadedCryptos = cryptoProcessor.getCryptos(exchangeInfo, allForbiddenPairs);
        }
        return loadedCryptos;
    }

    private Set<String> getForbiddenOrderSymbols(List<Order> openOrders) {
        return openOrders.stream()
                         .map(Order::getSymbol)
                         .filter(allForbiddenPairs::contains)
                         .collect(Collectors.toSet());
    }

    private void handleForbiddenOrder(Map<String, BigDecimal> totalAmounts,
                                      ExchangeInfo exchangeInfo,
                                      String symbol,
                                      List<OrderWrapper> orderWrappers) {
        OrderWrapper forbiddenOrder = orderWrappers.stream()
                                                   .filter(orderWrapper -> orderWrapper.getOrder()
                                                                                       .getSymbol()
                                                                                       .equals(symbol))
                                                   .findFirst()
                                                   .orElseThrow();
        logger.log("Forbidden order:" + forbiddenOrder);
        if (forbiddenOrder.getOrderPricePercentage()
                          .compareTo(MIN_ORDER_PRICE_PERCENTAGE_TO_SELL_FORBIDDEN_ORDER) < 0) {
            List<Crypto> cryptos = getCryptos(exchangeInfo);
            if (splitProcessor.hasCryptosToBuy(cryptos, totalAmounts.keySet())) {
                splitProcessor.splitCancelledOrder(orderWrappers, symbol, exchangeInfo, cryptos, totalAmounts.keySet());
            } else {
                logger.log("No crypto to split into, selling order " + symbol);
                cancelProcessor.cancelAndSell(forbiddenOrder, exchangeInfo);
            }
        } else {
            cancelProcessor.cancelAndSell(forbiddenOrder, exchangeInfo);
        }
    }

    private boolean shouldSplitOrderWithLowestOrderPrice(List<OrderWrapper> orderWrappers) {
        return !orderWrappers.isEmpty()
               && allOrdersHaveHighOrderPricePercentage(orderWrappers)
               && allOlderThanDay(orderWrappers)
               && orderWrappers.stream().anyMatch(SplitProcessor::hasEnoughBtcAmountToSplit);
    }

    private boolean allOrdersHaveHighOrderPricePercentage(List<OrderWrapper> orderWrappers) {
        return orderWrappers.stream()
                            .map(OrderWrapper::getOrderPricePercentage)
                            .allMatch(percentage -> percentage.compareTo(
                                MIN_ORDER_PRICE_PERCENTAGE_OF_ALL_ORDERS_TO_SPLIT) > 0);
    }

    private boolean shouldAcquireNewCryptos(long uniqueOpenOrdersSize, long minOpenOrders) {
        return uniqueOpenOrdersSize <= minOpenOrders;
    }

    private boolean shouldRebuyAllOrders(BigDecimal myBtcBalance, BigDecimal ordersAmount) {
        return myBtcBalance.compareTo(ordersAmount.multiply(MIN_BTC_TO_ORDERS_RATIO_TO_REBUY_ALL)) > 0;
    }

    private boolean shouldRebuyAnyOrder(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
        return orderWrappers.stream()
                            .anyMatch(
                                orderWrapper -> repeatTradingProcessor.shouldBeRebought(orderWrapper,
                                                                                        myBtcBalance));
    }

    private boolean shouldExtractMoreOrders(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
        return allOlderThanDay(orderWrappers)
               && haveEnoughBtcToExtractMoreOrders(myBtcBalance)
               && orderWrappers.stream().anyMatch(ExtractProcessor::canBeExtracted);
    }

    private boolean shouldExtractOneOrder(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
        return allOlderThanDay(orderWrappers)
               && !haveEnoughBtcToExtractMoreOrders(myBtcBalance)
               && !isBtcBalanceBelowMinToExtract(myBtcBalance)
               && ExtractProcessor.canExtractFirstOrder(orderWrappers);
    }

    private boolean haveEnoughBtcToExtractMoreOrders(BigDecimal myBtcBalance) {
        return myBtcBalance.compareTo(MIN_BTC_BALANCE_TO_EXTRACT_MORE_ORDERS) > 0;
    }

    private boolean isBtcBalanceBelowMinToExtract(BigDecimal myBtcBalance) {
        return myBtcBalance.compareTo(MIN_BTC_BALANCE_TO_EXTRACT) < 0;
    }

    private boolean shouldRebuyOrderWithHighPricePercentage(List<OrderWrapper> orderWrappers,
                                                            BigDecimal myBtcBalance) {
        return orderWrappers.stream()
                            .anyMatch(ow -> canRebuyOrderWithHighLoss(ow, myBtcBalance));
    }

    private boolean canRebuyOrderWithHighLoss(OrderWrapper orderWrapper, BigDecimal myBtcBalance) {
        return orderWrapper.getOrderPricePercentage().compareTo(MIN_ORDER_PRICE_PERCENTAGE_TO_REBUY_HIGH_LOSS) > 0
               && myBtcBalance.compareTo(orderWrapper.getNeededBtcAmount()) > 0;
    }

    private boolean haveOrdersToSplitByOrderPricePercentage(List<OrderWrapper> orderWrappers) {
        return orderWrappers.stream()
                            .anyMatch(ow -> ow.getOrderPricePercentage()
                                              .compareTo(MIN_ORDER_PRICE_PERCENTAGE_TO_SPLIT) > 0
                                            && SplitProcessor.hasEnoughBtcAmountToSplit(ow));
    }

    private boolean shouldCancelTrade(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
        return isBtcBalanceBelowMinToExtract(myBtcBalance)
               && allOlderThanDay(orderWrappers)
               && allRemainWaitingTimeLessThanZero(orderWrappers)
               && allNeededBtcAmountBiggerThanMyBtcBalance(orderWrappers, myBtcBalance);
    }

    private boolean allOlderThanDay(List<OrderWrapper> orderWrappers) {
        return orderWrappers.stream()
                            .allMatch(orderWrapper -> orderWrapper.getActualWaitingTime()
                                                                  .compareTo(HOURS_IN_DAY) > 0);
    }

    private boolean allRemainWaitingTimeLessThanZero(List<OrderWrapper> orderWrappers) {
        return orderWrappers.stream()
                            .map(OrderWrapper::getRemainWaitingTime)
                            .allMatch(time -> time.compareTo(ZERO) < 0);
    }

    private boolean allNeededBtcAmountBiggerThanMyBtcBalance(List<OrderWrapper> orderWrappers,
                                                             BigDecimal myBtcBalance) {
        return orderWrappers.stream()
                            .map(OrderWrapper::getNeededBtcAmount)
                            .allMatch(orderBtcAmount -> orderBtcAmount.compareTo(myBtcBalance) > 0);
    }
}
