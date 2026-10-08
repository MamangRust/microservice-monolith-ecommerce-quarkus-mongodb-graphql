package com.sanedge.shipping_address.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.shipping_address.domain.requests.FindAllShippingAddress;
import com.sanedge.shipping_address.entity.ShippingAddress;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class ShippingAddressRepositoryTest extends MongoTestBase {

    @Inject
    ShippingAddressQueryRepository shippingAddressQueryRepo;

    @Inject
    ShippingAddressCommandRepository shippingAddressCommandRepo;

    private String orderIdHex(int orderId) {
        return String.format("%024x", orderId);
    }

    private Uni<ShippingAddress> createAndPersistShippingAddress(int orderId, String kota, String provinsi) {
        ShippingAddress address = new ShippingAddress();
        address.setOrderId(new ObjectId(orderIdHex(orderId)));
        address.setAlamat("Jl. Contoh " + kota);
        address.setKota(kota);
        address.setProvinsi(provinsi);
        address.setNegara("Indonesia");
        address.setCourier("JNE");
        address.setShippingMethod("REGULER");
        address.setShippingCost(15000);
        return shippingAddressQueryRepo.persist(address).replaceWith(address);
    }

    private FindAllShippingAddress buildFindAllRequest(int page, int pageSize, String search) {
        FindAllShippingAddress req = new FindAllShippingAddress();
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(search);
        return req;
    }

    @Test
    void testCreateAndFindById() {
        createAndPersistShippingAddress(1, "Jakarta Selatan", "DKI Jakarta")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getKota()).isEqualTo("Jakarta Selatan");
                })
                .chain(saved -> shippingAddressQueryRepo.findByIdNative(saved.id.toHexString()))
                .invoke(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().getKota()).isEqualTo("Jakarta Selatan");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByOrderId() {
        createAndPersistShippingAddress(42, "Bandung", "Jawa Barat")
                .chain(ignored -> shippingAddressQueryRepo.findByOrderId(orderIdHex(42)))
                .invoke(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().getProvinsi()).isEqualTo("Jawa Barat");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByOrderIdReturnsEmptyWhenNotFound() {
        shippingAddressQueryRepo.findByOrderId(orderIdHex(99999))
                .invoke(opt -> assertThat(opt).isEmpty())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashShippingAddress() {
        createAndPersistShippingAddress(2, "Surabaya", "Jawa Timur")
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> shippingAddressCommandRepo.trash(saved.id.toHexString()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashShippingAddressReturnsNullIfAlreadyTrashed() {
        createAndPersistShippingAddress(3, "Semarang", "Jawa Tengah")
                .chain(saved -> shippingAddressCommandRepo.trash(saved.id.toHexString())
                        .chain(ignored -> shippingAddressCommandRepo.trash(saved.id.toHexString())))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashShippingAddressReturnsNullIfNotFound() {
        shippingAddressCommandRepo.trash("507f1f77bcf86cd799439011")
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreShippingAddress() {
        createAndPersistShippingAddress(4, "Yogyakarta", "DI Yogyakarta")
                .chain(saved -> shippingAddressCommandRepo.trash(saved.id.toHexString())
                        .chain(ignored -> shippingAddressCommandRepo.restore(saved.id.toHexString())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreShippingAddressReturnsNullIfNotTrashed() {
        createAndPersistShippingAddress(5, "Malang", "Jawa Timur")
                .chain(saved -> shippingAddressCommandRepo.restore(saved.id.toHexString()))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        createAndPersistShippingAddress(6, "Medan", "Sumatera Utara")
                .chain(saved -> shippingAddressCommandRepo.trash(saved.id.toHexString())
                        .chain(ignored -> shippingAddressCommandRepo.deletePermanent(saved.id.toHexString()))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return shippingAddressQueryRepo.findByIdNative(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isEmpty())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        createAndPersistShippingAddress(7, "Makassar", "Sulawesi Selatan")
                .chain(saved -> shippingAddressCommandRepo.deletePermanent(saved.id.toHexString())
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return shippingAddressQueryRepo.findByIdNative(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isPresent())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistShippingAddress(10, "Bekasi", "Jawa Barat"),
                        createAndPersistShippingAddress(11, "Depok", "Jawa Barat"),
                        createAndPersistShippingAddress(12, "Tangerang", "Banten"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        shippingAddressCommandRepo.trash(tuple.getItem1().id.toHexString()),
                        shippingAddressCommandRepo.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> shippingAddressCommandRepo.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    shippingAddressQueryRepo.findByIdNative(tuple.getItem1().id.toHexString()),
                                    shippingAddressQueryRepo.findByIdNative(tuple.getItem2().id.toHexString()),
                                    shippingAddressQueryRepo.findByIdNative(tuple.getItem3().id.toHexString()))
                                    .andCollectFailures()
                                    .replaceWith(tuple);
                        }))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistShippingAddress(20, "Bogor", "Jawa Barat"),
                        createAndPersistShippingAddress(21, "Cirebon", "Jawa Barat"),
                        createAndPersistShippingAddress(22, "Garut", "Jawa Barat"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        shippingAddressCommandRepo.trash(tuple.getItem1().id.toHexString()),
                        shippingAddressCommandRepo.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> shippingAddressCommandRepo.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    shippingAddressQueryRepo.findByIdNative(tuple.getItem1().id.toHexString()),
                                    shippingAddressQueryRepo.findByIdNative(tuple.getItem2().id.toHexString()),
                                    shippingAddressQueryRepo.findByIdNative(tuple.getItem3().id.toHexString()))
                                    .andCollectFailures()
                                    .replaceWith(tuple);
                        }))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindShippingAddressesExcludesTrashed() {
        Uni.combine().all()
                .unis(createAndPersistShippingAddress(30, "Jakarta Pusat", "DKI Jakarta"),
                        createAndPersistShippingAddress(31, "Jakarta Utara", "DKI Jakarta"))
                .asTuple()
                .chain(tuple -> shippingAddressCommandRepo.trash(tuple.getItem2().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllShippingAddress req = buildFindAllRequest(1, 10, null);
                    return shippingAddressQueryRepo.findShippingAddresses(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getKota()).isEqualTo("Jakarta Pusat");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindActiveShippingAddresses() {
        Uni.combine().all()
                .unis(createAndPersistShippingAddress(32, "Solo", "Jawa Tengah"),
                        createAndPersistShippingAddress(33, "Palembang", "Sumatera Selatan"))
                .asTuple()
                .chain(tuple -> shippingAddressCommandRepo.trash(tuple.getItem2().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllShippingAddress req = buildFindAllRequest(1, 10, null);
                    return shippingAddressQueryRepo.findActiveShippingAddresses(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getKota()).isEqualTo("Solo");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedShippingAddresses() {
        Uni.combine().all()
                .unis(createAndPersistShippingAddress(34, "Denpasar", "Bali"),
                        createAndPersistShippingAddress(35, "Kuta", "Bali"))
                .asTuple()
                .chain(tuple -> shippingAddressCommandRepo.trash(tuple.getItem1().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllShippingAddress req = buildFindAllRequest(1, 10, null);
                    return shippingAddressQueryRepo.findTrashedShippingAddresses(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getKota()).isEqualTo("Denpasar");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindShippingAddressesWithSearchKeyword() {
        Uni.join().all(
                createAndPersistShippingAddress(40, "Bandung", "Jawa Barat"),
                createAndPersistShippingAddress(41, "Bekasi", "Jawa Barat"),
                createAndPersistShippingAddress(42, "Medan", "Sumatera Utara"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllShippingAddress req = buildFindAllRequest(1, 10, "Barat");
                    return shippingAddressQueryRepo.findShippingAddresses(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedShippingAddressesWithSearchKeyword() {
        Uni.combine().all()
                .unis(createAndPersistShippingAddress(43, "Samarinda", "Kalimantan Timur"),
                        createAndPersistShippingAddress(44, "Balikpapan", "Kalimantan Timur"),
                        createAndPersistShippingAddress(45, "Pontianak", "Kalimantan Barat"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        shippingAddressCommandRepo.trash(tuple.getItem1().id.toHexString()),
                        shippingAddressCommandRepo.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllShippingAddress req = buildFindAllRequest(1, 10, "Timur");
                    return shippingAddressQueryRepo.findTrashedShippingAddresses(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindShippingAddressesWithPagination() {
        Uni.join().all(
                createAndPersistShippingAddress(50, "Kota1", "Prov1"),
                createAndPersistShippingAddress(51, "Kota2", "Prov1"),
                createAndPersistShippingAddress(52, "Kota3", "Prov1"),
                createAndPersistShippingAddress(53, "Kota4", "Prov1"),
                createAndPersistShippingAddress(54, "Kota5", "Prov1"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllShippingAddress reqPage1 = buildFindAllRequest(1, 2, null);
                    return shippingAddressQueryRepo.findShippingAddresses(reqPage1);
                })
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> {
                    FindAllShippingAddress reqPage2 = buildFindAllRequest(2, 2, null);
                    return shippingAddressQueryRepo.findShippingAddresses(reqPage2)
                            .invoke(page2 -> {
                                assertThat(page2.getData()).hasSize(2);
                                assertThat(page2.getData().get(0).getKota()).isNotIn(
                                        page1.getData().get(0).getKota(),
                                        page1.getData().get(1).getKota());
                            });
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindShippingAddressesWithEmptySearchReturnsAll() {
        Uni.join().all(
                createAndPersistShippingAddress(60, "KotaA", "ProvA"),
                createAndPersistShippingAddress(61, "KotaB", "ProvB"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllShippingAddress req = buildFindAllRequest(1, 10, "");
                    return shippingAddressQueryRepo.findShippingAddresses(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindShippingAddressesWithNullSearchReturnsAll() {
        Uni.join().all(
                createAndPersistShippingAddress(62, "KotaC", "ProvC"),
                createAndPersistShippingAddress(63, "KotaD", "ProvD"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllShippingAddress req = buildFindAllRequest(1, 10, null);
                    return shippingAddressQueryRepo.findShippingAddresses(req);
                })
                .invoke(result -> assertThat(result.getData()).hasSize(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindShippingAddressesSearchReturnsEmptyWhenNoMatch() {
        createAndPersistShippingAddress(70, "Jakarta", "DKI")
                .chain(ignored -> {
                    FindAllShippingAddress req = buildFindAllRequest(1, 10, "TidakAda");
                    return shippingAddressQueryRepo.findShippingAddresses(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).isEmpty();
                    assertThat(result.getTotalRecords()).isZero();
                })
                .replaceWithVoid().await().indefinitely();
    }
}
