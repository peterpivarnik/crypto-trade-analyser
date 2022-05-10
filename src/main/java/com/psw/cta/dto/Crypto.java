package com.psw.cta.dto;

import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.OrderBook;
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

  public void setNumberOfCandles(BigDecimal numberOfCandles) {
    this.numberOfCandles = numberOfCandles;
  }

  public BigDecimal getPriceCountToSlope() {
    return priceCountToSlope;
  }

  public void setPriceCountToSlope(BigDecimal priceCountToSlope) {
    this.priceCountToSlope = priceCountToSlope;
  }

  public List<Candlestick> getFifteenMinutesCandleStickData() {
    return fifteenMinutesCandleStickData;
  }

  public void setFifteenMinutesCandleStickData(List<Candlestick> fifteenMinutesCandleStickData) {
    this.fifteenMinutesCandleStickData = fifteenMinutesCandleStickData;
  }

  public List<Candlestick> getThreeMonthsCandleStickData() {
    return threeMonthsCandleStickData;
  }

  public Crypto setThreeMonthsCandleStickData(List<Candlestick> threeMonthsCandleStickData) {
    this.threeMonthsCandleStickData = threeMonthsCandleStickData;
    return this;
  }

  public OrderBook getOrderBook() {
    return orderBook;
  }

  public void setOrderBook(OrderBook orderBook) {
    this.orderBook = orderBook;
  }

  public SymbolInfo getSymbolInfo() {
    return symbolInfo;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public void setCurrentPrice(BigDecimal currentPrice) {
    this.currentPrice = currentPrice;
  }

  public BigDecimal getVolume() {
    return volume;
  }

  public void setVolume(BigDecimal volume) {
    this.volume = volume;
  }

  public BigDecimal getSumPercentageDifferences1h() {
    return sumPercentageDifferences1h;
  }

  public void setSumPercentageDifferences1h(BigDecimal sumPercentageDifferences1h) {
    this.sumPercentageDifferences1h = sumPercentageDifferences1h;
  }

  public BigDecimal getSumPercentageDifferences10h() {
    return sumPercentageDifferences10h;
  }

  public void setSumPercentageDifferences10h(BigDecimal sumPercentageDifferences10h) {
    this.sumPercentageDifferences10h = sumPercentageDifferences10h;
  }

  public BigDecimal getPriceToSell() {
    return priceToSell;
  }

  public void setPriceToSell(BigDecimal priceToSell) {
    this.priceToSell = priceToSell;
  }

  public BigDecimal getPriceToSellPercentage() {
    return priceToSellPercentage;
  }

  public void setPriceToSellPercentage(BigDecimal priceToSellPercentage) {
    this.priceToSellPercentage = priceToSellPercentage;
  }

  public BigDecimal getLastThreeHighAverage() {
    return lastThreeHighAverage;
  }

  public void setLastThreeHighAverage(BigDecimal lastThreeHighAverage) {
    this.lastThreeHighAverage = lastThreeHighAverage;
  }

  public BigDecimal getPreviousThreeHighAverage() {
    return previousThreeHighAverage;
  }

  public void setPreviousThreeHighAverage(BigDecimal previousThreeHighAverage) {
    this.previousThreeHighAverage = previousThreeHighAverage;
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
