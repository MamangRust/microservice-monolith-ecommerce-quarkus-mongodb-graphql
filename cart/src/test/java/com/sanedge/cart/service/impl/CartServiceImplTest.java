package com.sanedge.cart.service.impl;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.cart.domain.requests.CreateCartRequest;
import com.sanedge.cart.domain.requests.DeleteCartRequest;
import com.sanedge.cart.domain.requests.FindAllCartsRequest;
import com.sanedge.cart.domain.response.CartResponse;
import com.sanedge.cart.entity.Cart;
import com.sanedge.cart.repository.CartCommandRepository;
import com.sanedge.cart.repository.CartQueryRepository;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

        private static final String USER_1 = "507f1f77bcf86cd799439011";
        private static final String USER_999 = "507f1f77bcf86cd799439099";
        private static final String PRODUCT_100 = "507f1f77bcf86cd799439064";
        private static final String PRODUCT_101 = "507f1f77bcf86cd799439065";
        private static final String CART_1 = "507f1f77bcf86cd799439001";
        private static final String CART_2 = "507f1f77bcf86cd799439002";
        private static final String CART_3 = "507f1f77bcf86cd799439003";
        private static final String CART_999 = "507f1f77bcf86cd799439099";

        @Mock
        private CartQueryRepository cartQueryRepository;

        @Mock
        private CartCommandRepository cartCommandRepository;

        @Mock
        private RedisService redisService;

        @Mock
        private TracingMetrics tracingMetrics;

        private CartServiceImpl cartService;
        private ObjectMapper objectMapper;

        @BeforeEach
        void setUp() throws Exception {
                objectMapper = new ObjectMapper();
                lenient().doAnswer(invokeSupplier())
                                .when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any());
                lenient().doAnswer(invokeSupplier())
                                .when(tracingMetrics)
                                .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());
                cartService = new CartServiceImpl(
                                cartQueryRepository,
                                cartCommandRepository,
                                redisService,
                                objectMapper,
                                tracingMetrics);
        }

        @Test
        @DisplayName("createCart - should successfully create a cart")
        void createCart_Success() {
                CreateCartRequest request = new CreateCartRequest();
                request.setUserId(USER_1);
                request.setProductId(PRODUCT_100);
                request.setQuantity(2);

                Cart savedCart = createTestCart(CART_1, USER_1, PRODUCT_100, "Product " + PRODUCT_100, 2);

                when(cartCommandRepository.persist(any(Cart.class)))
                                .thenReturn(Uni.createFrom().item(savedCart));

                ApiResponse<CartResponse> result = cartService.createCart(request)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Cart created successfully");
                assertThat(result.data()).isNotNull();
                assertThat(result.data().getUserId()).isEqualTo(USER_1);
                assertThat(result.data().getProductId()).isEqualTo(PRODUCT_100);
                assertThat(result.data().getQuantity()).isEqualTo(2);
                verify(cartCommandRepository).persist(any(Cart.class));
        }

        @Test
        @DisplayName("deletePermanent - should successfully delete cart permanently")
        void deletePermanent_Success() {
                String cartId = CART_1;
                Cart existingCart = createTestCart(cartId, USER_1, PRODUCT_100, "Product 100", 2);

                when(cartCommandRepository.findById(cartId))
                                .thenReturn(Uni.createFrom().item(existingCart));
                when(cartCommandRepository.deleteCartById(cartId))
                                .thenReturn(Uni.createFrom().item(true));

                ApiResponse<Void> result = cartService.deletePermanent(cartId)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Cart deleted permanently");
                verify(cartCommandRepository).findById(cartId);
                verify(cartCommandRepository).deleteCartById(cartId);
        }

        @Test
        @DisplayName("deletePermanent - should fail when cart not found")
        void deletePermanent_NotFound() {
                String cartId = CART_999;

                when(cartCommandRepository.findById(cartId))
                                .thenReturn(Uni.createFrom().nullItem());

                assertThatThrownBy(() -> cartService.deletePermanent(cartId).await().indefinitely())
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Cart not found with id: " + cartId);
        }

        @Test
        @DisplayName("deleteAllPermanently - should successfully delete all carts")
        void deleteAllPermanently_Success() {
                DeleteCartRequest request = new DeleteCartRequest();
                request.setCartIds(List.of(CART_1, CART_2, CART_3));

                when(cartCommandRepository.deleteCartsByIds(any()))
                                .thenReturn(Uni.createFrom().item(true));

                ApiResponse<Void> result = cartService.deleteAllPermanently(request)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.message()).isEqualTo("Carts deleted permanently");
                verify(cartCommandRepository).deleteCartsByIds(any());
        }

        @Test
        @DisplayName("findAll - should fetch from DB and cache when cache miss")
        void findAll_CacheMiss() {
                FindAllCartsRequest request = new FindAllCartsRequest();
                request.setUserId(USER_1);
                request.setPage(1);
                request.setPageSize(10);
                request.setSearch(null);

                Cart cart1 = createTestCart(CART_1, USER_1, PRODUCT_100, "Product 100", 2);
                Cart cart2 = createTestCart(CART_2, USER_1, PRODUCT_101, "Product 101", 1);

                PagedResult<Cart> pagedResult = new PagedResult<>(List.of(cart1, cart2), 2);

                when(redisService.getReactive(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());
                when(cartQueryRepository.findCartsByUser(any()))
                                .thenReturn(Uni.createFrom().item(pagedResult));
                when(redisService.setWithExpirationReactive(anyString(), anyString(),
                                org.mockito.ArgumentMatchers.anyLong()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponsePagination<List<CartResponse>> result = cartService.findAll(request)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.data()).hasSize(2);
                verify(cartQueryRepository).findCartsByUser(any());
                verify(redisService).setWithExpirationReactive(anyString(), anyString(),
                                org.mockito.ArgumentMatchers.anyLong());
        }

        @Test
        @DisplayName("findAll - should return empty list when no carts found")
        void findAll_EmptyList() {
                FindAllCartsRequest request = new FindAllCartsRequest();
                request.setUserId(USER_999);
                request.setPage(1);
                request.setPageSize(10);
                request.setSearch(null);

                PagedResult<Cart> emptyResult = new PagedResult<>(List.of(), 0);

                when(redisService.getReactive(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());
                when(cartQueryRepository.findCartsByUser(any()))
                                .thenReturn(Uni.createFrom().item(emptyResult));
                when(redisService.setWithExpirationReactive(anyString(), anyString(),
                                org.mockito.ArgumentMatchers.anyLong()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponsePagination<List<CartResponse>> result = cartService.findAll(request)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.status()).isEqualTo("success");
                assertThat(result.data()).isEmpty();
                assertThat(result.pagination().totalRecords()).isEqualTo(0);
        }

        @Test
        @DisplayName("findAll - should handle search query correctly")
        void findAll_WithSearch() {
                FindAllCartsRequest request = new FindAllCartsRequest();
                request.setUserId(USER_1);
                request.setPage(1);
                request.setPageSize(10);
                request.setSearch("Product 100");

                Cart cart1 = createTestCart(CART_1, USER_1, PRODUCT_100, "Product 100", 2);

                PagedResult<Cart> pagedResult = new PagedResult<>(List.of(cart1), 1);

                when(redisService.getReactive(anyString()))
                                .thenReturn(Uni.createFrom().nullItem());
                when(cartQueryRepository.findCartsByUser(any()))
                                .thenReturn(Uni.createFrom().item(pagedResult));
                when(redisService.setWithExpirationReactive(anyString(), anyString(),
                                org.mockito.ArgumentMatchers.anyLong()))
                                .thenReturn(Uni.createFrom().voidItem());

                ApiResponsePagination<List<CartResponse>> result = cartService.findAll(request)
                                .await().indefinitely();

                assertThat(result).isNotNull();
                assertThat(result.data()).hasSize(1);
                assertThat(result.data().get(0).getName()).isEqualTo("Product 100");
                verify(cartQueryRepository).findCartsByUser(any());
        }

        private Cart createTestCart(String id, String userId, String productId, String name, Integer quantity) {
                Cart cart = new Cart();
                cart.id = new ObjectId(id);
                cart.setUserId(new ObjectId(userId));
                cart.setProductId(new ObjectId(productId));
                cart.setName(name);
                cart.setPrice(100);
                cart.setImage("default.png");
                cart.setQuantity(quantity);
                cart.setWeight(100);
                Instant now = Instant.now();
                cart.setCreatedAt(now);
                cart.setUpdatedAt(now);
                return cart;
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
