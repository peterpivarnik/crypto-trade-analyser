package com.psw.cta.processor.trade;

import static java.math.BigDecimal.ZERO;
import static java.util.Comparator.comparing;

import com.psw.cta.dto.Crypto;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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
                      .sorted(comparing(Crypto::getPriceCountToSlope).reversed())
                      .toList();
    }

    /**
     * Returns cryptos not in existing symbols without a downtrend, sorted from the flattest trend
     * (lowest slope) to the steepest.
     *
     * @param cryptos         list of all available cryptocurrencies to analyze
     * @param existingSymbols symbols to be filtered out from the list
     * @return list of cryptocurrencies sorted from the flattest trend
     */
    default List<Crypto> getFlattestCryptosToBuy(List<Crypto> cryptos, Set<String> existingSymbols) {
        return cryptos.stream()
                      .filter(crypto -> !existingSymbols.contains(crypto.getSymbolInfo().getSymbol()))
                      .map(Crypto::calculateSlopeData)
                      .filter(crypto -> crypto.getPriceCountToSlope().compareTo(ZERO) > 0)
                      .sorted(comparing(Crypto::getPriceCountToSlope).reversed())
                      .toList();
    }

    /**
     * Returns cryptos to split an order into. Cryptos in a downtrend go first, and when there are
     * fewer than two of them, cryptos with the flattest trend are added so the amount can be spread.
     *
     * @param cryptos         list of all available cryptocurrencies to analyze
     * @param existingSymbols symbols to be filtered out from the list
     * @return list of cryptocurrencies to split an order into
     */
    default List<Crypto> getCryptosToSplitInto(List<Crypto> cryptos, Set<String> existingSymbols) {
        List<Crypto> cryptosToBuy = getCryptosToBuy(cryptos, existingSymbols);
        if (cryptosToBuy.size() >= 2) {
            return cryptosToBuy;
        }
        return Stream.concat(cryptosToBuy.stream(), getFlattestCryptosToBuy(cryptos, existingSymbols).stream())
                     .toList();
    }
}
