package com.psw.cta.service.dto;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psw.cta.exception.CryptoTradeAnalyserException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jsoup.helper.StringUtil;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

@Data
@Slf4j
public class BinanceRequest {

    public String userAgent = "Mozilla/5.0 (Windows NT 5.1; rv:19.0) Gecko/20100101 Firefox/19.0";
    public HttpsURLConnection conn = null;
    public String requestUrl;
    public String method = "GET";
    public String lastResponse = "";

    public Map<String, String> headers = new HashMap<>();

    // Internal JSON parser
    private JsonParser jsonParser = new JsonParser();
    private String requestBody = "";

    // Creating public request
    public BinanceRequest(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    /**
     * Opens HTTPS connection and save connection Handler
     *
     * @throws CryptoTradeAnalyserException in case of any error
     */
    private void connect() throws CryptoTradeAnalyserException {

        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }

                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        URL url;
        try {
            url = new URL(requestUrl);
            log.debug("{} {}", getMethod(), url);
        } catch (MalformedURLException e) {
            throw new CryptoTradeAnalyserException("Mailformed URL " + e.getMessage());
        }
        SSLContext sc;
        try {
            sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoTradeAnalyserException("SSL Error " + e.getMessage());
        } catch (KeyManagementException e) {
            throw new CryptoTradeAnalyserException("Key Management Error " + e.getMessage());
        }

        try {
            conn = (HttpsURLConnection) url.openConnection();
        } catch (IOException e) {
            throw new CryptoTradeAnalyserException("HTTPS Connection error " + e.getMessage());
        }

        try {
            conn.setRequestMethod(method);
        } catch (ProtocolException e) {
            throw new CryptoTradeAnalyserException("HTTP method error " + e.getMessage());
        }
        conn.setRequestProperty("User-Agent", getUserAgent());
        for (String header : headers.keySet()) {
            conn.setRequestProperty(header, headers.get(header));
        }
    }

    /**
     * Saving response into local string variable
     *
     * @return this request object
     * @throws CryptoTradeAnalyserException in case of any error
     */
    public BinanceRequest read() throws CryptoTradeAnalyserException {
        if (conn == null) {
            connect();
        }
        try {

            // posting payload it we do not have it yet
            if (!StringUtil.isBlank(getRequestBody())) {
                log.debug("Payload: {}", getRequestBody());
                conn.setDoInput(true);
                conn.setDoOutput(true);
                OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8);
                writer.write(getRequestBody());
                writer.close();
            }

            InputStream is;
            if (conn.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
                is = conn.getInputStream();
            } else {
                /* error from server */
                is = conn.getErrorStream();
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            lastResponse = IOUtils.toString(br);
            log.debug("Response: {}", lastResponse);

            if (conn.getResponseCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
                // Try to parse JSON
                JsonObject obj = (JsonObject) jsonParser.parse(lastResponse);
                if (obj.has("code") && obj.has("msg")) {
                    throw new CryptoTradeAnalyserException("ERROR: " +
                                                           obj.get("code").getAsString() +
                                                           ", " +
                                                           obj.get("msg").getAsString());
                }
            }
        } catch (IOException e) {
            throw new CryptoTradeAnalyserException("Error in reading response " + e.getMessage());
        }
        return this;
    }


    /**
     * Getting last response as google JsonObject
     *
     * @return response as Json Object
     */
    public JsonObject asJsonObject() {
        return (JsonObject) jsonParser.parse(getLastResponse());
    }

    /**
     * Getting last response as google GAON JsonArray
     *
     * @return response as Json Array
     */
    public JsonArray asJsonArray() {
        return (JsonArray) jsonParser.parse(getLastResponse());
    }

}
