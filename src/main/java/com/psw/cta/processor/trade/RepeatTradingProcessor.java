package com.psw.cta.processor.trade;

import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MIN_PROFIT_PERCENTAGE;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.NewOrderResponse;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Service to rebuy crypto.
 */
public class RepeatTradingProcessor {

  private final BinanceService binanceService;
  private final LambdaLogger logger;

  /**
   * Default constructor.
   *
   * @param binanceService service for {@link BinanceApi}
   * @param logger         logger
   */
  public RepeatTradingProcessor(BinanceService binanceService, LambdaLogger logger) {
    this.binanceService = binanceService;
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
    BiPredicate<BinanceService, OrderWrapper> hasBtcPredicate = (service, wrapper) -> hasEnoughBtcAmount(
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
                                                           (binanceService, wrapper) -> true,
                                                           exchangeInfo));
  }

  private void rebuySingleOrder(OrderWrapper orderWrapper,
                                BiPredicate<BinanceService, OrderWrapper> hasBtcPredicate,
                                ExchangeInfo exchangeInfo) {
    logger.log("***** ***** Repeat trading ***** *****");
    logger.log("OrderWrapper: " + orderWrapper);


    // 0. Check if still have enough balance
    if (!hasBtcPredicate.test(binanceService, orderWrapper)) {
      logger.log("Conditions to rebuy crypto not valid.");
      return;
    }
    // 1. cancel existing order
    binanceService.cancelOrder(orderWrapper);

    // 2. buy
    SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol());
    NewOrderResponse orderResponse = binanceService.buy(symbolInfo, orderWrapper);

    BigDecimal newPriceToSell = binanceService.getNewPriceToSell(symbolInfo, orderResponse, orderWrapper);
    // 3. create new order
    BigDecimal quantityToSell = orderWrapper.getQuantity();
    BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
    binanceService.placeSellOrder(symbolInfo, newPriceToSell, completeQuantityToSell);
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