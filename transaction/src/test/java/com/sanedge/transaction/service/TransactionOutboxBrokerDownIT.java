package com.sanedge.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.transaction.entity.TransactionOutbox;
import com.sanedge.transaction.repository.TransactionOutboxRepository;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;

/**
 * Transaction outbox resilience against an UNAVAILABLE Kafka broker: no Kafka
 * container is started; {@code kafka.bootstrap.servers} points at a closed port
 * so every send fails with a real connection error. Verifies the event stays
 * PENDING with backoff, is not re-attempted before the backoff, and that the
 * Kafka publish failure metric is recorded.
 */
@QuarkusTest
@QuarkusTestResource(value = MongoDbResource.class, restrictToAnnotatedClass = true)
@TestProfile(TransactionOutboxBrokerDownIT.NoBrokerProfile.class)
class TransactionOutboxBrokerDownIT extends MongoTestBase {

    public static class NoBrokerProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.scheduler.enabled", "false",
                    "kafka.bootstrap.servers", "127.0.0.1:59999",
                    "kafka.retries", "1",
                    "kafka.request.timeout.ms", "1000",
                    "kafka.delivery.timeout.ms", "2000",
                    "kafka.max.block.ms", "1000");
        }
    }

    @Inject
    TransactionOutboxPublisher publisher;

    @Inject
    TransactionOutboxRepository repository;

    @InjectMock
    TracingMetrics tracingMetrics;

    private TransactionOutbox persistPending(String eventId) {
        TransactionOutbox event = new TransactionOutbox();
        event.setEventId(eventId);
        event.setTopic("email-service-topic-transaction-create-it-down");
        event.setEventKey("txn-1");
        event.setPayload(new JsonObject()
                .put("event_id", eventId)
                .put("schema_version", 1)
                .put("event_type", "test")
                .put("email", "down@example.test")
                .put("subject", "s")
                .put("body", "b")
                .encode());
        return repository.persist(event).replaceWith(event).await().indefinitely();
    }

    private TransactionOutbox findByEventId(String eventId) {
        return repository.find("eventId", eventId).firstResult().await().indefinitely();
    }

    private void pinNextAttemptAt(TransactionOutbox event, Instant nextAttemptAt) {
        repository.update("{ '$set': { 'nextAttemptAt': ?1 } }", nextAttemptAt)
                .where("{ '_id': ?1 }", event.id)
                .await().indefinitely();
    }

    @Test
    void eventStaysPendingWithBackoffWhenBrokerUnavailable() {
        String eventId = "txn-it-down-" + UUID.randomUUID();

        Instant before = Instant.now();

        persistPending(eventId);
        publisher.poll().await().indefinitely();

        TransactionOutbox saved = findByEventId(eventId);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getLastError()).isNotBlank();
        // nextAttemptAt must be scheduled strictly after the poll began.
        assertThat(saved.getNextAttemptAt()).isAfter(before);

        // A re-poll while the event is still within its backoff window must not
        // re-attempt it: pin nextAttemptAt far in the future (as the backoff did)
        // and verify a subsequent poll leaves attempts untouched. Deterministic
        // even when the test JVM is slow (the real 1s backoff could otherwise
        // expire between the two polls).
        pinNextAttemptAt(saved, Instant.now().plusSeconds(1800));
        publisher.poll().await().indefinitely();

        TransactionOutbox rePolled = findByEventId(eventId);
        assertThat(rePolled.getAttempts()).isEqualTo(1);
        assertThat(rePolled.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void kafkaPublishFailureMetricRecorded() {
        String eventId = "txn-it-down-metric-" + UUID.randomUUID();

        persistPending(eventId);
        publisher.poll().await().indefinitely();

        verify(tracingMetrics, atLeastOnce()).recordKafkaPublish("failure");
        verify(tracingMetrics, never()).recordKafkaPublish("success");

        assertThat(repository.countPending().await().indefinitely()).isGreaterThanOrEqualTo(1);
    }
}
