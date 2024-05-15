package com.psw.cta.processor;

import static com.psw.cta.dto.binance.FilterType.MIN_NOTIONAL;
import static com.psw.cta.dto.binance.FilterType.NOTIONAL;
import static com.psw.cta.dto.binance.OrderSide.BUY;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.haveBalanceForInitialTrading;
import static com.psw.cta.utils.CommonUtils.roundAmount;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.math.RoundingMode.CEILING;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.binance.OrderBookEntry;
import com.psw.cta.dto.binance.SymbolFilter;
import com.psw.cta.service.BinanceApiService;
import com.psw.cta.utils.CommonUtils;
import java.math.BigDecimal;

/**
 * Service for acquire crypto.
 */
public class AcquireProcessor {

  private final BinanceApiService binanceApiService;
  private final LambdaLogger logger;

  public AcquireProcessor(BinanceApiService binanceApiService, LambdaLogger logger) {
    this.binanceApiService = binanceApiService;
    this.logger = logger;
  }

  /**
   * Initial buy of crypto.
   *
   * @param crypto Crypto to buy.
   */
  public synchronized void acquireCrypto(Crypto crypto) {
    // 1. get balance on account
    logger.log("Trading crypto " + crypto);
    String symbol = crypto.getSymbolInfo().getSymbol();
    BigDecimal myBtcBalance = binanceApiService.getMyBalance(ASSET_BTC);

    // 2. get max possible buy
    OrderBookEntry orderBookEntry = binanceApiService.getMinOrderBookEntry(symbol);
    logger.log("OrderBookEntry: " + orderBookEntry);

    // 3. calculate quantity to buy
    BigDecimal quantity = getQuantityToBuy(crypto, myBtcBalance, orderBookEntry);
    BigDecimal btcAmount = quantity.multiply(new BigDecimal(orderBookEntry.getPrice()));
    BigDecimal minNotionalFromMinNotionalFilter = getValueFromFilter(crypto.getSymbolInfo(),
                                                                     SymbolFilter::getMinNotional,
                                                                     MIN_NOTIONAL,
                                                                     NOTIONAL);
    if (shouldBuyAndSell(crypto,
                         myBtcBalance,
                         orderBookEntry,
                         btcAmount,
                         minNotionalFromMinNotionalFilter)) {
      // 4. buy
      binanceApiService.createNewOrder(symbol, BUY, quantity);
      // 5. place sell order
      placeSellOrder(crypto, quantity);
    }
  }

  private BigDecimal getQuantityToBuy(Crypto crypto,
                                      BigDecimal myBtcBalance,
                                      OrderBookEntry orderBookEntry) {
    BigDecimal maxBtcBalanceToBuy = myBtcBalance.min(new BigDecimal("0.0002"));
    BigDecimal myMaxQuantity = maxBtcBalanceToBuy.divide(new BigDecimal(orderBookEntry.getPrice()), 8, CEILING);
    BigDecimal min = myMaxQuantity.min(new BigDecimal(orderBookEntry.getQty()));
    return roundAmount(crypto.getSymbolInfo(), min);
  }

  private boolean shouldBuyAndSell(Crypto crypto,
                                   BigDecimal myBtcBalance,
                                   OrderBookEntry orderBookEntry,
                                   BigDecimal btcAmount,
                                   BigDecimal minNotionalFromMinNotionalFilter) {
    return isStillValid(crypto, orderBookEntry)
           && haveBalanceForInitialTrading(myBtcBalance)
           && isMoreThanMinValue(btcAmount, minNotionalFromMinNotionalFilter);
  }

  private boolean isStillValid(Crypto crypto, OrderBookEntry orderBookEntry) {
    return new BigDecimal(orderBookEntry.getPrice()).equals(crypto.getCurrentPrice());
  }

  private boolean isMoreThanMinValue(BigDecimal btcAmount,
                                     BigDecimal minNotionalFromMinNotionalFilter) {
    return btcAmount.compareTo(minNotionalFromMinNotionalFilter) >= 0;
  }

  private void placeSellOrder(Crypto crypto, BigDecimal quantity) {
    try {
      binanceApiService.placeSellOrder(crypto.getSymbolInfo(),
                                       crypto.getPriceToSell(),
                                       quantity,
                                       CommonUtils::roundPrice);
    } catch (Exception e) {
      logger.log("Catched exception: " + e.getClass().getName() + ", with message: " + e.getMessage());
      sleep(61000, logger);
      binanceApiService.placeSellOrder(crypto.getSymbolInfo(),
                                       crypto.getPriceToSell(),
                                       quantity,
                                       CommonUtils::roundPrice);
    }
  }
}
