package com.io.shortly.infrastructure.shorturl.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.io.shortly.infrastructure.shorturl.Base62ShortUrlGenerator;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Base62ShortUrlGeneratorTest {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    @Test
    void generateProducesBase62StringWithFixedLength() {
        Base62ShortUrlGenerator generator = new Base62ShortUrlGenerator();

        String code = generator.generate("https://example.com");

        assertThat(code).hasSize(6);
        assertThat(code.chars().allMatch(ch -> ALPHABET.indexOf(ch) >= 0)).isTrue();
    }

    @Test
    void generateProducesDiverseCodesForSameSeed() {
        Base62ShortUrlGenerator generator = new Base62ShortUrlGenerator();

        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            codes.add(generator.generate("https://example.com"));
        }

        assertThat(codes).hasSizeGreaterThan(1);
    }
}

