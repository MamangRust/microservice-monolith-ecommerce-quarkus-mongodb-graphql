package com.sanedge.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order.domain.requests.FindAllOrderByMerchantRequest;
import com.sanedge.order.domain.requests.FindAllOrderRequest;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.entity.Order;
import com.sanedge.order.repository.OrderQueryRepository;

import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceImplTest {

    private static final String MERCHANT_ID = "507f1f77bcf86cd7994390a1";
    private static final String USER_ID = "507f1f77bcf86cd7994390b1";
    private static final String ORDER_ID = "507f1f77bcf86cd799439011";

    @Mock
    private OrderQueryRepository orderQueryRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private OrderQueryServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Answer<Uni<?>> invokeSupplierOrFunction() {
        return invocation -> {
            for (Object arg : invocation.getArguments()) {
                if (arg instanceof Supplier) {
                    return ((Supplier<Uni<?>>) arg).get();
                } else if (arg instanceof Function) {
                    return ((Function<Object, Uni<?>>) arg).apply(null);
                }
            }
            return Uni.createFrom().nullItem();
        };
    }

    @BeforeEach
    void setUp() {
        service = new OrderQueryServiceImpl(orderQueryRepository, redisService, objectMapper, tracingMetrics);

        lenient().doAnswer(invokeSupplierOrFunction()).when(tracingMetrics)
                .traceAndMeasure(any(), any(), any());
        lenient().doAnswer(invokeSupplierOrFunction()).when(tracingMetrics)
                .traceAndMeasure(any(), any(), any(), any());
    }

    private Order createMockOrder(ObjectId id) {
        Order o = new Order();
        o.id = id;
        o.setMerchantId(new ObjectId(MERCHANT_ID));
        o.setUserId(new ObjectId(USER_ID));
        o.setTotalPrice(500);
        return o;
    }

    @Test
    void findAll_cacheMiss_returnsFromDb() {
        FindAllOrderRequest req = new FindAllOrderRequest();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("");

        PagedResult<Order> paged = new PagedResult<>(List.of(createMockOrder(new ObjectId(ORDER_ID))), 1);

        when(redisService.getReactive(any())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findOrders(any())).thenReturn(Uni.createFrom().item(paged));
        when(redisService.setWithExpirationReactive(any(), any(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<OrderResponse>> result = service.findAll(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }

    @Test
    void findById_cacheMiss_returnsFromDb() {
        Order o = createMockOrder(new ObjectId(ORDER_ID));

        when(redisService.getReactive(any())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findOrderById(anyString())).thenReturn(Uni.createFrom().item(Optional.of(o)));
        when(redisService.setReactive(any(), any())).thenReturn(Uni.createFrom().voidItem());

        ApiResponse<OrderResponse> result = service.findById(ORDER_ID).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data().getId()).isEqualTo(ORDER_ID);
    }

    @Test
    void findById_notFound_throwsResourceNotFoundException() {
        when(redisService.getReactive(any())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findOrderById(anyString())).thenReturn(Uni.createFrom().item(Optional.empty()));

        try {
            service.findById("999999999999999999999999").await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("Order not found");
        }
    }

    @Test
    void findByActive_cacheMiss_returnsFromDb() {
        FindAllOrderRequest req = new FindAllOrderRequest();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("");

        PagedResult<Order> paged = new PagedResult<>(List.of(createMockOrder(new ObjectId(ORDER_ID))), 1);

        when(redisService.getReactive(any())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findActiveOrders(any())).thenReturn(Uni.createFrom().item(paged));
        when(redisService.setWithExpirationReactive(any(), any(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<OrderResponseDeleteAt>> result = service.findByActive(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data()).hasSize(1);
    }

    @Test
    void findByTrashed_cacheMiss_returnsFromDb() {
        FindAllOrderRequest req = new FindAllOrderRequest();
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("");

        PagedResult<Order> paged = new PagedResult<>(List.of(createMockOrder(new ObjectId(ORDER_ID))), 1);

        when(redisService.getReactive(any())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findTrashedOrders(any())).thenReturn(Uni.createFrom().item(paged));
        when(redisService.setWithExpirationReactive(any(), any(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<OrderResponseDeleteAt>> result = service.findByTrashed(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
    }

    @Test
    void findByMerchantId_cacheMiss_returnsFromDb() {
        FindAllOrderByMerchantRequest req = new FindAllOrderByMerchantRequest();
        req.setMerchantId(MERCHANT_ID);
        req.setPage(1);
        req.setPageSize(10);
        req.setSearch("");

        PagedResult<Order> paged = new PagedResult<>(List.of(createMockOrder(new ObjectId(ORDER_ID))), 1);

        when(redisService.getReactive(any())).thenReturn(Uni.createFrom().nullItem());
        when(orderQueryRepository.findOrdersByMerchant(any())).thenReturn(Uni.createFrom().item(paged));
        when(redisService.setWithExpirationReactive(any(), any(), anyLong()))
                .thenReturn(Uni.createFrom().voidItem());

        ApiResponsePagination<List<OrderResponse>> result = service.findByMerchantId(req).await().indefinitely();

        assertThat(result.status()).isEqualTo("success");
    }
}
