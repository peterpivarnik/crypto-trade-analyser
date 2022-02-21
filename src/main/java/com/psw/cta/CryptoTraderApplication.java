package com.psw.cta;

import static com.psw.cta.utils.CommonUtils.initializeTradingService;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.service.TradingService;
import java.io.IOException;

public class CryptoTraderApplication {
    public static void main(String[] args) {
        TradingService tradingService = initializeTradingService(args[0], args[1], getLogger());
        tradingService.startTrading();
    }

    private static LambdaLogger getLogger() {
        return new LambdaLogger() {
            @Override public void log(String message) {
                System.out.println(message);
            }

            @Override public void log(byte[] bytes) {
                try {
                    System.out.write(bytes);
                } catch (IOException var3) {
                    var3.printStackTrace();
                }
            }
        };
    }
}