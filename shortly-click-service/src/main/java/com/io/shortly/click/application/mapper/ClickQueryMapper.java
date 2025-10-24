package com.io.shortly.click.application.mapper;

import static com.io.shortly.click.api.dto.ClickResponse.ClickDetail;
import static com.io.shortly.click.api.dto.ClickResponse.ClickDetailListResponse;

import com.io.shortly.click.application.dto.ClickResult.ClickDetailResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ClickQueryMapper {

    public ClickDetailListResponse toClickDetailListResponse(
        String shortCode,
        List<ClickDetailResult> results
    ) {
        List<ClickDetail> clicks = results.stream()
            .map(result -> new ClickDetail(result.clickedAt()))
            .toList();

        return new ClickDetailListResponse(shortCode, clicks.size(), clicks);
    }
}
