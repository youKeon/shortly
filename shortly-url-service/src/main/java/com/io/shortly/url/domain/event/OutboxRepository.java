package com.io.shortly.url.domain.event;

public interface OutboxRepository {

    void save(Outbox outbox);
}
