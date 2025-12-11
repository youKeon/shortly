package com.io.shortly.url.infrastructure.generator;

import com.io.shortly.shared.id.UniqueIdGenerator;
import com.io.shortly.url.domain.GeneratedShortCode;
import com.io.shortly.url.domain.ShortUrlGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class ShortUrlGeneratorSnowflakeImpl implements ShortUrlGenerator {

    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int MIN_SHORT_CODE_LENGTH = 6;

    private final UniqueIdGenerator uniqueIdGenerator;

    @Override
    public GeneratedShortCode generate(String seed) {
        long id = uniqueIdGenerator.generate();
        String shortCode = encodeBase62(id);
        return GeneratedShortCode.of(id, shortCode);
    }

    private String encodeBase62(long value) {
        if (value == 0) {
            return "0".repeat(MIN_SHORT_CODE_LENGTH);
        }

        StringBuilder builder = new StringBuilder();
        long current = value;

        while (current > 0) {
            int index = (int) (current % BASE62.length);
            builder.append(BASE62[index]);
            current /= BASE62.length;
        }

        while (builder.length() < MIN_SHORT_CODE_LENGTH) {
            builder.append('0');
        }

        return builder.reverse().toString();
    }
}
