package com.sanedge.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.product.domain.requests.FindAllProductByMerchantRequest;
import com.sanedge.product.entity.Product;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class ProductRepositoryTest extends MongoTestBase {

    private static final String MERCHANT_1 = "507f1f77bcf86cd799439011";
    private static final String MERCHANT_2 = "507f1f77bcf86cd799439012";
    private static final String CATEGORY_1 = "507f1f77bcf86cd799439021";

    @Inject
    ProductQueryRepository productQueryRepo;
    @Inject
    ProductCommandRepository productCommandRepo;

    private Uni<Product> createAndPersistProduct(String name, String merchantId) {
        Product p = new Product();
        p.setMerchantId(new ObjectId(merchantId));
        p.setCategoryId(new ObjectId(CATEGORY_1));
        p.setName(name);
        p.setDescription("Test description for " + name);
        p.setPrice(100000);
        p.setCountInStock(10);
        p.setBrand("TestBrand");
        p.setWeight(500);
        p.setRating(4.5f);
        p.setSlugProduct(name.toLowerCase().replace(" ", "-"));
        p.setImageProduct("http://example.com/" + name + ".jpg");
        Instant now = Instant.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return productQueryRepo.persist(p).replaceWith(p);
    }

    @Test
    void testCreateAndFindById() {
        createAndPersistProduct("Phone", MERCHANT_1)
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getName()).isEqualTo("Phone");
                })
                .chain(saved -> productQueryRepo.findById(saved.id.toHexString()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Phone");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashProduct() {
        createAndPersistProduct("ToTrash", MERCHANT_1)
                .chain(saved -> productCommandRepo.trashed(saved.id.toHexString()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashReturnsNullIfAlreadyTrashed() {
        createAndPersistProduct("TrashTwice", MERCHANT_1)
                .chain(saved -> productCommandRepo.trashed(saved.id.toHexString())
                        .chain(ignored -> productCommandRepo.trashed(saved.id.toHexString())))
                .invoke(second -> assertThat(second).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreProduct() {
        createAndPersistProduct("ToRestore", MERCHANT_1)
                .chain(saved -> productCommandRepo.trashed(saved.id.toHexString())
                        .chain(ignored -> productCommandRepo.restore(saved.id.toHexString())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        createAndPersistProduct("ToDelete", MERCHANT_1)
                .chain(saved -> productCommandRepo.trashed(saved.id.toHexString())
                        .chain(ignored -> productCommandRepo.deletePermanent(saved.id.toHexString()))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return productQueryRepo.findById(saved.id.toHexString());
                        }))
                .invoke(check -> assertThat(check).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindProductsByMerchant() {
        Uni.join().all(
                createAndPersistProduct("Phone1", MERCHANT_1),
                createAndPersistProduct("Phone2", MERCHANT_2))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllProductByMerchantRequest req = new FindAllProductByMerchantRequest();
                    req.setMerchantId(MERCHANT_1);
                    req.setCategoryId("");
                    req.setMinPrice(0);
                    req.setMaxPrice(999999999);
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("");
                    return productQueryRepo.findByMerchant(req);
                })
                .invoke(result -> assertThat(result.getData()).isNotEmpty())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testAdjustStockNeverOversells() {
        createAndPersistProduct("Stocked", MERCHANT_1)
                .invoke(saved -> saved.setCountInStock(1))
                .chain(saved -> productQueryRepo.update(saved))
                .chain(saved -> productCommandRepo.adjustStock(saved.id.toHexString(), -1)
                        .chain(adjusted -> {
                            assertThat(adjusted).isNotNull();
                            assertThat(adjusted.getCountInStock()).isZero();
                            return productCommandRepo.adjustStock(saved.id.toHexString(), -1);
                        }))
                .invoke(rejected -> assertThat(rejected).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        productCommandRepo.restoreAllDeleted()
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        productCommandRepo.deleteAllDeleted()
                .invoke(result -> assertThat(result).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }
}
