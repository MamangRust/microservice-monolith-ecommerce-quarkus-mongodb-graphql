package com.sanedge.merchant_business.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.merchant_business.domain.requests.FindAllMerchantRequest;
import com.sanedge.merchant_business.entity.MerchantBusinessInformation;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class MerchantBusinessRepositoryTest extends MongoTestBase {

    private static final String MERCHANT_ID = "507f1f77bcf86cd799439011";

    @Inject
    MerchantBusinessQueryRepository businessQueryRepo;

    @Inject
    MerchantBusinessCommandRepository businessCommandRepo;

    private Uni<String> persist(String businessType, String taxId, String websiteUrl) {
        MerchantBusinessInformation info = new MerchantBusinessInformation();
        info.setBusinessType(businessType);
        info.setTaxId(taxId);
        info.setWebsiteUrl(websiteUrl);

        info.setMerchantId(new ObjectId(MERCHANT_ID));
        return businessQueryRepo.persist(info).map(i -> i.id.toHexString());
    }

    private Uni<String> persist(String businessType) {
        return persist(businessType, "TAX-" + businessType, "http://" + businessType.toLowerCase() + ".com");
    }

    private Uni<Void> clean() {
        return businessQueryRepo.deleteAll().replaceWithVoid();
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
                .chain(() -> persist("Retail"))
                .chain(id -> businessQueryRepo.findById(id))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getBusinessType()).isEqualTo("Retail");
                    assertThat(found.getTaxId()).isEqualTo("TAX-Retail");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantBusinessInformationById() {
        clean()
                .chain(() -> persist("Wholesale"))
                .chain(id -> businessQueryRepo.findMerchantBusinessInformationById(id))
                .invoke(info -> {
                    assertThat(info).isNotNull();
                    assertThat(info.getBusinessType()).isEqualTo("Wholesale");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantBusinessInformationById_ReturnsNullIfNotFound() {
        clean()
                .chain(() -> businessQueryRepo.findMerchantBusinessInformationById("507f1f77bcf86cd799439099"))
                .invoke(opt -> assertThat(opt).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantBusinessInformationById_ReturnsNullIfTrashed() {
        clean()
                .chain(() -> persist("Old"))
                .chain(id -> businessCommandRepo.trashed(id)
                        .chain(() -> businessQueryRepo.findMerchantBusinessInformationById(id)))
                .invoke(opt -> assertThat(opt).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        clean()
                .chain(() -> businessQueryRepo.findById("507f1f77bcf86cd799439099"))
                .invoke(n -> assertThat(n).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashed() {
        clean()
                .chain(() -> persist("Trash Me"))
                .chain(id -> businessCommandRepo.trashed(id))
                .invoke(info -> {
                    assertThat(info).isNotNull();
                    assertThat(info.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashed_ReturnsNullIfAlreadyTrashed() {
        clean()
                .chain(() -> persist("Already Trashed"))
                .chain(id -> businessCommandRepo.trashed(id)
                        .chain(() -> businessCommandRepo.trashed(id)))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashed_ReturnsNullIfNotFound() {
        clean()
                .chain(() -> businessCommandRepo.trashed("507f1f77bcf86cd799439099"))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestore() {
        clean()
                .chain(() -> persist("Restore Me"))
                .chain(id -> businessCommandRepo.trashed(id)
                        .chain(() -> businessCommandRepo.restore(id)))
                .invoke(info -> {
                    assertThat(info).isNotNull();
                    assertThat(info.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestore_ReturnsNullIfNotTrashed() {
        clean()
                .chain(() -> persist("Active"))
                .chain(id -> businessCommandRepo.restore(id))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestore_ReturnsNullIfNotFound() {
        clean()
                .chain(() -> businessCommandRepo.restore("507f1f77bcf86cd799439099"))
                .invoke(a -> assertThat(a).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        clean()
                .chain(() -> persist("Delete Perm"))
                .chain(id -> businessCommandRepo.trashed(id)
                        .chain(() -> businessCommandRepo.deletePermanent(id))
                        .chain(deleted -> businessQueryRepo.findById(id)))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent_ReturnsNullIfNotTrashed() {
        clean()
                .chain(() -> persist("No Trash"))
                .chain(id -> businessCommandRepo.deletePermanent(id))
                .invoke(d -> assertThat(d).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        clean()
                .chain(() -> persist("A").chain(id -> businessCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("B").chain(id -> businessCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> businessCommandRepo.restoreAllDeleted())
                .invoke(r -> assertThat(r).isTrue())
                .chain(() -> businessQueryRepo.findActiveMerchantBusinessInformation(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        clean()
                .chain(() -> persist("T1").chain(id -> businessCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("T2").chain(id -> businessCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("Keep"))
                .chain(() -> businessCommandRepo.deleteAllDeleted())
                .invoke(r -> assertThat(r).isTrue())
                .chain(() -> businessQueryRepo.findActiveMerchantBusinessInformation(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindActiveMerchantBusinessInformation() {
        clean()
                .chain(() -> persist("Active1"))
                .chain(() -> persist("Active2"))
                .chain(() -> businessQueryRepo.findActiveMerchantBusinessInformation(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedMerchantBusinessInformation() {
        clean()
                .chain(() -> persist("T").chain(id -> businessCommandRepo.trashed(id).replaceWithVoid()))
                .chain(() -> persist("Keep"))
                .chain(() -> businessQueryRepo.findTrashedMerchantBusinessInformation(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantBusinessInformationWithSearch() {
        clean()
                .chain(() -> persist("Retail", "TAX-111", "http://retail.com"))
                .chain(() -> persist("Service", "TAX-222", "http://service.com"))
                .chain(() -> persist("Technology", "TAX-333", "http://tech.com"))
                .chain(() -> businessQueryRepo.findMerchantBusinessInformation(findAllReq(1, 10, "service")))
                .invoke(page -> {
                    assertThat(page.getTotalRecords()).isEqualTo(1);
                    assertThat(page.getData().get(0).getBusinessType()).isEqualTo("Service");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantBusinessInformationWithPagination() {
        clean()
                .chain(() -> persist("A"))
                .chain(() -> persist("B"))
                .chain(() -> persist("C"))
                .chain(() -> persist("D"))
                .chain(() -> persist("E"))
                .chain(() -> businessQueryRepo.findMerchantBusinessInformation(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> businessQueryRepo.findMerchantBusinessInformation(findAllReq(2, 2, "")))
                .invoke(page2 -> assertThat(page2.getData()).hasSize(2))
                .replaceWithVoid().await().indefinitely();
    }
}
