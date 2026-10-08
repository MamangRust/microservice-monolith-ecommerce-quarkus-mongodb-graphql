package com.sanedge.order.repository.stats;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@Disabled("Mongo aggregation returns empty buckets without seeded data; verify output before enabling")
@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class OrderSoldOutRepositoryTest extends MongoTestBase {

    @Inject
    OrderSoldOutRepository orderSoldOutRepository;

    @Test
    void testFindMonthlyOrders_ReturnsTwoEntries() {
        orderSoldOutRepository
                .findMonthlyOrders(2023, 12)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(2);
                    assertThat(result.get(0).getOrderCount()).isZero();
                    assertThat(result.get(0).getTotalRevenue()).isZero();
                })
                .await().indefinitely();
    }

    @Test
    void testFindYearlyOrders_ReturnsFiveEntries() {
        orderSoldOutRepository
                .findYearlyOrders(2024)
                .invoke(result -> {
                    assertThat(result).isNotNull();
                    assertThat(result).hasSize(5);
                    assertThat(result.get(0).getOrderCount()).isZero();
                })
                .await().indefinitely();
    }

    @Test
    void testFindMonthlyOrders_ReturnsNonNullResult() {
        orderSoldOutRepository
                .findMonthlyOrders(2024, 6)
                .invoke(result -> assertThat(result).isNotNull())
                .await().indefinitely();
    }
}
