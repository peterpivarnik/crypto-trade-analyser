package com.psw.cta.utils;

import static com.psw.cta.utils.BinanceApiConstants.API_BASE_URL;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.psw.cta.CryptoTrader;
import com.psw.cta.adapter.BinanceAdapter;
import com.psw.cta.adapter.BinanceClient;
import com.psw.cta.api.BinanceApi;
import com.psw.cta.processor.LambdaTradeProcessor;
import com.psw.cta.processor.LocalTradeProcessor;
import com.psw.cta.security.AuthenticationInterceptor;
import com.psw.cta.service.AcquireService;
import com.psw.cta.service.BnbTradeService;
import com.psw.cta.service.CancelService;
import com.psw.cta.service.CryptoService;
import com.psw.cta.service.ExtractService;
import com.psw.cta.service.ForbiddenPairsService;
import com.psw.cta.service.OrphanBalanceService;
import com.psw.cta.service.RepeatTradingService;
import com.psw.cta.service.SplitService;
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
     * Private constructor to prevent instantiation of this utility class.
     */
    private IocProvider() {
    }

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
        BinanceAdapter binanceAdapter = createBinanceService(binanceClient, logger);
        BnbTradeService bnbTradeService = createBnbTradeProcessor(binanceAdapter, logger);
        LambdaTradeProcessor tradeProcessor = createLambdaTradeProcessor(binanceAdapter, forbiddenPairs, logger);
        OrphanBalanceService orphanBalanceService = new OrphanBalanceService(binanceAdapter, logger);
        return new CryptoTrader(binanceAdapter, bnbTradeService, tradeProcessor, orphanBalanceService, logger);
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
        BinanceAdapter binanceAdapter = createBinanceService(binanceClient, logger);
        BnbTradeService bnbTradeService = createBnbTradeProcessor(binanceAdapter, logger);
        LocalTradeProcessor tradeProcessor = createLocalTradeProcessor(binanceAdapter, logger);
        OrphanBalanceService orphanBalanceService = new OrphanBalanceService(binanceAdapter, logger);
        return new CryptoTrader(binanceAdapter, bnbTradeService, tradeProcessor, orphanBalanceService, logger);
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

    private static BinanceAdapter createBinanceService(BinanceClient binanceClient, LambdaLogger logger) {
        return new BinanceAdapter(binanceClient, logger);
    }

    private static BnbTradeService createBnbTradeProcessor(BinanceAdapter binanceAdapter, LambdaLogger logger) {
        return new BnbTradeService(binanceAdapter, logger);
    }

    private static LambdaTradeProcessor createLambdaTradeProcessor(BinanceAdapter binanceAdapter,
                                                                   List<String> forbiddenPairs,
                                                                   LambdaLogger logger) {
        return new LambdaTradeProcessor(binanceAdapter,
                                        new CryptoService(binanceAdapter, logger),
                                        new SplitService(binanceAdapter, logger),
                                        new AcquireService(binanceAdapter, logger),
                                        new RepeatTradingService(binanceAdapter, logger),
                                        new ExtractService(binanceAdapter, logger),
                                        new CancelService(binanceAdapter, logger),
                                        new ForbiddenPairsService(binanceAdapter, forbiddenPairs, logger),
                                        logger);
    }

    private static LocalTradeProcessor createLocalTradeProcessor(BinanceAdapter binanceAdapter, LambdaLogger logger) {
        return new LocalTradeProcessor(binanceAdapter, logger);
    }
}
