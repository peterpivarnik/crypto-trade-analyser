package com.psw.cta.processor.trade;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.binance.Candlestick;
import static com.psw.cta.dto.binance.CandlestickInterval.FIFTEEN_MINUTES;
import com.psw.cta.service.BinanceService;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MIN_PRICE_TO_SELL_PERCENTAGE;
import java.math.BigDecimal;
import static java.time.temporal.ChronoUnit.MINUTES;
import java.util.List;

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
   * @param cryptos cryptos for initial trading
   */
  public void initTrading(List<Crypto> cryptos) {
    logger.log("***** ***** Initial trading ***** *****");
    cryptos.stream()
           .map(crypto -> {
             List<Candlestick> candleStickData = binanceService.getCandleStickData(crypto.getSymbolInfo().getSymbol(),
                                                                                   FIFTEEN_MINUTES,
                                                                                   96L * 15L,
                                                                                   MINUTES);
             return crypto.calculateDataFromCandlesticks(candleStickData);
           })
           .filter(crypto -> crypto.getLastThreeHighAverage().compareTo(crypto.getPreviousThreeHighAverage()) > 0)
           .filter(crypto -> crypto.getPriceToSellPercentage().compareTo(MIN_PRICE_TO_SELL_PERCENTAGE) > 0)
           .filter(crypto -> crypto.getSumPercentageDifferences1h().compareTo(new BigDecimal("4")) < 0)
           .filter(crypto -> crypto.getSumPercentageDifferences10h().compareTo(new BigDecimal("400")) < 0)
           .forEach(this::acquireCrypto);
  }

  private void acquireCrypto(Crypto crypto) {
    // 1. get balance on account
    logger.log("Trading crypto " + crypto);
    String symbol = crypto.getSymbolInfo().getSymbol();
    BigDecimal myBtcBalance = binanceService.getMyBalance(ASSET_BTC);

    // 2. get max possible buy
    BigDecimal price = binanceService.getMinPriceFromOrderBookEntry(symbol);

    // 3. calculate quantity to buy
    BigDecimal maxBtcBalanceToBuy = myBtcBalance.min(new BigDecimal("0.0002"));

    if (shouldBuyAndSell(crypto, myBtcBalance, price)) {
      // 4. buy
      BigDecimal quantity = binanceService.buy(crypto.getSymbolInfo(), maxBtcBalanceToBuy, price);

      // 5. place sell order
      placeSellOrder(crypto, quantity);
    }
  }

  private boolean shouldBuyAndSell(Crypto crypto, BigDecimal myBtcBalance, BigDecimal price) {
    return isStillValid(crypto, price) && haveBalanceForInitialTrading(myBtcBalance);
  }

  /**
   * Returns whether BTC balance is higher than minimal balance for trading.
   *
   * @param myBtcBalance Actual BTC balance
   * @return Flag whether BTC balance is higher than minimal balance
   */
  public boolean haveBalanceForInitialTrading(BigDecimal myBtcBalance) {
    return myBtcBalance.compareTo(new BigDecimal("0.0002")) > 0;
  }

  private boolean isStillValid(Crypto crypto, BigDecimal price) {
    return price.equals(crypto.getCurrentPrice());
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
