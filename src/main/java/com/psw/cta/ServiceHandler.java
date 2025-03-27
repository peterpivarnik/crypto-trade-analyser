package com.psw.cta;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.psw.cta.dto.Input;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import software.amazon.lambda.powertools.parameters.ParamManager;
import software.amazon.lambda.powertools.parameters.SecretsProvider;

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


    getSecret(context.getLogger());
    cryptoTrader.startTrading();
    return "Lambda Function is invoked....";
  }

  private List<String> splitForbiddenPairs(String forbiddenPairs) {
    return Arrays.asList(forbiddenPairs.split(","));
  }

  private void getSecret(LambdaLogger logger) {
    // Get an instance of the Secrets Provider
    SecretsProvider secretsProvider = ParamManager.getSecretsProvider();

    // Retrieve a single secret
    String value2 = secretsProvider.get("testSecret");
    logger.log("Secret: " + value2);
  }
}
