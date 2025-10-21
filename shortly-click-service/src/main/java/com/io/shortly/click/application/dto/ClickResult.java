package com.io.shortly.click.application.dto;

import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;

@UtilityClass
public class ClickResult {

    public record ClickStatsResult(
            String shortCode,
            long totalClicks,
            long clicksLast24Hours,
            long clicksLast7Days
    ) {
        public static ClickStatsResult of(
                String shortCode,
                long totalClicks,
                long clicksLast24Hours,
                long clicksLast7Days
        ) {
            return new ClickStatsResult(shortCode, totalClicks, clicksLast24Hours, clicksLast7Days);
        }
    }

    public record ClickDetailResult(
            LocalDateTime clickedAt
    ) {
        public static ClickDetailResult of(LocalDateTime clickedAt) {
            return new ClickDetailResult(clickedAt);
        }
    }
}
