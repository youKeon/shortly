USE shortly_click;

CREATE TABLE IF NOT EXISTS url_clicks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    short_code VARCHAR(10) NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    clicked_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_short_code_clicked_at (short_code, clicked_at DESC)
) ENGINE=InnoDB;
