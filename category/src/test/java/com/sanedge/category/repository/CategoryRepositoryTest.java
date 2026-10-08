package com.sanedge.category.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sanedge.category.domain.requests.FindAllCategoryRequest;
import com.sanedge.category.entity.Category;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class CategoryRepositoryTest extends MongoTestBase {

    @Inject
    CategoryQueryRepository categoryQueryRepo;

    @Inject
    CategoryCommandRepository categoryCommandRepo;

    private Uni<Category> persist(String name, String description, String slug) {
        Category cat = new Category();
        cat.setName(name);
        cat.setDescription(description);
        cat.setSlugCategory(slug);
        return categoryQueryRepo.persist(cat).replaceWith(cat);
    }

    private Uni<Category> persist(String name) {
        return persist(name, "description of " + name, "slug-" + name.toLowerCase().replace(' ', '-'));
    }

    private Uni<Void> clean() {
        return categoryQueryRepo.deleteAll().replaceWithVoid();
    }

    private FindAllCategoryRequest findAllReq(int page, int size, String search) {
        FindAllCategoryRequest r = new FindAllCategoryRequest();
        r.setPage(page);
        r.setPageSize(size);
        r.setSearch(search == null ? "" : search);
        return r;
    }

    @Test
    void testCreateAndFindById() {
        clean()
                .chain(() -> persist("Electronics"))
                .chain(saved -> categoryQueryRepo.findById(saved.id.toHexString()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getName()).isEqualTo("Electronics");
                    assertThat(found.getSlugCategory()).isEqualTo("slug-electronics");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindCategoryById() {
        clean()
                .chain(() -> persist("Books"))
                .chain(saved -> categoryQueryRepo.findCategoryById(saved.id.toHexString()))
                .invoke(cat -> {
                    assertThat(cat).isNotNull();
                    assertThat(cat.getName()).isEqualTo("Books");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindCategoryByIdReturnsNullIfNotFound() {
        clean()
                .chain(() -> categoryQueryRepo.findCategoryById("507f1f77bcf86cd799439011"))
                .invoke(cat -> assertThat(cat).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindCategoryByIdReturnsNullIfTrashed() {
        clean()
                .chain(() -> persist("Clothing"))
                .chain(saved -> categoryCommandRepo.trash(saved.id.toHexString())
                        .chain(() -> categoryQueryRepo.findCategoryById(saved.id.toHexString())))
                .invoke(cat -> assertThat(cat).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        clean()
                .chain(() -> categoryQueryRepo.findById("507f1f77bcf86cd799439011"))
                .invoke(cat -> assertThat(cat).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashCategory() {
        clean()
                .chain(() -> persist("Sports"))
                .chain(saved -> categoryCommandRepo.trash(saved.id.toHexString()))
                .invoke(cat -> {
                    assertThat(cat).isNotNull();
                    assertThat(cat.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashCategoryReturnsNullIfAlreadyTrashed() {
        clean()
                .chain(() -> persist("Toys"))
                .chain(saved -> categoryCommandRepo.trash(saved.id.toHexString())
                        .chain(() -> categoryCommandRepo.trash(saved.id.toHexString())))
                .invoke(cat -> assertThat(cat).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashCategoryReturnsNullIfNotFound() {
        clean()
                .chain(() -> categoryCommandRepo.trash("507f1f77bcf86cd799439011"))
                .invoke(cat -> assertThat(cat).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreCategory() {
        clean()
                .chain(() -> persist("Food"))
                .chain(saved -> categoryCommandRepo.trash(saved.id.toHexString())
                        .chain(() -> categoryCommandRepo.restore(saved.id.toHexString())))
                .invoke(cat -> {
                    assertThat(cat).isNotNull();
                    assertThat(cat.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreCategoryReturnsNullIfNotTrashed() {
        clean()
                .chain(() -> persist("Beverages"))
                .chain(saved -> categoryCommandRepo.restore(saved.id.toHexString()))
                .invoke(cat -> assertThat(cat).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreCategoryReturnsNullIfNotFound() {
        clean()
                .chain(() -> categoryCommandRepo.restore("507f1f77bcf86cd799439011"))
                .invoke(cat -> assertThat(cat).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        clean()
                .chain(() -> persist("Furniture"))
                .chain(saved -> categoryCommandRepo.trash(saved.id.toHexString())
                        .chain(() -> categoryCommandRepo.deletePermanent(saved.id.toHexString()))
                        .chain(deleted -> categoryQueryRepo.findById(saved.id.toHexString())))
                .invoke(cat -> assertThat(cat).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        clean()
                .chain(() -> persist("Garden"))
                .chain(saved -> categoryCommandRepo.deletePermanent(saved.id.toHexString())
                        .chain(deleted -> {
                            assertThat(deleted).isNull();
                            return categoryQueryRepo.findById(saved.id.toHexString());
                        }))
                .invoke(cat -> assertThat(cat).isNotNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        clean()
                .chain(() -> persist("Music").chain(saved -> categoryCommandRepo.trash(saved.id.toHexString()).replaceWithVoid()))
                .chain(() -> persist("Movies").chain(saved -> categoryCommandRepo.trash(saved.id.toHexString()).replaceWithVoid()))
                .chain(() -> categoryCommandRepo.restoreAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> categoryQueryRepo.findActiveCategories(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        clean()
                .chain(() -> persist("Art").chain(saved -> categoryCommandRepo.trash(saved.id.toHexString()).replaceWithVoid()))
                .chain(() -> persist("Crafts").chain(saved -> categoryCommandRepo.trash(saved.id.toHexString()).replaceWithVoid()))
                .chain(() -> persist("Jewelry")) // tidak di‑trash
                .chain(() -> categoryCommandRepo.deleteAllDeleted())
                .invoke(result -> assertThat(result).isTrue())
                .chain(() -> categoryQueryRepo.findActiveCategories(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1)) // hanya Jewelry yang tersisa
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindActiveCategories() {
        clean()
                .chain(() -> persist("A"))
                .chain(() -> persist("B"))
                .chain(() -> categoryQueryRepo.findActiveCategories(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(2))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindTrashedCategories() {
        clean()
                .chain(() -> persist("C"))
                .chain(() -> persist("D").chain(saved -> categoryCommandRepo.trash(saved.id.toHexString()).replaceWithVoid()))
                .chain(() -> categoryQueryRepo.findTrashedCategories(findAllReq(1, 10, "")))
                .invoke(page -> assertThat(page.getTotalRecords()).isEqualTo(1))
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindCategoriesWithSearchKeyword() {
        clean()
                .chain(() -> persist("Smartphone"))
                .chain(() -> persist("Smartwatch"))
                .chain(() -> persist("Tablet"))
                .chain(() -> categoryQueryRepo.findCategories(findAllReq(1, 10, "Smart")))
                .invoke(page -> {
                    assertThat(page.getTotalRecords()).isEqualTo(2);
                    assertThat(page.getData()).extracting(Category::getName)
                            .containsExactlyInAnyOrder("Smartphone", "Smartwatch");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindCategoriesWithPagination() {
        clean()
                .chain(() -> persist("Cat1"))
                .chain(() -> persist("Cat2"))
                .chain(() -> persist("Cat3"))
                .chain(() -> persist("Cat4"))
                .chain(() -> persist("Cat5"))
                .chain(() -> categoryQueryRepo.findCategories(findAllReq(1, 2, "")))
                .invoke(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                })
                .chain(() -> categoryQueryRepo.findCategories(findAllReq(2, 2, "")))
                .invoke(page2 -> assertThat(page2.getData()).hasSize(2))
                .replaceWithVoid().await().indefinitely();
    }
}
