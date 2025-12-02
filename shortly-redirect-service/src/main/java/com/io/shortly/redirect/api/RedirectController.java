package com.io.shortly.redirect.api;

import com.io.shortly.redirect.application.RedirectFacade;
import com.io.shortly.redirect.application.dto.RedirectResult.RedirectLookupResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final RedirectFacade redirectFacade;

    @GetMapping("/r/{shortCode}")
    @Operation(summary = "단축 URL 리다이렉션", description = "단축 코드로 원본 URL을 조회하여 리다이렉션 (HTTP 302)")
    @ApiResponse(responseCode = "302", description = "리다이렉션 성공", content = @Content)
    @ApiResponse(responseCode = "404", description = "단축 코드를 찾을 수 없음")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        log.debug("[Controller] 리디렉션 요청: shortCode={}", shortCode);

        RedirectLookupResult result = redirectFacade.getOriginalUrl(shortCode);

        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create(result.originalUrl()))
            .build();
    }
}
