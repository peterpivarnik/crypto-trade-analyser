package com.psw.cta.processor;

import static com.psw.cta.dto.binance.OrderSide.BUY;
import static com.psw.cta.utils.Constants.ASSET_BNB;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_BNB_BTC;
import static java.math.BigDecimal.ONE;
import static java.math.RoundingMode.CEILING;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.binance.OrderBookEntry;
import com.psw.cta.service.BinanceApiService;
import java.math.BigDecimal;

/**
 * Service for handling with BNB.
 */
public class BnbTradeProcessor {

  public static final BigDecimal MIN_BNB_BALANCE = new BigDecimal("2");
  public static final BigDecimal MAX_BNB_BALANCE_TO_BUY = ONE;

  private final LambdaLogger logger;
  private final BinanceApiService binanceApiService;

  public BnbTradeProcessor(BinanceApiService binanceApiService, LambdaLogger logger) {
    this.logger = logger;
    this.binanceApiService = binanceApiService;
  }

  /**
   * Buy BNB to be used for fees on binance exchange.
   *
   * @return Actual amount of BNB
   */
  public BigDecimal buyBnB() {
    logger.log("***** ***** Buying BNB ***** *****");
    BigDecimal myBnbBalance = binanceApiService.getMyBalance(ASSET_BNB);
    if (myBnbBalance.compareTo(MIN_BNB_BALANCE) < 0) {
      BigDecimal quantityToBuy = getBnbQuantityToBuy();
      binanceApiService.createNewOrder(SYMBOL_BNB_BTC, BUY, quantityToBuy);
      return binanceApiService.getMyBalance(ASSET_BNB);
    }
    return myBnbBalance;
  }

  private BigDecimal getBnbQuantityToBuy() {
    BigDecimal currentBnbBtcPrice = getCurrentBnbBtcPrice();
    logger.log("currentBnbBtcPrice: " + currentBnbBtcPrice);
    BigDecimal myBtcBalance = binanceApiService.getMyBalance(ASSET_BTC);
    BigDecimal totalPossibleBnbQuantity = myBtcBalance.divide(currentBnbBtcPrice, 8, CEILING);
    logger.log("totalPossibleBnbQuantity: " + totalPossibleBnbQuantity);
    return MAX_BNB_BALANCE_TO_BUY.min(totalPossibleBnbQuantity);
  }

  /**
   * Returns current BNB price.
   *
   * @return BNB price
   */
  public BigDecimal getCurrentBnbBtcPrice() {
    return binanceApiService.getOrderBook(SYMBOL_BNB_BTC)
                            .getBids()
                            .parallelStream()
                            .max(comparing(OrderBookEntry::getPrice))
                            .map(OrderBookEntry::getPrice)
                            .map(BigDecimal::new)
                            .orElseThrow(RuntimeException::new);
  }
}
