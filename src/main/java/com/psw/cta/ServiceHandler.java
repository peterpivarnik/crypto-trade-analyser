package com.psw.cta;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.psw.cta.dto.Input;
import com.psw.cta.service.BinanceApiService;
import com.psw.cta.service.BnbService;
import com.psw.cta.service.BtcService;

public class ServiceHandler implements RequestHandler<Input, Object> {

    @Override
    public Object handleRequest(Input input, Context context) {
        LambdaLogger logger = context.getLogger();
        BinanceApiService binanceApiService = new BinanceApiService(input.getApiKey(), input.getApiSecret(), logger);
        BnbService bnbService = new BnbService(binanceApiService, logger);
        BtcService btcService = new BtcService(bnbService, binanceApiService, logger);
        btcService.startTrading();
        return "Lambda Function is invoked....";
    }
}
