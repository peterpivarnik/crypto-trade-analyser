package com.psw.cta.service;

import static com.binance.api.client.domain.OrderSide.BUY;
import static com.binance.api.client.domain.general.FilterType.LOT_SIZE;
import static com.binance.api.client.domain.general.FilterType.MIN_NOTIONAL;
import static com.psw.cta.utils.CommonUtils.getValueFromFilter;
import static com.psw.cta.utils.CommonUtils.haveBalanceForInitialTrading;
import static com.psw.cta.utils.CommonUtils.round;
import static com.psw.cta.utils.CommonUtils.sleep;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.math.RoundingMode.CEILING;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.market.OrderBookEntry;
import com.psw.cta.dto.Crypto;
import java.math.BigDecimal;

public class InitialTradingService {

    private final BinanceApiService binanceApiService;
    private final LambdaLogger logger;

    public InitialTradingService(BinanceApiService binanceApiService, LambdaLogger logger) {
        this.binanceApiService = binanceApiService;
        this.logger = logger;
    }

    public synchronized void buyCrypto(Crypto crypto) {
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
            binanceApiService.createNewOrder(symbol, BUY, quantity);
            // 5. place sell order
            placeSellOrder(crypto, quantity);
        }
    }

    private BigDecimal getQuantityToBuy(Crypto crypto, BigDecimal myBtcBalance, OrderBookEntry orderBookEntry) {
        BigDecimal maxBtcBalanceToBuy = myBtcBalance.min(new BigDecimal("0.0002"));
        BigDecimal myMaxQuantity = maxBtcBalanceToBuy.divide(new BigDecimal(orderBookEntry.getPrice()), 8, CEILING);
        BigDecimal min = myMaxQuantity.min(new BigDecimal(orderBookEntry.getQty()));
        return round(crypto.getSymbolInfo(), min, LOT_SIZE, SymbolFilter::getMinQty);
    }

    private boolean shouldBuyAndSell(Crypto crypto,
                                     BigDecimal myBtcBalance,
                                     OrderBookEntry orderBookEntry,
                                     BigDecimal btcAmount,
                                     BigDecimal minNotionalFromMinNotionalFilter) {
        return isStillValid(crypto, orderBookEntry)
               && haveBalanceForInitialTrading(myBtcBalance)
               && isMoreThanMinValue(btcAmount, minNotionalFromMinNotionalFilter);
    }

    private boolean isStillValid(Crypto crypto, OrderBookEntry orderBookEntry) {
        return new BigDecimal(orderBookEntry.getPrice()).equals(crypto.getCurrentPrice());
    }

    private boolean isMoreThanMinValue(BigDecimal btcAmount, BigDecimal minNotionalFromMinNotionalFilter) {
        return btcAmount.compareTo(minNotionalFromMinNotionalFilter) >= 0;
    }

    private void placeSellOrder(Crypto crypto, BigDecimal quantity) {
        try {
            binanceApiService.placeSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), quantity);
        } catch (Exception e) {
            logger.log("Catched exception: " + e.getClass().getName() + ", with message: " + e.getMessage());
            sleep(61000, logger);
            binanceApiService.placeSellOrder(crypto.getSymbolInfo(), crypto.getPriceToSell(), quantity);
        }
    }
}
