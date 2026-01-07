package com.io.shortly.click.api.admin;

import com.io.shortly.click.api.admin.dto.DlqReprocessResponse;
import com.io.shortly.click.application.admin.DlqAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "DLQ Admin", description = "DLQ 관리 API")
@RestController
@RequestMapping("/api/v1/admin/dlq")
@RequiredArgsConstructor
public class DlqAdminController {

    private final DlqAdminService dlqAdminService;

    /**
     * DLQ 수동 재처리
     */
    @Operation(
        summary = "Permanent DLQ 수동 재처리",
        description = "특정 eventId의 Permanent DLQ 메시지를 수동으로 재처리"
    )
    @PostMapping("/permanent/reprocess/{eventId}")
    public ResponseEntity<DlqReprocessResponse> reprocessPermanentDlq(
            @PathVariable long eventId) {

        log.info("[DLQ Admin API] Permanent DLQ 수동 재처리 요청 - eventId={}", eventId);
        DlqReprocessResponse response = dlqAdminService.reprocessPermanentMessage(eventId);
        return ResponseEntity.ok(response);
    }
}
