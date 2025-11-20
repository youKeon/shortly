package com.io.shortly.url.domain.outbox;

public class Outbox {

    private final Aggregate aggregate;
    private final String aggregateId;
    private final String payload;

    private Outbox(
        final Aggregate aggregate,
        final String aggregateId,
        final String payload
    ) {
        if (aggregate == null) {
            throw new IllegalArgumentException("Aggregate must not be null");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("Aggregate ID must not be blank");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payload must not be blank");
        }

        this.aggregate = aggregate;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    public static Outbox create(
        Aggregate aggregate,
        String aggregateId,
        String payload
    ) {
        return new Outbox(aggregate, aggregateId, payload);
    }

    public Aggregate getAggregate() {
        return aggregate;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }
}
