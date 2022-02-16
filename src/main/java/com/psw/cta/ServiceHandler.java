package com.psw.cta;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.psw.cta.dto.Input;
import com.psw.cta.service.BinanceApiService;
import com.psw.cta.service.BnbService;
import com.psw.cta.service.InitialTradingService;
import com.psw.cta.service.TradingService;

public class ServiceHandler implements RequestHandler<Input, Object> {

    @Override
    public Object handleRequest(Input input, Context context) {
        LambdaLogger logger = context.getLogger();
        BinanceApiService binanceApiService = new BinanceApiService(input.getApiKey(), input.getApiSecret(), logger);
        BnbService bnbService = new BnbService(binanceApiService, logger);
        InitialTradingService initialTradingService = new InitialTradingService(binanceApiService, logger);
        TradingService tradingService = new TradingService(initialTradingService, bnbService, binanceApiService, logger);
        tradingService.startTrading();
        return "Lambda Function is invoked....";
    }
}
