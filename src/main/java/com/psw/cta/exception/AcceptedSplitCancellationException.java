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
     * @param acceptedLoss the BTC amount lost due to the cancelled split
     */
    public AcceptedSplitCancellationException(BigDecimal acceptedLoss) {
        super("Split was cancelled due to not enough available cryptos, accepted loss is "
              + acceptedLoss.abs().stripTrailingZeros().toPlainString()
              + " BTC");
    }
}