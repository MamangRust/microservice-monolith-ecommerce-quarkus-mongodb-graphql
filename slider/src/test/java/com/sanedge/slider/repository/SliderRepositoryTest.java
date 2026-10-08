package com.sanedge.slider.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.slider.domain.requests.FindAllSliderRequest;
import com.sanedge.slider.entity.Slider;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class SliderRepositoryTest extends MongoTestBase {

    @Inject
    SliderQueryRepository sliderQueryRepo;

    @Inject
    SliderCommandRepository sliderCommandRepo;

    private Uni<Slider> createAndPersistSlider(String name) {
        Slider slider = new Slider();
        slider.setName(name);
        return sliderQueryRepo.persist(slider).replaceWith(slider);
    }

    @Test
    void testCreateAndFindById() {
        createAndPersistSlider("Main Banner")
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                    assertThat(saved.getName()).isEqualTo("Main Banner");
                })
                .chain(saved -> sliderQueryRepo.findById(saved.id.toHexString()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Main Banner");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashSlider() {
        createAndPersistSlider("Trash Me")
                .invoke(saved -> assertThat(saved.getDeletedAt()).isNull())
                .chain(saved -> sliderCommandRepo.trashed(saved.id.toHexString()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashSliderReturnsNullIfAlreadyTrashed() {
        createAndPersistSlider("Trash Me Twice")
                .chain(saved -> sliderCommandRepo.trashed(saved.id.toHexString())
                        .chain(ignored -> sliderCommandRepo.trashed(saved.id.toHexString())))
                .invoke(trashedAgain -> assertThat(trashedAgain).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashSliderReturnsNullIfNotFound() {
        sliderCommandRepo.trashed("507f1f77bcf86cd799439011")
                .invoke(trashed -> assertThat(trashed).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreSlider() {
        createAndPersistSlider("Restore Me")
                .chain(saved -> sliderCommandRepo.trashed(saved.id.toHexString())
                        .chain(ignored -> sliderCommandRepo.restore(saved.id.toHexString())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreSliderReturnsNullIfNotTrashed() {
        createAndPersistSlider("Restore Me Not")
                .chain(saved -> sliderCommandRepo.restore(saved.id.toHexString()))
                .invoke(restored -> assertThat(restored).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        createAndPersistSlider("Delete Permanent")
                .chain(saved -> sliderCommandRepo.trashed(saved.id.toHexString())
                        .chain(ignored -> sliderCommandRepo.deletePermanent(saved.id.toHexString()))
                        .chain(deleted -> {
                            assertThat(deleted).isNotNull();
                            return sliderQueryRepo.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        createAndPersistSlider("Delete Permanent Fail")
                .chain(saved -> sliderCommandRepo.deletePermanent(saved.id.toHexString())
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return sliderQueryRepo.findById(saved.id.toHexString());
                        }))
                .invoke(checkDb -> assertThat(checkDb).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        Uni.combine().all()
                .unis(createAndPersistSlider("Bulk Restore 1"),
                        createAndPersistSlider("Bulk Restore 2"),
                        createAndPersistSlider("Bulk Restore 3"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        sliderCommandRepo.trashed(tuple.getItem1().id.toHexString()),
                        sliderCommandRepo.trashed(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> sliderCommandRepo.restoreAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    sliderQueryRepo.findById(tuple.getItem1().id.toHexString()),
                                    sliderQueryRepo.findById(tuple.getItem2().id.toHexString()),
                                    sliderQueryRepo.findById(tuple.getItem3().id.toHexString()))
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
                .unis(createAndPersistSlider("Bulk Delete 1"),
                        createAndPersistSlider("Bulk Delete 2"),
                        createAndPersistSlider("Bulk Delete 3"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        sliderCommandRepo.trashed(tuple.getItem1().id.toHexString()),
                        sliderCommandRepo.trashed(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> sliderCommandRepo.deleteAllDeleted()
                        .chain(result -> {
                            assertThat(result).isTrue();
                            return Uni.join().all(
                                    sliderQueryRepo.findById(tuple.getItem1().id.toHexString()),
                                    sliderQueryRepo.findById(tuple.getItem2().id.toHexString()),
                                    sliderQueryRepo.findById(tuple.getItem3().id.toHexString()))
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
    void testFindSlidersExcludesTrashed() {
        Uni.combine().all()
                .unis(createAndPersistSlider("Active Slider"),
                        createAndPersistSlider("Trashed Slider"))
                .asTuple()
                .chain(tuple -> sliderCommandRepo.trashed(tuple.getItem2().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllSliderRequest req = new FindAllSliderRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    return sliderQueryRepo.findSliders(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Active Slider");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedSliders() {
        Uni.combine().all()
                .unis(createAndPersistSlider("Trashed Only"),
                        createAndPersistSlider("Not Trashed"))
                .asTuple()
                .chain(tuple -> sliderCommandRepo.trashed(tuple.getItem1().id.toHexString())
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllSliderRequest req = new FindAllSliderRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    return sliderQueryRepo.findTrashedSliders(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Only");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindSlidersWithSearchKeyword() {
        Uni.join().all(
                createAndPersistSlider("Summer Promo"),
                createAndPersistSlider("Summer Clearance"),
                createAndPersistSlider("Winter Sale"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllSliderRequest req = new FindAllSliderRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("Summer");
                    return sliderQueryRepo.findSliders(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedSlidersWithSearchKeyword() {
        Uni.combine().all()
                .unis(createAndPersistSlider("Trashed Promo"),
                        createAndPersistSlider("Trashed Discount"),
                        createAndPersistSlider("Active Promo"))
                .asTuple()
                .chain(tuple -> Uni.join().all(
                        sliderCommandRepo.trashed(tuple.getItem1().id.toHexString()),
                        sliderCommandRepo.trashed(tuple.getItem2().id.toHexString()))
                        .andCollectFailures()
                        .replaceWith(tuple))
                .chain(tuple -> {
                    FindAllSliderRequest req = new FindAllSliderRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("Promo");
                    return sliderQueryRepo.findTrashedSliders(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Trashed Promo");
                    assertThat(result.getTotalRecords()).isEqualTo(1);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindSlidersWithPagination() {
        Uni.join().all(
                createAndPersistSlider("Page Slider 1"),
                createAndPersistSlider("Page Slider 2"),
                createAndPersistSlider("Page Slider 3"),
                createAndPersistSlider("Page Slider 4"),
                createAndPersistSlider("Page Slider 5"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllSliderRequest reqPage1 = new FindAllSliderRequest();
                    reqPage1.setPage(1);
                    reqPage1.setPageSize(2);
                    return sliderQueryRepo.findSliders(reqPage1);
                })
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(page1 -> {
                    FindAllSliderRequest reqPage2 = new FindAllSliderRequest();
                    reqPage2.setPage(2);
                    reqPage2.setPageSize(2);
                    return sliderQueryRepo.findSliders(reqPage2)
                            .invoke(page2 -> {
                                assertThat(page2.getData()).hasSize(2);
                                assertThat(page2.getData().get(0).getName()).isNotIn(
                                        page1.getData().get(0).getName(),
                                        page1.getData().get(1).getName());
                            });
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindSlidersWithEmptySearchReturnsAll() {
        Uni.join().all(
                createAndPersistSlider("Slider A"),
                createAndPersistSlider("Slider B"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllSliderRequest req = new FindAllSliderRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("");
                    return sliderQueryRepo.findSliders(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindSlidersWithNullSearchReturnsAll() {
        Uni.join().all(
                createAndPersistSlider("Slider C"),
                createAndPersistSlider("Slider D"))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllSliderRequest req = new FindAllSliderRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch(null);
                    return sliderQueryRepo.findSliders(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindSlidersSearchReturnsEmptyWhenNoMatch() {
        createAndPersistSlider("Summer Sale")
                .chain(ignored -> {
                    FindAllSliderRequest req = new FindAllSliderRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    req.setSearch("Winter");
                    return sliderQueryRepo.findSliders(req);
                })
                .invoke(result -> {
                    assertThat(result.getData()).isEmpty();
                    assertThat(result.getTotalRecords()).isZero();
                })
                .replaceWithVoid().await().indefinitely();
    }
}
