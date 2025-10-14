package com.io.shortly.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ShortenRequest {
    public record CreateShortUrlRequest(
            @NotBlank(message = "originalUrl must not be blank")
            @Pattern(regexp = "^(https?://).+", message = "originalUrl must start with http:// or https://")
            String originalUrl
    ) {
    }
}

