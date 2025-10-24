package com.io.shortly.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RetryPolicy {

    /**
     * Short retry - 1 minute
     * Use for: Temporary issues, rate limiting, transient failures
     */
    SHORT("60"),

    /**
     * Medium retry - 2 minutes
     * Use for: Service unavailable, circuit breaker open
     */
    MEDIUM("120"),

    /**
     * Long retry - 5 minutes
     * Use for: Message processing failures, Kafka issues, database issues
     */
    LONG("300"),

    /**
     * Very long retry - 15 minutes
     * Use for: Major outages, maintenance windows
     */
    VERY_LONG("900"),

    /**
     * No retry - do not retry
     * Use for: Permanent failures, validation errors
     */
    NONE(null);

    private final String retryAfterSeconds;

    /**
     * Check if retry is allowed
     */
    public boolean isRetryable() {
        return retryAfterSeconds != null;
    }

    /**
     * Get Retry-After header value
     */
    public String getHeaderValue() {
        return retryAfterSeconds;
    }
}
