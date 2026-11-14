package com.splitsmart.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacSignature {
    private HmacSignature() {}

    public static boolean verify(String secret, String message, String signature) {
        if (signature == null || !signature.matches("[a-fA-F0-9]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return MessageDigest.isEqual(
                    mac.doFinal(message.getBytes(StandardCharsets.UTF_8)),
                    HexFormat.of().parseHex(signature));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
