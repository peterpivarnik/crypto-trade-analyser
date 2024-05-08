package com.psw.cta;


import static com.psw.cta.utils.CommonUtils.splitForbiddenPairs;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.psw.cta.dto.Input;
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

    CryptoTrader cryptoTrader = new CryptoTrader(variables.get("apiKey"),
                                                 variables.get("apiSecret"),
                                                 forbiddenPairs,
                                                 context.getLogger());
    cryptoTrader.startTrading();
    return "Lambda Function is invoked....";
  }


}
