package com.binance.api.client.constant;


import static java.util.List.of;

import java.util.List;

/**
 * Utility class.
 */
public final class Util {

  /**
   * List of fiat currencies.
   */
  public static final List<String> FIAT_CURRENCY = of("USDT", "BUSD", "PAX", "TUSD", "USDC", "NGN", "RUB", "USDS, TRY");

  public static final String BTC_TICKER = "BTC";

  private Util() {

  }

  /**
   * Check if the ticker is fiat currency.
   */
  public static boolean isFiatCurrency(String symbol) {
    for (String fiat : FIAT_CURRENCY) {
      if (symbol.equals(fiat)) {
        return true;
      }
    }
    return false;
  }
}
