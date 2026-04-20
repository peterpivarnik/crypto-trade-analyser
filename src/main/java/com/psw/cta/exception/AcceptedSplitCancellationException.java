package com.psw.cta.exception;

import java.math.BigDecimal;

/**
 * Thrown when a split operation was cancelled due to an insufficient number of
 * cryptos available to buy, resulting in an accepted (expected) BTC loss.
 */
public class AcceptedSplitCancellationException extends BinanceApiException {

    /**
     * Instantiates a new accepted split cancellation exception.
     *
     * @param symbol       the symbol of the pair for which the split was attempted
     * @param acceptedLoss the BTC amount lost due to the cancelled split
     */
    public AcceptedSplitCancellationException(String symbol, BigDecimal acceptedLoss) {
        super("Split for pair " + symbol
              + " was cancelled due to not enough available cryptos, accepted loss is "
              + acceptedLoss.abs().stripTrailingZeros().toPlainString()
              + " BTC");
    }
}