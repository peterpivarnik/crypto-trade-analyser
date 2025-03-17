package com.psw.cta.utils;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

/**
 * Common utils to be used everywhere.
 */
public class CommonUtils {

  private CommonUtils() {
  }

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
}