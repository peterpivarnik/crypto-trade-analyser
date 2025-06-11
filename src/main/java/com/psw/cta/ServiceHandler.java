package com.psw.cta;

import static com.psw.cta.utils.IocProvider.createCryptoTrader;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.psw.cta.dto.Input;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Main handler class for AWS Lambda function that processes cryptocurrency trading operations.
 * This class initializes and coordinates various components required for crypto trading,
 * including Binance API client, service layers, and trade processors.
 */
public class ServiceHandler implements RequestHandler<Input, Object> {

  /**
   * Handles the AWS Lambda function request by initializing trading components and starting the trading process.
   *
   * @param input   The input parameters for the Lambda function
   * @param context The Lambda execution context
   * @return String message indicating the Lambda function has been invoked
   */
  @Override
  public Object handleRequest(Input input, Context context) {
    Map<String, String> variables = System.getenv();
    String forbiddenPairsVariable = variables.get("forbiddenPairs");
    String apiKey = variables.get("apiKey");
    String apiSecret = variables.get("apiSecret");
    LambdaLogger logger = context.getLogger();
    List<String> forbiddenPairs = splitForbiddenPairs(forbiddenPairsVariable);
    CryptoTrader cryptoTrader = createCryptoTrader(apiKey, apiSecret, logger, forbiddenPairs);
    cryptoTrader.startTrading();
    return "Lambda Function is invoked....";
  }

  private List<String> splitForbiddenPairs(String forbiddenPairs) {
    return Arrays.asList(forbiddenPairs.split(","));
  }
}
