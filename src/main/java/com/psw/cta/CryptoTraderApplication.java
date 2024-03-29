package com.psw.cta;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.service.CryptoTradeService;

/**
 * Main application class for running from command line.
 */
public class CryptoTraderApplication {
  public static void main(String[] args) {
    CryptoTradeService cryptoTradeService = new CryptoTradeService(args[0], args[1], getLogger());
    cryptoTradeService.startTrading();
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
