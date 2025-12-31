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
            String originalUrl,

            @Size(min = 3, max = 20, message = "Custom code must be between 3 and 20 characters")
            @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Custom code can only contain letters, numbers, hyphens, and underscores")
            String customCode
    ) {}
}
