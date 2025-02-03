package com.psw.cta.processor.trade;

import static com.psw.cta.utils.CommonUtils.haveBalanceForInitialTrading;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.binance.OrderBookEntry;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;

/**
 * Service for acquire crypto.
 */
public class AcquireProcessor {

  private final BinanceService binanceService;
  private final LambdaLogger logger;

  public AcquireProcessor(BinanceService binanceService, LambdaLogger logger) {
    this.binanceService = binanceService;
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
    BigDecimal myBtcBalance = binanceService.getMyBalance(ASSET_BTC);

    // 2. get max possible buy
    OrderBookEntry orderBookEntry = binanceService.getMinOrderBookEntry(symbol);
    logger.log("OrderBookEntry: " + orderBookEntry);

    // 3. calculate quantity to buy
    BigDecimal maxBtcBalanceToBuy = myBtcBalance.min(new BigDecimal("0.0002"));
    BigDecimal price = new BigDecimal(orderBookEntry.getPrice());

    if (shouldBuyAndSell(crypto, myBtcBalance, orderBookEntry)) {
      // 4. buy
      BigDecimal quantity = binanceService.buyReturnQuantity(crypto.getSymbolInfo(), maxBtcBalanceToBuy, price);

      // 5. place sell order
      placeSellOrder(crypto, quantity);
    }
  }

  private boolean shouldBuyAndSell(Crypto crypto, BigDecimal myBtcBalance, OrderBookEntry orderBookEntry) {
    return isStillValid(crypto, orderBookEntry) && haveBalanceForInitialTrading(myBtcBalance);
  }

  private boolean isStillValid(Crypto crypto, OrderBookEntry orderBookEntry) {
    return new BigDecimal(orderBookEntry.getPrice()).equals(crypto.getCurrentPrice());
  }

  private void placeSellOrder(Crypto crypto, BigDecimal quantity) {
    try {
      binanceService.placeSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), quantity);
    } catch (Exception e) {
      logger.log("Catched exception: " + e.getClass().getName() + ", with message: " + e.getMessage());
      sleep(61000, logger);
      binanceService.placeSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), quantity);
    }
  }
}
