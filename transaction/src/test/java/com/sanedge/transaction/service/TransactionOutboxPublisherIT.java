package com.sanedge.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.common.test.KafkaResource;
import com.sanedge.common.test.KafkaTestUtils;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.transaction.entity.TransactionOutbox;
import com.sanedge.transaction.repository.TransactionOutboxRepository;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Integration tests for {@link TransactionOutboxPublisher} against a REAL Kafka
 * broker (Testcontainers) and a real MongoDB: drain-to-SENT, exactly-once
 * under concurrent polls, lease re-processing after a crash window, no
 * republish of SENT events, and backoff-respecting polls.
 */
@QuarkusTest
@QuarkusTestResource(value = KafkaResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = MongoDbResource.class, restrictToAnnotatedClass = true)
@TestProfile(TransactionOutboxPublisherIT.NoSchedulerProfile.class)
class TransactionOutboxPublisherIT extends MongoTestBase {

    public static class NoSchedulerProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.scheduler.enabled", "false",
                    "transaction.outbox.lease.seconds", "2");
        }
    }

    static final String TOPIC_PREFIX = "email-service-topic-transaction-create-it-";

    @Inject
    TransactionOutboxPublisher publisher;

    @Inject
    TransactionOutboxRepository repository;

    @InjectMock
    TracingMetrics tracingMetrics;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    private TransactionOutbox persistPending(String eventId, String topic) {
        TransactionOutbox event = new TransactionOutbox();
        event.setEventId(eventId);
        event.setTopic(topic);
        event.setEventKey("txn-1");
        event.setPayload(new JsonObject()
                .put("event_id", eventId)
                .put("schema_version", 1)
                .put("event_type", topic)
                .put("email", "txn@example.test")
                .put("subject", "IT subject")
                .put("body", "<p>it body</p>")
                .encode());
        return repository.persist(event).replaceWith(event).await().indefinitely();
    }

    private TransactionOutbox findByEventId(String eventId) {
        return repository.find("eventId", eventId).firstResult().await().indefinitely();
    }

    private void ageClaim(TransactionOutbox event, Instant claimedAt) {
        repository.update("{ '$set': { 'claimedAt': ?1 } }", claimedAt)
                .where("{ '_id': ?1 }", event.id)
                .await().indefinitely();
    }

    private void setNextAttemptAt(TransactionOutbox event, Instant nextAttemptAt) {
        repository.update("{ '$set': { 'nextAttemptAt': ?1 } }", nextAttemptAt)
                .where("{ '_id': ?1 }", event.id)
                .await().indefinitely();
    }

    private Long countRecordsOnTopic(String topic, String eventId) {
        return Uni.createFrom()
                .item(() -> KafkaTestUtils.countMatching(bootstrap, topic,
                        r -> eventId.equals(new JsonObject(r.value()).getString("event_id")),
                        Duration.ofSeconds(10)))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .await().indefinitely();
    }

    @Test
    void publishesPendingEventToKafkaAndMarksSent() {
        String topic = TOPIC_PREFIX + UUID.randomUUID();
        String eventId = "txn-it-drain-" + UUID.randomUUID();

        persistPending(eventId, topic);
        publisher.poll().await().indefinitely();

        TransactionOutbox saved = findByEventId(eventId);
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getLastError()).isNull();

        assertThat(KafkaTestUtils.awaitRecord(bootstrap, topic,
                r -> eventId.equals(new JsonObject(r.value()).getString("event_id")),
                Duration.ofSeconds(20))).isNotNull();
        // Observability: the Kafka publish success metric call-site fired.
        verify(tracingMetrics, atLeastOnce()).recordKafkaPublish("success");
    }

    @Test
    void concurrentPollsPublishExactlyOnce() {
        String topic = TOPIC_PREFIX + UUID.randomUUID();
        String eventId = "txn-it-exact-" + UUID.randomUUID();

        persistPending(eventId, topic);
        Uni.combine().all().unis(publisher.poll(), publisher.poll()).asTuple().await().indefinitely();

        TransactionOutbox saved = findByEventId(eventId);
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(countRecordsOnTopic(topic, eventId)).isEqualTo(1);
    }

    @Test
    void orphanedClaimIsReprocessedAfterLeaseExpiry() {
        String topic = TOPIC_PREFIX + UUID.randomUUID();
        String eventId = "txn-it-lease-" + UUID.randomUUID();

        TransactionOutbox event = persistPending(eventId, topic);
        // Simulate a crash after claim: the row is claimed but the event was
        // never sent. Age the claim past the (2s) lease so it becomes due.
        TransactionOutbox claimed = repository.claim(event).await().indefinitely();
        assertThat(claimed).isNotNull();
        ageClaim(event, Instant.now().minusSeconds(5));

        publisher.poll().await().indefinitely();

        TransactionOutbox saved = findByEventId(eventId);
        assertThat(saved.getStatus()).isEqualTo("SENT");
        // 1 claim (stale, crashed) + 1 reprocessing claim after lease expiry.
        assertThat(saved.getAttempts()).isEqualTo(2);
        assertThat(countRecordsOnTopic(topic, eventId)).isEqualTo(1);
    }

    @Test
    void sentEventIsNotRepublishedByLaterPolls() {
        String topic = TOPIC_PREFIX + UUID.randomUUID();
        String eventId = "txn-it-sent-" + UUID.randomUUID();

        persistPending(eventId, topic);
        publisher.poll().await().indefinitely();
        publisher.poll().await().indefinitely();

        TransactionOutbox saved = findByEventId(eventId);
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(countRecordsOnTopic(topic, eventId)).isEqualTo(1);
    }

    @Test
    void nonDueEventWithFutureBackoffIsNotPolled() {
        String topic = TOPIC_PREFIX + UUID.randomUUID();
        String eventId = "txn-it-backoff-" + UUID.randomUUID();

        TransactionOutbox event = persistPending(eventId, topic);
        setNextAttemptAt(event, Instant.now().plusSeconds(1800));

        publisher.poll().await().indefinitely();

        TransactionOutbox saved = findByEventId(eventId);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttempts()).isZero();
        assertThat(countRecordsOnTopic(topic, eventId)).isZero();
    }
}
