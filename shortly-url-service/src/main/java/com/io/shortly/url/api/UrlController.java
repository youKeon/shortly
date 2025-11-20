package com.io.shortly.url.api;

import static com.io.shortly.url.application.dto.ShortUrlCommand.FindCommand;
import static com.io.shortly.url.application.dto.ShortUrlCommand.ShortenCommand;
import static com.io.shortly.url.application.dto.ShortUrlResult.ShortenedResult;

import com.io.shortly.url.api.dto.ShortUrlRequest;
import com.io.shortly.url.api.dto.ShortUrlResponse.GetShortUrlResponse;
import com.io.shortly.url.api.dto.ShortUrlResponse.ShortenedResponse;
import com.io.shortly.url.application.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/urls")
@Tag(name = "URL Service", description = "URL 단축 및 조회 API")
public class UrlController {

    private final UrlService urlService;

    @PostMapping("/shorten")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "URL 단축", description = "긴 URL을 짧은 코드로 변환합니다")
    public ShortenedResponse shortenUrl(@Valid @RequestBody ShortUrlRequest.ShortenRequest request) {
        ShortenedResult result = urlService.shortenUrl(
            ShortenCommand.of(request.originalUrl())
        );
        log.info("URL shortened: {}", result.shortCode());
        return ShortenedResponse.of(result);
    }

    @GetMapping("/{shortCode}")
    @Operation(summary = "Short Code로 URL 조회", description = "Redirect Service의 fallback용 API")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "Short Code를 찾을 수 없음")
    public GetShortUrlResponse findUrl(@PathVariable String shortCode) {
        ShortenedResult result = urlService.findByShortCode(FindCommand.of(shortCode));
        return GetShortUrlResponse.of(result.shortCode(), result.originalUrl());
    }
}
