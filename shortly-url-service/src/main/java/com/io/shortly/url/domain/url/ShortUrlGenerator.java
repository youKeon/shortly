package com.io.shortly.url.domain.url;

public interface ShortUrlGenerator {

    GeneratedShortCode generate(String seed);
}
