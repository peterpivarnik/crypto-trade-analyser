package com.psw.cta.processor.trade;

import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;

import com.psw.cta.dto.Crypto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Provider interface for crypto trading operations.
 * Provides methods to analyze and filter cryptocurrencies based on market conditions
 * and trading criteria to identify potential buying opportunities.
 */
public interface CryptoToBuyProvider {

    /**
     * Identifies cryptocurrencies that are potential candidates for buying.
     * Filters cryptos based on several criteria:
     *
     * @param cryptos         list of all available cryptocurrencies to analyze
     * @param existingSymbols symbols to be filtered out from the list
     * @return list of filtered and sorted cryptocurrencies that meet buying criteria
     */
    default List<Crypto> getCryptosToBuy(List<Crypto> cryptos, Set<String> existingSymbols) {
        return cryptos.stream()
                      .filter(crypto -> !existingSymbols.contains(crypto.getSymbolInfo().getSymbol()))
                      .map(Crypto::calculateSlopeData)
                      .filter(crypto -> crypto.getPriceCountToSlope().compareTo(ZERO) < 0)
                      .filter(crypto -> crypto.getNumberOfCandles().compareTo(new BigDecimal("30")) > 0)
                      .sorted(comparing(Crypto::getPriceCountToSlope).reversed())
                      .toList();
    }
}
