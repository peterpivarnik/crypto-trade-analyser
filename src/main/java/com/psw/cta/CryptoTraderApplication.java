package com.psw.cta;

import static com.psw.cta.utils.IocProvider.createCryptoTrader;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

/**
 * Main application class for running the crypto trading system from command line.
 * This class initializes and connects all necessary components:
 * - Binance API connection
 * - Trading service
 * - Trade processors for BNB and local trading
 * The application uses AWS Lambda logger for logging operations.
 */
public class CryptoTraderApplication {

    /**
     * Main method to initialize and start the crypto trading system.
     * Creates necessary components and starts the trading process.
     *
     * @param args Command line arguments where:
     *             args[0] - Binance API key
     *             args[1] - Binance API secret
     */
    public static void main(String[] args) {
        CryptoTrader cryptoTrader = createCryptoTrader(args[0], args[1], getLogger());
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
