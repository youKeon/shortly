package com.io.shortly.url.domain.outbox;

public interface OutboxRepository {

    void save(Outbox outbox);
}
