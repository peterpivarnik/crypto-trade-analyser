package com.psw.cta.service;

import static com.psw.cta.utils.Constants.FIBONACCI_SEQUENCE;
import static com.psw.cta.utils.Constants.MIN_ORDER_PRICE_PERCENTAGE_TO_SPLIT;
import static java.math.RoundingMode.CEILING;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.adapter.BinanceAdapter;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.exception.CryptoTraderException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Service to split crypto to cryptos with small amount.
 */
public class SplitService implements CryptoToBuyService {

    private static final BigDecimal MIN_BTC_AMOUNT_TO_SPLIT = new BigDecimal("0.001");

    private final BinanceAdapter binanceAdapter;
    private final LambdaLogger logger;

    /**
     * Default constructor.
     *
     * @param binanceAdapter service for {@link BinanceApi}
     * @param logger         logger
     */
    public SplitService(BinanceAdapter binanceAdapter, LambdaLogger logger) {
        this.binanceAdapter = binanceAdapter;
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
                     .filter(SplitService::hasEnoughBtcAmountToSplit)
                     .min(Comparator.comparing(OrderWrapper::getOrderPricePercentage))
                     .ifPresent(orderToSplit -> split(orderToSplit, exchangeInfo, cryptos, existingSymbols));
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
        orderWrappers.stream()
                     .filter(orderWrapper -> orderWrapper.getOrderPricePercentage()
                                                         .compareTo(MIN_ORDER_PRICE_PERCENTAGE_TO_SPLIT) > 0)
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
            binanceAdapter.checkOrderStatus(symbol, orderId);
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
        binanceAdapter.cancelOrder(orderToCancel);

        // 3. sell cancelled order
        SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(symbol);
        BigDecimal currentQuantity = orderToCancel.getQuantity();
        BigDecimal totalBtcAmountToSpend = binanceAdapter.sellAvailableBalance(symbolInfoOfSellOrder,
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
            buyAndSell(orderToCancel, btcAmountToSpend, cryptoToBuy);
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
        BigDecimal boughtQuantity = binanceAdapter.buyWithBtcs(cryptoToBuy.getSymbolInfo(), btcAmountToSpend);
        logger.log("boughtQuantity: " + boughtQuantity);

        // 4b. place sell order
        BigDecimal finalPriceWithProfit = cryptoToBuy.getCurrentPrice()
                                                     .multiply(orderToCancel.getOrderPrice())
                                                     .multiply(new BigDecimal("1.01"))
                                                     .divide(orderToCancel.getCurrentPrice(), 8, CEILING);
        logger.log("finalPriceWithProfit: " + finalPriceWithProfit);
        binanceAdapter.placeSellOrder(cryptoToBuy.getSymbolInfo(), finalPriceWithProfit, boughtQuantity);
    }
}
