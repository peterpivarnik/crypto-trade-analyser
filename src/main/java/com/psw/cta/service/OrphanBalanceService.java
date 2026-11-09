package com.psw.cta.service;

import static com.psw.cta.dto.binance.SymbolStatus.TRADING;
import static com.psw.cta.utils.Constants.ASSET_BNB;
import static com.psw.cta.utils.Constants.ASSET_BTC;
import static java.math.RoundingMode.FLOOR;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.adapter.BinanceAdapter;
import com.psw.cta.dto.binance.ExchangeInfo;
import com.psw.cta.dto.binance.SymbolInfo;
import com.psw.cta.exception.BinanceApiException;
import com.psw.cta.exception.CryptoTraderException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds free balances of cryptos that have no sell order, so they are not managed by the trader.
 */
public class OrphanBalanceService {

    private static final Set<String> IGNORED_ASSETS = Set.of(ASSET_BTC, ASSET_BNB);

    private final BinanceAdapter binanceAdapter;
    private final LambdaLogger logger;

    /**
     * Default constructor.
     *
     * @param binanceAdapter service for Binance API
     * @param logger         logger
     */
    public OrphanBalanceService(BinanceAdapter binanceAdapter, LambdaLogger logger) {
        this.binanceAdapter = binanceAdapter;
        this.logger = logger;
    }

    /**
     * Returns descriptions of free balances which are worth at least the minimal order value and can be sold for BTC.
     * BTC and BNB are ignored. Free balances without BTC pair are only logged.
     *
     * @param exchangeInfo exchange info
     * @return descriptions of orphan balances
     */
    public List<String> findOrphanBalances(ExchangeInfo exchangeInfo) {
        logger.log("Check orphan balances");
        List<String> orphanBalances = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> balance : binanceAdapter.getMyFreeBalances().entrySet()) {
            String asset = balance.getKey();
            if (IGNORED_ASSETS.contains(asset)) {
                continue;
            }
            BigDecimal quantity = balance.getValue();
            Optional<SymbolInfo> symbolInfo = findTradingSymbol(exchangeInfo, asset + ASSET_BTC);
            if (symbolInfo.isEmpty()) {
                logger.log("Free balance without BTC pair: " + asset + " qty=" + toPlainString(quantity));
                continue;
            }
            Optional<BigDecimal> btcValue = getBtcValue(symbolInfo.get(), quantity);
            if (btcValue.isEmpty()) {
                logger.log("Free balance without price: " + asset + " qty=" + toPlainString(quantity));
            } else if (btcValue.get().compareTo(binanceAdapter.getMinNotional(symbolInfo.get())) >= 0) {
                orphanBalances.add(asset
                                   + " qty=" + toPlainString(quantity)
                                   + " value=" + toPlainString(btcValue.get()) + " BTC");
            }
        }
        orphanBalances.forEach(orphanBalance -> logger.log("Orphan balance: " + orphanBalance));
        return orphanBalances;
    }

    private Optional<SymbolInfo> findTradingSymbol(ExchangeInfo exchangeInfo, String symbol) {
        return exchangeInfo.getSymbols()
                           .stream()
                           .filter(symbolInfo -> symbolInfo.getSymbol().equals(symbol))
                           .filter(symbolInfo -> symbolInfo.getStatus() == TRADING)
                           .findFirst();
    }

    private Optional<BigDecimal> getBtcValue(SymbolInfo symbolInfo, BigDecimal quantity) {
        try {
            BigDecimal price = binanceAdapter.getCurrentPrice(symbolInfo.getSymbol());
            return Optional.of(quantity.multiply(price).setScale(8, FLOOR));
        } catch (BinanceApiException | CryptoTraderException e) {
            return Optional.empty();
        }
    }

    private String toPlainString(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
