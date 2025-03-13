package com.psw.cta.utils;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import java.math.BigDecimal;

/**
 * Common utils to be used everywhere.
 */
public class CommonUtils {

  /**
   * Sleep program for provided amount of milliseconds.
   *
   * @param millis Milliseconds to sleep
   * @param logger Logger to log the exception
   */
  @SuppressWarnings("java:S2142")
  public static void sleep(int millis, LambdaLogger logger) {
    logger.log("Sleeping for " + millis / 1000 + " seconds");
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      logger.log("Error during sleeping");
    }
  }

  /**
   * Returns whether BTC balance is higher than minimal balance for trading.
   *
   * @param myBtcBalance Actual BTC balance
   * @return Flag whether BTC balance is higher than minimal balance
   */
  public static boolean haveBalanceForInitialTrading(BigDecimal myBtcBalance) {
    return myBtcBalance.compareTo(new BigDecimal("0.0002")) > 0;
  }

  private CommonUtils() {
  }
}