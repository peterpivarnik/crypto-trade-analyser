package com.psw.cta.utils;

import static java.math.RoundingMode.CEILING;

import java.math.BigDecimal;

/**
 * Constants used in application.
 */
public class Constants {

  public static final BigDecimal TWO = new BigDecimal("2");
  public static final BigDecimal HUNDRED_PERCENT = new BigDecimal("100");
  public static final BigDecimal TIME_CONSTANT = TWO;
  public static final String SYMBOL_BNB_BTC = "BNBBTC";
  public static final String SYMBOL_WBTC_BTC = "WBTCBTC";
  public static final String SYMBOL_TORN_BTC = "TORNBTC";
  public static final String ASSET_BNB = "BNB";
  public static final String ASSET_BTC = "BTC";
  public static final BigDecimal MIN_PRICE_TO_SELL_PERCENTAGE = new BigDecimal("0.5");
  public static final BigDecimal MIN_PROFIT_PERCENTAGE = MIN_PRICE_TO_SELL_PERCENTAGE.divide(TWO,
                                                                                             8,
                                                                                             CEILING);
  public static final BigDecimal[] FIBONACCI_SEQUENCE = new BigDecimal[] {new BigDecimal("1"),
                                                                          new BigDecimal("1"),
                                                                          new BigDecimal("2"),
                                                                          new BigDecimal("3"),
                                                                          new BigDecimal("5"),
                                                                          new BigDecimal("8"),
                                                                          new BigDecimal("13"),
                                                                          new BigDecimal("21"),
                                                                          new BigDecimal("34"),
                                                                          new BigDecimal("55"),
                                                                          new BigDecimal("89"),
                                                                          new BigDecimal("144"),
                                                                          new BigDecimal("233"),
                                                                          new BigDecimal("377"),
                                                                          new BigDecimal("610"),
                                                                          new BigDecimal("987"),
                                                                          new BigDecimal("1597"),
                                                                          new BigDecimal("2584"),
                                                                          new BigDecimal("4181"),
                                                                          new BigDecimal("6765"),
                                                                          new BigDecimal("10946"),
                                                                          new BigDecimal("17711"),
                                                                          new BigDecimal("28657"),
                                                                          new BigDecimal("46368"),
                                                                          new BigDecimal("75025"),
                                                                          new BigDecimal("121393")};

  private Constants() {
  }
}
