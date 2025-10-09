package com.io.bitly.infrastructure.shorturl;

import com.io.bitly.domain.shorturl.ShortUrlGenerator;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

@Component
public class Base62ShortUrlGenerator implements ShortUrlGenerator {

    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SHORT_CODE_LENGTH = 6;

    @Override
    public String generate(String seed) {
        MessageDigest digest = getMessageDigest();
        byte[] hash = digest.digest(seed.getBytes());

        StringBuilder shortCode = new StringBuilder();
        Random random = new Random(bytesToLong(hash));

        for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
            int index = Math.abs(random.nextInt()) % BASE62_CHARS.length();
            shortCode.append(BASE62_CHARS.charAt(index));
        }

        return shortCode.toString();
    }

    private MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " algorithm not available", e);
        }
    }

    private long bytesToLong(byte[] bytes) {
        long result = 0;
        for (int i = 0; i < Math.min(8, bytes.length); i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }
}
