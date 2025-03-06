package com.psw.cta.processor.trade;

import static com.psw.cta.dto.binance.FilterType.LOT_SIZE;
import static com.psw.cta.dto.binance.FilterType.MIN_NOTIONAL;
import static com.psw.cta.dto.binance.FilterType.NOTIONAL;
import static com.psw.cta.utils.CommonUtils.getMinBtcAmount;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MIN_PROFIT_PERCENTAGE;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.TEN;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.UP;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.SymbolFilter;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.dto.binance.Trade;
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
                 .forEach(orderWrapper -> {
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
           && hasEnoughBtcAmount(orderWrapper, function);
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

  private boolean hasMinProfit(OrderWrapper orderWrapper) {
    return orderWrapper.getOrderPricePercentage()
                       .subtract(orderWrapper.getPriceToSellPercentage())
                       .compareTo(MIN_PROFIT_PERCENTAGE) > 0;
  }

  private void rebuySingleOrder(SymbolInfo symbolInfo, OrderWrapper orderWrapper) {
    logger.log("***** ***** Repeat trading ***** *****");
    logger.log("OrderWrapper: " + orderWrapper);
    // 1. cancel existing order
    binanceService.cancelOrder(orderWrapper);
    // 2. buy
    BigDecimal orderBtcAmount = orderWrapper.getOrderBtcAmount();
    BigDecimal minValueFromLotSizeFilter = getValueFromFilter(symbolInfo, SymbolFilter::getMinQty, LOT_SIZE);
    logger.log("minValueFromLotSizeFilter: " + minValueFromLotSizeFilter);
    BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo,
                                                                  SymbolFilter::getMinNotional,
                                                                  MIN_NOTIONAL,
                                                                  NOTIONAL);
    logger.log("minValueFromMinNotionalFilter: " + minValueFromMinNotionalFilter);
    BigDecimal orderPrice = orderWrapper.getOrderPrice();
    BigDecimal minAddition = minValueFromLotSizeFilter.multiply(orderPrice);
    logger.log("minAddition: " + minAddition);
    BigDecimal btcAmount = getMinBtcAmount(orderBtcAmount, minAddition, minValueFromMinNotionalFilter);
    Long orderId = binanceService.buy(symbolInfo, orderWrapper.getQuantity());
    logger.log("OrderId: " + orderId);
    List<Trade> myTrades = binanceService.getMyTrades(symbolInfo.getSymbol(), orderId);
    myTrades.forEach(trade -> logger.log(trade.toString()));
    BigDecimal boughtQuantity = getSumFromTrades(myTrades, trade -> new BigDecimal(trade.getQty()));
    if (boughtQuantity.compareTo(ZERO) == 0) {
      sleep(50 * 1000, logger);
      myTrades = binanceService.getMyTrades(symbolInfo.getSymbol(), orderId);
      myTrades.forEach(trade -> logger.log(trade.toString()));
      boughtQuantity = getSumFromTrades(myTrades, trade -> new BigDecimal(trade.getQty()));
      logger.log("Bought quantity after waiting: " + boughtQuantity);
    }
    if (boughtQuantity.compareTo(ZERO) == 0) {
      boughtQuantity = btcAmount.divide(orderPrice, 8, UP);
      logger.log("Bought quantity after still not having: " + boughtQuantity);
    }
    logger.log("boughtQuantity: " + boughtQuantity);
    BigDecimal boughtBtcs = getSumFromTrades(myTrades,
                                             trade -> new BigDecimal(trade.getQty())
                                                 .multiply(new BigDecimal(trade.getPrice())));
    logger.log("boughtBtcs: " + boughtBtcs);
    BigDecimal boughtPrice = boughtBtcs.divide(boughtQuantity, 8, CEILING);
    logger.log("boughtPrice: " + boughtPrice);
    BigDecimal priceDifference = boughtPrice.subtract(orderWrapper.getCurrentPrice());
    logger.log("priceDifference: " + priceDifference);
    BigDecimal newPriceToSell = orderWrapper.getPriceToSell().add(priceDifference);
    logger.log("newPriceToSell: " + newPriceToSell);
    // 3. create new order
    BigDecimal quantityToSell = orderWrapper.getQuantity();
    BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
    binanceService.placeSellOrder(symbolInfo, newPriceToSell, completeQuantityToSell);
  }


  private BigDecimal getSumFromTrades(List<Trade> myTrades, Function<Trade, BigDecimal> function) {
    return myTrades.stream()
                   .map(function)
                   .reduce(ZERO, BigDecimal::add);
  }
}