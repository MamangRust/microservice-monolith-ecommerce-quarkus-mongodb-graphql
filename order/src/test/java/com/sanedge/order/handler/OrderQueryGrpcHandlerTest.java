package com.sanedge.order.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PaginationMeta;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.service.OrderQueryService;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.order.OrderCommon;
import pb.order.OrderQuery;

@ExtendWith(MockitoExtension.class)
class OrderQueryGrpcHandlerTest {

    private static final String MERCHANT_ID = "507f1f77bcf86cd7994390a1";
    private static final String USER_ID = "507f1f77bcf86cd7994390b1";
    private static final String ORDER_ID = "507f1f77bcf86cd799439011";
    private static final String ORDER_ID_404 = "507f1f77bcf86cd799439099";

    @Mock
    private OrderQueryService orderQueryService;

    private OrderQueryGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderQueryGrpcHandler();
        handler.orderQueryService = orderQueryService;
    }

    private OrderResponse createMockResponse(String id) {
        return OrderResponse.builder()
                .id(id)
                .merchantId(MERCHANT_ID)
                .userId(USER_ID)
                .totalPrice(500)
                .createdAt("2024-01-01")
                .updatedAt("2024-01-01")
                .build();
    }

    private OrderResponseDeleteAt createMockResponseDeleteAt(String id) {
        return OrderResponseDeleteAt.builder()
                .id(id)
                .merchantId(MERCHANT_ID)
                .userId(USER_ID)
                .totalPrice(500)
                .build();
    }

    @Test
    void findAll_success_returnsPaginationResponse() {
        OrderQuery.FindAllOrderRequest request = OrderQuery.FindAllOrderRequest.newBuilder()
                .setPage(1)
                .setPageSize(10)
                .build();

        List<OrderResponse> data = List.of(createMockResponse(ORDER_ID));
        PaginationMeta meta = new PaginationMeta(1, 10, 1, 1);
        ApiResponsePagination<List<OrderResponse>> apiResp = new ApiResponsePagination<>(
                "success", "Orders retrieved successfully", data, meta);

        when(orderQueryService.findAll(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponsePaginationOrder response = handler.findAll(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    void findAll_failure_returnsInternalError() {
        OrderQuery.FindAllOrderRequest request = OrderQuery.FindAllOrderRequest.newBuilder()
                .setPage(1)
                .setPageSize(10)
                .build();

        when(orderQueryService.findAll(any()))
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
    void findById_success_returnsOrder() {
        OrderCommon.FindByIdOrderRequest request = OrderCommon.FindByIdOrderRequest.newBuilder()
                .setId(ORDER_ID)
                .build();

        ApiResponse<OrderResponse> apiResp = ApiResponse.success("Order retrieved successfully",
                createMockResponse(ORDER_ID));
        when(orderQueryService.findById(anyString())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrder response = handler.findById(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.hasData()).isTrue();
    }

    @Test
    void findById_notFound_returnsNotFoundStatus() {
        OrderCommon.FindByIdOrderRequest request = OrderCommon.FindByIdOrderRequest.newBuilder()
                .setId(ORDER_ID_404)
                .build();

        when(orderQueryService.findById(anyString()))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Order not found")));

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
        OrderQuery.FindAllOrderRequest request = OrderQuery.FindAllOrderRequest.newBuilder()
                .setPage(1)
                .setPageSize(10)
                .build();

        List<OrderResponseDeleteAt> data = List.of(createMockResponseDeleteAt(ORDER_ID));
        PaginationMeta meta = new PaginationMeta(1, 10, 1, 1);
        ApiResponsePagination<List<OrderResponseDeleteAt>> apiResp = new ApiResponsePagination<>(
                "success", "Active orders retrieved successfully", data, meta);

        when(orderQueryService.findByActive(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponsePaginationOrderDeleteAt response = handler.findByActive(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }

    @Test
    void findByTrashed_success_returnsPaginationResponse() {
        OrderQuery.FindAllOrderRequest request = OrderQuery.FindAllOrderRequest.newBuilder()
                .setPage(1)
                .setPageSize(10)
                .build();

        List<OrderResponseDeleteAt> data = List.of(createMockResponseDeleteAt(ORDER_ID));
        PaginationMeta meta = new PaginationMeta(1, 10, 1, 1);
        ApiResponsePagination<List<OrderResponseDeleteAt>> apiResp = new ApiResponsePagination<>(
                "success", "Trashed orders retrieved successfully", data, meta);

        when(orderQueryService.findByTrashed(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponsePaginationOrderDeleteAt response = handler.findByTrashed(request).await().indefinitely();

        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getDataCount()).isEqualTo(1);
    }
}
