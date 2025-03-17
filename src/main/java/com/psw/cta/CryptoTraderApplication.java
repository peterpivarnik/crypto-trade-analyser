package com.psw.cta;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

/**
 * Main application class for running from command line.
 */
public class CryptoTraderApplication {
  public static void main(String[] args) {
    CryptoTrader cryptoTrader = new CryptoTrader(args[0], args[1], getLogger());
    cryptoTrader.startTrading();
  }

  private static LambdaLogger getLogger() {
    return new LambdaLogger() {
      @Override
      public void log(String message) {
        System.out.println(message);
      }

      @Override
      public void log(byte[] bytes) {
        System.out.println(new String(bytes));
      }
    };
  }
}
