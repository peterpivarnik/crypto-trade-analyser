package com.psw.cta;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.psw.cta.dto.Input;
import com.psw.cta.service.BinanceApiService;
import com.psw.cta.service.BtcBinanceService;

public class ServiceHandler implements RequestHandler<Input, Object> {

    @Override
    public Object handleRequest(Input input, Context context) {
        LambdaLogger logger = context.getLogger();
        BinanceApiService binanceApiService = new BinanceApiService(input.getApiKey(), input.getApiSecret(), logger);
        BtcBinanceService btcBinanceService = new BtcBinanceService(binanceApiService, logger);
        btcBinanceService.invest();
        return "Lambda Function is invoked....";
    }
}
