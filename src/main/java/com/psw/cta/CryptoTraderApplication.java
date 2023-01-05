package com.psw.cta;

import static com.psw.cta.utils.CommonUtils.initializeTradingService;
import static java.util.Collections.emptyList;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.service.TradingService;
import java.io.IOException;

/**
 * Main application class for running from command line.
 */
public class CryptoTraderApplication {
  public static void main(String[] args) {
    TradingService tradingService = initializeTradingService(args[0], args[1], emptyList(), getLogger());
    tradingService.startTrading();
  }

  private static LambdaLogger getLogger() {
    return new LambdaLogger() {
      @Override
      public void log(String message) {
        System.out.println(message);
      }

      @Override
      public void log(byte[] bytes) {
        try {
          System.out.write(bytes);
        } catch (IOException exception) {
          exception.printStackTrace();
        }
      }
    };
  }
}
