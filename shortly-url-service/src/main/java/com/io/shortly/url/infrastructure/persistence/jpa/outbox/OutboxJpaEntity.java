package com.io.shortly.url.infrastructure.persistence.jpa.outbox;

import com.io.shortly.url.domain.event.Aggregate;
import com.io.shortly.url.domain.event.Outbox;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@Table(name = "outbox", indexes = {
    @Index(name = "idx_status_created_at", columnList = "status, created_at"),
    @Index(name = "idx_aggregate_aggregate_id", columnList = "aggregate, aggregate_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate", nullable = false, length = 20)
    private Aggregate aggregate;

    @Column(name = "aggregate_id", nullable = false, length = 50)
    private String aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant createdAt;

    public static OutboxJpaEntity fromDomain(Outbox outbox) {
        OutboxJpaEntity entity = new OutboxJpaEntity();
        entity.aggregate = outbox.getAggregate();
        entity.aggregateId = outbox.getAggregateId();
        entity.payload = outbox.getPayload();
        return entity;
    }

    public void markAsPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
    }
}
