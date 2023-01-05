package com.psw.cta;


import static com.psw.cta.utils.CommonUtils.initializeTradingService;
import static com.psw.cta.utils.CommonUtils.splitForbiddenPairs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.psw.cta.dto.Input;
import com.psw.cta.service.TradingService;
import java.util.List;
import java.util.Map;

/**
 * Main class to be used in AWS lambda.
 */
public class ServiceHandler implements RequestHandler<Input, Object> {

  @Override
  public Object handleRequest(Input input, Context context) {
    Map<String, String> variables = System.getenv();

    String forbiddenPairsVariable = variables.get("forbiddenPairs");
    List<String> forbiddenPairs = splitForbiddenPairs(forbiddenPairsVariable);
    TradingService tradingService = initializeTradingService(variables.get("apiKey"),
                                                             variables.get("apiSecret"),
                                                             forbiddenPairs,
                                                             context.getLogger());
    tradingService.startTrading();
    return "Lambda Function is invoked....";
  }


}
