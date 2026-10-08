package com.sanedge.category.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.sanedge.category.domain.requests.CreateCategoryRequest;
import com.sanedge.category.domain.requests.UpdateCategoryRequest;
import com.sanedge.category.domain.response.CategoryResponse;
import com.sanedge.category.domain.response.CategoryResponseDeleteAt;
import com.sanedge.category.entity.Category;
import com.sanedge.category.repository.CategoryCommandRepository;
import com.sanedge.category.repository.CategoryQueryRepository;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CategoryCommandServiceImplTest {

    private static final String CATEGORY_ID = "507f1f77bcf86cd799439011";

    @Mock
    private CategoryCommandRepository categoryCommandRepository;

    @Mock
    private CategoryQueryRepository categoryQueryRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private CategoryCommandServiceImpl service;

    private Answer<Uni<?>> invokeSupplier() {
        return invocation -> {
            for (Object arg : invocation.getArguments()) {
                if (arg instanceof Supplier) {
                    return ((Supplier<Uni<?>>) arg).get();
                }
            }
            return Uni.createFrom().nullItem();
        };
    }

    @BeforeEach
    void setUp() {
        service = new CategoryCommandServiceImpl(
                categoryCommandRepository,
                categoryQueryRepository,
                redisService,
                tracingMetrics);

        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics).traceAndMeasure(
                        anyString(),
                        anyString(),
                        any());
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics).traceAndMeasure(
                        anyString(),
                        anyString(),
                        any(Attributes.class),
                        any());

        lenient()
                .when(redisService.deleteReactive(anyString()))
                .thenReturn(Uni.createFrom().voidItem());
    }

    private Category createMockCategory(String id, String name) {
        Category c = new Category();
        c.id = new ObjectId(id);
        c.setName(name);
        c.setDescription("Description " + name);
        c.setSlugCategory("slug-" + name);
        c.setImageCategory(name + ".jpg");
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        return c;
    }

    @Test
    void createCategory_success_returnsNewCategory() {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName("Electronics");
        req.setDescription("Electronic devices");
        req.setSlugCategory("electronics");
        req.setImageCategory("electronics.jpg");

        lenient().when(categoryCommandRepository.persist(any(Category.class)))
                .thenAnswer(invocation -> {
                    Category c = invocation.getArgument(0);
                    c.id = new ObjectId(CATEGORY_ID);
                    return Uni.createFrom().item(c);
                });

        ApiResponse<CategoryResponse> result = service.createCategory(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Category created successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getName()).isEqualTo("Electronics");
    }

    @Test
    void updateCategory_success_returnsUpdatedCategory() {
        UpdateCategoryRequest req = new UpdateCategoryRequest();
        req.setCategoryId(CATEGORY_ID);
        req.setName("Updated");
        req.setDescription("Updated desc");
        req.setSlugCategory("updated");
        req.setImageCategory("updated.jpg");

        Category existing = createMockCategory(CATEGORY_ID, "Old");
        when(categoryQueryRepository.findById(anyString())).thenReturn(Uni.createFrom().item(existing));
        when(categoryCommandRepository.update(any(Category.class)))
                .thenReturn(Uni.createFrom().item(existing));

        ApiResponse<CategoryResponse> result = service.updateCategory(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Category updated successfully");
        assertThat(result.data().getName()).isEqualTo("Updated");
    }

    @Test
    void updateCategory_notFound_throwsResourceNotFoundException() {
        UpdateCategoryRequest req = new UpdateCategoryRequest();
        req.setCategoryId(CATEGORY_ID);
        req.setName("Test");
        req.setDescription("Test");
        req.setSlugCategory("test");

        when(categoryQueryRepository.findById(anyString())).thenReturn(Uni.createFrom().nullItem());

        try {
            service.updateCategory(req).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("Category not found");
        }
    }

    @Test
    void updateCategory_nullCategoryId_throwsResourceNotFoundException() {
        UpdateCategoryRequest req = new UpdateCategoryRequest();
        req.setCategoryId(null);

        try {
            service.updateCategory(req).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("category_id is required");
        }
    }

    @Test
    void trashedCategory_success_returnsTrashedCategory() {
        Category trashed = createMockCategory(CATEGORY_ID, "Electronics");
        trashed.setDeletedAt(Instant.now());
        when(categoryCommandRepository.trash(anyString())).thenReturn(Uni.createFrom().item(trashed));

        ApiResponse<CategoryResponseDeleteAt> result = service.trashedCategory(CATEGORY_ID).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Category trashed successfully");
        assertThat(result.data()).isNotNull();
    }

    @Test
    void trashedCategory_notFound_throwsResourceNotFoundException() {
        when(categoryCommandRepository.trash(anyString())).thenReturn(Uni.createFrom().nullItem());

        try {
            service.trashedCategory(CATEGORY_ID).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("Trashed category not found");
        }
    }

    @Test
    void restoreCategory_success_returnsRestoredCategory() {
        Category restored = createMockCategory(CATEGORY_ID, "Electronics");
        when(categoryCommandRepository.restore(anyString())).thenReturn(Uni.createFrom().item(restored));

        ApiResponse<CategoryResponseDeleteAt> result = service.restoreCategory(CATEGORY_ID).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Category restored successfully");
    }

    @Test
    void restoreCategory_notFound_throwsResourceNotFoundException() {
        when(categoryCommandRepository.restore(anyString())).thenReturn(Uni.createFrom().nullItem());

        try {
            service.restoreCategory(CATEGORY_ID).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("Restore category not found");
        }
    }

    @Test
    void deleteCategoryPermanent_success_returnsSuccess() {
        Category deleted = createMockCategory(CATEGORY_ID, "Electronics");
        deleted.setDeletedAt(Instant.now());
        when(categoryCommandRepository.deletePermanent(anyString())).thenReturn(Uni.createFrom().item(deleted));

        ApiResponse<Void> result = service.deleteCategoryPermanent(CATEGORY_ID).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Category deleted permanently");
    }

    @Test
    void deleteCategoryPermanent_notFound_throwsInvalidRequestException() {
        when(categoryCommandRepository.deletePermanent(anyString())).thenReturn(Uni.createFrom().nullItem());

        try {
            service.deleteCategoryPermanent(CATEGORY_ID).await().indefinitely();
            Assertions.fail("Expected InvalidRequestException");
        } catch (InvalidRequestException e) {
            assertThat(e.getMessage()).contains("must be trashed before permanent deletion");
        }
    }

    @Test
    void restoreAllCategories_success_returnsSuccess() {
        when(categoryCommandRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(true));

        ApiResponse<Void> result = service.restoreAllCategories().await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("All categories restored successfully");
    }

    @Test
    void restoreAllCategories_noneFound_throwsResourceNotFoundException() {
        when(categoryCommandRepository.restoreAllDeleted()).thenReturn(Uni.createFrom().item(false));

        try {
            service.restoreAllCategories().await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("No trashed categories found");
        }
    }

    @Test
    void deleteAllCategoriesPermanent_success_returnsSuccess() {
        when(categoryCommandRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(true));

        ApiResponse<Void> result = service.deleteAllCategoriesPermanent().await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("All categories permanently deleted");
    }

    @Test
    void deleteAllCategoriesPermanent_noneFound_throwsResourceNotFoundException() {
        when(categoryCommandRepository.deleteAllDeleted()).thenReturn(Uni.createFrom().item(false));

        try {
            service.deleteAllCategoriesPermanent().await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("No trashed categories found");
        }
    }
}
