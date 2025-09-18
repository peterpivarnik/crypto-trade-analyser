package com.psw.cta.security;

import com.psw.cta.exception.BinanceApiException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Hex;

/**
 * Utility class to sign messages using HMAC-SHA256.
 */
public class HmacSha256Signer {

    private HmacSha256Signer() {
    }

    /**
     * Sign the given message using the given secret.
     *
     * @param message message to sign
     * @param secret  secret key
     * @return a signed message
     */
    public static String sign(String message, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            sha256Hmac.init(secretKeySpec);
            return new String(Hex.encodeHex(sha256Hmac.doFinal(message.getBytes())));
        } catch (Exception e) {
            throw new BinanceApiException("Unable to sign message.", e);
        }
    }
}
