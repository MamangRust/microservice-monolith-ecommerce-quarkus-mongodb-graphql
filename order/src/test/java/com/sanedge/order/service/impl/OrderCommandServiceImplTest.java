package com.sanedge.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.adapter.merchant.MerchantPort;
import com.sanedge.common.adapter.model.Merchant;
import com.sanedge.common.adapter.model.OrderItem;
import com.sanedge.common.adapter.model.Product;
import com.sanedge.common.adapter.model.ShippingAddress;
import com.sanedge.common.adapter.model.User;
import com.sanedge.common.adapter.order_item.OrderItemPort;
import com.sanedge.common.adapter.product.ProductPort;
import com.sanedge.common.adapter.shipping_address.ShippingAddressPort;
import com.sanedge.common.adapter.transaction.TransactionPort;
import com.sanedge.common.adapter.user.UserPort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ForbiddenException;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order.domain.requests.CreateOrderItemRequest;
import com.sanedge.order.domain.requests.CreateOrderRequest;
import com.sanedge.order.domain.requests.CreateShippingAddressRequest;
import com.sanedge.order.domain.requests.UpdateOrderRequest;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.entity.Order;
import com.sanedge.order.repository.OrderCommandRepository;
import com.sanedge.order.repository.OrderQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceImplTest {

        private static final String MERCHANT_ID = "507f1f77bcf86cd7994390a1";
        private static final String USER_ID = "507f1f77bcf86cd7994390b1";
        private static final String USER_ID_2 = "507f1f77bcf86cd7994390d1";
        private static final String USER_ID_3 = "507f1f77bcf86cd7994390e1";
        private static final String ORDER_ID = "507f1f77bcf86cd799439011";

        @Mock
        private OrderQueryRepository orderQueryRepository;

        @Mock
        private OrderCommandRepository orderCommandRepository;

        @Mock
        private Validator validator;

        @Mock
        private RedisService redisService;

        @Mock
        private TracingMetrics tracingMetrics;

        @Mock
        private MerchantPort merchantPort;

        @Mock
        private UserPort userPort;

        @Mock
        private ProductPort productPort;

        @Mock
        private OrderItemPort orderItemPort;

        @Mock
        private TransactionPort transactionPort;

        @Mock
        private ShippingAddressPort shippingAddressPort;

        private OrderCommandServiceImpl service;

        @BeforeEach
        void setUp() {
                service = new OrderCommandServiceImpl(
                                orderQueryRepository,
                                orderCommandRepository,
                                validator,
                                redisService,
                                tracingMetrics);

                service.merchantPort = merchantPort;
                service.userPort = userPort;
                service.productPort = productPort;
                service.orderItemPort = orderItemPort;
                service.transactionPort = transactionPort;
                service.shippingAddressPort = shippingAddressPort;

                lenient().doAnswer(invocation -> {
                        Supplier<Uni<?>> supplier = invocation.getArgument(2);
                        return supplier.get();
                }).when(tracingMetrics)
                                .traceAndMeasure(
                                                anyString(),
                                                anyString(),
                                                any());

                lenient().doAnswer(invocation -> {
                        Supplier<Uni<?>> supplier = invocation.getArgument(3);
                        return supplier.get();
                }).when(tracingMetrics)
                                .traceAndMeasure(
                                                anyString(),
                                                anyString(),
                                                any(Attributes.class),
                                                any());

                lenient().when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());
                lenient().when(orderCommandRepository.updateTotalPrice(anyString(), anyInt()))
                                .thenReturn(Uni.createFrom().item(1L));
                lenient().when(validator.validate(any())).thenReturn(Collections.emptySet());

                // Defaults for trash/restore/delete paths that resolve related aggregates.
                lenient().when(orderItemPort.findOrderItemByOrder(anyString()))
                                .thenReturn(Uni.createFrom().item(Collections.emptyList()));
                lenient().when(transactionPort.findByOrderId(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());
        }

        private Order createTestOrder(ObjectId id, ObjectId merchantId, ObjectId userId, Integer totalPrice) {
                Order order = new Order();
                order.id = id;
                order.setMerchantId(merchantId);
                order.setUserId(userId);
                order.setTotalPrice(totalPrice);
                order.setCreatedAt(Instant.now());
                order.setUpdatedAt(Instant.now());
                return order;
        }

        private CreateOrderRequest createValidCreateOrderRequest() {
                CreateOrderRequest request = new CreateOrderRequest();
                request.setMerchantId(MERCHANT_ID);
                request.setUserId(USER_ID);

                CreateOrderItemRequest item = new CreateOrderItemRequest();
                item.setProductId("1");
                item.setQuantity(2);
                item.setPrice(100);
                request.setItems(List.of(item));

                CreateShippingAddressRequest shipping = new CreateShippingAddressRequest();
                shipping.setAlamat("123 Test Street");
                shipping.setProvinsi("Test Province");
                shipping.setKota("Test City");
                shipping.setCourier("Test Courier");
                shipping.setShippingMethod("standard");
                shipping.setShippingCost(10000);
                shipping.setNegara("Test Country");
                request.setShippingAddress(shipping);

                return request;
        }

        private Merchant merchant() {
                return new Merchant(MERCHANT_ID, null, "Merchant", null, null, null, null, null,
                                Instant.now(), Instant.now());
        }

        private User user(String id) {
                return new User(id, "John", "Doe", "john.doe@example.com", null, Instant.now(), Instant.now());
        }

        private Product product(String id, int price, int countInStock) {
                return new Product(id, null, null, "Product", null, price, countInStock, null, 0, 0f,
                                null, null, Instant.now(), Instant.now());
        }

        private void mockMerchantAndUser() {
                lenient().when(merchantPort.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(merchant()));
                lenient().when(userPort.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(user(USER_ID)));
        }

        private void mockProductAndOrderItem() {
                lenient().when(productPort.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(product("1", 0, 100)));
                lenient().when(orderItemPort.create(any()))
                                .thenReturn(Uni.createFrom().item(new OrderItem("oi-1", ORDER_ID, "1", 2, 0,
                                                Instant.now(), Instant.now())));
                lenient().when(productPort.adjustStock(anyString(), anyInt()))
                                .thenReturn(Uni.createFrom().item(product("1", 0, 98)));
        }

        private void mockShipping() {
                lenient().when(shippingAddressPort.create(any()))
                                .thenReturn(Uni.createFrom().item(new ShippingAddress("sa-1", ORDER_ID,
                                                "123 Test Street", "Test Province", "Test Country", "Test City",
                                                "standard", 10000, Instant.now(), Instant.now())));
        }

        private void mockPersistNew() {
                when(orderCommandRepository.persistNew(any(Order.class)))
                                .thenAnswer(inv -> {
                                        Order o = inv.getArgument(0);
                                        if (o.id == null)
                                                o.id = new ObjectId(ORDER_ID);
                                        return Uni.createFrom().item(o);
                                });
        }

        @Nested
        @DisplayName("create order tests")
        class CreateOrderTests {

                @Test
                @DisplayName("should successfully create order when all validations pass")
                void createOrder_Success() {
                        CreateOrderRequest request = createValidCreateOrderRequest();
                        mockMerchantAndUser();
                        mockProductAndOrderItem();
                        mockShipping();
                        mockPersistNew();

                        ApiResponse<OrderResponse> response = service.create(request).await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Order created successfully");
                        assertThat(response.data()).isNotNull();
                        assertThat(response.data().getId()).isEqualTo(ORDER_ID);
                }

                @Test
                @DisplayName("should fail when validation errors occur")
                void createOrder_ValidationError() {
                        CreateOrderRequest request = createValidCreateOrderRequest();

                        @SuppressWarnings("unchecked")
                        ConstraintViolation<Object> violation = (ConstraintViolation<Object>) org.mockito.Mockito
                                        .mock(ConstraintViolation.class);
                        lenient().when(violation.getPropertyPath())
                                        .thenReturn(org.mockito.Mockito.mock(jakarta.validation.Path.class));
                        lenient().when(violation.getMessage()).thenReturn("test error message");
                        when(validator.validate(any())).thenReturn(Set.of(violation));

                        assertThatThrownBy(() -> service.create(request).await().indefinitely())
                                        .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
                }

                @Test
                @DisplayName("should fail when merchant not found")
                void createOrder_MerchantNotFound() {
                        CreateOrderRequest request = createValidCreateOrderRequest();

                        when(merchantPort.findById(anyString()))
                                        .thenReturn(Uni.createFrom().failure(
                                                        new ResourceNotFoundException("Merchant not found: "
                                                                        + MERCHANT_ID)));

                        assertThatThrownBy(() -> service.create(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Merchant not found");
                }

                @Test
                @DisplayName("should fail when user not found")
                void createOrder_UserNotFound() {
                        CreateOrderRequest request = createValidCreateOrderRequest();

                        when(merchantPort.findById(anyString()))
                                        .thenReturn(Uni.createFrom().item(merchant()));
                        when(userPort.findById(anyString()))
                                        .thenReturn(Uni.createFrom().failure(
                                                        new ResourceNotFoundException("User not found: " + USER_ID)));

                        assertThatThrownBy(() -> service.create(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("User not found");
                }

                @Test
                @DisplayName("should fail when product not found for order item")
                void createOrder_ProductNotFound() {
                        CreateOrderRequest request = createValidCreateOrderRequest();
                        mockMerchantAndUser();
                        mockPersistNew();

                        when(productPort.findById(anyString()))
                                        .thenReturn(Uni.createFrom().failure(
                                                        new ResourceNotFoundException("Product not found: 1")));

                        assertThatThrownBy(() -> service.create(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Product not found");
                }

                @Test
                @DisplayName("should fail when insufficient stock")
                void createOrder_InsufficientStock() {
                        CreateOrderRequest request = createValidCreateOrderRequest();
                        mockMerchantAndUser();
                        mockPersistNew();

                        when(productPort.findById(anyString()))
                                        .thenReturn(Uni.createFrom().item(product("1", 500, 1)));

                        assertThatThrownBy(() -> service.create(request).await().indefinitely())
                                        .isInstanceOf(InvalidRequestException.class)
                                        .hasMessageContaining("Insufficient stock");
                }
        }

        @Test
        @DisplayName("should use authoritative product price and atomic stock delta")
        void createOrder_UsesAuthoritativePriceAndStockDelta() {
                CreateOrderRequest request = createValidCreateOrderRequest();
                request.getItems().get(0).setPrice(1);
                mockMerchantAndUser();
                mockShipping();
                mockPersistNew();

                when(productPort.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(product("1", 250, 100)));
                when(productPort.adjustStock(anyString(), anyInt()))
                                .thenReturn(Uni.createFrom().item(product("1", 250, 98)));
                when(orderItemPort.create(any()))
                                .thenReturn(Uni.createFrom().item(new OrderItem("oi-1", ORDER_ID, "1", 2, 250,
                                                Instant.now(), Instant.now())));

                service.create(request).await().indefinitely();

                ArgumentCaptor<String> productIdCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<Integer> deltaCaptor = ArgumentCaptor.forClass(Integer.class);
                verify(productPort).adjustStock(productIdCaptor.capture(), deltaCaptor.capture());
                assertThat(productIdCaptor.getValue()).isEqualTo("1");
                assertThat(deltaCaptor.getValue()).isEqualTo(-2);

                ArgumentCaptor<OrderItemPort.CreateData> itemCaptor =
                                ArgumentCaptor.forClass(OrderItemPort.CreateData.class);
                verify(orderItemPort).create(itemCaptor.capture());
                assertThat(itemCaptor.getValue().price()).isEqualTo(250);
        }

        @Nested
        @DisplayName("update order tests")
        class UpdateOrderTests {

                @Test
                @DisplayName("should fail when orderId is null")
                void updateOrder_NullOrderId() {
                        UpdateOrderRequest request = new UpdateOrderRequest();
                        request.setOrderId(null);
                        request.setUserId(USER_ID);

                        assertThatThrownBy(() -> service.update(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("OrderId is required");
                }

                @Test
                @DisplayName("should fail when order not found")
                void updateOrder_OrderNotFound() {
                        UpdateOrderRequest request = new UpdateOrderRequest();
                        request.setOrderId("999999999999999999999999");
                        request.setUserId(USER_ID);
                        request.setItems(List.of());
                        request.setShippingAddress(new com.sanedge.order.domain.requests.UpdateShippingAddressRequest());

                        when(orderQueryRepository.findOrderById(anyString()))
                                        .thenReturn(Uni.createFrom().item(Optional.empty()));

                        assertThatThrownBy(() -> service.update(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Order not found");
                }

                @Test
                @DisplayName("should fail with validation error when required fields are missing")
                void updateOrder_ValidationError() {
                        UpdateOrderRequest request = new UpdateOrderRequest();
                        request.setOrderId(ORDER_ID);
                        request.setUserId(USER_ID);
                        request.setItems(null);
                        request.setShippingAddress(null);

                        @SuppressWarnings("unchecked")
                        ConstraintViolation<Object> violation = (ConstraintViolation<Object>) org.mockito.Mockito
                                        .mock(ConstraintViolation.class);
                        lenient().when(violation.getPropertyPath())
                                        .thenReturn(org.mockito.Mockito.mock(jakarta.validation.Path.class));
                        lenient().when(violation.getMessage()).thenReturn("must not be null");
                        when(validator.validate(any())).thenReturn(Set.of(violation));

                        assertThatThrownBy(() -> service.update(request).await().indefinitely())
                                        .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
                }

                @Test
                @DisplayName("should reject update when user does not own the order")
                void updateOrder_DifferentOwner() {
                        UpdateOrderRequest request = new UpdateOrderRequest();
                        request.setOrderId(ORDER_ID);
                        request.setUserId(USER_ID_2);

                        Order existingOrder = createTestOrder(new ObjectId(ORDER_ID), new ObjectId(MERCHANT_ID),
                                        new ObjectId(USER_ID), 500);
                        when(orderQueryRepository.findOrderById(anyString()))
                                        .thenReturn(Uni.createFrom().item(Optional.of(existingOrder)));

                        when(userPort.findById(anyString()))
                                        .thenReturn(Uni.createFrom().item(user(USER_ID_2)));

                        assertThatThrownBy(() -> service.update(request).await().indefinitely())
                                        .isInstanceOf(ForbiddenException.class)
                                        .hasMessageContaining("not allowed");
                }

                @Test
                @DisplayName("should fail when user not found")
                void updateOrder_UserNotFound() {
                        UpdateOrderRequest request = new UpdateOrderRequest();
                        request.setOrderId(ORDER_ID);
                        request.setUserId(USER_ID_3);

                        Order existingOrder = createTestOrder(new ObjectId(ORDER_ID), new ObjectId(MERCHANT_ID),
                                        new ObjectId(USER_ID), 500);
                        when(orderQueryRepository.findOrderById(anyString()))
                                        .thenReturn(Uni.createFrom().item(Optional.of(existingOrder)));

                        when(userPort.findById(anyString()))
                                        .thenReturn(Uni.createFrom().failure(
                                                        new ResourceNotFoundException("User not found: " + USER_ID_3)));

                        assertThatThrownBy(() -> service.update(request).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("User not found");
                }
        }

        @Nested
        @DisplayName("trash order tests")
        class TrashOrderTests {

                @Test
                @DisplayName("should successfully trash existing order")
                void trashOrder_Success() {
                        String orderId = ORDER_ID;
                        Order trashedOrder = createTestOrder(new ObjectId(orderId), new ObjectId(MERCHANT_ID),
                                        new ObjectId(USER_ID), 500);
                        trashedOrder.setDeletedAt(Instant.now());

                        when(orderCommandRepository.trashed(orderId)).thenReturn(Uni.createFrom().item(trashedOrder));

                        ApiResponse<OrderResponseDeleteAt> response = service.trash(orderId).await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Order trashed successfully!");
                        assertThat(response.data()).isNotNull();
                        assertThat(response.data().getId()).isEqualTo(orderId);
                }

                @Test
                @DisplayName("should fail when order not found or already trashed")
                void trashOrder_NotFound() {
                        String orderId = "999999999999999999999999";

                        when(orderCommandRepository.trashed(orderId)).thenReturn(Uni.createFrom().nullItem());

                        assertThatThrownBy(() -> service.trash(orderId).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Order not found or already trashed");
                }
        }

        @Nested
        @DisplayName("restore order tests")
        class RestoreOrderTests {

                @Test
                @DisplayName("should successfully restore trashed order")
                void restoreOrder_Success() {
                        String orderId = ORDER_ID;
                        Order restoredOrder = createTestOrder(new ObjectId(orderId), new ObjectId(MERCHANT_ID),
                                        new ObjectId(USER_ID), 500);

                        when(orderCommandRepository.restore(orderId)).thenReturn(Uni.createFrom().item(restoredOrder));

                        ApiResponse<OrderResponseDeleteAt> response = service.restore(orderId).await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Order restored successfully!");
                        assertThat(response.data()).isNotNull();
                }

                @Test
                @DisplayName("should fail when order not found or not trashed")
                void restoreOrder_NotFound() {
                        String orderId = "999999999999999999999999";

                        when(orderCommandRepository.restore(orderId)).thenReturn(Uni.createFrom().nullItem());

                        assertThatThrownBy(() -> service.restore(orderId).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Order not found or not trashed");
                }
        }

        @Nested
        @DisplayName("delete order tests")
        class DeleteOrderTests {

                @Test
                @DisplayName("should successfully permanently delete trashed order")
                void deleteOrder_Success() {
                        String orderId = ORDER_ID;
                        Order deletedOrder = createTestOrder(new ObjectId(orderId), new ObjectId(MERCHANT_ID),
                                        new ObjectId(USER_ID), 500);

                        when(orderCommandRepository.deletePermanent(orderId))
                                        .thenReturn(Uni.createFrom().item(deletedOrder));

                        ApiResponse<Void> response = service.delete(orderId).await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Order permanently deleted!");
                }

                @Test
                @DisplayName("should fail when order not found or not trashed")
                void deleteOrder_NotFound() {
                        String orderId = "999999999999999999999999";

                        when(orderCommandRepository.deletePermanent(orderId)).thenReturn(Uni.createFrom().nullItem());

                        assertThatThrownBy(() -> service.delete(orderId).await().indefinitely())
                                        .isInstanceOf(InvalidRequestException.class)
                                        .hasMessageContaining("must be trashed");
                }
        }

        @Nested
        @DisplayName("restore all orders tests")
        class RestoreAllTests {

                @Test
                @DisplayName("should successfully restore all trashed orders")
                void restoreAll_Success() {
                        Order trashed = createTestOrder(new ObjectId(ORDER_ID), new ObjectId(MERCHANT_ID),
                                        new ObjectId(USER_ID), 500);
                        trashed.setDeletedAt(Instant.now());
                        when(orderCommandRepository.findAllDeleted())
                                        .thenReturn(Uni.createFrom().item(List.of(trashed)));
                        when(orderCommandRepository.restore(ORDER_ID))
                                        .thenReturn(Uni.createFrom().item(trashed));

                        ApiResponse<Void> response = service.restoreAll().await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("All orders restored successfully!");
                }

                @Test
                @DisplayName("should fail when no trashed orders found")
                void restoreAll_NoTrashedOrders() {
                        when(orderCommandRepository.findAllDeleted())
                                        .thenReturn(Uni.createFrom().item(Collections.emptyList()));

                        assertThatThrownBy(() -> service.restoreAll().await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("No trashed orders found");
                }
        }

        @Nested
        @DisplayName("delete all orders tests")
        class DeleteAllTests {

                @Test
                @DisplayName("should successfully delete all trashed orders")
                void deleteAll_Success() {
                        when(orderCommandRepository.deleteAllDeleted())
                                        .thenReturn(Uni.createFrom().item(true));

                        ApiResponse<Void> response = service.deleteAll().await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("All orders permanently deleted!");
                }

                @Test
                @DisplayName("should fail when no trashed orders found")
                void deleteAll_NoTrashedOrders() {
                        when(orderCommandRepository.deleteAllDeleted())
                                        .thenReturn(Uni.createFrom().item(false));

                        assertThatThrownBy(() -> service.deleteAll().await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("No trashed orders found");
                }
        }
}
