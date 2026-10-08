package com.sanedge.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sanedge.auth.entity.AuthOutbox;
import com.sanedge.auth.repository.AuthOutboxRepository;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.common.test.KafkaResource;
import com.sanedge.common.test.KafkaTestUtils;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;

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
 * Integration tests for {@link AuthOutboxPublisher} against a REAL Kafka broker
 * (Testcontainers) and a real MongoDB. The scheduler is disabled so each
 * test drives {@code poll()} manually and asserts the full claim → send →
 * mark-SENT lifecycle, exactly-once behavior under concurrent polls, lease
 * re-processing after a crash window, and the outbox observability metrics.
 */
@QuarkusTest
@QuarkusTestResource(value = KafkaResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = MongoDbResource.class, restrictToAnnotatedClass = true)
@TestProfile(AuthOutboxPublisherIT.NoSchedulerProfile.class)
class AuthOutboxPublisherIT extends MongoTestBase {

    public static class NoSchedulerProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.scheduler.enabled", "false",
                    "auth.outbox.lease.seconds", "2");
        }
    }

    static final String TOPIC_PREFIX = "email-service-topic-auth-register-it-";

    @Inject
    AuthOutboxPublisher publisher;

    @Inject
    AuthOutboxRepository repository;

    @InjectMock
    TracingMetrics tracingMetrics;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrap;

    private AuthOutbox persistPending(String eventId, String topic) {
        AuthOutbox event = new AuthOutbox();
        event.setEventId(eventId);
        event.setTopic(topic);
        event.setEventKey("user-1");
        event.setPayload(new JsonObject()
                .put("event_id", eventId)
                .put("schema_version", 1)
                .put("event_type", topic)
                .put("email", "it@example.test")
                .put("subject", "IT subject")
                .put("body", "<p>it body</p>")
                .encode());
        return repository.persist(event).replaceWith(event).await().indefinitely();
    }

    private AuthOutbox findByEventId(String eventId) {
        return repository.find("eventId", eventId).firstResult().await().indefinitely();
    }

    private void ageClaim(AuthOutbox event, Instant claimedAt) {
        repository.update("{ '$set': { 'claimedAt': ?1 } }", claimedAt)
                .where("{ '_id': ?1 }", event.id)
                .await().indefinitely();
    }

    private void setNextAttemptAt(AuthOutbox event, Instant nextAttemptAt) {
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
        String eventId = "auth-it-drain-" + UUID.randomUUID();

        persistPending(eventId, topic);
        publisher.poll().await().indefinitely();

        AuthOutbox saved = findByEventId(eventId);
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
        String eventId = "auth-it-exact-" + UUID.randomUUID();

        persistPending(eventId, topic);
        Uni.combine().all().unis(publisher.poll(), publisher.poll()).asTuple().await().indefinitely();

        AuthOutbox saved = findByEventId(eventId);
        assertThat(saved.getStatus()).isEqualTo("SENT");
        // Exactly one claim won; the concurrent poller was rejected by the
        // in-flight guard and/or the atomic claim token.
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(countRecordsOnTopic(topic, eventId)).isEqualTo(1);
    }

    @Test
    void orphanedClaimIsReprocessedAfterLeaseExpiry() {
        String topic = TOPIC_PREFIX + UUID.randomUUID();
        String eventId = "auth-it-lease-" + UUID.randomUUID();

        AuthOutbox event = persistPending(eventId, topic);
        // Simulate a crash after claim: the row is claimed but the event was
        // never sent. Age the claim past the (2s) lease so it becomes due.
        AuthOutbox claimed = repository.claim(event).await().indefinitely();
        assertThat(claimed).isNotNull();
        ageClaim(event, Instant.now().minusSeconds(5));

        publisher.poll().await().indefinitely();

        AuthOutbox saved = findByEventId(eventId);
        assertThat(saved.getStatus()).isEqualTo("SENT");
        // 1 claim (stale, crashed) + 1 reprocessing claim after lease expiry.
        assertThat(saved.getAttempts()).isEqualTo(2);
        assertThat(countRecordsOnTopic(topic, eventId)).isEqualTo(1);
    }

    @Test
    void sentEventIsNotRepublishedByLaterPolls() {
        String topic = TOPIC_PREFIX + UUID.randomUUID();
        String eventId = "auth-it-sent-" + UUID.randomUUID();

        persistPending(eventId, topic);
        publisher.poll().await().indefinitely();
        publisher.poll().await().indefinitely();

        AuthOutbox saved = findByEventId(eventId);
        assertThat(saved.getStatus()).isEqualTo("SENT");
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(countRecordsOnTopic(topic, eventId)).isEqualTo(1);
    }

    @Test
    void nonDueEventWithFutureBackoffIsNotPolled() {
        String topic = TOPIC_PREFIX + UUID.randomUUID();
        String eventId = "auth-it-backoff-" + UUID.randomUUID();

        AuthOutbox event = persistPending(eventId, topic);
        setNextAttemptAt(event, Instant.now().plusSeconds(1800));

        publisher.poll().await().indefinitely();

        AuthOutbox saved = findByEventId(eventId);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttempts()).isZero();
        assertThat(countRecordsOnTopic(topic, eventId)).isZero();
    }
}
