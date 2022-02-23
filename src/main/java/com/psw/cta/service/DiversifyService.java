package com.psw.cta.service;

import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.psw.cta.utils.CommonUtils.getQuantity;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.roundPrice;
import static com.psw.cta.utils.Constants.FIBONACCI_SEQUENCE;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.DOWN;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.dto.Crypto;
import com.psw.cta.dto.OrderWrapper;
import com.psw.cta.utils.CryptoBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DiversifyService {

    private final BinanceApiService binanceApiService;
    private final LambdaLogger logger;

    public DiversifyService(BinanceApiService binanceApiService, LambdaLogger logger) {
        this.binanceApiService = binanceApiService;
        this.logger = logger;
    }

    public void diversify(OrderWrapper orderToCancel,
                          Supplier<List<Crypto>> cryptosSupplier,
                          Map<String, BigDecimal> totalAmounts,
                          ExchangeInfo exchangeInfo) {
        logger.log("***** ***** Diversifying amounts ***** *****");

        // 1. cancel existing order
        logger.log("orderToCancel: " + orderToCancel);
        binanceApiService.cancelRequest(orderToCancel);

        // 2. sell cancelled order
        BigDecimal currentQuantity = getQuantity(orderToCancel.getOrder());
        logger.log("currentQuantity: " + currentQuantity);
        SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
        binanceApiService.sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);

        List<Crypto> cryptos = cryptosSupplier.get();
        BigDecimal totalBtcAmountToSpend = currentQuantity.multiply(orderToCancel.getCurrentPrice());
        List<Crypto> cryptoToBuy = getCryptoToBuy(cryptos, totalAmounts);
        buyAndSellWithFibonacci(orderToCancel, cryptoToBuy, totalBtcAmountToSpend, 1);
    }

    private List<Crypto> getCryptoToBuy(List<Crypto> cryptos, Map<String, BigDecimal> totalAmounts) {
        List<String> bigOrderKeys = totalAmounts.entrySet()
                                                .parallelStream()
                                                .filter(entry -> entry.getValue().compareTo(new BigDecimal("0.005")) > 0)
                                                .map(Map.Entry::getKey)
                                                .collect(Collectors.toList());
        return cryptos.stream()
                      .filter(crypto -> !bigOrderKeys.contains(crypto.getSymbolInfo().getSymbol()))
                      .map(CryptoBuilder::withSlopeData)
                      .filter(crypto -> crypto.getPriceCountToSlope().compareTo(BigDecimal.ZERO) < 0)
                      .sorted(comparing(Crypto::getPriceCountToSlope))
                      .collect(Collectors.toList());
    }

    private void buyAndSellWithFibonacci(OrderWrapper orderToCancel, List<Crypto> cryptoToBuy, BigDecimal btcAmountToSpend, int fibonacciIndex) {
        BigDecimal minBtcAmountToTrade = new BigDecimal("0.0001");
        BigDecimal fibonnaciAmountToSpend = minBtcAmountToTrade.multiply(FIBONACCI_SEQUENCE[fibonacciIndex]);
        logger.log("btcAmountToSpend: " + btcAmountToSpend);
        logger.log("fibonnaciAmountToSpend: " + fibonnaciAmountToSpend);
        if (btcAmountToSpend.compareTo(fibonnaciAmountToSpend) > 0) {
            buyAndSell(orderToCancel, cryptoToBuy.get(fibonacciIndex - 1), fibonnaciAmountToSpend);
            buyAndSellWithFibonacci(orderToCancel, cryptoToBuy, btcAmountToSpend.subtract(fibonnaciAmountToSpend), fibonacciIndex + 1);
        } else {
            buyAndSell(orderToCancel, cryptoToBuy.get(fibonacciIndex - 1), btcAmountToSpend);
        }
    }

    private void buyAndSell(OrderWrapper orderToCancel, Crypto crypto, BigDecimal btcAmountToSpend) {
        // 3. buy
        logger.log("cryptoToBuy: " + crypto);
        SymbolInfo symbolInfo = crypto.getSymbolInfo();
        BigDecimal cryptoToBuyCurrentPrice = crypto.getCurrentPrice();
        logger.log("cryptoToBuyCurrentPrice: " + cryptoToBuyCurrentPrice);
        BigDecimal minValueFromLotSizeFilter = getValueFromFilter(symbolInfo, LOT_SIZE, SymbolFilter::getMinQty);
        logger.log("minValueFromLotSizeFilter: " + minValueFromLotSizeFilter);
        BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo, MIN_NOTIONAL, SymbolFilter::getMinNotional);
        logger.log("minValueFromMinNotionalFilter: " + minValueFromMinNotionalFilter);
        BigDecimal minAddition = minValueFromLotSizeFilter.multiply(cryptoToBuyCurrentPrice);
        logger.log("minAddition: " + minAddition);
        BigDecimal btcAmount = getBtcAmount(btcAmountToSpend, minAddition, minValueFromMinNotionalFilter);
        logger.log("btcAmount: " + btcAmount);
        BigDecimal boughtQuantity = binanceApiService.buy(symbolInfo, btcAmount, cryptoToBuyCurrentPrice);
        logger.log("boughtQuantity: " + boughtQuantity);

        // 4. place sell order
        BigDecimal finalPriceWithProfit = cryptoToBuyCurrentPrice.multiply(orderToCancel.getOrderPrice())
                                                                 .multiply(new BigDecimal("1.01"))
                                                                 .divide(orderToCancel.getCurrentPrice(), 8, CEILING);
        logger.log("finalPriceWithProfit: " + finalPriceWithProfit);
        BigDecimal roundedPriceToSell = roundPrice(symbolInfo, finalPriceWithProfit);
        logger.log("roundedPriceToSell: " + roundedPriceToSell);
        roundedPriceToSell = roundedPriceToSell.setScale(8, DOWN);
        logger.log("roundedPriceToSell with scale: " + roundedPriceToSell);
        binanceApiService.placeSellOrder(symbolInfo, finalPriceWithProfit, boughtQuantity);
    }

    private BigDecimal getBtcAmount(BigDecimal btcAmountToSpend, BigDecimal minValueFromLotSizeFilter, BigDecimal minValueFromMinNotionalFilter) {
        if (btcAmountToSpend.compareTo(minValueFromLotSizeFilter) < 0) {
            return getBtcAmount(btcAmountToSpend.add(minValueFromMinNotionalFilter), minValueFromLotSizeFilter, minValueFromMinNotionalFilter);
        }
        return btcAmountToSpend;
    }
}
