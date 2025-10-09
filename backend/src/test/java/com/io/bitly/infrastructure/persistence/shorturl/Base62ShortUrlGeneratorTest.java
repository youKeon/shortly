package com.io.bitly.infrastructure.persistence.shorturl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base62ShortUrlGeneratorTest {

    private static final String BASE62_PATTERN = "[0-9A-Za-z]{6}";

    @Test
    void generate_producesDifferentCodesForSameSeed() {
        Base62ShortUrlGenerator generator = new Base62ShortUrlGenerator();

        String first = generator.generate("https://example.com");
        String second = generator.generate("https://example.com");

        assertAll(
                () -> assertNotEquals(first, second),
                () -> assertTrue(first.matches(BASE62_PATTERN)),
                () -> assertTrue(second.matches(BASE62_PATTERN))
        );
    }
}
