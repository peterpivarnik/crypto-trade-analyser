package com.psw.cta.security;

import com.psw.cta.utils.BinanceApiConstants;
import java.io.IOException;
import java.util.Objects;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * A request interceptor that injects the API Key Header into requests, and signs messages, whenever required.
 */
public class AuthenticationInterceptor implements Interceptor {

  private final String apiKey;
  private final String secret;

  public AuthenticationInterceptor(String apiKey, String secret) {
    this.apiKey = apiKey;
    this.secret = secret;
  }

  @NotNull
  @Override
  public Response intercept(Chain chain) throws IOException {
    Request original = chain.request();
    Request.Builder newRequestBuilder = original.newBuilder();

    boolean isApiKeyRequired = original.header(BinanceApiConstants.ENDPOINT_SECURITY_TYPE_APIKEY)
                               != null;
    boolean isSignatureRequired = original.header(BinanceApiConstants.ENDPOINT_SECURITY_TYPE_SIGNED)
                                  != null;
    newRequestBuilder.removeHeader(BinanceApiConstants.ENDPOINT_SECURITY_TYPE_APIKEY)
                     .removeHeader(BinanceApiConstants.ENDPOINT_SECURITY_TYPE_SIGNED);

    // Endpoint requires sending a valid API-KEY
    if (isApiKeyRequired || isSignatureRequired) {
      newRequestBuilder.addHeader(BinanceApiConstants.API_KEY_HEADER, apiKey);
    }

    // Endpoint requires signing the payload
    if (isSignatureRequired) {
      String payload = original.url().query();
      if (!StringUtils.isEmpty(payload)) {
        String signature = HmacSha256Signer.sign(payload, secret);
        HttpUrl signedUrl = original.url()
                                    .newBuilder()
                                    .addQueryParameter("signature", signature)
                                    .build();
        newRequestBuilder.url(signedUrl);
      }
    }

    // Build new request after adding the necessary authentication information
    Request newRequest = newRequestBuilder.build();
    return chain.proceed(newRequest);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AuthenticationInterceptor that = (AuthenticationInterceptor) o;
    return Objects.equals(apiKey, that.apiKey) && Objects.equals(secret, that.secret);
  }

  @Override
  public int hashCode() {
    return Objects.hash(apiKey, secret);
  }
}