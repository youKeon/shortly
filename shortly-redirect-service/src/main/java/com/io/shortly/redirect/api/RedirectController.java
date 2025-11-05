package com.io.shortly.redirect.api;

import static com.io.shortly.redirect.api.dto.RedirectRequest.GetRedirectRequest;
import static com.io.shortly.redirect.api.dto.RedirectRequest.SHORT_CODE_REGEX;

import com.io.shortly.redirect.application.RedirectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final RedirectService redirectService;

    @Operation(
        summary = "단축 URL 리다이렉션",
        description = "단축 코드로 원본 URL을 조회하여 리다이렉션 (HTTP 302)"
    )
    @ApiResponse(
        responseCode = "302",
        description = "리다이렉션 성공",
        content = @Content
    )
    @ApiResponse(
        responseCode = "404",
        description = "단축 코드를 찾을 수 없음"
    )
    @GetMapping("/r/{shortCode}")
    public ResponseEntity<Void> redirect(
        @PathVariable
        @NotBlank(message = "Short code must not be blank")
        @Pattern(
            regexp = SHORT_CODE_REGEX,
            message = "Short code must be 6-10 alphanumeric characters"
        )
        String shortCode
    ) {
        log.debug("[Controller] 리디렉션 요청: shortCode={}", shortCode);

        GetRedirectRequest request = GetRedirectRequest.of(shortCode);
        var result = redirectService.getOriginalUrl(request.shortCode());

        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create(result.getOriginalUrl()))
            .build();
    }
}
