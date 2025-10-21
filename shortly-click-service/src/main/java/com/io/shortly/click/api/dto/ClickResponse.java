package com.io.shortly.click.api.dto;

import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.util.List;

@UtilityClass
public class ClickResponse {

    public record ClickStatsResponse(
            String shortCode,
            long totalClicks,
            long clicksLast24Hours,
            long clicksLast7Days
    ) {}

    public record ClickDetailListResponse(
            String shortCode,
            int count,
            List<ClickDetail> clicks
    ) {}

    public record ClickDetail(
            LocalDateTime clickedAt
    ) {}
}
