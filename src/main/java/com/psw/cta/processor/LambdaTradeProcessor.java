package com.psw.cta.processor;

import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MIN_ORDER_PRICE_PERCENTAGE_TO_SPLIT;
import static java.math.BigDecimal.ZERO;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.Order;
import com.psw.cta.service.AcquireService;
import com.psw.cta.service.CancelService;
import com.psw.cta.service.CryptoService;
import com.psw.cta.service.ExtractService;
import com.psw.cta.service.ForbiddenPairsService;
import com.psw.cta.service.RepeatTradingService;
import com.psw.cta.service.SplitService;
import com.psw.cta.adapter.BinanceAdapter;
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
    private final CryptoService cryptoService;
    private final SplitService splitService;
    private final AcquireService acquireService;
    private final RepeatTradingService repeatTradingService;
    private final ExtractService extractService;
    private final CancelService cancelService;
    private final ForbiddenPairsService forbiddenPairsService;
    private List<String> allForbiddenPairs;
    private List<Crypto> loadedCryptos;

    /**
     * Constructs a new LambdaTradeProcessor with the specified dependencies.
     *
     * @param binanceAdapter         service for executing Binance API operations
     * @param cryptoService        processor for handling cryptocurrency operations
     * @param splitService         processor for splitting trade orders
     * @param acquireService       processor for acquiring new cryptocurrencies
     * @param repeatTradingService processor for handling repeated trading operations
     * @param extractService       processor for extracting trade orders
     * @param cancelService        processor for canceling trade orders
     * @param forbiddenPairsService provider of trading pairs that are forbidden for trading
     * @param logger                 lambda logger for logging operations
     */
    public LambdaTradeProcessor(BinanceAdapter binanceAdapter,
                                CryptoService cryptoService,
                                SplitService splitService,
                                AcquireService acquireService,
                                RepeatTradingService repeatTradingService,
                                ExtractService extractService,
                                CancelService cancelService,
                                ForbiddenPairsService forbiddenPairsService,
                                LambdaLogger logger) {
        super(binanceAdapter);
        this.cryptoService = cryptoService;
        this.splitService = splitService;
        this.acquireService = acquireService;
        this.repeatTradingService = repeatTradingService;
        this.extractService = extractService;
        this.cancelService = cancelService;
        this.logger = logger;
        this.forbiddenPairsService = forbiddenPairsService;
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
        allForbiddenPairs = forbiddenPairsService.getAllForbiddenPairs();
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
            repeatTradingService.rebuyAllOrders(orderWrappers, exchangeInfo);
        } else if (shouldRebuyOrderWithHighPricePercentage(orderWrappers, myBtcBalance)) {
            orderWrappers.stream()
                         .filter(ow -> canRebuyOrderWithHighLoss(ow, myBtcBalance))
                         .findFirst()
                         .ifPresent(ow -> repeatTradingService.rebuySingleOrder(
                             ow,
                             (service, wrapper) -> service.getMyBalance(ASSET_BTC)
                                                          .compareTo(wrapper.getNeededBtcAmount()) > 0,
                             exchangeInfo));
        } else if (shouldRebuyAnyOrder(orderWrappers, myBtcBalance)) {
            repeatTradingService.rebuyOrders(orderWrappers, myBtcBalance, exchangeInfo);
        } else if (shouldSplitOrderWithLowestOrderPrice(orderWrappers)) {
            splitService.splitOrderWithLowestOrderPrice(orderWrappers,
                                                        exchangeInfo,
                                                        getCryptos(exchangeInfo),
                                                        existingSymbols);
        } else if (shouldExtractMoreOrders(orderWrappers, myBtcBalance)) {
            extractService.extractOrders(orderWrappers, myBtcBalance, exchangeInfo);
        } else if (shouldExtractOneOrder(orderWrappers, myBtcBalance)) {
            extractService.extractOnlyFirstOrder(orderWrappers, exchangeInfo);
        } else if (haveOrdersToSplitByOrderPricePercentage(orderWrappers)) {
            splitService.splitOrdersWithOrderPricePercGreaterThen10(orderWrappers,
                                                                    exchangeInfo,
                                                                    getCryptos(exchangeInfo),
                                                                    existingSymbols);
        } else if (shouldCancelTrade(orderWrappers, myBtcBalance)) {
            cancelService.cancelTrade(orderWrappers, exchangeInfo);
        }
    }

    private List<String> acquireNewCryptos(ExchangeInfo exchangeInfo, Set<String> existingSymbols) {
        BigDecimal myBalance = binanceAdapter.getMyBalance(ASSET_BTC);
        if (!acquireService.haveBalanceForInitialTrading(myBalance)) {
            return List.of();
        }
        List<String> acquiredSymbols = acquireService.initTrading(getCryptos(exchangeInfo), existingSymbols);
        existingSymbols.addAll(acquiredSymbols);
        return acquiredSymbols;
    }

    private List<Crypto> getCryptos(ExchangeInfo exchangeInfo) {
        if (loadedCryptos == null) {
            loadedCryptos = cryptoService.getCryptos(exchangeInfo, allForbiddenPairs);
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
            if (splitService.hasCryptosToBuy(cryptos, totalAmounts.keySet())) {
                splitService.splitCancelledOrder(orderWrappers, symbol, exchangeInfo, cryptos, totalAmounts.keySet());
            } else {
                logger.log("No crypto to split into, selling order " + symbol);
                cancelService.cancelAndSell(forbiddenOrder, exchangeInfo);
            }
        } else {
            cancelService.cancelAndSell(forbiddenOrder, exchangeInfo);
        }
    }

    private boolean shouldSplitOrderWithLowestOrderPrice(List<OrderWrapper> orderWrappers) {
        return !orderWrappers.isEmpty()
               && allOrdersHaveHighOrderPricePercentage(orderWrappers)
               && allOlderThanDay(orderWrappers)
               && orderWrappers.stream().anyMatch(SplitService::hasEnoughBtcAmountToSplit);
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
                                orderWrapper -> repeatTradingService.shouldBeRebought(orderWrapper,
                                                                                      myBtcBalance));
    }

    private boolean shouldExtractMoreOrders(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
        return allOlderThanDay(orderWrappers)
               && haveEnoughBtcToExtractMoreOrders(myBtcBalance)
               && orderWrappers.stream().anyMatch(ExtractService::canBeExtracted);
    }

    private boolean shouldExtractOneOrder(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance) {
        return allOlderThanDay(orderWrappers)
               && !haveEnoughBtcToExtractMoreOrders(myBtcBalance)
               && !isBtcBalanceBelowMinToExtract(myBtcBalance)
               && ExtractService.canExtractFirstOrder(orderWrappers);
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
                                            && SplitService.hasEnoughBtcAmountToSplit(ow));
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
