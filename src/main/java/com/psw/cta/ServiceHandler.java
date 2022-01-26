package com.psw.cta;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.impl.BinanceApiRestClientImpl;
import com.psw.cta.service.BtcBinanceService;
import com.psw.cta.service.dto.Input;

public class ServiceHandler implements RequestHandler<Input, Object> {

    @Override
    public Object handleRequest(Input input, Context context) {
        LambdaLogger logger = context.getLogger();
        BinanceApiRestClient binanceApiRestClient = new BinanceApiRestClientImpl(input.getApiKey(), input.getApiSecret());
        BtcBinanceService btcBinanceService = new BtcBinanceService(binanceApiRestClient, logger);
        btcBinanceService.invest();
        return "Lambda Function is invoked....";
    }
}
