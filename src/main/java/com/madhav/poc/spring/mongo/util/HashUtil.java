package com.madhav.poc.spring.mongo.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class HashUtil {

    private static final String HASH_PREFIX = "HASH::";

    private HashUtil() {}

    public static String sha256Hex(String input) {
        if (isHashed(input)) return input; // idempotent
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            String base64 = Base64.getEncoder().encodeToString(hashBytes);
            return HASH_PREFIX + base64;
        } catch (Exception e) {
            throw new RuntimeException("Error generating SHA-256 hash", e);
        }
    }

    public static boolean isHashed(String value) {
        return value != null && value.startsWith(HASH_PREFIX);
    }
}
