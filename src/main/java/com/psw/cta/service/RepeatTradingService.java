package com.psw.cta.service;

import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MIN_PROFIT_PERCENTAGE;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.NewOrderResponse;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.adapter.BinanceAdapter;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Service to rebuy crypto.
 */
public class RepeatTradingService {

    private final BinanceAdapter binanceAdapter;
    private final LambdaLogger logger;

    /**
     * Default constructor.
     *
     * @param binanceAdapter service for {@link BinanceApi}
     * @param logger         logger
     */
    public RepeatTradingService(BinanceAdapter binanceAdapter, LambdaLogger logger) {
        this.binanceAdapter = binanceAdapter;
        this.logger = logger;
    }

    /**
     * Rebuy orders which have valid conditions to rebuy.
     *
     * @param orderWrappers list of possible orders to rebuy
     * @param myBtcBalance  current balance in BTC
     * @param exchangeInfo  exchange info
     */
    public void rebuyOrders(List<OrderWrapper> orderWrappers, BigDecimal myBtcBalance, ExchangeInfo exchangeInfo) {
        logger.log("***** ***** Rebuy orders ***** *****");
        BiPredicate<BinanceAdapter, OrderWrapper> hasBtcPredicate = (service, wrapper) -> hasEnoughBtcAmount(
            service.getMyBalance(ASSET_BTC),
            wrapper);
        orderWrappers.stream()
                     .filter(orderWrapper -> shouldBeRebought(orderWrapper, myBtcBalance))
                     .forEachOrdered(orderWrapper -> rebuySingleOrder(orderWrapper, hasBtcPredicate, exchangeInfo));
    }

    /**
     * Rebuy all orders.
     *
     * @param orderWrappers list of orders to rebuy
     * @param exchangeInfo  exchange info
     */
    public void rebuyAllOrders(List<OrderWrapper> orderWrappers, ExchangeInfo exchangeInfo) {
        logger.log("***** ***** Rebuy all orders ***** *****");
        orderWrappers.stream()
                     .filter(this::hasMinProfit)
                     .forEach(orderWrapper -> rebuySingleOrder(orderWrapper,
                                                               (binanceServiceParam, wrapper) -> true,
                                                               exchangeInfo));
    }

    /**
     * Rebuy a single order by placing a new buy order and cancelling the existing order,
     * then creating a new sell order with doubled quantity. The existing order is cancelled
     * only after the buy succeeds, so a failed buy leaves it untouched.
     *
     * @param orderWrapper    wrapper holding the order to rebuy
     * @param hasBtcPredicate predicate to check if there is enough BTC balance
     * @param exchangeInfo    exchange info containing symbol details
     */
    public void rebuySingleOrder(OrderWrapper orderWrapper,
                                 BiPredicate<BinanceAdapter, OrderWrapper> hasBtcPredicate,
                                 ExchangeInfo exchangeInfo) {
        logger.log("***** ***** Repeat trading ***** *****");
        logger.log("OrderWrapper: " + orderWrapper);


        // 0. Check if still have enough balance
        if (!hasBtcPredicate.test(binanceAdapter, orderWrapper)) {
            logger.log("Conditions to rebuy crypto not valid.");
            return;
        }
        // 1. buy
        SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol());
        NewOrderResponse orderResponse = binanceAdapter.buyWithQuantityRoundedUp(symbolInfo,
                                                                                 orderWrapper.getQuantity());

        // 2. cancel existing order
        binanceAdapter.cancelOrder(orderWrapper);

        // 3. create new order
        BigDecimal newPriceToSell = binanceAdapter.getNewPriceToSell(symbolInfo, orderResponse, orderWrapper);
        BigDecimal completeQuantityToSell = orderWrapper.getQuantity()
                                                        .multiply(new BigDecimal("2"));
        binanceAdapter.placeSellOrder(symbolInfo, newPriceToSell, completeQuantityToSell);
    }

    /**
     * Check whether order can be rebought.
     *
     * @param orderWrapper wrapper holding order info
     * @param myBtcBalance actual balance in btc
     * @return whether order can be rebought
     */
    public boolean shouldBeRebought(OrderWrapper orderWrapper, BigDecimal myBtcBalance) {
        return hasMinProfit(orderWrapper)
               && isRemainingTimeLessZero(orderWrapper)
               && hasEnoughBtcAmount(myBtcBalance, orderWrapper);
    }

    private boolean hasMinProfit(OrderWrapper orderWrapper) {
        return orderWrapper.getOrderPricePercentage()
                           .subtract(orderWrapper.getPriceToSellPercentage())
                           .compareTo(MIN_PROFIT_PERCENTAGE) > 0;
    }

    private boolean isRemainingTimeLessZero(OrderWrapper orderWrapper) {
        return orderWrapper.getRemainWaitingTime()
                           .compareTo(BigDecimal.ZERO) < 0;
    }

    private boolean hasEnoughBtcAmount(BigDecimal myBtcBalance, OrderWrapper orderWrapper) {
        return myBtcBalance.compareTo(orderWrapper.getNeededBtcAmount()) > 0;
    }
}