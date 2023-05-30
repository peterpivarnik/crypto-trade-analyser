package com.psw.cta.service;

import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.binance.api.client.domain.general.FilterType.NOTIONAL;
import static com.psw.cta.utils.CommonUtils.getMinBtcAmount;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.roundPriceUp;
import static com.psw.cta.utils.Constants.FIBONACCI_SEQUENCE;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.DOWN;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.exception.BinanceApiException;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.utils.CommonUtils;
import com.psw.cta.utils.CryptoBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Service to split crypto to cryptos with small amount.
 */
public class SplitService {

  private final BinanceApiService binanceApiService;
  private final LambdaLogger logger;

  public SplitService(BinanceApiService binanceApiService, LambdaLogger logger) {
    this.binanceApiService = binanceApiService;
    this.logger = logger;
  }

  /**
   * Split big order to smaller orders.
   *
   * @param orderToCancel            Order to split
   * @param cryptosSupplier          Cryptos to new orders
   * @param totalAmounts             Total amount
   * @param exchangeInfo             Current exchange trading rules and symbol information
   * @param isForbiddenPairSplitting Flag whether is splitting of forbidden pair
   */
  public void split(OrderWrapper orderToCancel,
                    Supplier<List<Crypto>> cryptosSupplier,
                    Map<String, BigDecimal> totalAmounts,
                    ExchangeInfo exchangeInfo,
                    boolean isForbiddenPairSplitting) {
    logger.log("***** ***** Splitting amounts ***** *****");

    //0. Check order still exist
    try {
      binanceApiService.checkOrderStatus(orderToCancel.getOrder().getSymbol(),
                                         orderToCancel.getOrder().getOrderId());
    } catch (BinanceApiException exception) {
      logger.log("Order do not exist anymore: " + orderToCancel.getOrder().getSymbol());
      return;
    }

    // 1. cancel existing order
    cancelRequest(orderToCancel);

    // 2. sell cancelled order
    SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
    BigDecimal currentQuantity = getQuantity(orderToCancel.getOrder());
    sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);

    List<Crypto> cryptos = cryptosSupplier.get();
    BigDecimal totalBtcAmountToSpend = currentQuantity.multiply(orderToCancel.getCurrentPrice());
    List<Crypto> cryptoToBuy = getCryptoToBuy(cryptos, totalAmounts);
    buyAndSellWithFibonacci(orderToCancel,
                            cryptoToBuy,
                            totalBtcAmountToSpend,
                            2,
                            symbolInfoOfSellOrder,
                            isForbiddenPairSplitting);
  }

  /**
   * Cancel actual order.
   *
   * @param orderToCancel Order to cancel
   */
  public void cancelRequest(OrderWrapper orderToCancel) {
    logger.log("orderToCancel: " + orderToCancel);
    binanceApiService.cancelRequest(orderToCancel);
  }

  /**
   * Sell available balance of cancelled order.
   *
   * @param symbolInfoOfSellOrder symbol info of sell order
   * @param currentQuantity quantity
   */
  public void sellAvailableBalance(SymbolInfo symbolInfoOfSellOrder, BigDecimal currentQuantity) {
    logger.log("currentQuantity: " + currentQuantity);
    binanceApiService.sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);
  }

  private List<Crypto> getCryptoToBuy(List<Crypto> cryptos, Map<String, BigDecimal> totalAmounts) {
    Set<String> existingSymbols = totalAmounts.keySet();
    return cryptos.stream()
                  .filter(crypto -> !existingSymbols.contains(crypto.getSymbolInfo().getSymbol()))
                  .map(CryptoBuilder::withSlopeData)
                  .filter(crypto -> crypto.getPriceCountToSlope().compareTo(BigDecimal.ZERO) < 0)
                  .filter(crypto -> crypto.getNumberOfCandles().compareTo(new BigDecimal("30")) > 0)
                  .sorted(comparing(Crypto::getPriceCountToSlope).reversed())
                  .collect(Collectors.toList());
  }

  private void buyAndSellWithFibonacci(OrderWrapper orderToCancel,
                                       List<Crypto> cryptosToBuy,
                                       BigDecimal btcAmountToSpend,
                                       int fibonacciIndex,
                                       SymbolInfo symbolInfoOfSellOrder,
                                       boolean isForbiddenPairSplitting) {
    BigDecimal minBtcAmountToTrade = new BigDecimal("0.0001");
    logger.log("btcAmountToSpend: " + btcAmountToSpend);
    BigDecimal fibonacciAmountToSpend = minBtcAmountToTrade.multiply(FIBONACCI_SEQUENCE[fibonacciIndex]);
    logger.log("fibonacciAmountToSpend: " + fibonacciAmountToSpend);
    int cryptoToBuyIndex = fibonacciIndex - 1;
    logger.log("cryptoToBuyIndex: " + cryptoToBuyIndex);
    logger.log("cryptosToBuy.size(): " + cryptosToBuy.size());
    if (btcAmountToSpend.compareTo(fibonacciAmountToSpend) > 0 && cryptoToBuyIndex < cryptosToBuy.size()) {
      Crypto cryptoToBuy = cryptosToBuy.get(cryptoToBuyIndex);
      logger.log("cryptoToBuy: " + cryptoToBuy);
      buyAndSell(orderToCancel, fibonacciAmountToSpend, cryptoToBuy.getSymbolInfo(), cryptoToBuy.getCurrentPrice());
      buyAndSellWithFibonacci(orderToCancel,
                              cryptosToBuy,
                              btcAmountToSpend.subtract(fibonacciAmountToSpend),
                              fibonacciIndex + 1,
                              symbolInfoOfSellOrder,
                              isForbiddenPairSplitting);
    } else {
      logger.log("isForbiddenPairSplitting: " + isForbiddenPairSplitting);
      if (isForbiddenPairSplitting) {
        Crypto cryptoToBuy = cryptosToBuy.get(cryptoToBuyIndex);
        logger.log("cryptoToBuy: " + cryptoToBuy);
        buyAndSell(orderToCancel, btcAmountToSpend, cryptoToBuy.getSymbolInfo(), cryptoToBuy.getCurrentPrice());
      } else {
        buyAndSell(orderToCancel, btcAmountToSpend, symbolInfoOfSellOrder, orderToCancel.getCurrentPrice());
      }
    }
  }

  private void buyAndSell(OrderWrapper orderToCancel,
                          BigDecimal btcAmountToSpend,
                          SymbolInfo symbolInfo,
                          BigDecimal cryptoToBuyCurrentPrice) {
    // 3. buy
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
    Pair<Long, BigDecimal> pair = binanceApiService.buy(symbolInfo, btcAmount, cryptoToBuyCurrentPrice);
    BigDecimal boughtQuantity = pair.getRight();
    logger.log("boughtQuantity: " + boughtQuantity);

    // 4. place sell order
    BigDecimal finalPriceWithProfit = cryptoToBuyCurrentPrice.multiply(orderToCancel.getOrderPrice())
                                                             .multiply(new BigDecimal("1.01"))
                                                             .divide(orderToCancel.getCurrentPrice(), 8, CEILING);
    logger.log("finalPriceWithProfit: " + finalPriceWithProfit);
    BigDecimal roundedPriceToSell = roundPriceUp(symbolInfo, finalPriceWithProfit);
    logger.log("roundedPriceToSell: " + roundedPriceToSell);
    roundedPriceToSell = roundedPriceToSell.setScale(8, DOWN);
    logger.log("roundedPriceToSell with scale: " + roundedPriceToSell);
    binanceApiService.placeSellOrder(symbolInfo, finalPriceWithProfit, boughtQuantity, CommonUtils::roundPrice);
  }
}
