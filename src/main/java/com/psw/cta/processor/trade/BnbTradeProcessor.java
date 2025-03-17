package com.psw.cta.processor.trade;

import static com.psw.cta.utils.Constants.ASSET_BNB;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.math.BigDecimal.ONE;
import static java.math.RoundingMode.CEILING;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.api.BinanceApi;
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

  /**
   * Default constructor.
   *
   * @param binanceService service for {@link BinanceApi}
   * @param logger logger
   */
  public BnbTradeProcessor(BinanceService binanceService, LambdaLogger logger) {
    this.logger = logger;
    this.binanceService = binanceService;
  }

  /**
   * Buy BNB to be used for fees on binance exchange.
   *
   * @return Actual amount of BNB
   */
  public BigDecimal buyBnB(BigDecimal currentBnbBtcPrice, SymbolInfo symbolInfo) {
    BigDecimal myBnbBalance = binanceService.getMyBalance(ASSET_BNB);
    if (myBnbBalance.compareTo(MIN_BNB_BALANCE) < 0) {
      logger.log("***** ***** Buying BNB ***** *****");
      BigDecimal myBtcBalance = binanceService.getMyBalance(ASSET_BTC);
      BigDecimal totalPossibleBnbQuantity = myBtcBalance.divide(currentBnbBtcPrice, 8, CEILING);
      logger.log("totalPossibleBnbQuantity: " + totalPossibleBnbQuantity);
      BigDecimal min = MAX_BNB_BALANCE_TO_BUY.min(totalPossibleBnbQuantity);
      binanceService.createBuyMarketOrder(symbolInfo, min);
      return binanceService.getMyBalance(ASSET_BNB);
    }
    return myBnbBalance;
  }
}
