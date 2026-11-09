package com.psw.cta.service;

import static java.util.Comparator.comparing;

import com.psw.cta.dto.Crypto;
import java.util.List;
import java.util.Set;

/**
 * Provider interface for crypto trading operations.
 * Provides methods to analyze and filter cryptocurrencies based on market conditions
 * and trading criteria to identify potential buying opportunities.
 */
public interface CryptoToBuyService {

    /**
     * Returns cryptos to split an order into, sorted from the crypto keeping its value best to the crypto
     * changing its value most, so the biggest part goes to the most stable crypto. Only cryptos in a downtrend
     * are used, and when there are fewer than two of them, all available cryptos are used.
     *
     * @param cryptos         list of all available cryptocurrencies to analyze
     * @param existingSymbols symbols to be filtered out from the list
     * @return list of cryptocurrencies to split an order into
     */
    default List<Crypto> getCryptosToSplitInto(List<Crypto> cryptos, Set<String> existingSymbols) {
        List<Crypto> availableCryptos = cryptos.stream()
                                               .filter(crypto -> !existingSymbols.contains(crypto.getSymbolInfo()
                                                                                                 .getSymbol()))
                                               .map(Crypto::calculateSlopeData)
                                               .toList();
        List<Crypto> cryptosInDowntrend = availableCryptos.stream()
                                                          .filter(crypto -> crypto.getSlopePercentagePerDay()
                                                                                  .signum() < 0)
                                                          .toList();
        List<Crypto> cryptosToSplitInto = cryptosInDowntrend.size() >= 2 ? cryptosInDowntrend : availableCryptos;
        return cryptosToSplitInto.stream()
                                 .sorted(comparing(crypto -> crypto.getSlopePercentagePerDay().abs()))
                                 .toList();
    }
}
