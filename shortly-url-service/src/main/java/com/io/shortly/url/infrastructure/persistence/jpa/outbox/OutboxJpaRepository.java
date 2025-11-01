package com.io.shortly.url.infrastructure.persistence.jpa.outbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, Long> {

    /**
     * 발행 대기 중인 이벤트 조회
     */
    @Query("SELECT e FROM OutboxJpaEntity e WHERE e.status = com.io.shortly.url.infrastructure.persistence.jpa.outbox.OutboxEventStatus.PENDING ORDER BY e.createdAt ASC")
    List<OutboxJpaEntity> findPendingEvents(Pageable pageable);
}
