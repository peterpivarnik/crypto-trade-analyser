package com.psw.cta.processor.trade;

import static com.psw.cta.dto.binance.OrderSide.BUY;
import static com.psw.cta.utils.CommonUtils.roundAmount;
import static com.psw.cta.utils.Constants.ASSET_BNB;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_BNB_BTC;
import static java.math.BigDecimal.ONE;
import static java.math.RoundingMode.CEILING;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.OrderBookEntry;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;

/**
 * Service for handling with BNB.
 */
public class BnbTradeProcessor {

  public static final BigDecimal MIN_BNB_BALANCE = new BigDecimal("2");
  public static final BigDecimal MAX_BNB_BALANCE_TO_BUY = ONE;

  private final LambdaLogger logger;
  private final BinanceService binanceService;

  public BnbTradeProcessor(BinanceService binanceService, LambdaLogger logger) {
    this.logger = logger;
    this.binanceService = binanceService;
  }

  /**
   * Buy BNB to be used for fees on binance exchange.
   *
   * @return Actual amount of BNB
   */
  public BigDecimal buyBnB(ExchangeInfo exchangeInfo) {
    logger.log("***** ***** Buying BNB ***** *****");
    BigDecimal myBnbBalance = binanceService.getMyBalance(ASSET_BNB);
    if (myBnbBalance.compareTo(MIN_BNB_BALANCE) < 0) {
      BigDecimal quantityToBuy = getBnbQuantityToBuy(exchangeInfo.getSymbolInfo(SYMBOL_BNB_BTC));
      binanceService.createNewOrder(SYMBOL_BNB_BTC, BUY, quantityToBuy);
      return binanceService.getMyBalance(ASSET_BNB);
    }
    return myBnbBalance;
  }

  private BigDecimal getBnbQuantityToBuy(SymbolInfo symbolInfo) {
    BigDecimal currentBnbBtcPrice = getCurrentBnbBtcPrice();
    logger.log("currentBnbBtcPrice: " + currentBnbBtcPrice);
    BigDecimal myBtcBalance = binanceService.getMyBalance(ASSET_BTC);
    BigDecimal totalPossibleBnbQuantity = myBtcBalance.divide(currentBnbBtcPrice, 8, CEILING);
    logger.log("totalPossibleBnbQuantity: " + totalPossibleBnbQuantity);
    BigDecimal min = MAX_BNB_BALANCE_TO_BUY.min(totalPossibleBnbQuantity);
    return roundAmount(symbolInfo, min);
  }

  /**
   * Returns current BNB price.
   *
   * @return BNB price
   */
  public BigDecimal getCurrentBnbBtcPrice() {
    return binanceService.getOrderBook(SYMBOL_BNB_BTC, 20)
                         .getBids()
                         .parallelStream()
                         .max(comparing(OrderBookEntry::getPrice))
                         .map(OrderBookEntry::getPrice)
                         .map(BigDecimal::new)
                         .orElseThrow(RuntimeException::new);
  }
}
