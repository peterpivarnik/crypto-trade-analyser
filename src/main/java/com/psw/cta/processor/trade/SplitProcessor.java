package com.psw.cta.processor.trade;

import static com.psw.cta.utils.Constants.FIBONACCI_SEQUENCE;
import static java.math.RoundingMode.CEILING;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.exception.CryptoTraderException;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Service to split crypto to cryptos with small amount.
 */
public class SplitProcessor implements CryptoToBuyProvider {

    private static final BigDecimal MAX_BTC_AMOUNT_FOR_LAST_CRYPTO = new BigDecimal("0.005");
    private static final BigDecimal MIN_BTC_AMOUNT_TO_SPLIT = new BigDecimal("0.001");

    private final BinanceService binanceService;
    private final LambdaLogger logger;

    /**
     * Default constructor.
     *
     * @param binanceService service for {@link BinanceApi}
     * @param logger         logger
     */
    public SplitProcessor(BinanceService binanceService, LambdaLogger logger) {
        this.binanceService = binanceService;
        this.logger = logger;
    }

    /**
     * Returns whether there is any crypto an order can be split into.
     *
     * @param cryptos         cryptos for new buy
     * @param existingSymbols existing symbols
     * @return whether there is any crypto to buy
     */
    public boolean hasCryptosToBuy(List<Crypto> cryptos, Set<String> existingSymbols) {
        return !getCryptosToSplitInto(cryptos, existingSymbols).isEmpty();
    }

    /**
     * Splits cancelled order.
     *
     * @param orderWrappers   all orders
     * @param symbol          symbol of order to splitting
     * @param exchangeInfo    exchange info
     * @param cryptos         cryptos for new buy
     * @param existingSymbols existing symbols
     */
    public void splitCancelledOrder(List<OrderWrapper> orderWrappers,
                                    String symbol,
                                    ExchangeInfo exchangeInfo,
                                    List<Crypto> cryptos,
                                    Set<String> existingSymbols) {
        orderWrappers.stream()
                     .filter(orderWrapper -> orderWrapper.getOrder()
                                                         .getSymbol()
                                                         .equals(symbol))
                     .findFirst()
                     .ifPresent(orderToSplit -> split(orderToSplit, exchangeInfo, cryptos, existingSymbols));
    }

    /**
     * Splits order with high needed BTC amount. Threshold scales linearly with orderPricePercentage:
     * higher percentage (more stuck order) → lower required neededBtcAmount to trigger split.
     *
     * @param orderWrappers   all orders
     * @param exchangeInfo    exchange info
     * @param cryptos         cryptos for new buy
     * @param existingSymbols existing symbols
     */
    public void splitOrderWithHighNeededAmount(List<OrderWrapper> orderWrappers,
                                               ExchangeInfo exchangeInfo,
                                               List<Crypto> cryptos,
                                               Set<String> existingSymbols) {
        logger.log("***** ***** Splitting order with high needed BTC amount ***** *****");
        orderWrappers.stream()
                     .filter(SplitProcessor::hasHighNeededBtcAmount)
                     .findFirst()
                     .ifPresent(ow -> split(ow, exchangeInfo, cryptos, existingSymbols));
    }

    /**
     * Checks whether the order's needed BTC amount exceeds the dynamic threshold.
     * Threshold: max(0.002, 0.02 - 0.0004 * orderPricePercentage).
     *
     * @param orderWrapper order to evaluate
     * @return true if neededBtcAmount is above the threshold
     */
    public static boolean hasHighNeededBtcAmount(OrderWrapper orderWrapper) {
        BigDecimal threshold = new BigDecimal("0.02")
            .subtract(new BigDecimal("0.0004").multiply(orderWrapper.getOrderPricePercentage()))
            .max(new BigDecimal("0.002"));
        return orderWrapper.getNeededBtcAmount().compareTo(threshold) > 0;
    }

    /**
     * Checks whether the order is big enough to be split.
     *
     * @param orderWrapper order to evaluate
     * @return true if order BTC amount is above the minimal amount to split
     */
    public static boolean hasEnoughBtcAmountToSplit(OrderWrapper orderWrapper) {
        return orderWrapper.getOrderBtcAmount().compareTo(MIN_BTC_AMOUNT_TO_SPLIT) > 0;
    }

    /**
     * Splits order with lower order price.
     *
     * @param orderWrappers   all orders
     * @param exchangeInfo    exchange info
     * @param cryptos         cryptos for new buy
     * @param existingSymbols existing symbols
     */
    public void splitOrderWithLowestOrderPrice(List<OrderWrapper> orderWrappers,
                                               ExchangeInfo exchangeInfo,
                                               List<Crypto> cryptos,
                                               Set<String> existingSymbols) {
        logger.log("***** ***** Splitting order with lowest order price percentage ***** *****");
        orderWrappers.stream()
                     .filter(SplitProcessor::hasEnoughBtcAmountToSplit)
                     .min(Comparator.comparing(OrderWrapper::getOrderPricePercentage))
                     .ifPresent(orderToSplit -> split(orderToSplit, exchangeInfo, cryptos, existingSymbols));
    }

    /**
     * Split order when have enough BTC balance to have more orders for rebuy.
     *
     * @param orderWrappers   all orders
     * @param exchangeInfo    exchange info
     * @param cryptos         cryptos for new buy
     * @param existingSymbols existing symbols
     */
    public void splitOrdersForQuickerSelling(List<OrderWrapper> orderWrappers,
                                             ExchangeInfo exchangeInfo,
                                             List<Crypto> cryptos,
                                             Set<String> existingSymbols) {
        logger.log("***** ***** Splitting trade for quicker selling ***** *****");
        Predicate<OrderWrapper> orderWrapperPredicate =
            orderWrapper -> orderWrapper.getOrderPricePercentage().compareTo(new BigDecimal("5")) < 0
                            && hasEnoughBtcAmountToSplit(orderWrapper);
        splitOrderWithHighestOrderAmount(orderWrappers,
                                         orderWrapperPredicate,
                                         exchangeInfo,
                                         cryptos,
                                         existingSymbols);
    }

    /**
     * Split order with the highest order btc amount.
     *
     * @param orderWrappers   all orders
     * @param exchangeInfo    exchange info
     * @param cryptos         cryptos for new buy
     * @param existingSymbols existing symbols
     */
    public void splitHighestOrder(List<OrderWrapper> orderWrappers,
                                  ExchangeInfo exchangeInfo,
                                  List<Crypto> cryptos,
                                  Set<String> existingSymbols) {
        logger.log("***** ***** Splitting order with highest btc amount and init trading ***** *****");
        Predicate<OrderWrapper> orderWrapperPredicate =
            orderWrapper -> orderWrapper.getOrderPricePercentage().compareTo(new BigDecimal("20")) < 0;
        splitOrderWithHighestOrderAmount(orderWrappers,
                                         orderWrapperPredicate,
                                         exchangeInfo,
                                         cryptos,
                                         existingSymbols);
    }

    /**
     * Split orders with orderPrice percentage > 10.
     *
     * @param orderWrappers   all orders
     * @param exchangeInfo    exchange info
     * @param cryptos         cryptos for new buy
     * @param existingSymbols existing symbols
     */
    public void splitOrdersWithOrderPricePercGreaterThen10(List<OrderWrapper> orderWrappers,
                                                           ExchangeInfo exchangeInfo,
                                                           List<Crypto> cryptos,
                                                           Set<String> existingSymbols) {
        logger.log("***** ***** Splitting orders with orderPricePercentage > 10 ***** *****");
        Predicate<OrderWrapper> orderWrapperPredicate =
            orderWrapper -> orderWrapper.getOrderPricePercentage().compareTo(new BigDecimal("10")) > 0;
        splitOrderWithHighestOrderAmount(orderWrappers,
                                         orderWrapperPredicate,
                                         exchangeInfo,
                                         cryptos,
                                         existingSymbols);
    }

    private void splitOrderWithHighestOrderAmount(List<OrderWrapper> orderWrappers,
                                                  Predicate<OrderWrapper> orderWrapperPredicate,
                                                  ExchangeInfo exchangeInfo,
                                                  List<Crypto> cryptos,
                                                  Set<String> existingSymbols) {
        orderWrappers.stream()
                     .filter(orderWrapperPredicate)
                     .max(comparing(OrderWrapper::getOrderBtcAmount))
                     .ifPresent(orderWrapper -> split(orderWrapper, exchangeInfo, cryptos, existingSymbols));
    }

    private void split(OrderWrapper orderToCancel,
                       ExchangeInfo exchangeInfo,
                       List<Crypto> cryptos,
                       Set<String> existingSymbols) {
        logger.log("***** ***** Splitting amounts ***** *****");

        // 0. Check order still exist
        String symbol = orderToCancel.getOrder().getSymbol();
        try {
            Long orderId = orderToCancel.getOrder().getOrderId();
            binanceService.checkOrderStatus(symbol, orderId);
        } catch (BinanceApiException exception) {
            logger.log("Order do not exist anymore: " + symbol);
            return;
        }

        // 1. find cryptos to buy before anything is sold
        List<Crypto> cryptosToBuy = getCryptosToSplitInto(cryptos, existingSymbols);
        if (cryptosToBuy.isEmpty()) {
            throw new CryptoTraderException("No crypto available to buy, keeping order " + symbol);
        }
        logger.log("cryptosToBuy.size(): " + cryptosToBuy.size());

        // 2. cancel existing order
        binanceService.cancelOrder(orderToCancel);

        // 3. sell cancelled order
        SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(symbol);
        BigDecimal currentQuantity = orderToCancel.getQuantity();
        BigDecimal totalBtcAmountToSpend = binanceService.sellAvailableBalance(symbolInfoOfSellOrder,
                                                                               currentQuantity);
        logger.log("totalBtcAmountToSpend: " + totalBtcAmountToSpend);

        // 4. buy new cryptos and place sell orders
        buyAndSellWithFibonacci(orderToCancel, cryptosToBuy, totalBtcAmountToSpend, 0);
    }

    private void buyAndSellWithFibonacci(OrderWrapper orderToCancel,
                                         List<Crypto> cryptosToBuy,
                                         BigDecimal btcAmountToSpend,
                                         int cryptoToBuyIndex) {
        logger.log("cryptosToBuy.size(): " + cryptosToBuy.size());
        logger.log("btcAmountToSpend: " + btcAmountToSpend);
        logger.log("cryptoToBuyIndex: " + cryptoToBuyIndex);
        BigDecimal minBtcAmountToTrade = new BigDecimal("0.0001");
        logger.log("minBtcAmountToTrade: " + minBtcAmountToTrade);
        if (btcAmountToSpend.compareTo(minBtcAmountToTrade) < 0) {
            logger.log("Remaining BTC too low to trade, stopping split.");
            return;
        }
        Crypto cryptoToBuy = cryptosToBuy.get(cryptoToBuyIndex);
        logger.log("cryptoToBuy: " + cryptoToBuy);
        if (cryptoToBuyIndex == cryptosToBuy.size() - 1) {
            logger.log("Splitting for last cryptoToBuy");
            buyAndSell(orderToCancel, btcAmountToSpend.min(MAX_BTC_AMOUNT_FOR_LAST_CRYPTO), cryptoToBuy);
            return;
        }
        BigDecimal minBtcAmountToSplit = minBtcAmountToTrade.multiply(new BigDecimal("2"));
        if (btcAmountToSpend.compareTo(minBtcAmountToSplit.multiply(new BigDecimal("2"))) <= 0) {
            logger.log("Splitting for remaining BTC");
            buyAndSell(orderToCancel, btcAmountToSpend, cryptoToBuy);
            return;
        }
        BigDecimal quarterOfBtcAmountToSpend = btcAmountToSpend.divide(new BigDecimal("4"), 8, CEILING);
        logger.log("quarterOfBtcAmountToSpend: " + quarterOfBtcAmountToSpend);
        BigDecimal fibonacciAmount = quarterOfBtcAmountToSpend.multiply(new BigDecimal("10000"));
        logger.log("fibonacciAmount: " + fibonacciAmount);
        BigDecimal amountToSpend = Stream.of(FIBONACCI_SEQUENCE)
                                         .filter(number -> number.compareTo(fibonacciAmount) < 0)
                                         .max(Comparator.naturalOrder())
                                         .map(minBtcAmountToTrade::multiply)
                                         .orElse(minBtcAmountToSplit);
        logger.log("amountToSpend: " + amountToSpend);
        buyAndSell(orderToCancel, amountToSpend, cryptoToBuy);
        buyAndSellWithFibonacci(orderToCancel,
                                cryptosToBuy,
                                btcAmountToSpend.subtract(amountToSpend),
                                cryptoToBuyIndex + 1);
    }

    private void buyAndSell(OrderWrapper orderToCancel, BigDecimal btcAmountToSpend, Crypto cryptoToBuy) {
        // 4a. buy
        BigDecimal boughtQuantity = binanceService.buyWithBtcs(cryptoToBuy.getSymbolInfo(), btcAmountToSpend);
        logger.log("boughtQuantity: " + boughtQuantity);

        // 4b. place sell order
        BigDecimal finalPriceWithProfit = cryptoToBuy.getCurrentPrice()
                                                     .multiply(orderToCancel.getOrderPrice())
                                                     .multiply(new BigDecimal("1.01"))
                                                     .divide(orderToCancel.getCurrentPrice(), 8, CEILING);
        logger.log("finalPriceWithProfit: " + finalPriceWithProfit);
        binanceService.placeSellOrder(cryptoToBuy.getSymbolInfo(), finalPriceWithProfit, boughtQuantity);
    }
}
