package com.io.shortly.url.infrastructure.persistence.jpa;

import com.io.shortly.url.domain.event.Outbox;
import com.io.shortly.url.domain.event.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class OutboxRepositoryJpaImpl implements OutboxRepository {

    private final OutboxJpaRepository jpaRepository;

    @Override
    @Transactional
    public void save(Outbox outbox) {
        OutboxEntity entity = OutboxEntity.fromDomain(outbox);
        jpaRepository.save(entity);
    }
}
