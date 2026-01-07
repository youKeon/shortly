package com.io.shortly.click.api.admin.dto;

/**
 * DLQ 재처리 응답
 */
public record DlqReprocessResponse(
    int processedCount,
    int successCount,
    int failedCount,
    String status
) {

    public static DlqReprocessResponse of(int processed, int success, int failed, String status) {
        return new DlqReprocessResponse(processed, success, failed, status);
    }
}
