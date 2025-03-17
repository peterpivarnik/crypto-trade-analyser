package com.psw.cta.dto;

import static com.psw.cta.utils.Constants.HUNDRED_PERCENT;
import static com.psw.cta.utils.LeastSquares.getSlope;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.UP;
import static java.util.Comparator.naturalOrder;

import com.psw.cta.dto.binance.Candlestick;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.dto.binance.TickerStatistics;
import com.psw.cta.exception.CryptoTraderException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Object holding information about crypto.
 */
public class Crypto {

  private final SymbolInfo symbolInfo;
  private List<Candlestick> threeMonthsCandleStickData;
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

  /**
   * Default constructor.
   *
   * @param symbolInfo symbol information
   */
  public Crypto(SymbolInfo symbolInfo) {
    this.symbolInfo = symbolInfo;
  }

  /**
   * Calculate volume.
   *
   * @param tickers ticker statistics
   * @return volume
   */
  public Crypto calculateVolume(List<TickerStatistics> tickers) {
    this.volume = tickers.parallelStream()
                         .filter(ticker -> ticker.getSymbol().equals(symbolInfo.getSymbol()))
                         .map(TickerStatistics::getVolume)
                         .map(BigDecimal::new)
                         .findAny()
                         .orElseThrow(() -> new CryptoTraderException("Ticker with symbol: "
                                                                      + symbolInfo.getSymbol()
                                                                      + " not found."));
    return this;
  }

  /**
   * Calculate all available data from candle sticks.
   *
   * @param fifteenMinutesCandleStickData candle stick data
   * @return {@link Crypto}
   */
  public Crypto calculateDataFromCandlesticks(List<Candlestick> fifteenMinutesCandleStickData) {
    this.lastThreeHighAverage = calculateHighAverage(fifteenMinutesCandleStickData, 3);
    this.previousThreeHighAverage = calculateHighAverage(fifteenMinutesCandleStickData, 6);
    this.priceToSell = calculatePriceToSell(fifteenMinutesCandleStickData, currentPrice);
    this.priceToSellPercentage = calculatePricePercentage(currentPrice, priceToSell);
    this.sumPercentageDifferences1h = calculateSumPercentageDifferences1h(fifteenMinutesCandleStickData, currentPrice);
    this.sumPercentageDifferences10h = calculateSumPercentageDifferences10h(fifteenMinutesCandleStickData,
                                                                            currentPrice);
    return this;
  }

  private BigDecimal calculateHighAverage(List<Candlestick> fifteenMinutesCandleStickData, int notSkipped) {
    int skipSize = fifteenMinutesCandleStickData.size() - notSkipped;
    return fifteenMinutesCandleStickData.stream()
                                        .skip(skipSize)
                                        .limit(3)
                                        .map(Candlestick::getHigh)
                                        .map(BigDecimal::new)
                                        .reduce(ZERO, BigDecimal::add)
                                        .divide(new BigDecimal("3"), 8, UP);
  }

  private BigDecimal calculatePriceToSell(List<Candlestick> fifteenMinutesCandleStickData,
                                          BigDecimal currentPrice) {
    int size = fifteenMinutesCandleStickData.size();
    if (size - 4 < 0) {
      return currentPrice;
    }
    return fifteenMinutesCandleStickData.stream()
                                        .skip(size - 4)
                                        .map(Candlestick::getHigh)
                                        .map(BigDecimal::new)
                                        .max(naturalOrder())
                                        .orElse(currentPrice)
                                        .subtract(currentPrice)
                                        .divide(new BigDecimal("2"), 8, UP)
                                        .add(currentPrice);
  }

  private BigDecimal calculatePricePercentage(BigDecimal lowestPrice,
                                              BigDecimal highestPrice) {
    BigDecimal percentage = lowestPrice.multiply(HUNDRED_PERCENT).divide(highestPrice, 8, UP);
    return HUNDRED_PERCENT.subtract(percentage);
  }

  private BigDecimal calculateSumPercentageDifferences1h(List<Candlestick> fifteenMinutesCandleStickData,
                                                         BigDecimal currentPrice) {
    return calculateSumPercentageDifferences(4, fifteenMinutesCandleStickData, currentPrice);
  }

  private BigDecimal calculateSumPercentageDifferences10h(List<Candlestick> fifteenMinutesCandleStickData,
                                                          BigDecimal currentPrice) {
    return calculateSumPercentageDifferences(40, fifteenMinutesCandleStickData, currentPrice);
  }

  private BigDecimal calculateSumPercentageDifferences(int numberOfDataToKeep,
                                                       List<Candlestick> fifteenMinutesCandleStickData,
                                                       BigDecimal currentPrice) {
    int size = fifteenMinutesCandleStickData.size();
    if (size - numberOfDataToKeep < 0) {
      return ZERO;
    }
    return sumPercentageDifferences(size - numberOfDataToKeep, fifteenMinutesCandleStickData, currentPrice);
  }

  private BigDecimal sumPercentageDifferences(int size,
                                              List<Candlestick> fifteenMinutesCandleStickData,
                                              BigDecimal currentPrice) {
    return fifteenMinutesCandleStickData.stream()
                                        .skip(size)
                                        .map(data -> getPercentageDifference(data, currentPrice))
                                        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal getPercentageDifference(Candlestick data, BigDecimal currentPrice) {
    BigDecimal averagePrice = getAveragePrice(data);
    BigDecimal relativeValue = averagePrice.multiply(new BigDecimal("100"))
                                           .divide(currentPrice, 8, UP);
    return relativeValue.subtract(new BigDecimal("100")).abs();
  }

  /**
   * Calculate slope data.
   *
   * @return {@link Crypto}
   */
  public Crypto calculateSlopeData() {
    List<BigDecimal> averagePrices = getAveragePrices(threeMonthsCandleStickData);
    this.priceCountToSlope = calculatePriceCountToSlope(averagePrices);
    this.numberOfCandles = new BigDecimal(threeMonthsCandleStickData.size());
    return this;
  }

  private List<BigDecimal> getAveragePrices(List<Candlestick> threeMonthsCandleStickData) {
    return threeMonthsCandleStickData.parallelStream()
                                     .map(this::getAveragePrice)
                                     .collect(Collectors.toList());
  }

  private BigDecimal calculatePriceCountToSlope(List<BigDecimal> averagePrices) {
    BigDecimal priceCount = new BigDecimal(averagePrices.size(), new MathContext(8));
    double leastSquaresSlope = getSlope(averagePrices);
    if (Double.isNaN(leastSquaresSlope)) {
      leastSquaresSlope = 0.00000001;
    }
    BigDecimal slope = new BigDecimal(String.valueOf(leastSquaresSlope), new MathContext(8));
    if (ZERO.compareTo(slope) == 0) {
      slope = new BigDecimal("0.00000001");
    }
    return priceCount.divide(slope, 8, CEILING);
  }

  private BigDecimal getAveragePrice(Candlestick candle) {
    BigDecimal open = new BigDecimal(candle.getOpen());
    BigDecimal close = new BigDecimal(candle.getClose());
    BigDecimal high = new BigDecimal(candle.getHigh());
    BigDecimal low = new BigDecimal(candle.getLow());
    return open.add(close).add(high).add(low).divide(new BigDecimal("4"), 8, CEILING);
  }

  public BigDecimal getNumberOfCandles() {
    return numberOfCandles;
  }

  public BigDecimal getPriceCountToSlope() {
    return priceCountToSlope;
  }

  public List<Candlestick> getThreeMonthsCandleStickData() {
    return threeMonthsCandleStickData;
  }

  /**
   * Set candle stick data for last three months.
   *
   * @param threeMonthsCandleStickData candle stick data
   * @return {@link Crypto}
   */
  public Crypto setThreeMonthsCandleStickData(List<Candlestick> threeMonthsCandleStickData) {
    this.threeMonthsCandleStickData = threeMonthsCandleStickData;
    return this;
  }

  public SymbolInfo getSymbolInfo() {
    return symbolInfo;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  /**
   * Set current price.
   *
   * @param currentPrice current order price
   * @return {@link Crypto}
   */
  public Crypto setCurrentPrice(BigDecimal currentPrice) {
    this.currentPrice = currentPrice;
    return this;
  }

  public BigDecimal getVolume() {
    return volume;
  }

  public BigDecimal getPriceToSell() {
    return priceToSell;
  }

  public BigDecimal getPriceToSellPercentage() {
    return priceToSellPercentage;
  }

  public BigDecimal getSumPercentageDifferences1h() {
    return sumPercentageDifferences1h;
  }

  public BigDecimal getSumPercentageDifferences10h() {
    return sumPercentageDifferences10h;
  }

  public BigDecimal getLastThreeHighAverage() {
    return lastThreeHighAverage;
  }

  public BigDecimal getPreviousThreeHighAverage() {
    return previousThreeHighAverage;
  }

  @Override
  public String toString() {
    return "Crypto{"
           + "symbol=" + symbolInfo.getSymbol() + ", "
           + "currentPrice=" + currentPrice + ", "
           + "volume=" + volume + ", "
           + "sumPercentageDifferences1h=" + sumPercentageDifferences1h + ", "
           + "sumPercentageDifferences10h=" + sumPercentageDifferences10h + ", "
           + "priceToSell=" + priceToSell + ", "
           + "priceToSellPercentage=" + priceToSellPercentage + ", "
           + "lastThreeHighAverage=" + lastThreeHighAverage + ", "
           + "previousThreeHighAverage=" + previousThreeHighAverage + ", "
           + "priceCountToSlope=" + (priceCountToSlope != null ? priceCountToSlope.toPlainString() : null) + ", "
           + "numberOfCandles=" + numberOfCandles + '}';
  }
}
