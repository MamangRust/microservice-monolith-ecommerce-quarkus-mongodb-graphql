package com.sanedge.merchant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.common.enums.Status;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.merchant.domain.requests.FindAllMerchants;
import com.sanedge.merchant.entity.Merchant;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class MerchantRepositoryTest extends MongoTestBase {

    @Inject
    MerchantQueryRepository merchantQueryRepo;

    @Inject
    MerchantCommandRepository merchantCommandRepo;

    private static final String USER_ID = "507f1f77bcf86cd799439010";
    private static final String UNKNOWN_ID = "000000000000000000000000";

    private Uni<Merchant> persist(String name, String description, String address, String email, String phone,
            Status status) {
        Merchant m = new Merchant();
        m.setName(name);
        m.setDescription(description);
        m.setAddress(address);
        m.setContactEmail(email);
        m.setContactPhone(phone);
        m.setStatus(status);
        m.setUserId(new ObjectId(USER_ID));
        Instant now = Instant.now();
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        return merchantQueryRepo.persist(m);
    }

    private Uni<Merchant> persist(String name) {
        return persist(name, "desc " + name, "address " + name, name + "@mail.com", "08123456789", Status.SUCCESS);
    }

    private Uni<Void> clean() {
        return merchantQueryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllMerchants findAllReq(int page, int size, String search) {
        FindAllMerchants r = new FindAllMerchants();
        r.setPage(page);
        r.setPageSize(size);
        r.setSearch(search == null ? "" : search);
        return r;
    }

    @Test
    void testCreateAndFindByMerchantId() {
        clean()
                .chain(() -> persist("Toko Makmur"))
                .chain(m -> merchantQueryRepo.findMerchantById(m.getMerchantId()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Toko Makmur");
                    assertThat(found.getStatus()).isEqualTo(Status.SUCCESS);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantById_ReturnsNullIfNotFound() {
        clean()
                .chain(() -> merchantQueryRepo.findMerchantById(UNKNOWN_ID))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantById_ReturnsNullIfTrashed() {
        clean()
                .chain(() -> persist("Toko Sementara"))
                .chain(m -> merchantCommandRepo.trashed(m.getMerchantId())
                        .chain(() -> merchantQueryRepo.findMerchantById(m.getMerchantId())))
                .invoke(result -> assertThat(result).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testExistsByName_ReturnsTrue() {
        clean()
                .chain(() -> persist("UniqueMart"))
                .chain(() -> merchantQueryRepo.existsByName("UniqueMart"))
                .invoke(exists -> assertThat(exists).isTrue())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testExistsByName_ReturnsFalse() {
        clean()
                .chain(() -> merchantQueryRepo.existsByName("GhostStore"))
                .invoke(exists -> assertThat(exists).isFalse())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashed() {
        clean()
                .chain(() -> persist("Trash Test"))
                .chain(m -> merchantCommandRepo.trashed(m.getMerchantId()))
                .invoke(m -> {
                    assertThat(m).isNotNull();
                    assertThat(m.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashed_ReturnsNullIfAlreadyTrashed() {
        clean()
                .chain(() -> persist("Double Trash"))
                .chain(m -> merchantCommandRepo.trashed(m.getMerchantId())
                        .chain(() -> merchantCommandRepo.trashed(m.getMerchantId())))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashed_ReturnsNullIfNotFound() {
        clean()
                .chain(() -> merchantCommandRepo.trashed(UNKNOWN_ID))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestore() {
        clean()
                .chain(() -> persist("Restore Me"))
                .chain(m -> merchantCommandRepo.trashed(m.getMerchantId())
                        .chain(() -> merchantCommandRepo.restore(m.getMerchantId())))
                .invoke(m -> {
                    assertThat(m).isNotNull();
                    assertThat(m.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestore_ReturnsNullIfNotTrashed() {
        clean()
                .chain(() -> persist("Active Only"))
                .chain(m -> merchantCommandRepo.restore(m.getMerchantId()))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestore_ReturnsNullIfNotFound() {
        clean()
                .chain(() -> merchantCommandRepo.restore(UNKNOWN_ID))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        clean()
                .chain(() -> persist("Delete Perm"))
                .chain(m -> merchantCommandRepo.deletePermanent(m.getMerchantId())
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return merchantQueryRepo.findMerchantById(m.getMerchantId());
                        }))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent_ReturnsNullIfNotFound() {
        clean()
                .chain(() -> merchantCommandRepo.deletePermanent(UNKNOWN_ID))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        clean()
                .chain(() -> persist("A").chain(m -> merchantCommandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> persist("B").chain(m -> merchantCommandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> merchantCommandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> merchantQueryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        clean()
                .chain(() -> persist("Trash1").chain(m -> merchantCommandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> persist("Trash2").chain(m -> merchantCommandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> persist("Active1")) // tetap aktif
                .chain(() -> merchantCommandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> merchantQueryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testUpdateStatus_Valid() {
        clean()
                .chain(() -> persist("Change Status"))
                .chain(m -> merchantCommandRepo.updateStatus(m.getMerchantId(), "PENDING")
                        .chain(() -> merchantQueryRepo.findMerchantById(m.getMerchantId())))
                .invoke(m -> assertThat(m.getStatus()).isEqualTo(Status.PENDING))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testUpdateStatus_InvalidStatus() {
        clean()
                .chain(() -> persist("Invalid Status"))
                .chain(m -> merchantCommandRepo.updateStatus(m.getMerchantId(), "INVALID"))
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testUpdateStatus_MerchantNotFound() {
        clean()
                .chain(() -> merchantCommandRepo.updateStatus(UNKNOWN_ID, "ACTIVE"))
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindActiveMerchants() {
        clean()
                .chain(() -> persist("Active1"))
                .chain(() -> persist("Active2"))
                .chain(() -> merchantQueryRepo.findActiveMerchants(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedMerchants() {
        clean()
                .chain(() -> persist("ToTrash").chain(m -> merchantCommandRepo.trashed(m.getMerchantId()).replaceWithVoid()))
                .chain(() -> persist("KeepActive"))
                .chain(() -> merchantQueryRepo.findTrashedMerchants(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantsWithSearch() {
        clean()
                .chain(() -> persist("Apple Store", "Sells gadgets", "123 Main St", "apple@mail.com", "1111",
                        Status.SUCCESS))
                .chain(() -> persist("Banana Shop", "Food market", "456 Oak Ave", "banana@mail.com", "2222",
                        Status.SUCCESS))
                .chain(() -> persist("Tech Corner", "Gadget repair", "789 Pine Rd", "tech@mail.com", "3333",
                        Status.SUCCESS))
                .chain(() -> merchantQueryRepo.findMerchants(findAllReq(1, 10, "gadget")))
                .invoke(page -> {
                    assertThat(page.getTotalRecords()).isEqualTo(2);
                    assertThat(page.getData()).extracting(Merchant::getName)
                            .containsExactlyInAnyOrder("Apple Store", "Tech Corner");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindMerchantsWithPagination() {
        clean()
                .chain(() -> persist("M1"))
                .chain(() -> persist("M2"))
                .chain(() -> persist("M3"))
                .chain(() -> persist("M4"))
                .chain(() -> persist("M5"))
                .chain(() -> merchantQueryRepo.findMerchants(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> merchantQueryRepo.findMerchants(findAllReq(2, 2, "")))
                .invoke(page2 -> assertThat(page2.getData()).hasSize(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByName() {
        clean()
                .chain(() -> persist("Exact Match"))
                .chain(() -> merchantQueryRepo.findByName("Exact Match"))
                .invoke(m -> {
                    assertThat(m).isNotNull();
                    assertThat(m.getName()).isEqualTo("Exact Match");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByName_NotFound() {
        clean()
                .chain(() -> merchantQueryRepo.findByName("No Such Name"))
                .invoke(m -> assertThat(m).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByUserId() {
        clean()
                .chain(() -> {
                    Merchant m = new Merchant();
                    m.setName("User Merchant");
                    m.setUserId(new ObjectId(USER_ID));
                    m.setStatus(Status.SUCCESS);
                    Instant now = Instant.now();
                    m.setCreatedAt(now);
                    m.setUpdatedAt(now);
                    return merchantQueryRepo.persist(m);
                })
                .chain(() -> merchantQueryRepo.findByUserId(USER_ID))
                .invoke(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).getName()).isEqualTo("User Merchant");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByUserId_Null() {
        clean()
                .chain(() -> merchantQueryRepo.findByUserId(null))
                .invoke(list -> assertThat(list).isEmpty())
                .replaceWithVoid().await().indefinitely();
    }
}
