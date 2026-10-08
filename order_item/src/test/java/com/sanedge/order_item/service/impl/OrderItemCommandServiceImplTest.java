package com.sanedge.order_item.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order_item.domain.requests.CreateOrderItemRequest;
import com.sanedge.order_item.domain.requests.UpdateOrderItemRequest;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import com.sanedge.order_item.entity.OrderItem;
import com.sanedge.order_item.repository.OrderItemRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class OrderItemCommandServiceImplTest {

    private static final String ORDER_ITEM_ID = "507f1f77bcf86cd7994390e1";
    private static final String ORDER_ITEM_ID_404 = "507f1f77bcf86cd7994390f9";
    private static final String ORDER_ID = "507f1f77bcf86cd799439011";
    private static final String ORDER_ID_404 = "507f1f77bcf86cd799439099";
    private static final String PRODUCT_ID = "507f1f77bcf86cd7994390c1";

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private TracingMetrics tracingMetrics;

    private OrderItemCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any());
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics)
                        .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        service = new OrderItemCommandServiceImpl(orderItemRepository, tracingMetrics);
    }

    @Test
    void create_shouldSucceed_whenValidRequest() {
        CreateOrderItemRequest request = new CreateOrderItemRequest();
        request.setOrderId(ORDER_ID);
        request.setProductId(PRODUCT_ID);
        request.setQuantity(2);
        request.setPrice(5000);

        OrderItem savedItem = createOrderItem(new ObjectId(ORDER_ITEM_ID), new ObjectId(ORDER_ID),
                new ObjectId(PRODUCT_ID), 2, 5000);

        when(orderItemRepository.persist(any(OrderItem.class))).thenReturn(Uni.createFrom().item(savedItem));

        ApiResponse<OrderItemResponse> result = service.create(request).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Order item created successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getId()).isEqualTo(ORDER_ITEM_ID);
        assertThat(result.data().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(result.data().getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(result.data().getQuantity()).isEqualTo(2);
        assertThat(result.data().getPrice()).isEqualTo(5000);

        verify(orderItemRepository).persist(any(OrderItem.class));
    }

    @Test
    void create_shouldFail_whenRepositoryFails() {
        CreateOrderItemRequest request = new CreateOrderItemRequest();
        request.setOrderId(ORDER_ID);
        request.setProductId(PRODUCT_ID);
        request.setQuantity(2);
        request.setPrice(5000);

        when(orderItemRepository.persist(any(OrderItem.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        assertThatThrownBy(() -> service.create(request).await().indefinitely())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");
    }

    @Test
    void update_shouldSucceed_whenOrderItemExists() {
        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setOrderItemId(ORDER_ITEM_ID);
        request.setQuantity(5);
        request.setPrice(7500);

        OrderItem existingItem = createOrderItem(new ObjectId(ORDER_ITEM_ID), new ObjectId(ORDER_ID),
                new ObjectId(PRODUCT_ID), 2, 5000);
        OrderItem updatedItem = createOrderItem(new ObjectId(ORDER_ITEM_ID), new ObjectId(ORDER_ID),
                new ObjectId(PRODUCT_ID), 5, 7500);

        when(orderItemRepository.findById(ORDER_ITEM_ID)).thenReturn(Uni.createFrom().item(existingItem));
        when(orderItemRepository.update(any(OrderItem.class))).thenReturn(Uni.createFrom().item(updatedItem));

        ApiResponse<OrderItemResponse> result = service.update(request).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Order item updated successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getQuantity()).isEqualTo(5);
        assertThat(result.data().getPrice()).isEqualTo(7500);
    }

    @Test
    void update_shouldThrowResourceNotFoundException_whenOrderItemNotFound() {
        UpdateOrderItemRequest request = new UpdateOrderItemRequest();
        request.setOrderItemId(ORDER_ITEM_ID_404);
        request.setQuantity(5);
        request.setPrice(7500);

        when(orderItemRepository.findById(ORDER_ITEM_ID_404)).thenReturn(Uni.createFrom().nullItem());

        assertThatThrownBy(() -> service.update(request).await().indefinitely())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order item not found");
    }

    @Test
    void trash_shouldSucceed_whenOrderItemExists() {
        OrderItem trashedItem = createOrderItem(new ObjectId(ORDER_ITEM_ID), new ObjectId(ORDER_ID),
                new ObjectId(PRODUCT_ID), 2, 5000);
        trashedItem.setDeletedAt(Instant.now());

        when(orderItemRepository.trash(ORDER_ITEM_ID)).thenReturn(Uni.createFrom().item(trashedItem));

        ApiResponse<OrderItemResponseDeleteAt> result = service.trash(ORDER_ITEM_ID).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Order item trashed successfully");
        assertThat(result.data()).isNotNull();
        assertThat(result.data().getDeletedAt()).isNotNull();
    }

    @Test
    void trash_shouldThrowResourceNotFoundException_whenOrderItemNotFound() {
        when(orderItemRepository.trash(ORDER_ITEM_ID_404)).thenReturn(Uni.createFrom().nullItem());

        assertThatThrownBy(() -> service.trash(ORDER_ITEM_ID_404).await().indefinitely())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order item not found");
    }

    @Test
    void restore_shouldSucceed_whenOrderItemInTrash() {
        OrderItem restoredItem = createOrderItem(new ObjectId(ORDER_ITEM_ID), new ObjectId(ORDER_ID),
                new ObjectId(PRODUCT_ID), 2, 5000);

        when(orderItemRepository.restore(ORDER_ITEM_ID)).thenReturn(Uni.createFrom().item(restoredItem));

        ApiResponse<OrderItemResponseDeleteAt> result = service.restore(ORDER_ITEM_ID).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Order item restored successfully");
    }

    @Test
    void restore_shouldThrowResourceNotFoundException_whenOrderItemNotInTrash() {
        when(orderItemRepository.restore(ORDER_ITEM_ID_404)).thenReturn(Uni.createFrom().nullItem());

        assertThatThrownBy(() -> service.restore(ORDER_ITEM_ID_404).await().indefinitely())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order item not found in trash");
    }

    @Test
    void deletePermanent_shouldSucceed_whenOrderItemInTrash() {
        OrderItem deletedItem = createOrderItem(new ObjectId(ORDER_ITEM_ID), new ObjectId(ORDER_ID),
                new ObjectId(PRODUCT_ID), 2, 5000);

        when(orderItemRepository.deletePermanent(ORDER_ITEM_ID)).thenReturn(Uni.createFrom().item(deletedItem));

        ApiResponse<Void> result = service.deletePermanent(ORDER_ITEM_ID).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Order item permanently deleted");
    }

    @Test
    void deletePermanent_shouldThrowInvalidRequestException_whenOrderItemNotInTrash() {
        when(orderItemRepository.deletePermanent(ORDER_ITEM_ID_404)).thenReturn(Uni.createFrom().nullItem());

        assertThatThrownBy(() -> service.deletePermanent(ORDER_ITEM_ID_404).await().indefinitely())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Order item not found or must be trashed before permanent deletion");
    }

    @Test
    void calculateTotalPrice_shouldReturnCorrectTotal() {
        OrderItem item1 = createOrderItem(new ObjectId(ORDER_ITEM_ID), new ObjectId(ORDER_ID),
                new ObjectId(PRODUCT_ID), 2, 1000);
        OrderItem item2 = createOrderItem(new ObjectId("507f1f77bcf86cd7994390e2"), new ObjectId(ORDER_ID),
                new ObjectId(PRODUCT_ID), 3, 500);
        OrderItem item3 = createOrderItem(new ObjectId("507f1f77bcf86cd7994390e3"), new ObjectId(ORDER_ID),
                new ObjectId(PRODUCT_ID), 1, 3000);

        when(orderItemRepository.findOrderItemByOrder(ORDER_ID))
                .thenReturn(Uni.createFrom().item(List.of(item1, item2, item3)));

        ApiResponse<Integer> result = service.calculateTotalPrice(ORDER_ID).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.message()).isEqualTo("Total price calculated");
        assertThat(result.data()).isEqualTo(6500);
    }

    @Test
    void calculateTotalPrice_shouldReturnZero_whenNoItems() {
        when(orderItemRepository.findOrderItemByOrder(ORDER_ID))
                .thenReturn(Uni.createFrom().item(List.of()));

        ApiResponse<Integer> result = service.calculateTotalPrice(ORDER_ID).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).isEqualTo(0);
    }

    private OrderItem createOrderItem(ObjectId id, ObjectId orderId, ObjectId productId, Integer quantity, Integer price) {
        OrderItem item = new OrderItem();
        item.id = id;
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setPrice(price);
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        return item;
    }

    /**
     * Finds the Supplier argument in the invocation regardless of whether it was
     * passed positionally in the 3-arg overload (arg index 2) or 4-arg overload
     * (arg index 3), then invokes it and returns the resulting Uni. This lets
     * a single Answer<?> body serve both traceAndMeasure overloads.
     */
    private Answer<Uni<?>> invokeSupplier() {
        return invocation -> {
            Supplier<?> supplier = null;
            for (Object arg : invocation.getArguments()) {
                if (arg instanceof Supplier<?>) {
                    supplier = (Supplier<?>) arg;
                    break;
                }
            }
            return supplier != null ? (Uni<?>) supplier.get() : null;
        };
    }
}
