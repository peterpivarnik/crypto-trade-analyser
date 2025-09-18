package com.psw.cta.security;

import static com.psw.cta.utils.BinanceApiConstants.API_KEY_HEADER;
import static com.psw.cta.utils.BinanceApiConstants.ENDPOINT_SECURITY_TYPE_APIKEY;
import static com.psw.cta.utils.BinanceApiConstants.ENDPOINT_SECURITY_TYPE_SIGNED;

import java.io.IOException;
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

    /**
     * Default constructor.
     *
     * @param apiKey api key
     * @param secret secret
     */
    public AuthenticationInterceptor(String apiKey, String secret) {
        this.apiKey = apiKey;
        this.secret = secret;
    }

    @NotNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = getRequest(chain.request());
        return chain.proceed(request);
    }

    @NotNull
    private Request getRequest(Request originalRequest) {
        boolean isApiKeyRequired = originalRequest.header(ENDPOINT_SECURITY_TYPE_APIKEY) != null;
        boolean isSignatureRequired = originalRequest.header(ENDPOINT_SECURITY_TYPE_SIGNED) != null;
        String query = originalRequest.url().query();
        Request.Builder newRequestBuilder =
            originalRequest.newBuilder()
                           .removeHeader(ENDPOINT_SECURITY_TYPE_APIKEY)
                           .removeHeader(ENDPOINT_SECURITY_TYPE_SIGNED)
                           .url(getUrl(isSignatureRequired, query, originalRequest.url()));
        if (isApiKeyRequired || isSignatureRequired) {
            newRequestBuilder.addHeader(API_KEY_HEADER, apiKey);
        }
        return newRequestBuilder.build();
    }

    @NotNull
    private HttpUrl getUrl(boolean isSignatureRequired, String query, HttpUrl url) {
        if (isSignatureRequired && !StringUtils.isEmpty(query)) {
            return getSignedUrl(query, url);
        }
        return url;
    }

    @NotNull
    private HttpUrl getSignedUrl(String query, HttpUrl url) {
        String signature = HmacSha256Signer.sign(query, secret);
        return url.newBuilder()
                  .addQueryParameter("signature", signature)
                  .build();
    }
}