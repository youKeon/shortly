DROP TABLE IF EXISTS redirects;

CREATE TABLE redirects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    short_code VARCHAR(10) NOT NULL COMMENT 'Short URL code (e.g., abc123)',
    target_url VARCHAR(2048) NOT NULL COMMENT 'Original URL to redirect to',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Timestamp when redirect was created',

    UNIQUE KEY uk_short_code (short_code) COMMENT 'Unique constraint and index for short code lookups',
    KEY idx_created_at (created_at) COMMENT 'Index for time-series queries'

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Stores URL redirects for cache fallback and analytics';
