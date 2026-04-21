package com.psw.cta.tool;

import static com.psw.cta.utils.BinanceApiConstants.API_BASE_URL;
import static com.psw.cta.utils.BinanceApiConstants.DEFAULT_RECEIVING_WINDOW;
import static com.psw.cta.utils.BinanceApiConstants.ENDPOINT_SECURITY_TYPE_SIGNED_HEADER;
import static java.lang.System.currentTimeMillis;

import com.fasterxml.jackson.databind.JsonNode;
import com.psw.cta.security.AuthenticationInterceptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;

/**
 * Standalone utility that diffs per-symbol Binance permission sets against the account's
 * permissions and prints every *BTC symbol the account is not allowed to trade
 * (the root cause of runtime error -2010 "This symbol is not permitted for this account").
 *
 * <p>Binance assigns accounts to trading groups (e.g. {@code TRD_GRP_004}). Each symbol in
 * {@code GET /api/v3/exchangeInfo} exposes {@code permissionSets} — a list of alternative
 * permission combinations, any of which is sufficient to trade. If none of them is fully
 * contained in the account's permissions from {@code GET /api/v3/account}, the symbol is
 * forbidden. This is stricter than {@code POST /api/v3/order/test}, which skips the
 * permission check entirely.
 *
 * <p>Reads credentials from env vars {@code BINANCE_API_KEY} and {@code BINANCE_API_SECRET}.
 */
public class ForbiddenSymbolsFinder {

    private static final String QUOTE_ASSET = "BTC";

    /**
     * Main method.
     *
     * @param args Command line arguments
     * @throws Exception if anything goes wrong
     */
    public static void main(String[] args) throws Exception {
        String apiKey = requireEnv("BINANCE_API_KEY");
        String apiSecret = requireEnv("BINANCE_API_SECRET");

        RawApi api = buildApi(apiKey, apiSecret);

        System.out.println("Fetching account permissions...");
        JsonNode account = execute(api.getAccount(DEFAULT_RECEIVING_WINDOW, currentTimeMillis()));
        Set<String> accountPermissions = toStringSet(account.get("permissions"));
        System.out.println("Account permissions: " + accountPermissions);

        System.out.println("Fetching exchange info (SPOT)...");
        JsonNode exchangeInfo = execute(api.getExchangeInfo("SPOT"));
        JsonNode symbols = exchangeInfo.get("symbols");

        List<String> forbidden = new ArrayList<>();
        int checked = 0;
        for (JsonNode symbol : symbols) {
            String name = symbol.get("symbol").asText();
            if (!name.endsWith(QUOTE_ASSET) || name.equals(QUOTE_ASSET)) {
                continue;
            }
            String status = symbol.hasNonNull("status") ? symbol.get("status").asText() : "";
            if (!"TRADING".equals(status)) {
                continue;
            }
            checked++;
            if (!isTradable(symbol, accountPermissions)) {
                forbidden.add(name);
                System.out.printf("  FORBIDDEN %s  permissionSets=%s%n",
                                  name, describePermissionSets(symbol));
            }
        }

        System.out.println();
        System.out.println("=========================================");
        System.out.printf("Checked %d *BTC TRADING symbols%n", checked);
        System.out.println("Forbidden: " + forbidden.size());
        System.out.println("forbiddenPairs env value:");
        System.out.println(String.join(",", forbidden));
    }

    /**
     * A symbol is tradable when at least one of its {@code permissionSets} inner arrays
     * shares at least one permission with the account's permissions. Falls back to the flat
     * {@code permissions} field for older exchange info shapes.
     */
    private static boolean isTradable(JsonNode symbol, Set<String> accountPermissions) {
        JsonNode sets = symbol.get("permissionSets");
        if (sets != null && sets.isArray() && !sets.isEmpty()) {
            for (JsonNode set : sets) {
                Set<String> allowed = toStringSet(set);
                if (allowed.isEmpty() || hasAnyMatch(allowed, accountPermissions)) {
                    return true;
                }
            }
            return false;
        }
        JsonNode flat = symbol.get("permissions");
        if (flat != null && flat.isArray() && !flat.isEmpty()) {
            return hasAnyMatch(toStringSet(flat), accountPermissions);
        }
        return true;
    }

    private static boolean hasAnyMatch(Set<String> allowed, Set<String> accountPermissions) {
        for (String perm : allowed) {
            if (accountPermissions.contains(perm)) {
                return true;
            }
        }
        return false;
    }

    private static String describePermissionSets(JsonNode symbol) {
        JsonNode sets = symbol.get("permissionSets");
        if (sets != null && sets.isArray() && !sets.isEmpty()) {
            return sets.toString();
        }
        JsonNode flat = symbol.get("permissions");
        return flat == null ? "[]" : flat.toString();
    }

    private static Set<String> toStringSet(JsonNode node) {
        Set<String> result = new LinkedHashSet<>();
        if (node == null || !node.isArray()) {
            return result;
        }
        Iterator<JsonNode> it = node.elements();
        while (it.hasNext()) {
            result.add(it.next().asText());
        }
        return result;
    }

    private static <T> T execute(Call<T> call) throws IOException {
        Response<T> response = call.execute();
        if (!response.isSuccessful()) {
            String body;
            try (ResponseBody errorBody = response.errorBody()) {
                body = errorBody == null ? "" : errorBody.string();
            }
            throw new IllegalStateException("Call failed: " + response.code() + " " + body);
        }
        return response.body();
    }

    private static RawApi buildApi(String apiKey, String apiSecret) {
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new AuthenticationInterceptor(apiKey, apiSecret))
            .build();
        return new Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(client)
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(RawApi.class);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing env variable: " + name);
        }
        return value;
    }

    /**
     * Minimal Retrofit interface returning raw JSON so we can read fields absent from DTOs.
     */
    public interface RawApi {

        /**
         * GET /api/v3/exchangeInfo.
         *
         * @param permissions SPOT, MARGIN, FUTURES, etc.
         * @return exchange info
         */
        @GET("/api/v3/exchangeInfo")
        Call<JsonNode> getExchangeInfo(@Query("permissions") String permissions);

        /**
         * GET /api/v3/account.
         *
         * @param recvWindow window in milliseconds
         * @param timestamp  timestamp in milliseconds
         * @return account info
         */
        @Headers(ENDPOINT_SECURITY_TYPE_SIGNED_HEADER)
        @GET("/api/v3/account")
        Call<JsonNode> getAccount(@Query("recvWindow") Long recvWindow, @Query("timestamp") Long timestamp);
    }
}
