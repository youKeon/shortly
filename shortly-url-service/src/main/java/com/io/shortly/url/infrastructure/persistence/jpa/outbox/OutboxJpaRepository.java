package com.io.shortly.url.infrastructure.persistence.jpa.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, Long> {

    /**
     * 발행 대기 중인 이벤트 조회
     */
    @Query("SELECT e FROM OutboxJpaEntity e WHERE e.status = com.io.shortly.url.infrastructure.persistence.jpa.outbox.OutboxEventStatus.PENDING ORDER BY e.createdAt ASC")
    List<OutboxJpaEntity> findPendingEvents(Pageable pageable);

    /**
     * 장시간 PROCESSING 상태로 남아있는 stuck event 조회
     */
    @Query("SELECT e FROM OutboxJpaEntity e WHERE e.status = com.io.shortly.url.infrastructure.persistence.jpa.outbox.OutboxEventStatus.PROCESSING AND e.updatedAt < :threshold ORDER BY e.createdAt ASC")
    List<OutboxJpaEntity> findStuckEvents(@Param("threshold") Instant threshold);
}
