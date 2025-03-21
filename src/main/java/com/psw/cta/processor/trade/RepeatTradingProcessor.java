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
import java.util.function.Function;

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
    orderWrappers.stream()
                 .filter(orderWrapper -> shouldBeRebought(orderWrapper, service -> myBtcBalance))
                 .forEachOrdered(orderWrapper -> {
                   if (!shouldBeRebought(orderWrapper, service -> service.getMyBalance(ASSET_BTC))) {
                     logger.log("Conditions to rebuy crypto not valid.");
                     return;
                   }
                   SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol());
                   rebuySingleOrder(symbolInfo, orderWrapper);
                 });
  }

  private boolean shouldBeRebought(OrderWrapper orderWrapper, Function<BinanceService, BigDecimal> function) {
    return hasMinProfit(orderWrapper)
           && isRemainingTimeGreaterZero(orderWrapper)
           && hasEnoughBtcAmount(function, orderWrapper);
  }

  private void rebuySingleOrder(SymbolInfo symbolInfo, OrderWrapper orderWrapper) {
    logger.log("***** ***** Repeat trading ***** *****");
    logger.log("OrderWrapper: " + orderWrapper);

    // 1. cancel existing order
    binanceService.cancelOrder(orderWrapper);

    // 2. buy
    NewOrderResponse orderResponse = binanceService.buy(symbolInfo, orderWrapper);

    BigDecimal newPriceToSell = binanceService.getNewPriceToSell(symbolInfo, orderResponse, orderWrapper);
    // 3. create new order
    BigDecimal quantityToSell = orderWrapper.getQuantity();
    BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
    binanceService.placeSellOrder(symbolInfo, newPriceToSell, completeQuantityToSell);
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

  private boolean hasEnoughBtcAmount(Function<BinanceService, BigDecimal> function, OrderWrapper orderWrapper) {
    BigDecimal myBtcBalance = function.apply(binanceService);
    return myBtcBalance.compareTo(orderWrapper.getNeededBtcAmount()) > 0;
  }

  /**
   * Rebuy all ordes.
   *
   * @param orderWrappers list of orders to rebuy
   * @param exchangeInfo  exchange info
   */
  public void rebuyAllOrders(List<OrderWrapper> orderWrappers, ExchangeInfo exchangeInfo) {
    logger.log("***** ***** Rebuy all orders ***** *****");
    orderWrappers.stream()
                 .filter(this::hasMinProfit)
                 .forEach(orderWrapper -> {
                   SymbolInfo symbolInfo = exchangeInfo.getSymbolInfo(orderWrapper.getOrder().getSymbol());
                   rebuySingleOrder(symbolInfo, orderWrapper);
                 });
  }
}