package com.sanedge.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.RedisResource;
import com.sanedge.order.domain.requests.CreateOrderItemRequest;
import com.sanedge.order.domain.requests.CreateOrderRequest;
import com.sanedge.order.domain.requests.CreateShippingAddressRequest;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.repository.OrderCommandRepository;
import com.sanedge.order.repository.OrderQueryRepository;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Validator;

/**
 * Resilience integration test for order creation with a real MongoDB:
 * when the second order item hits insufficient stock, the stock reserved for
 * the first item must be compensated back (adjustStock with the opposite
 * delta) and the partially-created order must be rolled back so no order
 * remains in the database.
 *
 * <p>The service under test is assembled manually with the real Panache
 * repositories (MongoDB via Testcontainers) and Mockito Port implementations
 * assigned to the package-private fields, so no gRPC call leaves the process.
 * The service now depends on the {@code common/adapter} ports instead of raw
 * gRPC stubs.</p>
 */
@QuarkusTest
@QuarkusTestResource(value = MongoDbResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = RedisResource.class, restrictToAnnotatedClass = true)
class OrderStockCompensationIT {

    private static final String MERCHANT_ID = "507f1f77bcf86cd7994390a1";
    private static final String USER_ID = "507f1f77bcf86cd7994390b1";
    private static final String PRODUCT_ID_1 = "507f1f77bcf86cd7994390c1";
    private static final String PRODUCT_ID_2 = "507f1f77bcf86cd7994390c2";

    @Inject
    OrderQueryRepository orderQueryRepo;

    @Inject
    OrderCommandRepository orderCommandRepo;

    @Inject
    Validator validator;

    private RedisService redisService;
    private TracingMetrics tracingMetrics;
    private MerchantPort merchantPort;
    private UserPort userPort;
    private ProductPort productPort;
    private OrderItemPort orderItemPort;
    private TransactionPort transactionPort;
    private ShippingAddressPort shippingAddressPort;

    private OrderCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        redisService = mock(RedisService.class);
        tracingMetrics = mock(TracingMetrics.class);
        merchantPort = mock(MerchantPort.class);
        userPort = mock(UserPort.class);
        productPort = mock(ProductPort.class);
        orderItemPort = mock(OrderItemPort.class);
        transactionPort = mock(TransactionPort.class);
        shippingAddressPort = mock(ShippingAddressPort.class);

        service = new OrderCommandServiceImpl(orderQueryRepo, orderCommandRepo, validator,
                redisService, tracingMetrics);
        service.merchantPort = merchantPort;
        service.userPort = userPort;
        service.productPort = productPort;
        service.orderItemPort = orderItemPort;
        service.transactionPort = transactionPort;
        service.shippingAddressPort = shippingAddressPort;

        lenient().doAnswer(invocation -> {
            Supplier<?> supplier = null;
            for (Object arg : invocation.getArguments()) {
                if (arg instanceof Supplier<?>) {
                    supplier = (Supplier<?>) arg;
                    break;
                }
            }
            return supplier != null ? supplier.get() : null;
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any());
        lenient().doAnswer(invocation -> {
            Supplier<?> supplier = null;
            for (Object arg : invocation.getArguments()) {
                if (arg instanceof Supplier<?>) {
                    supplier = (Supplier<?>) arg;
                    break;
                }
            }
            return supplier != null ? supplier.get() : null;
        }).when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any(io.opentelemetry.api.common.Attributes.class), any());

        lenient().when(redisService.deleteReactive(anyString()))
                .thenReturn(Uni.createFrom().voidItem());
        lenient().when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());
        lenient().when(redisService.getReactive(anyString()))
                .thenReturn(Uni.createFrom().nullItem());
    }

    private void mockHappyPath() {
        when(merchantPort.findById(anyString()))
                .thenReturn(Uni.createFrom().item(new Merchant(MERCHANT_ID, null, "Merchant", null, null,
                        null, null, null, Instant.now(), Instant.now())));

        when(userPort.findById(anyString()))
                .thenReturn(Uni.createFrom().item(new User(USER_ID, "John", "Doe",
                        "john.doe@example.com", null, Instant.now(), Instant.now())));

        // Product 1 has stock 100 (reserved OK); product 2 has stock 0 (fails).
        when(productPort.findById(anyString()))
                .thenAnswer(invocation -> {
                    String productId = invocation.getArgument(0);
                    int stock = PRODUCT_ID_1.equals(productId) ? 100 : 0;
                    return Uni.createFrom().item(new Product(productId, null, null, "Product", null,
                            500, stock, null, 0, 0f, null, null, Instant.now(), Instant.now()));
                });

        when(productPort.adjustStock(anyString(), anyInt()))
                .thenReturn(Uni.createFrom().item(new Product(PRODUCT_ID_1, null, null, "Product", null,
                        500, 98, null, 0, 0f, null, null, Instant.now(), Instant.now())));

        when(orderItemPort.create(any()))
                .thenReturn(Uni.createFrom().item(new OrderItem("oi-1", null, PRODUCT_ID_1, 2, 500,
                        Instant.now(), Instant.now())));
        when(orderItemPort.deleteByOrderIdRollback(anyString()))
                .thenReturn(Uni.createFrom().item(true));
        when(orderItemPort.findOrderItemByOrder(anyString()))
                .thenReturn(Uni.createFrom().item(List.of()));

        when(shippingAddressPort.create(any()))
                .thenReturn(Uni.createFrom().item(new ShippingAddress("sa-1", null, "123 Test Street",
                        "Test Province", "Indonesia", "Test City", "REG", 1000, Instant.now(), Instant.now())));

        when(orderCommandRepo.deleteCreated(anyString()))
                .thenReturn(Uni.createFrom().item(true));
    }

    private CreateOrderRequest requestWithItems(boolean withFailingSecond) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setMerchantId(MERCHANT_ID);
        request.setUserId(USER_ID);

        CreateOrderItemRequest item1 = new CreateOrderItemRequest();
        item1.setProductId(PRODUCT_ID_1);
        item1.setQuantity(2);
        item1.setPrice(500);
        if (withFailingSecond) {
            CreateOrderItemRequest item2 = new CreateOrderItemRequest();
            item2.setProductId(PRODUCT_ID_2);
            item2.setQuantity(5);
            item2.setPrice(500);
            request.setItems(List.of(item1, item2));
        } else {
            request.setItems(List.of(item1));
        }

        CreateShippingAddressRequest shipping = new CreateShippingAddressRequest();
        shipping.setAlamat("123 Test Street");
        shipping.setProvinsi("Test Province");
        shipping.setKota("Test City");
        shipping.setCourier("Test Courier");
        shipping.setShippingMethod("REG");
        shipping.setShippingCost(1000);
        shipping.setNegara("Indonesia");
        request.setShippingAddress(shipping);

        return request;
    }

    private Uni<Void> cleanOrders() {
        return orderQueryRepo.deleteAll().replaceWithVoid();
    }

    @Test
    void createRollsBackAndCompensatesWhenSecondItemFails() {
        mockHappyPath();

        cleanOrders()
                .chain(() -> service.create(requestWithItems(true))
                        .onFailure().invoke(error -> assertThat(error)
                                .isInstanceOf(com.sanedge.common.exception.InvalidRequestException.class))
                        .onFailure().recoverWithItem(error -> ApiResponse.<OrderResponse>success(
                                "expected failure", null)))
                .chain(() -> orderQueryRepo.count())
                .invoke(count -> assertThat(count).isZero())
                .invoke(() -> {
                    ArgumentCaptor<String> productIdCaptor = ArgumentCaptor.forClass(String.class);
                    ArgumentCaptor<Integer> deltaCaptor = ArgumentCaptor.forClass(Integer.class);
                    verify(productPort, times(2)).adjustStock(productIdCaptor.capture(), deltaCaptor.capture());
                    List<String> productIds = productIdCaptor.getAllValues();
                    List<Integer> deltas = deltaCaptor.getAllValues();
                    assertThat(productIds).hasSize(2);
                    assertThat(productIds.get(0)).isEqualTo(PRODUCT_ID_1);
                    assertThat(deltas.get(0)).isEqualTo(-2);
                    assertThat(productIds.get(1)).isEqualTo(PRODUCT_ID_1);
                    assertThat(deltas.get(1)).isEqualTo(2);
                    verify(orderItemPort, atLeastOnce())
                            .deleteByOrderIdRollback(any());
                    verify(tracingMetrics, atLeastOnce())
                            .recordStockCompensation(eq("success"), eq(1));
                })
                .await().indefinitely();
    }

    @Test
    void happyPathDoesNotCompensate() {
        mockHappyPath();

        cleanOrders()
                .chain(() -> service.create(requestWithItems(false)))
                .invoke(response -> assertThat(response).isNotNull())
                .chain(() -> orderQueryRepo.count())
                .invoke(count -> assertThat(count).isEqualTo(1))
                .invoke(() -> {
                    verify(productPort, times(1)).adjustStock(anyString(), anyInt());
                    verify(tracingMetrics, never()).recordStockCompensation(any(), any());
                })
                .await().indefinitely();
    }
}
