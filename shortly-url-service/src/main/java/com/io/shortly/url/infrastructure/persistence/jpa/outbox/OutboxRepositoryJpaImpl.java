package com.io.shortly.url.infrastructure.persistence.jpa.outbox;

import com.io.shortly.url.domain.outbox.Outbox;
import com.io.shortly.url.domain.outbox.OutboxRepository;
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
        OutboxJpaEntity entity = OutboxJpaEntity.fromDomain(outbox);
        jpaRepository.save(entity);
    }
}
