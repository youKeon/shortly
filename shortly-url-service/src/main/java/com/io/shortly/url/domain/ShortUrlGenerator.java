package com.io.shortly.url.domain;

public interface ShortUrlGenerator {

    GeneratedShortCode generate(String seed);
}
