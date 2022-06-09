package com.psw.cta.service;

import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.psw.cta.utils.CommonUtils.getMinBtcAmount;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.OrderUtils.getOrderWrapperPredicate;
import static java.math.BigDecimal.ZERO;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.dto.OrderWrapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Service to rebuy crypto.
 */
public class RepeatTradingService {

  private final BinanceApiService binanceApiService;
  private final LambdaLogger logger;

  public RepeatTradingService(BinanceApiService binanceApiService, LambdaLogger logger) {
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
    if (orderWrapperPredicate.test(orderWrapper)) {
      logger.log("Conditions to rebuy crypto not valid.");
      return;
    }
    // 1. cancel existing order
    binanceApiService.cancelRequest(orderWrapper);
    // 2. buy
    BigDecimal orderBtcAmount = orderWrapper.getOrderBtcAmount();
    BigDecimal minValueFromLotSizeFilter = getValueFromFilter(symbolInfo,
                                                              LOT_SIZE,
                                                              SymbolFilter::getMinQty);
    logger.log("minValueFromLotSizeFilter: " + minValueFromLotSizeFilter);
    BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo,
                                                                  MIN_NOTIONAL,
                                                                  SymbolFilter::getMinNotional);
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
    myTrades.forEach(trade -> logger.log(trade.toString()));
    BigDecimal sumOfTrades = getSumOfTrades(myTrades, trade -> new BigDecimal(trade.getQty()));
    while (sumOfTrades.compareTo(ZERO) == 0) {
      sleep(1000, logger);
      sumOfTrades = getSumOfTrades(myTrades, trade -> new BigDecimal(trade.getQty()));
    }
    BigDecimal spentBtc = getSumOfTrades(myTrades,
                                         trade -> new BigDecimal(trade.getQty())
                                             .multiply(new BigDecimal(trade.getPrice())));
    logger.log("spentBtc: " + spentBtc);
    BigDecimal plannedSpentBtc = orderWrapper.getCurrentPrice()
                                             .multiply(getQuantity(orderWrapper.getOrder()));
    logger.log("plannedSpentBtc: " + plannedSpentBtc);
    BigDecimal missingBtcs = spentBtc.subtract(plannedSpentBtc);
    logger.log("missingBtcs: " + missingBtcs);

    // 3. create new order
    BigDecimal quantityToSell = getQuantity(orderWrapper.getOrder());
    BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
    binanceApiService.placeSellOrder(symbolInfo,
                                     orderWrapper.getPriceToSell(),
                                     completeQuantityToSell);
  }

  private BigDecimal getSumOfTrades(List<Trade> myTrades, Function<Trade, BigDecimal> function) {
    BigDecimal sumOfTrades = myTrades.stream()
                                     .map(function)
                                     .reduce(ZERO, BigDecimal::add);
    logger.log("sumOfTrades: " + sumOfTrades);
    return sumOfTrades;
  }
}
