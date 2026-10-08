package com.sanedge.statsbackfill;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.clickhouse.ClickHouseClient;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoDatabase;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * One-shot backfill of historical OLTP data into ClickHouse.
 *
 * <p>After the MongoDB migration the transactional data lives in the
 * {@code ec_sales} context: the {@code orders} and {@code transactions}
 * collections (ids are ObjectId, exposed as 24-char hex strings). The default
 * Mongo client of this app points at that context (see application.properties).
 */
@ApplicationScoped
public class BackfillLifecycle {
    private static final Logger log = LoggerFactory.getLogger(BackfillLifecycle.class);

    @Inject
    ClickHouseClient clickhouse;

    @Inject
    ReactiveMongoClient mongoClient;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "ec_sales")
    String databaseName;

    @ConfigProperty(name = "backfill.domains", defaultValue = "order,transaction")
    String domainsConfig;

    @ConfigProperty(name = "backfill.from-date", defaultValue = "2024-01-01")
    String fromDate;

    void onStart(@Observes StartupEvent ev) {
        log.info("=== Stats Backfill starting ===");
        Set<String> domains = Arrays.stream(domainsConfig.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());

        backfillOrders(domains)
                .chain(() -> backfillTransactions(domains))
                .invoke(() -> {
                    log.info("=== Stats Backfill completed ===");
                    Quarkus.asyncExit(0);
                })
                .onFailure().invoke(err -> {
                    log.error("Backfill FAILED", err);
                    Quarkus.asyncExit(1);
                })
                .subscribe().with(item -> {}, failure -> {});
    }

    private Instant fromInstant() {
        return LocalDate.parse(fromDate).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Uni<Void> backfillOrders(Set<String> domains) {
        if (!domains.contains("order")) {
            return Uni.createFrom().voidItem();
        }
        ReactiveMongoDatabase database = mongoClient.getDatabase(databaseName);
        Document filter = new Document("deletedAt", null)
                .append("createdAt", new Document("$gte", fromInstant()));

        AtomicInteger count = new AtomicInteger(0);
        return database.getCollection("orders", Document.class).find(filter).collect().asList()
                .chain(docs -> {
                    if (docs.isEmpty()) {
                        return Uni.createFrom().voidItem();
                    }
                    StringBuilder batchSql = new StringBuilder(
                            "INSERT INTO ecommerce_stats.order_daily (event_id, occurred_at, order_id, merchant_id, status, total_amount, event_version) FORMAT TabSeparated\n");
                    for (Document row : docs) {
                        String orderId = hex(row.get("_id"));
                        String eventId = "backfill:order:" + orderId;
                        String occurredAt = ClickHouseClient.normalizeDateTime(toLocalDateTime(row.get("createdAt")));
                        batchSql.append(String.join("\t", eventId, occurredAt != null ? occurredAt : "",
                                orderId, hex(row.get("merchantId")),
                                "completed", String.valueOf(number(row.get("totalPrice"))),
                                String.valueOf(System.currentTimeMillis()))).append("\n");
                        count.incrementAndGet();
                    }
                    return clickhouse.execute(batchSql.toString());
                })
                .invoke(() -> log.info("Backfilled {} order records", count.get()));
    }

    private Uni<Void> backfillTransactions(Set<String> domains) {
        if (!domains.contains("transaction")) {
            return Uni.createFrom().voidItem();
        }
        ReactiveMongoDatabase database = mongoClient.getDatabase(databaseName);
        Document filter = new Document("deletedAt", null)
                .append("createdAt", new Document("$gte", fromInstant()));

        AtomicInteger count = new AtomicInteger(0);
        return database.getCollection("transactions", Document.class).find(filter).collect().asList()
                .chain(docs -> {
                    if (docs.isEmpty()) {
                        return Uni.createFrom().voidItem();
                    }
                    StringBuilder batchSql = new StringBuilder(
                            "INSERT INTO ecommerce_stats.transaction_daily (event_id, occurred_at, transaction_id, order_id, merchant_id, payment_method, status, amount, event_version) FORMAT TabSeparated\n");
                    for (Document row : docs) {
                        String transactionId = hex(row.get("_id"));
                        String eventId = "backfill:transaction:" + transactionId;
                        String occurredAt = ClickHouseClient.normalizeDateTime(toLocalDateTime(row.get("createdAt")));
                        batchSql.append(String.join("\t", eventId, occurredAt != null ? occurredAt : "",
                                transactionId, hex(row.get("orderId")),
                                hex(row.get("merchantId")), String.valueOf(row.get("paymentMethod")),
                                String.valueOf(row.get("status")), String.valueOf(number(row.get("amount"))),
                                String.valueOf(System.currentTimeMillis()))).append("\n");
                        count.incrementAndGet();
                    }
                    return clickhouse.execute(batchSql.toString());
                })
                .invoke(() -> log.info("Backfilled {} transaction records", count.get()));
    }

    private static String hex(Object value) {
        if (value instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        return value == null ? "" : value.toString();
    }

    private static Object number(Object value) {
        return value == null ? 0 : value;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        return null;
    }
}
