package com.psw.cta.dto;

import static com.psw.cta.utils.CommonUtils.calculatePricePercentage;
import static com.psw.cta.utils.CommonUtils.getAveragePrices;
import static com.psw.cta.utils.CryptoUtils.calculateLastThreeHighAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePreviousThreeHighAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePriceToSell;
import static com.psw.cta.utils.CryptoUtils.calculateSumPercentageDifferences10h;
import static com.psw.cta.utils.CryptoUtils.calculateSumPercentageDifferences1h;

import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.OrderBook;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.utils.CommonUtils;
import java.math.BigDecimal;
import java.util.List;

/**
 * Object holding information about crypto.
 */
public class Crypto {

  private final SymbolInfo symbolInfo;
  private List<Candlestick> fifteenMinutesCandleStickData;
  private List<Candlestick> threeMonthsCandleStickData;
  private OrderBook orderBook;
  private BigDecimal currentPrice;
  private BigDecimal volume;
  private BigDecimal sumPercentageDifferences1h;
  private BigDecimal sumPercentageDifferences10h;
  private BigDecimal priceToSell;
  private BigDecimal priceToSellPercentage;
  private BigDecimal lastThreeHighAverage;
  private BigDecimal previousThreeHighAverage;
  private BigDecimal priceCountToSlope;
  private BigDecimal numberOfCandles;

  public Crypto(SymbolInfo symbolInfo) {
    this.symbolInfo = symbolInfo;
  }

  public BigDecimal getNumberOfCandles() {
    return numberOfCandles;
  }

  public Crypto setNumberOfCandles() {
    this.numberOfCandles = new BigDecimal(threeMonthsCandleStickData.size());
    return this;
  }

  public BigDecimal getPriceCountToSlope() {
    return priceCountToSlope;
  }

  public Crypto setPriceCountToSlope() {
    List<BigDecimal> averagePrices = getAveragePrices(threeMonthsCandleStickData);
    this.priceCountToSlope = CommonUtils.getPriceCountToSlope(averagePrices);
    return this;
  }

  public Crypto setFifteenMinutesCandleStickData(List<Candlestick> fifteenMinutesCandleStickData) {
    this.fifteenMinutesCandleStickData = fifteenMinutesCandleStickData;
    return this;
  }

  public List<Candlestick> getThreeMonthsCandleStickData() {
    return threeMonthsCandleStickData;
  }

  public Crypto setThreeMonthsCandleStickData(List<Candlestick> threeMonthsCandleStickData) {
    this.threeMonthsCandleStickData = threeMonthsCandleStickData;
    return this;
  }

  public Crypto setOrderBook(OrderBook orderBook) {
    this.orderBook = orderBook;
    return this;
  }

  public SymbolInfo getSymbolInfo() {
    return symbolInfo;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public Crypto setCurrentPrice() {
    this.currentPrice = CommonUtils.getCurrentPrice(orderBook);
    return this;
  }

  public BigDecimal getVolume() {
    return volume;
  }

  public Crypto setVolume(BigDecimal volume) {
    this.volume = volume;
    return this;
  }

  public BigDecimal getSumPercentageDifferences1h() {
    return sumPercentageDifferences1h;
  }

  public Crypto setSumPercentageDifferences1h() {
    this.sumPercentageDifferences1h = calculateSumPercentageDifferences1h(fifteenMinutesCandleStickData,
                                                                          currentPrice);
    return this;
  }

  public BigDecimal getSumPercentageDifferences10h() {
    return sumPercentageDifferences10h;
  }

  public Crypto setSumPercentageDifferences10h() {
    this.sumPercentageDifferences10h = calculateSumPercentageDifferences10h(fifteenMinutesCandleStickData,
                                                                            currentPrice);
    return this;
  }

  public BigDecimal getPriceToSell() {
    return priceToSell;
  }

  public Crypto setPriceToSell() {
    this.priceToSell = calculatePriceToSell(fifteenMinutesCandleStickData, currentPrice);
    return this;
  }

  public BigDecimal getPriceToSellPercentage() {
    return priceToSellPercentage;
  }

  public Crypto setPriceToSellPercentage() {
    this.priceToSellPercentage = calculatePricePercentage(currentPrice, priceToSell);
    return this;
  }

  public BigDecimal getLastThreeHighAverage() {
    return lastThreeHighAverage;
  }

  public Crypto setLastThreeHighAverage() {
    this.lastThreeHighAverage = calculateLastThreeHighAverage(fifteenMinutesCandleStickData);
    return this;
  }

  public BigDecimal getPreviousThreeHighAverage() {
    return previousThreeHighAverage;
  }

  public Crypto setPreviousThreeHighAverage() {
    this.previousThreeHighAverage = calculatePreviousThreeHighAverage(fifteenMinutesCandleStickData);
    return this;
  }

  @Override
  public String toString() {
    return "Crypto{"
           + "symbol="
           + symbolInfo.getSymbol()
           + ", currentPrice="
           + currentPrice
           + ", volume="
           + volume
           + ", sumPercentageDifferences1h="
           + sumPercentageDifferences1h
           + ", sumPercentageDifferences10h="
           + sumPercentageDifferences10h
           + ", priceToSell="
           + priceToSell
           + ", priceToSellPercentage="
           + priceToSellPercentage
           + ", lastThreeHighAverage="
           + lastThreeHighAverage
           + ", previousThreeHighAverage="
           + previousThreeHighAverage
           + ", priceCountToSlope="
           + (priceCountToSlope != null ? priceCountToSlope.toPlainString() : null)
           + ", numberOfCandles="
           + numberOfCandles
           + '}';
  }
}
