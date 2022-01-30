package com.psw.cta;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.impl.BinanceApiRestClientImpl;
import com.psw.cta.service.BtcBinanceService;
import java.io.IOException;

public class CryptoTraderApplication {
    public static void main(String[] args) {
        BinanceApiRestClient binanceApiRestClient = new BinanceApiRestClientImpl(args[0], args[1]);
        BtcBinanceService btcBinanceService = new BtcBinanceService(binanceApiRestClient, getLogger());
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
