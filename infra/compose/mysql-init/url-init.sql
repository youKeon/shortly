USE shortly_url;

CREATE TABLE IF NOT EXISTS urls (
    id BIGINT NOT NULL AUTO_INCREMENT,
    short_code VARCHAR(10) NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY idx_short_code (short_code)
) ENGINE=InnoDB;
