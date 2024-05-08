package com.psw.cta.processor;

import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.binance.api.client.domain.general.FilterType.NOTIONAL;
import static com.psw.cta.utils.CommonUtils.getMinBtcAmount;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.OrderUtils.getOrderWrapperPredicate;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.service.BinanceApiService;
import com.psw.cta.utils.CommonUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Service to rebuy crypto.
 */
public class RepeatTradingProcessor {

  private final BinanceApiService binanceApiService;
  private final LambdaLogger logger;

  public RepeatTradingProcessor(BinanceApiService binanceApiService, LambdaLogger logger) {
    this.binanceApiService = binanceApiService;
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
    BigDecimal mybtcBalance = binanceApiService.getMyBalance(ASSET_BTC);
    Predicate<OrderWrapper> orderWrapperPredicate = getOrderWrapperPredicate(mybtcBalance);
    if (!orderWrapperPredicate.test(orderWrapper)) {
      logger.log("Conditions to rebuy crypto not valid.");
      return;
    }
    // 1. cancel existing order
    binanceApiService.cancelRequest(orderWrapper);
    // 2. buy
    BigDecimal orderBtcAmount = orderWrapper.getOrderBtcAmount();
    BigDecimal minValueFromLotSizeFilter = getValueFromFilter(symbolInfo,
                                                              SymbolFilter::getMinQty,
                                                              LOT_SIZE);
    logger.log("minValueFromLotSizeFilter: " + minValueFromLotSizeFilter);
    BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo,
                                                                  SymbolFilter::getMinNotional,
                                                                  MIN_NOTIONAL,
                                                                  NOTIONAL);
    logger.log("minValueFromMinNotionalFilter: " + minValueFromMinNotionalFilter);
    BigDecimal orderPrice = orderWrapper.getOrderPrice();
    BigDecimal minAddition = minValueFromLotSizeFilter.multiply(orderPrice);
    logger.log("minAddition: " + minAddition);
    BigDecimal btcAmount = getMinBtcAmount(orderBtcAmount,
                                           minAddition,
                                           minValueFromMinNotionalFilter);
    Pair<Long, BigDecimal> pair = binanceApiService.buy(symbolInfo, btcAmount, orderPrice);
    Long orderId = pair.getLeft();
    List<Trade> myTrades = binanceApiService.getMyTrades(symbolInfo.getSymbol(), String.valueOf(orderId));
    BigDecimal newPriceToSell = getNewPriceToSell(orderWrapper, myTrades);

    // 3. create new order
    BigDecimal quantityToSell = getQuantity(orderWrapper.getOrder());
    BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
    binanceApiService.placeSellOrder(symbolInfo,
                                     newPriceToSell,
                                     completeQuantityToSell,
                                     CommonUtils::roundPriceUp);
  }

  private BigDecimal getNewPriceToSell(OrderWrapper orderWrapper, List<Trade> myTrades) {
    myTrades.forEach(trade -> logger.log(trade.toString()));
    BigDecimal soldQuantity = getSumFromTrades(myTrades, trade -> new BigDecimal(trade.getQty()));
    while (soldQuantity.compareTo(ZERO) == 0) {
      sleep(1000, logger);
      soldQuantity = getSumFromTrades(myTrades, trade -> new BigDecimal(trade.getQty()));
    }
    logger.log("soldQuantity: " + soldQuantity);
    BigDecimal earnedBtcs = getSumFromTrades(myTrades,
                                             trade -> new BigDecimal(trade.getQty())
                                                 .multiply(new BigDecimal(trade.getPrice())));
    logger.log("earnedBtcs: " + earnedBtcs);
    BigDecimal soldPrice = earnedBtcs.divide(soldQuantity, 8, CEILING);
    logger.log("soldPrice: " + soldPrice);
    BigDecimal priceDifference = soldPrice.subtract(orderWrapper.getCurrentPrice());
    BigDecimal newPriceToSell = orderWrapper.getPriceToSell().add(priceDifference);
    if (priceDifference.compareTo(ZERO) > 0) {
      logger.log("priceDifference: " + priceDifference);
      logger.log("newPriceToSell: " + newPriceToSell);
    }
    return newPriceToSell;
  }

  private BigDecimal getSumFromTrades(List<Trade> myTrades, Function<Trade, BigDecimal> function) {
    return myTrades.stream()
                   .map(function)
                   .reduce(ZERO, BigDecimal::add);
  }
}