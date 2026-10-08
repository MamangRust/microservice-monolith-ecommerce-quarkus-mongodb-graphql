package com.sanedge.order.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.protobuf.Empty;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.service.OrderCommandService;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.order.OrderCommand;
import pb.order.OrderCommon;
import pb.order.OrderCommon.FindByIdOrderRequest;

@ExtendWith(MockitoExtension.class)
class OrderCommandGrpcHandlerTest {

    private static final String MERCHANT_ID = "507f1f77bcf86cd7994390a1";
    private static final String USER_ID = "507f1f77bcf86cd7994390b1";
    private static final String ORDER_ID = "507f1f77bcf86cd799439011";
    private static final String ORDER_ID_404 = "507f1f77bcf86cd799439099";
    private static final String PRODUCT_ID = "507f1f77bcf86cd7994390c1";
    private static final String ORDER_ITEM_ID = "507f1f77bcf86cd7994390e1";
    private static final String SHIPPING_ID = "507f1f77bcf86cd7994390d1";

    @Mock
    private OrderCommandService orderCommandService;

    private OrderCommandGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrderCommandGrpcHandler();
        handler.orderCommandService = orderCommandService;
    }

    private OrderResponse createOrderResponse(String id, Integer totalPrice) {
        return OrderResponse.builder()
                .id(id)
                .merchantId(MERCHANT_ID)
                .userId(USER_ID)
                .totalPrice(totalPrice)
                .createdAt("2024-01-01 00:00:00.0")
                .updatedAt("2024-01-01 00:00:00.0")
                .build();
    }

    private OrderResponseDeleteAt createOrderResponseDeleteAt(String id) {
        return OrderResponseDeleteAt.builder()
                .id(id)
                .merchantId(MERCHANT_ID)
                .userId(USER_ID)
                .totalPrice(500)
                .createdAt("2024-01-01 00:00:00.0")
                .updatedAt("2024-01-01 00:00:00.0")
                .deletedAt("2024-01-02 00:00:00.0")
                .build();
    }

    private OrderCommand.CreateOrderRequest createValidProtoCreateRequest() {
        OrderCommand.CreateOrderRequest request = OrderCommand.CreateOrderRequest.newBuilder()
                .setMerchantId(MERCHANT_ID)
                .setUserId(USER_ID)
                .addItems(OrderCommand.CreateOrderItemRequest.newBuilder()
                        .setProductId(PRODUCT_ID).setQuantity(2).setPrice(100).build())
                .setShipping(pb.shipping_address.ShippingAddressCommand.CreateShippingAddressRequest.newBuilder()
                        .setOrderId(ORDER_ID)
                        .setAlamat("123 Test St")
                        .setProvinsi("Test Prov")
                        .setKota("Test City")
                        .setCourier("Test Courier")
                        .setShippingMethod("standard")
                        .setShippingCost(10000)
                        .setNegara("Test Country")
                        .build())
                .build();
        return request;
    }

    @Test
    @DisplayName("create - should return ApiResponseOrder on success")
    void create_Success() {
        OrderCommand.CreateOrderRequest request = createValidProtoCreateRequest();

        OrderResponse data = createOrderResponse(ORDER_ID, 200);
        ApiResponse<OrderResponse> apiResp = new ApiResponse<>("success", "Order created successfully", data);

        when(orderCommandService.create(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrder response = handler.create(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Order created successfully");
        assertThat(response.getData().getId()).isEqualTo(ORDER_ID);
        assertThat(response.getData().getTotalPrice()).isEqualTo(200);
    }

    @Test
    @DisplayName("create - should return NOT_FOUND when ResourceNotFoundException thrown")
    void create_NotFound() {
        OrderCommand.CreateOrderRequest request = createValidProtoCreateRequest();

        when(orderCommandService.create(any()))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Merchant not found")));

        StatusRuntimeException ex = null;
        try {
            handler.create(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
        assertThat(ex.getStatus().getDescription()).contains("Merchant not found");
    }

    @Test
    @DisplayName("create - should return INTERNAL on generic exception")
    void create_InternalError() {
        OrderCommand.CreateOrderRequest request = createValidProtoCreateRequest();

        when(orderCommandService.create(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.create(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    @Test
    @DisplayName("update - should return ApiResponseOrder on success")
    void update_Success() {
        OrderCommand.UpdateOrderRequest request = OrderCommand.UpdateOrderRequest.newBuilder()
                .setOrderId(ORDER_ID)
                .setUserId(USER_ID)
                .addItems(OrderCommand.UpdateOrderItemRequest.newBuilder()
                        .setOrderItemId(ORDER_ITEM_ID).setProductId(PRODUCT_ID).setQuantity(3).setPrice(150).build())
                .setShipping(pb.shipping_address.ShippingAddressCommand.UpdateShippingAddressRequest.newBuilder()
                        .setShippingId(SHIPPING_ID)
                        .setOrderId(ORDER_ID)
                        .setAlamat("Updated St")
                        .setCourier("Updated Courier")
                        .setShippingMethod("express")
                        .setShippingCost(20000)
                        .build())
                .build();

        OrderResponse data = createOrderResponse(ORDER_ID, 450);
        ApiResponse<OrderResponse> apiResp = new ApiResponse<>("success", "Order updated successfully", data);

        when(orderCommandService.update(any())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrder response = handler.update(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData().getId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("update - should return NOT_FOUND when order not found")
    void update_NotFound() {
        OrderCommand.UpdateOrderRequest request = OrderCommand.UpdateOrderRequest.newBuilder()
                .setOrderId(ORDER_ID_404)
                .setUserId(USER_ID)
                .build();

        when(orderCommandService.update(any()))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Order not found")));

        StatusRuntimeException ex = null;
        try {
            handler.update(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("trashedOrder - should return ApiResponseOrderDeleteAt on success")
    void trashedOrder_Success() {
        FindByIdOrderRequest request = FindByIdOrderRequest.newBuilder().setId(ORDER_ID).build();

        OrderResponseDeleteAt data = createOrderResponseDeleteAt(ORDER_ID);
        ApiResponse<OrderResponseDeleteAt> apiResp = new ApiResponse<>("success", "Order trashed successfully!", data);

        when(orderCommandService.trash(anyString())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderDeleteAt response = handler.trashedOrder(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData().getId()).isEqualTo(ORDER_ID);
        assertThat(response.getData().hasDeletedAt()).isTrue();
        assertThat(response.getData().getDeletedAt().getValue()).isEqualTo("2024-01-02 00:00:00.0");
    }

    @Test
    @DisplayName("trashedOrder - should return INVALID_ARGUMENT when id is invalid")
    void trashedOrder_InvalidId_ReturnsInvalidArgument() {
        FindByIdOrderRequest request = FindByIdOrderRequest.newBuilder().setId("0").build();

        StatusRuntimeException ex = null;
        try {
            handler.trashedOrder(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
        assertThat(ex.getStatus().getDescription()).contains("must be a valid id");
    }

    @Test
    @DisplayName("update - should return INVALID_ARGUMENT when orderId is invalid")
    void update_InvalidOrderId_ReturnsInvalidArgument() {
        OrderCommand.UpdateOrderRequest request = OrderCommand.UpdateOrderRequest.newBuilder()
                .setOrderId("0")
                .setUserId(USER_ID)
                .build();

        StatusRuntimeException ex = null;
        try {
            handler.update(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
        assertThat(ex.getStatus().getDescription()).contains("must be a valid id");
    }

    @Test
    @DisplayName("create - should return INVALID_ARGUMENT when item productId is invalid")
    void create_InvalidItemProductId_ReturnsInvalidArgument() {
        OrderCommand.CreateOrderRequest request = OrderCommand.CreateOrderRequest.newBuilder()
                .setMerchantId(MERCHANT_ID)
                .setUserId(USER_ID)
                .addItems(OrderCommand.CreateOrderItemRequest.newBuilder()
                        .setProductId("0").setQuantity(2).setPrice(100).build())
                .build();

        StatusRuntimeException ex = null;
        try {
            handler.create(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
        assertThat(ex.getStatus().getDescription()).contains("Product id");
    }

    @Test
    @DisplayName("update - should return INVALID_ARGUMENT when item productId is invalid")
    void update_InvalidItemProductId_ReturnsInvalidArgument() {
        OrderCommand.UpdateOrderRequest request = OrderCommand.UpdateOrderRequest.newBuilder()
                .setOrderId(ORDER_ID)
                .setUserId(USER_ID)
                .addItems(OrderCommand.UpdateOrderItemRequest.newBuilder()
                        .setOrderItemId(ORDER_ITEM_ID).setProductId("0").setQuantity(3).setPrice(150).build())
                .build();

        StatusRuntimeException ex = null;
        try {
            handler.update(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
        assertThat(ex.getStatus().getDescription()).contains("Product id");
    }

    @Test
    @DisplayName("update - should return INVALID_ARGUMENT when shipping shippingId is invalid")
    void update_InvalidShippingId_ReturnsInvalidArgument() {
        OrderCommand.UpdateOrderRequest request = OrderCommand.UpdateOrderRequest.newBuilder()
                .setOrderId(ORDER_ID)
                .setUserId(USER_ID)
                .setShipping(pb.shipping_address.ShippingAddressCommand.UpdateShippingAddressRequest.newBuilder()
                        .setShippingId("0")
                        .setOrderId(ORDER_ID)
                        .build())
                .build();

        StatusRuntimeException ex = null;
        try {
            handler.update(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
        assertThat(ex.getStatus().getDescription()).contains("Shipping id");
    }

    @Test
    @DisplayName("trashedOrder - should return NOT_FOUND when order not found")
    void trashedOrder_NotFound() {
        FindByIdOrderRequest request = FindByIdOrderRequest.newBuilder().setId(ORDER_ID_404).build();

        when(orderCommandService.trash(anyString()))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Order not found")));

        StatusRuntimeException ex = null;
        try {
            handler.trashedOrder(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("restoreOrder - should return ApiResponseOrderDeleteAt on success")
    void restoreOrder_Success() {
        FindByIdOrderRequest request = FindByIdOrderRequest.newBuilder().setId(ORDER_ID).build();

        OrderResponseDeleteAt data = OrderResponseDeleteAt.builder()
                .id(ORDER_ID).merchantId(MERCHANT_ID).userId(USER_ID).totalPrice(500)
                .createdAt("2024-01-01 00:00:00.0")
                .updatedAt("2024-01-02 00:00:00.0")
                .build();
        ApiResponse<OrderResponseDeleteAt> apiResp = new ApiResponse<>("success", "Order restored successfully!", data);

        when(orderCommandService.restore(anyString())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderDeleteAt response = handler.restoreOrder(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData().getId()).isEqualTo(ORDER_ID);
    }

    @Test
    @DisplayName("restoreOrder - should return NOT_FOUND when order not found")
    void restoreOrder_NotFound() {
        FindByIdOrderRequest request = FindByIdOrderRequest.newBuilder().setId(ORDER_ID_404).build();

        when(orderCommandService.restore(anyString()))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Order not found or not trashed")));

        StatusRuntimeException ex = null;
        try {
            handler.restoreOrder(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("deleteOrderPermanent - should return ApiResponseOrderDelete on success")
    void deleteOrderPermanent_Success() {
        FindByIdOrderRequest request = FindByIdOrderRequest.newBuilder().setId(ORDER_ID).build();

        ApiResponse<Void> apiResp = ApiResponse.success("Order permanently deleted!");
        when(orderCommandService.delete(anyString())).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderDelete response = handler.deleteOrderPermanent(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Order permanently deleted!");
    }

    @Test
    @DisplayName("deleteOrderPermanent - should return INTERNAL when order not found")
    void deleteOrderPermanent_InternalError() {
        FindByIdOrderRequest request = FindByIdOrderRequest.newBuilder().setId(ORDER_ID_404).build();

        when(orderCommandService.delete(anyString()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Must be trashed first")));

        StatusRuntimeException ex = null;
        try {
            handler.deleteOrderPermanent(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    @Test
    @DisplayName("restoreAllOrder - should return ApiResponseOrderAll on success")
    void restoreAllOrder_Success() {
        ApiResponse<Void> apiResp = ApiResponse.success("All orders restored successfully!");
        when(orderCommandService.restoreAll()).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderAll response = handler.restoreAllOrder(Empty.getDefaultInstance()).await()
                .indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("All orders restored successfully!");
    }

    @Test
    @DisplayName("restoreAllOrder - should return INTERNAL on failure")
    void restoreAllOrder_InternalError() {
        when(orderCommandService.restoreAll())
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.restoreAllOrder(Empty.getDefaultInstance()).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    @Test
    @DisplayName("deleteAllOrderPermanent - should return ApiResponseOrderAll on success")
    void deleteAllOrderPermanent_Success() {
        ApiResponse<Void> apiResp = ApiResponse.success("All orders permanently deleted!");
        when(orderCommandService.deleteAll()).thenReturn(Uni.createFrom().item(apiResp));

        OrderCommon.ApiResponseOrderAll response = handler.deleteAllOrderPermanent(Empty.getDefaultInstance()).await()
                .indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("All orders permanently deleted!");
    }

    @Test
    @DisplayName("deleteAllOrderPermanent - should return INTERNAL on failure")
    void deleteAllOrderPermanent_InternalError() {
        when(orderCommandService.deleteAll())
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.deleteAllOrderPermanent(Empty.getDefaultInstance()).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }
}
