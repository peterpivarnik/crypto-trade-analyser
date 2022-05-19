package com.psw.cta.service;

import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.psw.cta.utils.CommonUtils.getMinBtcAmount;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.roundPriceUp;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.math.RoundingMode.CEILING;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.dto.OrderWrapper;
import java.math.BigDecimal;
import java.util.List;
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
    if (mybtcBalance.compareTo(orderWrapper.getOrderBtcAmount()) < 0) {
      logger.log("BTC balance too low, skip rebuy of crypto.");
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
    BigDecimal averageBuyPrice = myTrades.stream()
                                         .map(Trade::getPrice)
                                         .map(BigDecimal::new)
                                         .reduce(BigDecimal.ZERO, BigDecimal::add)
                                         .divide(new BigDecimal(myTrades.size()), 8, CEILING);
    BigDecimal roundedAverageBuyPrice = roundPriceUp(symbolInfo, averageBuyPrice);
    BigDecimal buyPriceAverageDifference = roundedAverageBuyPrice.subtract(orderPrice);
    logger.log("buyPriceAverageDifference: " + buyPriceAverageDifference);

    // 3. create new order
    BigDecimal quantityToSell = getQuantity(orderWrapper.getOrder());
    BigDecimal completeQuantityToSell = quantityToSell.multiply(new BigDecimal("2"));
    binanceApiService.placeSellOrder(symbolInfo,
                                     orderWrapper.getPriceToSell(),
                                     completeQuantityToSell);
  }
}
