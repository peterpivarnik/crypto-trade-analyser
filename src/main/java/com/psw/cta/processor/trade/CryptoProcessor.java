package com.psw.cta.processor.trade;

import static com.psw.cta.dto.binance.CandlestickInterval.DAILY;
import static com.psw.cta.dto.binance.SymbolStatus.TRADING;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.time.temporal.ChronoUnit.DAYS;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.TickerStatistics;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service providing functionality to get cryptos.
 */
public class CryptoProcessor {

  private final BinanceService binanceService;
  private final LambdaLogger logger;

  public CryptoProcessor(BinanceService binanceService, LambdaLogger logger) {
    this.binanceService = binanceService;
    this.logger = logger;
  }

  /**
   * Return list of cryptos available to buy.
   *
   * @param exchangeInfo      Exchange information
   * @param allForbiddenPairs forbidden pairs to not be used
   * @return list of cryptos available to buy
   */
  public List<Crypto> getCryptos(ExchangeInfo exchangeInfo, List<String> allForbiddenPairs) {
    sleep(1000 * 60, logger);
    logger.log("Get all cryptos");
    List<TickerStatistics> tickers = binanceService.getAll24hTickers();
    List<Crypto> cryptos = exchangeInfo.getSymbols()
                                       .parallelStream()
                                       .map(Crypto::new)
                                       .filter(crypto -> crypto.getSymbolInfo().getSymbol().endsWith(ASSET_BTC))
                                       .filter(crypto -> !allForbiddenPairs.contains(crypto.getSymbolInfo()
                                                                                           .getSymbol()))
                                       .filter(crypto -> crypto.getSymbolInfo().getStatus() == TRADING)
                                       .map(crypto -> crypto.calculateVolume(tickers))
                                       .filter(crypto -> crypto.getVolume().compareTo(new BigDecimal("100")) > 0)
                                       .map(crypto -> crypto.setThreeMonthsCandleStickData(
                                           binanceService.getCandleStickData(
                                               crypto.getSymbolInfo().getSymbol(),
                                               DAILY,
                                               90L,
                                               DAYS)))
                                       .filter(crypto -> crypto.getThreeMonthsCandleStickData().size() >= 90)
                                       .map(crypto -> crypto.setCurrentPrice(
                                           binanceService.getCurrentPrice(crypto.getSymbolInfo()
                                                                                .getSymbol())))
                                       .filter(crypto -> crypto.getCurrentPrice().compareTo(new BigDecimal("0.000001"))
                                                         > 0)
                                       .collect(Collectors.toList());
    logger.log("Cryptos count: " + cryptos.size());
    return cryptos;
  }
}
