package com.sanedge.merchant_award.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.merchant_award.domain.requests.FindAllMerchantRequest;
import com.sanedge.merchant_award.entity.MerchantCertificationAndAward;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class MerchantAwardRepositoryTest extends MongoTestBase {

    private static final String MERCHANT_ID = "507f1f77bcf86cd799439011";

    @Inject
    MerchantAwardQueryRepository awardQueryRepo;

    @Inject
    MerchantAwardCommandRepository awardCommandRepo;

    private Uni<String> persist(String title, String description, String issuedBy, String certificateUrl) {
        MerchantCertificationAndAward award = new MerchantCertificationAndAward();
        award.setTitle(title);
        award.setDescription(description);
        award.setIssuedBy(issuedBy);
        award.setCertificateUrl(certificateUrl);
        award.setIssueDate(Instant.parse("2023-01-01T00:00:00Z"));
        award.setExpiryDate(Instant.parse("2025-01-01T00:00:00Z"));
        award.setMerchantId(new ObjectId(MERCHANT_ID));
        return awardQueryRepo.persist(award).map(a -> a.id.toHexString());
    }

    private Uni<String> persist(String title) {
        return persist(title, "Description " + title, "Issuer " + title, "http://cert/" + title);
    }

    private Uni<Void> clean() {
        return awardQueryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllMerchantRequest findAllReq(int page, int size, String search) {
        FindAllMerchantRequest r = new FindAllMerchantRequest();
        r.setPage(page);
        r.setPageSize(size);
        r.setSearch(search == null ? "" : search);
        return r;
    }

    @Test
    void testCreateAndFindById() {
        clean()
                .chain(() -> persist("ISO 9001"))
                .chain(id -> awardQueryRepo.findById(id))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getTitle()).isEqualTo("ISO 9001");
                    assertThat(found.getIssuedBy()).isEqualTo("Issuer ISO 9001");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantAwardById() {
        clean()
                .chain(() -> persist("Safety Award"))
                .chain(id -> awardQueryRepo.findMerchantAwardById(id))
                .invoke(award -> {
                    assertThat(award).isNotNull();
                    assertThat(award.getTitle()).isEqualTo("Safety Award");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantAwardById_ReturnsNullIfNotFound() {
        clean()
                .chain(() -> awardQueryRepo.findMerchantAwardById("507f1f77bcf86cd799439099"))
                .invoke(opt -> assertThat(opt).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantAwardById_ReturnsNullIfTrashed() {
        clean()
                .chain(() -> persist("Old Award"))
                .chain(id -> awardCommandRepo.trashed(id)
                        .chain(() -> awardQueryRepo.findMerchantAwardById(id)))
                .invoke(opt -> assertThat(opt).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        clean()
                .chain(() -> awardQueryRepo.findById("507f1f77bcf86cd799439099"))
                .invoke(n -> assertThat(n).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashed() {
        clean()
                .chain(() -> persist("Trash Me"))
                .chain(id -> awardCommandRepo.trashed(id))
                .invoke(award -> {
                    assertThat(award).isNotNull();
                    assertThat(award.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashed_ReturnsNullIfAlreadyTrashed() {
        clean()
                .chain(() -> persist("Already Trashed"))
                .chain(id -> awardCommandRepo.trashed(id)
                        .chain(() -> awardCommandRepo.trashed(id)))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashed_ReturnsNullIfNotFound() {
        clean()
                .chain(() -> awardCommandRepo.trashed("507f1f77bcf86cd799439099"))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestore() {
        clean()
                .chain(() -> persist("Restore Award"))
                .chain(id -> awardCommandRepo.trashed(id)
                        .chain(() -> awardCommandRepo.restore(id)))
                .invoke(award -> {
                    assertThat(award).isNotNull();
                    assertThat(award.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestore_ReturnsNullIfNotTrashed() {
        clean()
                .chain(() -> persist("Active Award"))
                .chain(id -> awardCommandRepo.restore(id))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestore_ReturnsNullIfNotFound() {
        clean()
                .chain(() -> awardCommandRepo.restore("507f1f77bcf86cd799439099"))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        clean()
                .chain(() -> persist("Delete Perm"))
                .chain(id -> awardCommandRepo.trashed(id)
                        .chain(() -> awardCommandRepo.deletePermanent(id))
                        .chain(deleted -> awardQueryRepo.findById(id)))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent_ReturnsNullIfNotTrashed() {
        clean()
                .chain(() -> persist("No Trash"))
                .chain(id -> awardCommandRepo.deletePermanent(id))
                .invoke(d -> assertThat(d).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        clean()
                .chain(() -> persist("A").chain(id -> awardCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("B").chain(id -> awardCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> awardCommandRepo.restoreAllDeleted())
                .invoke(r -> assertThat(r).isTrue())
                .chain(() -> awardQueryRepo.findActiveMerchantAwards(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        clean()
                .chain(() -> persist("T1").chain(id -> awardCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("T2").chain(id -> awardCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("Keep"))
                .chain(() -> awardCommandRepo.deleteAllDeleted())
                .invoke(r -> assertThat(r).isTrue())
                .chain(() -> awardQueryRepo.findActiveMerchantAwards(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindActiveMerchantAwards() {
        clean()
                .chain(() -> persist("Active1"))
                .chain(() -> persist("Active2"))
                .chain(() -> awardQueryRepo.findActiveMerchantAwards(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedMerchantAwards() {
        clean()
                .chain(() -> persist("T").chain(id -> awardCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("Keep"))
                .chain(() -> awardQueryRepo.findTrashedMerchantAwards(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantAwardsWithSearch() {
        clean()
                .chain(() -> persist("GoGreen", "Eco certification", "EcoOrg", "http://eco"))
                .chain(() -> persist("SafeWork", "Safety standard", "SafetyCo", "http://safe"))
                .chain(() -> persist("TechExcellence", "Tech innovation", "TechOrg", "http://tech"))
                .chain(() -> awardQueryRepo.findMerchantAwards(findAllReq(1, 10, "safety")))
                .invoke(page -> {
                    assertThat(page.getTotalRecords()).isEqualTo(1);
                    assertThat(page.getData().get(0).getTitle()).isEqualTo("SafeWork");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantAwardsWithPagination() {
        clean()
                .chain(() -> persist("A"))
                .chain(() -> persist("B"))
                .chain(() -> persist("C"))
                .chain(() -> persist("D"))
                .chain(() -> persist("E"))
                .chain(() -> awardQueryRepo.findMerchantAwards(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> awardQueryRepo.findMerchantAwards(findAllReq(2, 2, "")))
                .invoke(page2 -> assertThat(page2.getData()).hasSize(2))
                .replaceWithVoid().await().indefinitely();
    }
}
