package com.psw.cta;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.psw.cta.dto.Input;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

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
    String secretName = "testSecret";
    Region region = Region.of("ap-northeast-1");

    // Create a Secrets Manager client
    SecretsManagerClient client = SecretsManagerClient.builder()
                                                      .region(region)
                                                      .build();

    GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                                                                       .secretId(secretName)
                                                                       .build();

    GetSecretValueResponse getSecretValueResponse;

    try {
      getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
    } catch (Exception e) {
      // For a list of exceptions thrown, see
      // https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_GetSecretValue.html
      throw e;
    }

    String secret = getSecretValueResponse.secretString();
    logger.log("Secret: " + secret);
  }
}
