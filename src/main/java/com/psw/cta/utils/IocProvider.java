package com.psw.cta.utils;

import static com.psw.cta.utils.BinanceApiConstants.API_BASE_URL;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.CryptoTrader;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.processor.LambdaTradeProcessor;
import com.psw.cta.processor.LocalTradeProcessor;
import com.psw.cta.processor.trade.AcquireProcessor;
import com.psw.cta.processor.trade.BnbTradeProcessor;
import com.psw.cta.processor.trade.CancelProcessor;
import com.psw.cta.processor.trade.CryptoProcessor;
import com.psw.cta.processor.trade.ExtractProcessor;
import com.psw.cta.processor.trade.RepeatTradingProcessor;
import com.psw.cta.processor.trade.SplitProcessor;
import com.psw.cta.security.AuthenticationInterceptor;
import com.psw.cta.service.BinanceClient;
import com.psw.cta.service.BinanceService;
import java.util.List;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * IOCProvider is a utility class responsible for providing instances of CryptoTrader.
 * It includes methods for creating CryptoTrader instances with different configurations
 * and setups for handling market trades through Binance API integration.
 */
public class IocProvider {

    /**
     * Creates CryptoTrader instance with lambda trade processor and forbidden pairs.
     *
     * @param apiKey         Binance API key
     * @param apiSecret      Binance API secret
     * @param logger         Lambda logger instance
     * @param forbiddenPairs List of trading pairs that are forbidden for trading
     * @return CryptoTrader instance
     */
    public static CryptoTrader createCryptoTrader(String apiKey,
                                                  String apiSecret,
                                                  LambdaLogger logger,
                                                  List<String> forbiddenPairs) {
        BinanceApi binanceApi = createBinanceApi(apiKey, apiSecret);
        BinanceClient binanceClient = createBinanceClient(binanceApi, logger);
        BinanceService binanceService = createBinanceService(binanceClient, logger);
        BnbTradeProcessor bnbTradeProcessor = createBnbTradeProcessor(binanceService, logger);
        LambdaTradeProcessor tradeProcessor = createLambdaTradeProcessor(binanceService, forbiddenPairs, logger);
        return new CryptoTrader(binanceService, bnbTradeProcessor, tradeProcessor, logger);
    }

    /**
     * Creates CryptoTrader instance with local trade processor.
     *
     * @param apiKey    Binance API key
     * @param apiSecret Binance API secret
     * @param logger    Lambda logger instance
     * @return CryptoTrader instance
     */
    public static CryptoTrader createCryptoTrader(String apiKey, String apiSecret, LambdaLogger logger) {
        BinanceApi binanceApi = createBinanceApi(apiKey, apiSecret);
        BinanceClient binanceClient = createBinanceClient(binanceApi, logger);
        BinanceService binanceService = createBinanceService(binanceClient, logger);
        BnbTradeProcessor bnbTradeProcessor = createBnbTradeProcessor(binanceService, logger);
        LocalTradeProcessor tradeProcessor = createLocalTradeProcessor(binanceService, logger);
        return new CryptoTrader(binanceService, bnbTradeProcessor, tradeProcessor, logger);
    }

    private static BinanceApi createBinanceApi(String apiKey, String apiSecret) {
        return new Retrofit.Builder().baseUrl(API_BASE_URL)
                                     .client(getOkHttpClient(apiKey, apiSecret))
                                     .addConverterFactory(JacksonConverterFactory.create())
                                     .build()
                                     .create(BinanceApi.class);
    }

    private static OkHttpClient getOkHttpClient(String apiKey, String secret) {
        return new OkHttpClient.Builder().dispatcher(getDispatcher())
                                         .pingInterval(20, SECONDS)
                                         .addInterceptor(new AuthenticationInterceptor(apiKey, secret))
                                         .build();
    }

    private static Dispatcher getDispatcher() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(500);
        dispatcher.setMaxRequests(500);
        return dispatcher;
    }

    private static BinanceClient createBinanceClient(BinanceApi binanceApi, LambdaLogger logger) {
        return new BinanceClient(logger, binanceApi);
    }

    private static BinanceService createBinanceService(BinanceClient binanceClient, LambdaLogger logger) {
        return new BinanceService(binanceClient, logger);
    }

    private static BnbTradeProcessor createBnbTradeProcessor(BinanceService binanceService, LambdaLogger logger) {
        return new BnbTradeProcessor(binanceService, logger);
    }

    private static LambdaTradeProcessor createLambdaTradeProcessor(BinanceService binanceService,
                                                                   List<String> forbiddenPairs,
                                                                   LambdaLogger logger) {
        return new LambdaTradeProcessor(binanceService,
                                        new CryptoProcessor(binanceService, logger),
                                        new SplitProcessor(binanceService, logger),
                                        new AcquireProcessor(binanceService, logger),
                                        new RepeatTradingProcessor(binanceService, logger),
                                        new ExtractProcessor(binanceService, logger),
                                        new CancelProcessor(binanceService, logger),
                                        forbiddenPairs,
                                        logger);
    }

    private static LocalTradeProcessor createLocalTradeProcessor(BinanceService binanceService, LambdaLogger logger) {
        return new LocalTradeProcessor(binanceService, logger);
    }
}
