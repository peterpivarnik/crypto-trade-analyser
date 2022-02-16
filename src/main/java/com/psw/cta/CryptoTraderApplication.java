package com.psw.cta;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.service.BinanceApiService;
import com.psw.cta.service.BtcBinanceService;
import java.io.IOException;

public class CryptoTraderApplication {
    public static void main(String[] args) {
        LambdaLogger logger = getLogger();
        BinanceApiService binanceApiService = new BinanceApiService(args[0], args[1], logger);
        BtcBinanceService btcBinanceService = new BtcBinanceService(binanceApiService, logger);
        btcBinanceService.invest();
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
