package com.io.shortly.click.api;

import static com.io.shortly.click.api.dto.ClickResponse.ClickDetail;
import static com.io.shortly.click.api.dto.ClickResponse.ClickDetailListResponse;
import static com.io.shortly.click.api.dto.ClickResponse.ClickStatsResponse;

import com.io.shortly.click.application.ClickService;
import com.io.shortly.click.application.dto.ClickCommand.ClickDetailCommand;
import com.io.shortly.click.application.dto.ClickCommand.ClickStatsCommand;
import com.io.shortly.click.application.dto.ClickResult.ClickStatsResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final ClickService clickService;

    @GetMapping("/{shortCode}/stats")
    public ClickStatsResponse getStats(@PathVariable String shortCode) {
        ClickStatsResult result = clickService.getClickStats(
            ClickStatsCommand.of(shortCode)
        );


        return new ClickStatsResponse(
            result.shortCode(),
            result.totalClicks(),
            result.clicksLast24Hours(),
            result.clicksLast7Days()
        );
    }

    @GetMapping("/{shortCode}/clicks")
    public ClickDetailListResponse getClickDetails(
            @PathVariable String shortCode,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        List<ClickDetail> clickDetails = clickService.getClickDetails(
            ClickDetailCommand.of(shortCode, limit)
        ).stream()
            .map(r -> new ClickDetail(r.clickedAt()))
            .toList();

        return new ClickDetailListResponse(shortCode, clickDetails.size(), clickDetails);
    }
}
