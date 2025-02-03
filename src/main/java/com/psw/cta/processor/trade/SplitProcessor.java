package com.psw.cta.processor.trade;

import static com.psw.cta.dto.binance.FilterType.LOT_SIZE;
import static com.psw.cta.dto.binance.FilterType.MIN_NOTIONAL;
import static com.psw.cta.dto.binance.FilterType.NOTIONAL;
import static com.psw.cta.utils.CommonUtils.getMinBtcAmount;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.Constants.FIBONACCI_SEQUENCE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.CEILING;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.SymbolFilter;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.service.BinanceService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service to split crypto to cryptos with small amount.
 */
public class SplitProcessor {

  private final BinanceService binanceService;
  private final LambdaLogger logger;

  public SplitProcessor(BinanceService binanceService, LambdaLogger logger) {
    this.binanceService = binanceService;
    this.logger = logger;
  }

  /**
   * Split big order to smaller orders.
   *
   * @param orderToCancel Order to split
   * @param cryptos       Cryptos to new orders
   * @param totalAmounts  Total amount
   * @param exchangeInfo  Current exchange trading rules and symbol information
   */
  public void split(OrderWrapper orderToCancel,
                    List<Crypto> cryptos,
                    Map<String, BigDecimal> totalAmounts,
                    ExchangeInfo exchangeInfo) {
    logger.log("***** ***** Splitting amounts ***** *****");

    //0. Check order still exist
    String symbol = orderToCancel.getOrder().getSymbol();
    try {
      Long orderId = orderToCancel.getOrder().getOrderId();
      binanceService.checkOrderStatus(symbol, orderId);
    } catch (BinanceApiException exception) {
      logger.log("Order do not exist anymore: " + symbol);
      return;
    }

    // 1. cancel existing order
    cancelRequest(orderToCancel);

    // 2. sell cancelled order
    SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(symbol);
    BigDecimal currentQuantity = getQuantity(orderToCancel.getOrder());
    sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);

    BigDecimal totalBtcAmountToSpend = currentQuantity.multiply(orderToCancel.getCurrentPrice());
    List<Crypto> cryptosToBuy = getCryptosToBuy(cryptos, totalAmounts);
    buyAndSellWithFibonacci(orderToCancel, cryptosToBuy, totalBtcAmountToSpend, 0);
  }

  /**
   * Cancel actual order.
   *
   * @param orderToCancel Order to cancel
   */
  public void cancelRequest(OrderWrapper orderToCancel) {
    logger.log("orderToCancel: " + orderToCancel);
    binanceService.cancelRequest(orderToCancel);
  }

  /**
   * Sell available balance of cancelled order.
   *
   * @param symbolInfoOfSellOrder symbol info of sell order
   * @param currentQuantity       quantity
   */
  public void sellAvailableBalance(SymbolInfo symbolInfoOfSellOrder, BigDecimal currentQuantity) {
    logger.log("currentQuantity: " + currentQuantity);
    binanceService.sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);
  }

  private List<Crypto> getCryptosToBuy(List<Crypto> cryptos, Map<String, BigDecimal> totalAmounts) {
    Set<String> existingSymbols = totalAmounts.keySet();
    return cryptos.stream()
                  .filter(crypto -> !existingSymbols.contains(crypto.getSymbolInfo().getSymbol()))
                  .map(Crypto::calculateSlopeData)
                  .filter(crypto -> crypto.getPriceCountToSlope().compareTo(ZERO) < 0)
                  .filter(crypto -> crypto.getNumberOfCandles().compareTo(new BigDecimal("30")) > 0)
                  .sorted(comparing(Crypto::getPriceCountToSlope).reversed())
                  .collect(Collectors.toList());
  }

  private void buyAndSellWithFibonacci(OrderWrapper orderToCancel,
                                       List<Crypto> cryptosToBuy,
                                       BigDecimal btcAmountToSpend,
                                       int cryptoToBuyIndex) {
    logger.log("cryptosToBuy.size(): " + cryptosToBuy.size());
    logger.log("btcAmountToSpend: " + btcAmountToSpend);
    logger.log("cryptoToBuyIndex: " + cryptoToBuyIndex);
    BigDecimal minBtcAmountToTrade = new BigDecimal("0.0001");
    logger.log("minBtcAmountToTrade: " + minBtcAmountToTrade);
    BigDecimal quarterOfBtcAmountToSpend = btcAmountToSpend.divide(new BigDecimal("4"), 8, CEILING);
    logger.log("quarterOfBtcAmountToSpend: " + quarterOfBtcAmountToSpend);
    BigDecimal fibonacciAmount = quarterOfBtcAmountToSpend.multiply(new BigDecimal("10000"));
    logger.log("fibonacciAmount: " + fibonacciAmount);
    BigDecimal fibonacciNumber = Stream.of(FIBONACCI_SEQUENCE)
                                       .filter(number -> number.compareTo(fibonacciAmount) < 0)
                                       .max(Comparator.naturalOrder())
                                       .orElse(new BigDecimal("-1"));
    logger.log("fibonacciNumber: " + fibonacciNumber);
    BigDecimal fibonacciAmountToSpend = fibonacciNumber.multiply(minBtcAmountToTrade);
    logger.log("fibonacciAmountToSpend: " + fibonacciAmountToSpend);
    Crypto cryptoToBuy = cryptosToBuy.get(cryptoToBuyIndex);
    logger.log("cryptoToBuy: " + cryptoToBuy);
    if (fibonacciNumber.compareTo(ZERO) > 0 && cryptoToBuyIndex < cryptosToBuy.size() - 1) {
      logger.log("Splitting normal");
      buyAndSell(orderToCancel, fibonacciAmountToSpend, cryptoToBuy);
      buyAndSellWithFibonacci(orderToCancel,
                              cryptosToBuy,
                              btcAmountToSpend.subtract(fibonacciAmountToSpend),
                              cryptoToBuyIndex + 1);
    } else if (btcAmountToSpend.compareTo(new BigDecimal("0.0002")) > 0) {
      logger.log("Splitting for remaining BTC");
      BigDecimal amountToSpend = minBtcAmountToTrade.multiply(new BigDecimal("2"));
      buyAndSell(orderToCancel, amountToSpend, cryptoToBuy);
      buyAndSellWithFibonacci(orderToCancel,
                              cryptosToBuy,
                              btcAmountToSpend.subtract(amountToSpend),
                              cryptoToBuyIndex + 1);
    } else if (fibonacciNumber.compareTo(ZERO) <= 0) {
      logger.log("Splitting for last fibonacci");
      buyAndSell(orderToCancel, minBtcAmountToTrade.multiply(new BigDecimal("2")), cryptoToBuy);
    } else if (cryptoToBuyIndex == cryptosToBuy.size() - 1) {
      logger.log("Splitting for last cryptoToBuy");
      buyAndSell(orderToCancel, btcAmountToSpend, cryptoToBuy);
    }
  }

  private void buyAndSell(OrderWrapper orderToCancel, BigDecimal btcAmountToSpend, Crypto cryptoToBuy) {
    // 3. buy
    SymbolInfo symbolInfo = cryptoToBuy.getSymbolInfo();
    BigDecimal cryptoToBuyCurrentPrice = cryptoToBuy.getCurrentPrice();
    logger.log("cryptoToBuyCurrentPrice: " + cryptoToBuyCurrentPrice);
    BigDecimal minValueFromLotSizeFilter = getValueFromFilter(symbolInfo, SymbolFilter::getMinQty, LOT_SIZE);
    logger.log("minValueFromLotSizeFilter: " + minValueFromLotSizeFilter);
    BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo,
                                                                  SymbolFilter::getMinNotional,
                                                                  MIN_NOTIONAL,
                                                                  NOTIONAL);
    logger.log("minValueFromMinNotionalFilter: " + minValueFromMinNotionalFilter);
    BigDecimal minAddition = minValueFromLotSizeFilter.multiply(cryptoToBuyCurrentPrice);
    logger.log("minAddition: " + minAddition);
    BigDecimal btcAmount = getMinBtcAmount(btcAmountToSpend, minAddition, minValueFromMinNotionalFilter);
    logger.log("btcAmount: " + btcAmount);
    BigDecimal boughtQuantity = binanceService.buyAndReturnQuantity(symbolInfo, btcAmount, cryptoToBuyCurrentPrice);
    logger.log("boughtQuantity: " + boughtQuantity);

    // 4. place sell order
    BigDecimal finalPriceWithProfit = cryptoToBuyCurrentPrice.multiply(orderToCancel.getOrderPrice())
                                                             .multiply(new BigDecimal("1.01"))
                                                             .divide(orderToCancel.getCurrentPrice(), 8, CEILING);
    logger.log("finalPriceWithProfit: " + finalPriceWithProfit);
    binanceService.placeSellOrder(symbolInfo, finalPriceWithProfit, boughtQuantity);
  }
}
