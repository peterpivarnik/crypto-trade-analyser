package com.psw.cta.processor.trade;

import static com.psw.cta.dto.binance.CandlestickInterval.FIFTEEN_MINUTES;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.MIN_PRICE_TO_SELL_PERCENTAGE;
import static java.time.temporal.ChronoUnit.MINUTES;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.List;

/**
 * Service for acquire crypto.
 */
public class AcquireProcessor {

    private final BinanceService binanceService;
    private final LambdaLogger logger;

    /**
     * Default constructor.
     *
     * @param binanceService service for {@link BinanceApi}
     * @param logger         logger
     */
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
                   List<Candlestick> candleStickData = binanceService.getCandleStickData(
                       crypto.getSymbolInfo().getSymbol(),
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
        logger.log("Trading crypto " + crypto);
        BigDecimal myBtcBalance = binanceService.getMyBalance(ASSET_BTC);
        BigDecimal maxBtcBalanceToBuy = myBtcBalance.min(new BigDecimal("0.0002"));
        if (haveBalanceForInitialTrading(myBtcBalance)) {
            BigDecimal quantity = binanceService.buyWithBtcs(crypto.getSymbolInfo(), maxBtcBalanceToBuy);
            binanceService.placeSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), quantity);
        }
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
}
