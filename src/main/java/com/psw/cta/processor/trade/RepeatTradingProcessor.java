package com.psw.cta.processor.trade;

import static com.psw.cta.dto.binance.FilterType.LOT_SIZE;
import static com.psw.cta.dto.binance.FilterType.MIN_NOTIONAL;
import static com.psw.cta.dto.binance.FilterType.NOTIONAL;
import static com.psw.cta.utils.CommonUtils.getMinBtcAmount;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.sleep;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.UP;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.OrderWrapper;
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
   * Rebuy single order.
   *
   * @param symbolInfo   Symbol information
   * @param orderWrapper Order to rebuy
   */
  public synchronized void rebuySingleOrder(SymbolInfo symbolInfo, OrderWrapper orderWrapper) {
    logger.log("***** ***** Repeat trading ***** *****");
    logger.log("OrderWrapper: " + orderWrapper);
    // 1. cancel existing order
    binanceService.cancelRequest(orderWrapper);
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
    Long orderId = binanceService.buyReturnOrderId(symbolInfo, btcAmount, orderPrice);
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
    BigDecimal earnedBtcs = getSumFromTrades(myTrades,
                                             trade -> new BigDecimal(trade.getQty())
                                                 .multiply(new BigDecimal(trade.getPrice())));
    logger.log("earnedBtcs: " + earnedBtcs);
    BigDecimal soldPrice = earnedBtcs.divide(boughtQuantity, 8, CEILING);
    logger.log("soldPrice: " + soldPrice);
    BigDecimal priceDifference = soldPrice.subtract(orderWrapper.getCurrentPrice());
    BigDecimal newPriceToSell = orderWrapper.getPriceToSell().add(priceDifference);
    if (priceDifference.compareTo(ZERO) > 0) {
      logger.log("priceDifference: " + priceDifference);
      logger.log("newPriceToSell: " + newPriceToSell);
    }

    // 3. create new order
    BigDecimal quantityToSell = getQuantity(orderWrapper.getOrder());
    BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
    binanceService.placeSellOrder(symbolInfo, newPriceToSell, completeQuantityToSell);
  }


  private BigDecimal getSumFromTrades(List<Trade> myTrades, Function<Trade, BigDecimal> function) {
    return myTrades.stream()
                   .map(function)
                   .reduce(ZERO, BigDecimal::add);
  }
}