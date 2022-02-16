package com.psw.cta.service;

import static com.binance.api.client.domain.OrderSide.SELL;
import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.binance.api.client.domain.market.CandlestickInterval.FIFTEEN_MINUTES;
import static com.psw.cta.service.TradingService.ASSET_BTC;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.roundUp;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.CryptoUtils.calculateLastThreeMaxAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePreviousThreeMaxAverage;
import static com.psw.cta.utils.CryptoUtils.calculatePriceToSell;
import static com.psw.cta.utils.CryptoUtils.calculatePriceToSellPercentage;
import static com.psw.cta.utils.CryptoUtils.calculateSumDiffsPercent;
import static com.psw.cta.utils.CryptoUtils.calculateSumDiffsPercent10h;
import static java.math.RoundingMode.CEILING;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.psw.cta.dto.CryptoDto;
import java.math.BigDecimal;
import java.util.List;

public class InitialTradingService {

    private final BinanceApiService binanceApiService;
    private final LambdaLogger logger;

    public InitialTradingService(BinanceApiService binanceApiService, LambdaLogger logger) {
        this.binanceApiService = binanceApiService;
        this.logger = logger;
    }

    public boolean haveBalanceForInitialTrading(BigDecimal myBtcBalance) {
        return myBtcBalance.compareTo(new BigDecimal("0.0002")) > 0;
    }

    public CryptoDto updateCryptoDtoWithLeastMaxAverage(CryptoDto cryptoDto) {
        List<Candlestick> candleStickData = binanceApiService.getCandleStickData(cryptoDto, FIFTEEN_MINUTES, 96);
        BigDecimal lastThreeMaxAverage = calculateLastThreeMaxAverage(candleStickData);
        BigDecimal previousThreeMaxAverage = calculatePreviousThreeMaxAverage(candleStickData);
        cryptoDto.setFifteenMinutesCandleStickData(candleStickData);
        cryptoDto.setLastThreeMaxAverage(lastThreeMaxAverage);
        cryptoDto.setPreviousThreeMaxAverage(previousThreeMaxAverage);
        return cryptoDto;
    }

    public CryptoDto updateCryptoDtoWithPrices(CryptoDto cryptoDto) {
        List<Candlestick> fifteenMinutesCandleStickData = cryptoDto.getFifteenMinutesCandleStickData();
        BigDecimal currentPrice = cryptoDto.getCurrentPrice();
        BigDecimal priceToSell = calculatePriceToSell(fifteenMinutesCandleStickData, currentPrice);
        BigDecimal priceToSellPercentage = calculatePriceToSellPercentage(priceToSell, currentPrice);
        cryptoDto.setPriceToSell(priceToSell);
        cryptoDto.setPriceToSellPercentage(priceToSellPercentage);
        return cryptoDto;
    }

    public CryptoDto updateCryptoDtoWithSumDiffPerc(CryptoDto cryptoDto) {
        List<Candlestick> fifteenMinutesCandleStickData = cryptoDto.getFifteenMinutesCandleStickData();
        BigDecimal currentPrice = cryptoDto.getCurrentPrice();
        BigDecimal sumDiffsPerc = calculateSumDiffsPercent(fifteenMinutesCandleStickData, currentPrice);
        BigDecimal sumDiffsPerc10h = calculateSumDiffsPercent10h(fifteenMinutesCandleStickData, currentPrice);
        cryptoDto.setSumDiffsPerc(sumDiffsPerc);
        cryptoDto.setSumDiffsPerc10h(sumDiffsPerc10h);
        return cryptoDto;
    }

    public synchronized void buyCrypto(CryptoDto crypto) {
        // 1. get balance on account
        logger.log("Trading crypto " + crypto);
        String symbol = crypto.getSymbolInfo().getSymbol();
        BigDecimal myBtcBalance = binanceApiService.getMyBalance(ASSET_BTC);

        // 2. get max possible buy
        OrderBookEntry orderBookEntry = binanceApiService.getMinOrderBookEntry(symbol);
        logger.log("OrderBookEntry: " + orderBookEntry);

        // 3. calculate quantity to buy
        BigDecimal quantity = getQuantityToBuy(crypto, myBtcBalance, orderBookEntry);
        BigDecimal btcAmount = quantity.multiply(new BigDecimal(orderBookEntry.getPrice()));
        BigDecimal minNotionalFromMinNotionalFilter = getValueFromFilter(crypto.getSymbolInfo(), MIN_NOTIONAL, SymbolFilter::getMinNotional);
        if (shouldBuyAndSell(crypto, myBtcBalance, orderBookEntry, btcAmount, minNotionalFromMinNotionalFilter)) {
            // 4. buy
            binanceApiService.createNewOrder(symbol, SELL, quantity);
            // 5. place sell order
            placeSellOrder(crypto, quantity);
        }
    }

    private BigDecimal getQuantityToBuy(CryptoDto crypto, BigDecimal myBtcBalance, OrderBookEntry orderBookEntry) {
        BigDecimal maxBtcBalanceToBuy = myBtcBalance.min(new BigDecimal("0.0002"));
        BigDecimal myMaxQuantity = maxBtcBalanceToBuy.divide(new BigDecimal(orderBookEntry.getPrice()), 8, CEILING);
        BigDecimal min = myMaxQuantity.min(new BigDecimal(orderBookEntry.getQty()));
        return roundUp(crypto.getSymbolInfo(), min, LOT_SIZE, SymbolFilter::getMinQty);
    }

    private boolean shouldBuyAndSell(CryptoDto crypto,
                                     BigDecimal myBtcBalance,
                                     OrderBookEntry orderBookEntry,
                                     BigDecimal btcAmount,
                                     BigDecimal minNotionalFromMinNotionalFilter) {
        return isStillValid(crypto, orderBookEntry)
               && haveBalanceForInitialTrading(myBtcBalance)
               && isMoreThanMinValue(btcAmount, minNotionalFromMinNotionalFilter);
    }

    private boolean isStillValid(CryptoDto crypto, OrderBookEntry orderBookEntry) {
        return new BigDecimal(orderBookEntry.getPrice()).equals(crypto.getCurrentPrice());
    }

    private boolean isMoreThanMinValue(BigDecimal btcAmount, BigDecimal minNotionalFromMinNotionalFilter) {
        return btcAmount.compareTo(minNotionalFromMinNotionalFilter) >= 0;
    }

    private void placeSellOrder(CryptoDto crypto, BigDecimal quantity) {
        try {
            binanceApiService.placeSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), quantity);
        } catch (Exception e) {
            logger.log("Catched exception: " + e.getClass().getName() + ", with message: " + e.getMessage());
            sleep(61000, logger);
            binanceApiService.placeSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), quantity);
        }
    }
}
