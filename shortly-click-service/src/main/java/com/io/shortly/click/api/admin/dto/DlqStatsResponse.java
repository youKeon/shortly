package com.io.shortly.click.api.admin.dto;

public record DlqStatsResponse(
    long permanentDlqCount,
    long transientDlqCount,
    long totalDlqCount
) {

    public static DlqStatsResponse of(long permanent, long transientCount) {
        return new DlqStatsResponse(permanent, transientCount, permanent + transientCount);
    }
}
