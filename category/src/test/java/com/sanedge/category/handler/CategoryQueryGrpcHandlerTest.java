package com.sanedge.category.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.category.domain.response.CategoryResponse;
import com.sanedge.category.domain.response.CategoryResponseDeleteAt;
import com.sanedge.category.domain.requests.FindAllCategoryRequest;
import com.sanedge.category.service.CategoryQueryService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PaginationMeta;
import com.sanedge.common.exception.ResourceNotFoundException;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.category.CategoryCommon;
import pb.category.CategoryQuery;

@ExtendWith(MockitoExtension.class)
class CategoryQueryGrpcHandlerTest {

    private static final String VALID_ID = "507f1f77bcf86cd799439011";

    @Mock
    private CategoryQueryService categoryQueryService;

    private CategoryQueryGrpcHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new CategoryQueryGrpcHandler();
        Field field = CategoryQueryGrpcHandler.class.getDeclaredField("categoryQueryService");
        field.setAccessible(true);
        field.set(handler, categoryQueryService);
    }

    private CategoryResponse createMockResponse(String id) {
        return CategoryResponse.builder()
                .id(id)
                .name("Cat-" + id)
                .description("Desc " + id)
                .slugCategory("slug-" + id)
                .imageCategory("img-" + id + ".jpg")
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .build();
    }

    private CategoryResponseDeleteAt createMockResponseDeleteAt(String id) {
        return CategoryResponseDeleteAt.builder()
                .id(id)
                .name("Cat-" + id)
                .description("Desc " + id)
                .slugCategory("slug-" + id)
                .imageCategory("img-" + id + ".jpg")
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .build();
    }

    @Test
    void findAll_success_returnsPaginationResponse() {
        CategoryQuery.FindAllCategoryRequest request = CategoryQuery.FindAllCategoryRequest.newBuilder()
                .setPage(1)
                .setPageSize(10)
                .setSearch("")
                .build();

        List<CategoryResponse> data = List.of(createMockResponse(VALID_ID));
        PaginationMeta meta = new PaginationMeta(1, 10, 1, 1);
        ApiResponsePagination<List<CategoryResponse>> apiResp = new ApiResponsePagination<>(
                "success", "Categories retrieved successfully", data, meta);

        when(categoryQueryService.findAll(any(FindAllCategoryRequest.class)))
                .thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponsePaginationCategory response = handler.findAll(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Categories retrieved successfully");
        assertThat(response.getDataCount()).isEqualTo(1);
        assertThat(response.getData(0).getId()).isEqualTo(VALID_ID);
        assertThat(response.hasPagination()).isTrue();
    }

    @Test
    void findAll_failure_returnsInternalError() {
        CategoryQuery.FindAllCategoryRequest request = CategoryQuery.FindAllCategoryRequest.newBuilder()
                .setPage(1)
                .setPageSize(10)
                .build();

        when(categoryQueryService.findAll(any(FindAllCategoryRequest.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.findAll(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    @Test
    void findById_success_returnsCategory() {
        CategoryCommon.FindByIdCategoryRequest request = CategoryCommon.FindByIdCategoryRequest.newBuilder()
                .setId(VALID_ID)
                .build();

        ApiResponse<CategoryResponse> apiResp = ApiResponse.success("Category retrieved successfully",
                createMockResponse(VALID_ID));
        when(categoryQueryService.findById(anyString())).thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponseCategory response = handler.findById(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.hasData()).isTrue();
        assertThat(response.getData().getId()).isEqualTo(VALID_ID);
    }

    @Test
    void findById_notFound_returnsNotFoundStatus() {
        CategoryCommon.FindByIdCategoryRequest request = CategoryCommon.FindByIdCategoryRequest.newBuilder()
                .setId(VALID_ID)
                .build();

        when(categoryQueryService.findById(anyString()))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Category not found with id: 999")));

        StatusRuntimeException ex = null;
        try {
            handler.findById(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }
        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
    }

    @Test
    void findByActive_success_returnsPaginationResponse() {
        CategoryQuery.FindAllCategoryRequest request = CategoryQuery.FindAllCategoryRequest.newBuilder()
                .setPage(1)
                .setPageSize(10)
                .build();

        List<CategoryResponseDeleteAt> data = List.of(createMockResponseDeleteAt(VALID_ID));
        PaginationMeta meta = new PaginationMeta(1, 10, 1, 1);
        ApiResponsePagination<List<CategoryResponseDeleteAt>> apiResp = new ApiResponsePagination<>(
                "success", "Active categories retrieved successfully", data, meta);

        when(categoryQueryService.findByActive(any(FindAllCategoryRequest.class)))
                .thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponsePaginationCategoryDeleteAt response = handler.findByActive(request).await()
                .indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    void findByTrashed_success_returnsPaginationResponse() {
        CategoryQuery.FindAllCategoryRequest request = CategoryQuery.FindAllCategoryRequest.newBuilder()
                .setPage(1)
                .setPageSize(10)
                .build();

        List<CategoryResponseDeleteAt> data = List.of(createMockResponseDeleteAt(VALID_ID));
        PaginationMeta meta = new PaginationMeta(1, 10, 1, 1);
        ApiResponsePagination<List<CategoryResponseDeleteAt>> apiResp = new ApiResponsePagination<>(
                "success", "Trashed categories retrieved successfully", data, meta);

        when(categoryQueryService.findByTrashed(any(FindAllCategoryRequest.class)))
                .thenReturn(Uni.createFrom().item(apiResp));

        CategoryCommon.ApiResponsePaginationCategoryDeleteAt response = handler.findByTrashed(request).await()
                .indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }
}
