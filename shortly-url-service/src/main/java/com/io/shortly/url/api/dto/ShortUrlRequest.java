package com.io.shortly.url.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.URL;

@UtilityClass
public class ShortUrlRequest {

    public record ShortenRequest(
            @NotBlank(message = "Original URL must not be blank")
            @Size(max = 2048, message = "URL must not exceed 2048 characters")
            @Pattern(regexp = "^https?://.*", message = "URL must start with http:// or https://")
            String originalUrl
    ) {}
}
