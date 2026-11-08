package com.psw.cta.processor;

import static com.psw.cta.utils.Constants.ASSET_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_BNB_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_PAXG_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_WBTC_BTC;
import static com.psw.cta.utils.Constants.SYMBOL_XAUT_BTC;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.dto.binance.DelistResponse;
import com.psw.cta.service.BinanceService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Provides trading pairs which must not be traded: default pairs, configured pairs and pairs scheduled for delisting.
 */
public class ForbiddenPairsProvider {

    private final BinanceService binanceService;
    private final List<String> configuredForbiddenPairs;
    private final LambdaLogger logger;

    /**
     * Default constructor.
     *
     * @param binanceService           service for Binance API
     * @param configuredForbiddenPairs trading pairs configured as forbidden
     * @param logger                   logger
     */
    public ForbiddenPairsProvider(BinanceService binanceService,
                                  List<String> configuredForbiddenPairs,
                                  LambdaLogger logger) {
        this.binanceService = binanceService;
        this.configuredForbiddenPairs = configuredForbiddenPairs;
        this.logger = logger;
    }

    /**
     * Returns all forbidden trading pairs without duplicates.
     *
     * @return default, configured and delisted BTC trading pairs
     */
    public List<String> getAllForbiddenPairs() {
        Set<String> delistedPairs = binanceService.getDelistSchedule()
                                                  .stream()
                                                  .map(DelistResponse::getSymbols)
                                                  .filter(Objects::nonNull)
                                                  .flatMap(Collection::stream)
                                                  .filter(symbol -> symbol.endsWith(ASSET_BTC))
                                                  .collect(Collectors.toCollection(TreeSet::new));
        logger.log("delistedPairs:");
        delistedPairs.forEach(logger::log);
        Set<String> forbidden = new LinkedHashSet<>();
        forbidden.addAll(List.of(SYMBOL_BNB_BTC, SYMBOL_WBTC_BTC, SYMBOL_PAXG_BTC, SYMBOL_XAUT_BTC));
        forbidden.addAll(configuredForbiddenPairs);
        forbidden.addAll(delistedPairs);
        logger.log("ForbiddenPairs:");
        forbidden.forEach(logger::log);
        return new ArrayList<>(forbidden);
    }
}
