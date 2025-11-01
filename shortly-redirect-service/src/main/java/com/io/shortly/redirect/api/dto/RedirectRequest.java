package com.io.shortly.redirect.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RedirectRequest {

    public static final String SHORT_CODE_REGEX = "^[a-zA-Z0-9]{6,10}$";

    public record GetRedirectRequest(
        @NotBlank(message = "Short code must not be blank")
        @Pattern(
            regexp = SHORT_CODE_REGEX,
            message = "Short code must be 6-10 alphanumeric characters"
        )
        String shortCode
    ) {
        public static GetRedirectRequest of(String shortCode) {
            return new GetRedirectRequest(shortCode);
        }
    }
}
