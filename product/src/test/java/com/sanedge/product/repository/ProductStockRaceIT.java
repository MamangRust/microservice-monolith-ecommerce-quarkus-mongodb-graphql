package com.sanedge.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.product.entity.Product;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

/**
 * Resilience integration test for the atomic stock guard in
 * {@link ProductCommandRepository#adjustStock}: with a real MongoDB, many
 * concurrent decrements must never drive {@code countInStock} below zero.
 *
 * <p>The concurrent statements use the reactive MongoDB collection directly.
 * This exercises the same atomic guarded update predicate used by
 * {@code adjustStock} (a single {@code $inc} whose filter requires
 * {@code countInStock >= 1}).</p>
 */
@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class ProductStockRaceIT extends MongoTestBase {

    private static final String MERCHANT_1 = "507f1f77bcf86cd799439011";
    private static final String CATEGORY_1 = "507f1f77bcf86cd799439021";

    @Inject
    ProductQueryRepository productQueryRepo;

    @Inject
    ProductCommandRepository productCommandRepo;

    private Uni<Product> persistProduct(int stock, String name) {
        Product p = new Product();
        p.setMerchantId(new ObjectId(MERCHANT_1));
        p.setCategoryId(new ObjectId(CATEGORY_1));
        p.setName(name);
        p.setDescription("Stock race IT product");
        p.setPrice(100000);
        p.setCountInStock(stock);
        p.setBrand("RaceBrand");
        p.setWeight(100);
        p.setRating(4.5f);
        p.setSlugProduct(name);
        p.setImageProduct("http://example.com/" + name + ".jpg");
        Instant now = Instant.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return productQueryRepo.persist(p).replaceWith(p);
    }

    private Uni<Long> decrement(ObjectId id) {
        return productCommandRepo.mongoCollection().updateOne(
                Filters.and(
                        Filters.eq("_id", id),
                        Filters.eq("deletedAt", null),
                        Filters.gte("countInStock", 1)),
                Updates.inc("countInStock", -1))
                .map(result -> result.getModifiedCount());
    }

    private Uni<Integer> currentStock(ObjectId id) {
        return productCommandRepo.mongoCollection().find(Filters.eq("_id", id)).collect().first()
                .map(Product::getCountInStock);
    }

    @Test
    void concurrentDecrementsNeverOversell() {
        Product saved = persistProduct(2, "race-product-a").await().indefinitely();
        ObjectId id = saved.id;

        List<Uni<Long>> calls = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            calls.add(decrement(id));
        }

        long successes = Uni.combine().all().unis(calls).combinedWith(results -> {
            long ok = 0;
            for (Object result : results) {
                if (result instanceof Long && ((Long) result) == 1L) {
                    ok++;
                }
            }
            return ok;
        }).await().indefinitely();

        assertThat(successes).isEqualTo(2);
        assertThat(currentStock(id).await().indefinitely()).isZero();
    }

    @Test
    void rejectedDecrementDoesNotChangeStock() {
        Product saved = persistProduct(1, "race-product-b").await().indefinitely();
        ObjectId id = saved.id;

        assertThat(decrement(id).await().indefinitely()).isEqualTo(1L);
        assertThat(decrement(id).await().indefinitely()).isZero();
        assertThat(currentStock(id).await().indefinitely()).isZero();
    }
}
