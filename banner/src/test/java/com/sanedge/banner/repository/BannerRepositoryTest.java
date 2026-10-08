package com.sanedge.banner.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.sanedge.banner.domain.requests.FindAllBannerRequest;
import com.sanedge.banner.entity.Banner;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class BannerRepositoryTest extends MongoTestBase {

    @Inject
    BannerQueryRepository bannerQueryRepo;

    @Inject
    BannerCommandRepository bannerCommandRepo;

    private Uni<Banner> createAndPersistBanner(String name, boolean isActive) {
        Banner banner = new Banner();
        banner.setName(name);
        banner.setStartDate(LocalDate.parse("2025-01-01"));
        banner.setEndDate(LocalDate.parse("2025-12-31"));
        banner.setStartTime(LocalTime.parse("00:00:00"));
        banner.setEndTime(LocalTime.parse("23:59:59"));
        banner.setIsActive(isActive);
        return bannerQueryRepo.persist(banner);
    }

    private Uni<Banner> createAndPersistBannerWithSchedule(String name, boolean isActive,
            String startDate, String endDate, String startTime, String endTime) {
        Banner banner = new Banner();
        banner.setName(name);
        banner.setStartDate(LocalDate.parse(startDate));
        banner.setEndDate(LocalDate.parse(endDate));
        banner.setStartTime(LocalTime.parse(startTime));
        banner.setEndTime(LocalTime.parse(endTime));
        banner.setIsActive(isActive);
        return bannerQueryRepo.persist(banner);
    }

    @Test
    void testCreateAndFindById() {
        createAndPersistBannerWithSchedule("Summer Sale", true,
                "2025-06-01", "2025-08-31", "08:00:00", "22:00:00")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getName()).isEqualTo("Summer Sale");
                    assertThat(saved.getIsActive()).isTrue();
                    assertThat(saved.getStartDate()).isEqualTo(LocalDate.parse("2025-06-01"));
                    assertThat(saved.getEndDate()).isEqualTo(LocalDate.parse("2025-08-31"));
                    assertThat(saved.getStartTime()).isEqualTo(LocalTime.parse("08:00:00"));
                    assertThat(saved.getEndTime()).isEqualTo(LocalTime.parse("22:00:00"));
                })
                .chain(saved -> bannerQueryRepo.findById(saved.id.toHexString()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Summer Sale");
                    assertThat(found.getStartDate()).isEqualTo(LocalDate.parse("2025-06-01"));
                    assertThat(found.getEndDate()).isEqualTo(LocalDate.parse("2025-08-31"));
                    assertThat(found.getStartTime()).isEqualTo(LocalTime.parse("08:00:00"));
                    assertThat(found.getEndTime()).isEqualTo(LocalTime.parse("22:00:00"));
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByName() {
        createAndPersistBanner("Flash Sale", true)
                .chain(ignored -> bannerQueryRepo.findByName("Flash Sale"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Flash Sale");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByNameCaseInsensitive() {
        createAndPersistBanner("Mega Sale", true)
                .chain(ignored -> bannerQueryRepo.findByName("MEGA SALE"))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Mega Sale");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByNameReturnsNullWhenNotFound() {
        bannerQueryRepo.findByName("Nonexistent Banner")
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByNameExcludesTrashed() {
        createAndPersistBanner("Trashed Banner", true)
                .chain(saved -> bannerCommandRepo.trash(saved.id.toHexString())
                        .chain(ignored -> bannerQueryRepo.findByName("Trashed Banner")))
                .invoke(found -> assertThat(found).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        bannerQueryRepo.findById("507f1f77bcf86cd799439011")
                .invoke(notFound -> assertThat(notFound).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashBanner() {
        createAndPersistBanner("Trash Me", true)
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> bannerCommandRepo.trash(saved.id.toHexString()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashBannerReturnsNullIfAlreadyTrashed() {
        createAndPersistBanner("Trash Me Twice", true)
                .chain(saved -> bannerCommandRepo.trash(saved.id.toHexString())
                        .chain(ignored -> bannerCommandRepo.trash(saved.id.toHexString())))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashBannerReturnsNullIfNotFound() {
        bannerCommandRepo.trash("507f1f77bcf86cd799439099")
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreBanner() {
        createAndPersistBanner("Restore Me", true)
                .chain(saved -> bannerCommandRepo.trash(saved.id.toHexString())
                        .chain(ignored -> bannerCommandRepo.restore(saved.id.toHexString())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreBannerReturnsNullIfNotTrashed() {
        createAndPersistBanner("Restore Me Not", true)
                .chain(saved -> bannerCommandRepo.restore(saved.id.toHexString()))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreBannerReturnsNullIfNotFound() {
        bannerCommandRepo.restore("507f1f77bcf86cd799439099")
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        createAndPersistBanner("Delete Permanent", true)
                .chain(saved -> bannerCommandRepo.trash(saved.id.toHexString())
                        .chain(ignored -> bannerCommandRepo.deletePermanent(saved.id.toHexString()))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return bannerQueryRepo.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        createAndPersistBanner("Delete Permanent Fail", true)
                .chain(saved -> bannerCommandRepo.deletePermanent(saved.id.toHexString())
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return bannerQueryRepo.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentReturnsNullIfNotFound() {
        bannerCommandRepo.deletePermanent("507f1f77bcf86cd799439099")
                .invoke(deleted -> assertThat(deleted).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistBanner("Bulk Restore 1", true),
                        createAndPersistBanner("Bulk Restore 2", true),
                        createAndPersistBanner("Bulk Restore 3", true))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        bannerCommandRepo.trash(tuple.getItem1().id.toHexString()),
                        bannerCommandRepo.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> bannerCommandRepo.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    bannerQueryRepo.findById(tuple.getItem1().id.toHexString()),
                                    bannerQueryRepo.findById(tuple.getItem2().id.toHexString()),
                                    bannerQueryRepo.findById(tuple.getItem3().id.toHexString()))
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
    void testRestoreAllDeletedReturnsFalseWhenNoTrashed() {
        createAndPersistBanner("No Trashed Here", true)
                .chain(ignored -> bannerCommandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistBanner("Bulk Delete 1", true),
                        createAndPersistBanner("Bulk Delete 2", true),
                        createAndPersistBanner("Bulk Delete 3", true))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        bannerCommandRepo.trash(tuple.getItem1().id.toHexString()),
                        bannerCommandRepo.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> bannerCommandRepo.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    bannerQueryRepo.findById(tuple.getItem1().id.toHexString()),
                                    bannerQueryRepo.findById(tuple.getItem2().id.toHexString()),
                                    bannerQueryRepo.findById(tuple.getItem3().id.toHexString()))
                                    .andCollectFailures();
                        }))
                .invoke(fetched -> {
                    assertThat(fetched.get(0)).isNull();
                    assertThat(fetched.get(1)).isNull();
                    assertThat(fetched.get(2)).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeletedReturnsFalseWhenNoTrashed() {
        createAndPersistBanner("No Trashed To Delete", true)
                .chain(ignored -> bannerCommandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isFalse())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindBannersExcludesTrashed() {
        Uni.combine().all()
                .unis(createAndPersistBanner("Active Banner", true),
                        createAndPersistBanner("Trashed Banner", true))
                .asTuple()
                .chain(tuple -> bannerCommandRepo.trash(tuple.getItem2().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    return bannerQueryRepo.findBanners(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Active Banner");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindActiveBanners() {
        Uni.combine().all()
                .unis(createAndPersistBanner("Active One", true),
                        createAndPersistBanner("Inactive One", false),
                        createAndPersistBanner("Trashed Active", true))
                .asTuple()
                .chain(tuple -> bannerCommandRepo.trash(tuple.getItem3().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    return bannerQueryRepo.findActiveBanners(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Active One");
                    assertThat(result.getData().get(0).getIsActive()).isTrue();
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedBanners() {
        Uni.combine().all()
                .unis(createAndPersistBanner("Trashed Only", true),
                        createAndPersistBanner("Not Trashed", true))
                .asTuple()
                .chain(tuple -> bannerCommandRepo.trash(tuple.getItem1().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    return bannerQueryRepo.findTrashedBanners(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Only");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindBannersWithSearchKeyword() {
        Uni.join().all(
                createAndPersistBanner("Summer Promotion", true),
                createAndPersistBanner("Summer Clearance", true),
                createAndPersistBanner("Winter Sale", true))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("Summer");
                    return bannerQueryRepo.findBanners(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindActiveBannersWithSearchKeyword() {
        Uni.join().all(
                createAndPersistBanner("Active Flash", true),
                createAndPersistBanner("Active Deal", true),
                createAndPersistBanner("Inactive Flash", false))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("Flash");
                    return bannerQueryRepo.findActiveBanners(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Active Flash");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedBannersWithSearchKeyword() {
        Uni.combine().all()
                .unis(createAndPersistBanner("Trashed Promo", true),
                        createAndPersistBanner("Trashed Discount", true),
                        createAndPersistBanner("Active Promo", true))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        bannerCommandRepo.trash(tuple.getItem1().id.toHexString()),
                        bannerCommandRepo.trash(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("Promo");
                    return bannerQueryRepo.findTrashedBanners(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Promo");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindBannersWithPagination() {
        Uni.join().all(
                createAndPersistBanner("Page Banner 1", true),
                createAndPersistBanner("Page Banner 2", true),
                createAndPersistBanner("Page Banner 3", true),
                createAndPersistBanner("Page Banner 4", true),
                createAndPersistBanner("Page Banner 5", true))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllBannerRequest reqPage1 = new FindAllBannerRequest();
                    reqPage1.setPage(1);
                    reqPage1.setPageSize(2);
                    return bannerQueryRepo.findBanners(reqPage1);
                })
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> {
                    FindAllBannerRequest reqPage2 = new FindAllBannerRequest();
                    reqPage2.setPage(2);
                    reqPage2.setPageSize(2);
                    return bannerQueryRepo.findBanners(reqPage2)
                            .invoke(page2 -> {
                                assertThat(page2.getData()).hasSize(2);
                                assertThat(page2.getData().get(0).getName()).isNotIn(
                                        page1.getData().get(0).getName(),
                                        page1.getData().get(1).getName());
                            });
                })
                .chain(ignored -> {
                    FindAllBannerRequest reqPage3 = new FindAllBannerRequest();
                    reqPage3.setPage(3);
                    reqPage3.setPageSize(2);
                    return bannerQueryRepo.findBanners(reqPage3);
                })
                .invoke(page3 -> assertThat(page3.getData()).hasSize(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindBannersWithZeroPageDefaultsToFirstPage() {
        Uni.join().all(
                createAndPersistBanner("Default Page 1", true),
                createAndPersistBanner("Default Page 2", true),
                createAndPersistBanner("Default Page 3", true))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(0);
                    req.setPageSize(10);
                    return bannerQueryRepo.findBanners(req);
                })
                .invoke(result -> assertThat(result.getData()).hasSize(3))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindBannersWithZeroPageSizeDefaultsToTen() {
        Uni<Banner> createBanners = Uni.createFrom().voidItem()
                .chain(ignored -> createAndPersistBanner("Default Size 1", true))
                .chain(ignored -> createAndPersistBanner("Default Size 2", true))
                .chain(ignored -> createAndPersistBanner("Default Size 3", true))
                .chain(ignored -> createAndPersistBanner("Default Size 4", true))
                .chain(ignored -> createAndPersistBanner("Default Size 5", true))
                .chain(ignored -> createAndPersistBanner("Default Size 6", true))
                .chain(ignored -> createAndPersistBanner("Default Size 7", true))
                .chain(ignored -> createAndPersistBanner("Default Size 8", true))
                .chain(ignored -> createAndPersistBanner("Default Size 9", true))
                .chain(ignored -> createAndPersistBanner("Default Size 10", true))
                .chain(ignored -> createAndPersistBanner("Default Size 11", true))
                .chain(ignored -> createAndPersistBanner("Default Size 12", true))
                .chain(ignored -> createAndPersistBanner("Default Size 13", true))
                .chain(ignored -> createAndPersistBanner("Default Size 14", true))
                .chain(ignored -> createAndPersistBanner("Default Size 15", true));

        createBanners
                .chain(ignored -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(1);
                    req.setPageSize(0);
                    return bannerQueryRepo.findBanners(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(10);
                    assertThat(result.getTotalRecords()).isEqualTo(15);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindBannersWithEmptySearchReturnsAll() {
        Uni.join().all(
                createAndPersistBanner("Banner A", true),
                createAndPersistBanner("Banner B", true))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("");
                    return bannerQueryRepo.findBanners(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindBannersWithNullSearchReturnsAll() {
        Uni.join().all(
                createAndPersistBanner("Banner C", true),
                createAndPersistBanner("Banner D", true))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch(null);
                    return bannerQueryRepo.findBanners(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindBannersSearchReturnsEmptyWhenNoMatch() {
        createAndPersistBanner("Summer Sale", true)
                .chain(ignored -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("Winter");
                    return bannerQueryRepo.findBanners(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).isEmpty();
                    assertThat(result.getTotalRecords()).isZero();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashRestoreAndFindAgain() {
        createAndPersistBanner("Lifecycle Banner", true)
                .chain(saved -> bannerQueryRepo.findByName("Lifecycle Banner")
                        .invoke(found1 -> assertThat(found1).isNotNull())
                        .replaceWith(saved))
                .chain(saved -> bannerCommandRepo.trash(saved.id.toHexString())
                        .replaceWith(saved))
                .chain(saved -> bannerQueryRepo.findByName("Lifecycle Banner")
                        .invoke(found2 -> assertThat(found2).isNull())
                        .replaceWith(saved))
                .chain(saved -> {
                    FindAllBannerRequest req = new FindAllBannerRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    return bannerQueryRepo.findTrashedBanners(req)
                            .invoke(trashedResult -> assertThat(trashedResult.getData()).hasSize(1))
                            .replaceWith(saved);
                })
                .chain(saved -> bannerCommandRepo.restore(saved.id.toHexString())
                        .replaceWith(saved))
                .chain(saved -> bannerQueryRepo.findByName("Lifecycle Banner"))
                .invoke(found3 -> {
                    assertThat(found3).isNotNull();
                    assertThat(found3.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashAndPermanentDeleteLifecycle() {
        FindAllBannerRequest req = new FindAllBannerRequest();
        req.setPage(1);
        req.setPageSize(10);

        createAndPersistBanner("Full Lifecycle", true)
                .chain(saved -> bannerCommandRepo.trash(saved.id.toHexString())
                        .invoke(trashed -> assertThat(trashed).isNotNull())
                        .replaceWith(saved))
                .chain(saved -> bannerQueryRepo.findTrashedBanners(req)
                        .invoke(trashedResult -> assertThat(trashedResult.getData()).hasSize(1))
                        .replaceWith(saved))
                .chain(saved -> bannerCommandRepo.deletePermanent(saved.id.toHexString())
                        .invoke(deleted -> assertThat(deleted).isNotNull())
                        .replaceWith(saved))
                .chain(saved -> bannerQueryRepo.findTrashedBanners(req)
                        .invoke(afterDelete -> assertThat(afterDelete.getData()).isEmpty())
                        .replaceWith(saved))
                .chain(saved -> bannerQueryRepo.findById(saved.id.toHexString()))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid().await().indefinitely();
    }
}
