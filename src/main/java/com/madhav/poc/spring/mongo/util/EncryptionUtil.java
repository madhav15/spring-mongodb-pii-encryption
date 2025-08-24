package com.madhav.poc.spring.mongo.util;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
public class EncryptionUtil {
    private static final String ALGORITHM = "AES";
    private static final String KEY = "1234567890123456"; // 16-char key

    public String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY.getBytes(), ALGORITHM));
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting field", e);
        }
    }

    public String decrypt(String cipherText) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY.getBytes(), ALGORITHM));
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)));
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting field", e);
        }
    }
}

