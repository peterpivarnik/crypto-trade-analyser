package com.psw.cta.service;

import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.binance.api.client.domain.general.FilterType.PRICE_FILTER;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.roundDown;
import static com.psw.cta.utils.Fibonacci.FIBONACCI_SEQUENCE;
import static com.psw.cta.utils.OrderUtils.getQuantityFromOrder;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.DOWN;
import static java.util.Comparator.comparing;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.psw.cta.dto.CryptoDto;
import com.psw.cta.dto.OrderDto;
import com.psw.cta.utils.CryptoUtils;
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

    public List<CryptoDto> diversify(OrderDto orderToCancel,
                                     Supplier<List<CryptoDto>> cryptoDtosSupplier,
                                     Map<String, BigDecimal> totalAmounts,
                                     ExchangeInfo exchangeInfo) {
        logger.log("***** ***** Diversifying amounts ***** *****");

        // 1. cancel existing order
        logger.log("orderToCancel: " + orderToCancel);
        binanceApiService.cancelRequest(orderToCancel);

        // 2. sell cancelled order
        BigDecimal currentQuantity = getQuantityFromOrder(orderToCancel);
        logger.log("currentQuantity: " + currentQuantity);
        SymbolInfo symbolInfoOfSellOrder = exchangeInfo.getSymbolInfo(orderToCancel.getOrder().getSymbol());
        binanceApiService.sellAvailableBalance(symbolInfoOfSellOrder, currentQuantity);

        List<CryptoDto> cryptoDtos = cryptoDtosSupplier.get();
        BigDecimal totalBtcAmountToSpend = currentQuantity.multiply(orderToCancel.getCurrentPrice());
        List<CryptoDto> cryptoToBuy = getCryptoToBuy(cryptoDtos, totalAmounts);
        buyAndSellWithFibonacci(orderToCancel, cryptoToBuy, totalBtcAmountToSpend, 1);
        return cryptoDtos;
    }

    private List<CryptoDto> getCryptoToBuy(List<CryptoDto> cryptoDtos, Map<String, BigDecimal> totalAmounts) {
        List<String> bigOrderKeys = totalAmounts.entrySet()
                                                .parallelStream()
                                                .filter(entry -> entry.getValue().compareTo(new BigDecimal("0.005")) > 0)
                                                .map(Map.Entry::getKey)
                                                .collect(Collectors.toList());

        return cryptoDtos.stream()
                         .filter(dto -> !bigOrderKeys.contains(dto.getSymbolInfo().getSymbol()))
                         .map(CryptoUtils::updateCryptoDtoWithSlopeData)
                         .filter(cryptoDto -> cryptoDto.getSlope().compareTo(BigDecimal.ZERO) < 0)
                         .sorted(comparing(CryptoDto::getPriceCountToSlope))
                         .collect(Collectors.toList());
    }

    private void buyAndSellWithFibonacci(OrderDto orderToCancel, List<CryptoDto> cryptoToBuy, BigDecimal btcAmountToSpend, int fibonacciIndex) {
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

    private void buyAndSell(OrderDto orderToCancel, CryptoDto cryptoDto, BigDecimal btcAmountToSpend) {
        // 3. buy
        logger.log("cryptoToBuy: " + cryptoDto);
        SymbolInfo symbolInfo = cryptoDto.getSymbolInfo();
        BigDecimal cryptoToBuyCurrentPrice = cryptoDto.getCurrentPrice();
        logger.log("cryptoToBuyCurrentPrice: " + cryptoToBuyCurrentPrice);
        BigDecimal boughtQuantity = binanceApiService.buy(symbolInfo, btcAmountToSpend, cryptoToBuyCurrentPrice);
        logger.log("boughtQuantity: " + boughtQuantity);

        // 4. place sell order
        BigDecimal finalPriceWithProfit = cryptoToBuyCurrentPrice.multiply(orderToCancel.getOrderPrice())
                                                                 .multiply(new BigDecimal("1.01"))
                                                                 .divide(orderToCancel.getCurrentPrice(), 8, CEILING);
        logger.log("finalPriceWithProfit: " + finalPriceWithProfit);

        BigDecimal minValueFromLotSizeFilter = getValueFromFilter(symbolInfo, LOT_SIZE, SymbolFilter::getMinQty);
        logger.log("minValueFromLotSizeFilter: " + minValueFromLotSizeFilter);
        BigDecimal minValueFromMinNotionalFilter = getValueFromFilter(symbolInfo, MIN_NOTIONAL, SymbolFilter::getMinNotional);
        logger.log("minValueFromMinNotionalFilter: " + minValueFromMinNotionalFilter);
        BigDecimal roundedPriceToSell = roundDown(symbolInfo, finalPriceWithProfit, PRICE_FILTER, SymbolFilter::getTickSize);
        logger.log("roundedPriceToSell: " + roundedPriceToSell);
        roundedPriceToSell = roundedPriceToSell.setScale(8, DOWN);
        logger.log("roundedPriceToSell with scale: " + roundedPriceToSell);
        binanceApiService.placeSellOrder(symbolInfo, finalPriceWithProfit, boughtQuantity);
    }
}
