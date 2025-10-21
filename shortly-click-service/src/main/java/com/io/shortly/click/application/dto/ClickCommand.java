package com.io.shortly.click.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ClickCommand {

    private static final String SHORT_CODE_REGEX = "^[a-zA-Z0-9]{6,10}$";

    public record ClickStatsCommand(
            @NotBlank(message = "Short code must not be blank")
            @Pattern(regexp = SHORT_CODE_REGEX, message = "Short code must be 6-10 alphanumeric characters")
            String shortCode
    ) {
        public static ClickStatsCommand of(String shortCode) {
            return new ClickStatsCommand(shortCode);
        }
    }

    public record ClickDetailCommand(
            @NotBlank(message = "Short code must not be blank")
            @Pattern(regexp = SHORT_CODE_REGEX, message = "Short code must be 6-10 alphanumeric characters")
            String shortCode,
            @Positive(message = "Limit must be positive")
            @Max(value = 1000, message = "Limit must not exceed 1000")
            Integer limit
    ) {
        public static ClickDetailCommand of(String shortCode, Integer limit) {
            return new ClickDetailCommand(shortCode, limit);
        }
    }
}
