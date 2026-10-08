package com.sanedge.merchant_detail.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.merchant_detail.entity.MerchantDetail;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class MerchantDetailRepositoryTest extends MongoTestBase {

    private static final String MERCHANT_ID = "507f1f77bcf86cd799439021";

    @Inject
    MerchantDetailQueryRepository merchantDetailQueryRepo;

    @Inject
    MerchantDetailCommandRepository merchantDetailCommandRepo;

    private Uni<MerchantDetail> createAndPersistDetail(String merchantId, String displayName) {
        MerchantDetail detail = new MerchantDetail();
        detail.setMerchantId(new ObjectId(merchantId));
        detail.setDisplayName(displayName);
        detail.setCoverImageUrl("http://example.com/cover.jpg");
        detail.setLogoUrl("http://example.com/logo.jpg");
        detail.setShortDescription("Short desc for " + displayName);
        detail.setWebsiteUrl("http://" + displayName.toLowerCase().replace(" ", "") + ".com");
        return merchantDetailQueryRepo.persist(detail).replaceWith(detail);
    }

    @Test
    void testCreateAndFindById() {
        createAndPersistDetail(MERCHANT_ID, "Toko Sejahtera")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getDisplayName()).isEqualTo("Toko Sejahtera");
                })
                .chain(saved -> merchantDetailQueryRepo.findById(saved.id.toHexString()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getWebsiteUrl()).contains("tokosejahtera");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashMerchantDetail() {
        createAndPersistDetail(MERCHANT_ID, "Toko Trash")
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> merchantDetailCommandRepo.trashed(saved.id.toHexString()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashMerchantDetailReturnsNullIfAlreadyTrashed() {
        createAndPersistDetail(MERCHANT_ID, "Toko Trash 2")
                .chain(saved -> merchantDetailCommandRepo.trashed(saved.id.toHexString())
                        .chain(ignored -> merchantDetailCommandRepo.trashed(saved.id.toHexString())))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashMerchantDetailReturnsNullIfNotFound() {
        merchantDetailCommandRepo.trashed("507f1f77bcf86cd799439099")
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreMerchantDetail() {
        createAndPersistDetail(MERCHANT_ID, "Toko Restore")
                .chain(saved -> merchantDetailCommandRepo.trashed(saved.id.toHexString())
                        .chain(ignored -> merchantDetailCommandRepo.restore(saved.id.toHexString())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreMerchantDetailReturnsNullIfNotTrashed() {
        createAndPersistDetail(MERCHANT_ID, "Toko Restore 2")
                .chain(saved -> merchantDetailCommandRepo.restore(saved.id.toHexString()))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        createAndPersistDetail(MERCHANT_ID, "Toko Delete")
                .chain(saved -> merchantDetailCommandRepo.trashed(saved.id.toHexString())
                        .chain(ignored -> merchantDetailCommandRepo.deletePermanent(saved.id.toHexString()))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return merchantDetailQueryRepo.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        createAndPersistDetail(MERCHANT_ID, "Toko Delete 2")
                .chain(saved -> merchantDetailCommandRepo.deletePermanent(saved.id.toHexString())
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return merchantDetailQueryRepo.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistDetail(MERCHANT_ID, "Bulk Restore 1"),
                        createAndPersistDetail(MERCHANT_ID, "Bulk Restore 2"),
                        createAndPersistDetail(MERCHANT_ID, "Bulk Restore 3"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        merchantDetailCommandRepo.trashed(tuple.getItem1().id.toHexString()),
                        merchantDetailCommandRepo.trashed(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> merchantDetailCommandRepo.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    merchantDetailQueryRepo.findById(tuple.getItem1().id.toHexString()),
                                    merchantDetailQueryRepo.findById(tuple.getItem2().id.toHexString()),
                                    merchantDetailQueryRepo.findById(tuple.getItem3().id.toHexString()))
                                    .andCollectFailures();
                        }))
                .invoke(fetched -> {
                    assertThat(fetched.get(0).getDeletedAt()).isNull();
                    assertThat(fetched.get(1).getDeletedAt()).isNull();
                    assertThat(fetched.get(2).getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistDetail(MERCHANT_ID, "Bulk Del 1"),
                        createAndPersistDetail(MERCHANT_ID, "Bulk Del 2"),
                        createAndPersistDetail(MERCHANT_ID, "Bulk Del 3"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        merchantDetailCommandRepo.trashed(tuple.getItem1().id.toHexString()),
                        merchantDetailCommandRepo.trashed(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> merchantDetailCommandRepo.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    merchantDetailQueryRepo.findById(tuple.getItem1().id.toHexString()),
                                    merchantDetailQueryRepo.findById(tuple.getItem2().id.toHexString()),
                                    merchantDetailQueryRepo.findById(tuple.getItem3().id.toHexString()))
                                    .andCollectFailures();
                        }))
                .invoke(fetched -> {
                    assertThat(fetched.get(0)).isNull();
                    assertThat(fetched.get(1)).isNull();
                    assertThat(fetched.get(2)).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }
}
