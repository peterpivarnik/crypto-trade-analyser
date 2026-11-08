package com.psw.cta;

import static com.psw.cta.utils.IocProvider.createCryptoTrader;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.psw.cta.exception.CryptoTraderException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Main handler class for AWS Lambda function that processes cryptocurrency trading operations.
 * This class initializes and coordinates various components required for crypto trading,
 * including Binance API client, service layers, and trade processors.
 */
public class ServiceHandler implements RequestHandler<Object, Object> {

    /**
     * Handles the AWS Lambda function request by initializing trading components and starting the trading process.
     *
     * @param input   The Lambda event, not used
     * @param context The Lambda execution context
     * @return String message indicating the Lambda function has been invoked
     */
    @Override
    public Object handleRequest(Object input, Context context) {
        Map<String, String> variables = System.getenv();
        String apiKey = getRequiredVariable(variables, "apiKey");
        String apiSecret = getRequiredVariable(variables, "apiSecret");
        LambdaLogger logger = context.getLogger();
        List<String> forbiddenPairs = splitForbiddenPairs(variables.get("forbiddenPairs"));
        CryptoTrader cryptoTrader = createCryptoTrader(apiKey, apiSecret, logger, forbiddenPairs);
        cryptoTrader.startTrading();
        return "Lambda Function is invoked....";
    }

    static String getRequiredVariable(Map<String, String> variables, String name) {
        String value = variables.get(name);
        if (value == null || value.isBlank()) {
            throw new CryptoTraderException("Missing environment variable " + name);
        }
        return value;
    }

    static List<String> splitForbiddenPairs(String forbiddenPairs) {
        if (forbiddenPairs == null) {
            return List.of();
        }
        return Arrays.stream(forbiddenPairs.split(","))
                     .map(String::trim)
                     .map(pair -> pair.toUpperCase(Locale.ROOT))
                     .filter(pair -> !pair.isEmpty())
                     .toList();
    }
}
