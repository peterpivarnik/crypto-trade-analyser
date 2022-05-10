package com.psw.cta;


import static com.psw.cta.utils.CommonUtils.initializeTradingService;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.psw.cta.dto.Input;
import com.psw.cta.service.TradingService;

/**
 * Main class to be used in AWS lambda.
 */
public class ServiceHandler implements RequestHandler<Input, Object> {

  @Override
  public Object handleRequest(Input input, Context context) {
    TradingService tradingService = initializeTradingService(input.getApiKey(),
                                                             input.getApiSecret(),
                                                             context.getLogger());
    tradingService.startTrading();
    return "Lambda Function is invoked....";
  }


}
