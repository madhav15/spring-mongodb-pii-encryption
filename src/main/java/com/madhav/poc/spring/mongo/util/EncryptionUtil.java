package com.madhav.poc.spring.mongo.util;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class EncryptionUtil {

    @Value("${encryption.secret}")
    private String secret;

    private SecretKeySpec secretKey;
    private IvParameterSpec ivSpec;

    private static final String PREFIX = "ENC::";

    @PostConstruct
    private void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 16) {
            throw new IllegalArgumentException("Secret key must be at least 16 bytes long");
        }
        byte[] key16 = new byte[16];
        System.arraycopy(keyBytes, 0, key16, 0, 16);

        this.secretKey = new SecretKeySpec(key16, "AES");
        this.ivSpec = new IvParameterSpec(key16); // fixed IV derived from secret (simple, deterministic)
    }

    public String encrypt(String plain) {
        if (isEncrypted(plain)) return plain; // idempotent
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Error while encrypting", e);
        }
    }

    public String decrypt(String value) {
        if (!isEncrypted(value)) return value; // already plaintext
        try {
            String base64 = value.substring(PREFIX.length());
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decoded = Base64.getDecoder().decode(base64);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error while decrypting", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
