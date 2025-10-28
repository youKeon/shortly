package com.io.shortly.url.infrastructure.persistence.jpa;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxJpaRepository extends JpaRepository<OutboxEntity, Long> {

    /**
     * 발행 대기 중인 이벤트 조회
     */
    @Query("SELECT e FROM OutboxEntity e WHERE e.status = com.io.shortly.url.infrastructure.persistence.jpa.OutboxEventStatus.PENDING ORDER BY e.createdAt ASC")
    List<OutboxEntity> findPendingEvents(Pageable pageable);
}
