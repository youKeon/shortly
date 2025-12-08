package com.io.shortly.click.application;

import com.io.shortly.click.application.dto.ClickCommand.ClickStatsCommand;
import com.io.shortly.click.application.dto.ClickResult.ClickStatsResult;
import com.io.shortly.click.domain.UrlClick;
import com.io.shortly.click.domain.UrlClickRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClickService {

    private final UrlClickRepository urlClickRepository;

    public ClickStatsResult getClickStats(ClickStatsCommand command) {

        long totalClicks = urlClickRepository.countByShortCode(command.shortCode());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24Hours = now.minusHours(24);
        LocalDateTime last7Days = now.minusDays(7);

        List<UrlClick> last7DaysClicks = urlClickRepository.findByShortCodeAndClickedAtBetween(
                command.shortCode(), last7Days, now
        );

        long clicksLast7Days = last7DaysClicks.size();
        long clicksLast24Hours = last7DaysClicks.stream()
                .filter(click -> click.getClickedAt().isAfter(last24Hours))
                .count();

        return ClickStatsResult.of(
            command.shortCode(),
            totalClicks,
            clicksLast24Hours,
            clicksLast7Days
        );
    }
}
