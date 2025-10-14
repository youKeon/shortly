package com.io.shortly.infrastructure.generator;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.io.shortly.infrastructure.shorturl.generator.ShortUrlGeneratorBase62Impl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShortUrlGeneratorBase62ImplTest {

    private static final String BASE62_PATTERN = "[0-9A-Za-z]{6}";

    @Test
    @DisplayName("동일한 seed로 생성하면 매번 다른 코드를 생성한다")
    void generate_producesDifferentCodesForSameSeed() {
        ShortUrlGeneratorBase62Impl generator = new ShortUrlGeneratorBase62Impl();

        String first = generator.generate("https://example.com");
        String second = generator.generate("https://example.com");

        assertAll(
                () -> assertNotEquals(first, second),
                () -> assertTrue(first.matches(BASE62_PATTERN)),
                () -> assertTrue(second.matches(BASE62_PATTERN))
        );
    }
}

