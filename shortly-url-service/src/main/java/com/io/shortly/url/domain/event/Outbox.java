package com.io.shortly.url.domain.event;

public class Outbox {

    private final Aggregate aggregate;
    private final String aggregateId;
    private final String payload;

    private Outbox(
        final Aggregate aggregate,
        final String aggregateId,
        final String payload
    ) {
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
