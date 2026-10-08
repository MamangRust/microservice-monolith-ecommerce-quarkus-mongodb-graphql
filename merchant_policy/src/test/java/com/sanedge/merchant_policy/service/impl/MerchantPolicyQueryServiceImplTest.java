package com.sanedge.merchant_policy.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.merchant_policy.domain.requests.FindAllMerchantRequest;
import com.sanedge.merchant_policy.domain.response.MerchantPoliciesResponse;
import com.sanedge.merchant_policy.domain.response.MerchantPoliciesResponseDeleteAt;
import com.sanedge.merchant_policy.entity.MerchantPolicy;
import com.sanedge.merchant_policy.repository.MerchantPolicyQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class MerchantPolicyQueryServiceImplTest {

    private static final String ID_1 = "507f1f77bcf86cd799439011";
    private static final String ID_999 = "507f1f77bcf86cd799439099";
    private static final String MERCHANT_ID = "507f1f77bcf86cd799439021";

    @Mock
    private MerchantPolicyQueryRepository merchantPolicyQueryRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private MerchantPolicyQueryServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new MerchantPolicyQueryServiceImpl(
                merchantPolicyQueryRepository,
                redisService,
                objectMapper,
                tracingMetrics);

        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics)
                .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<Uni<?>> supplier = invocation.getArgument(2);
            return supplier.get();
        }).when(tracingMetrics)
                .traceAndMeasure(anyString(), anyString(), any());
    }

    private MerchantPolicy createTestPolicy(String id, String merchantId, String title) {
        MerchantPolicy policy = new MerchantPolicy();
        policy.id = new ObjectId(id);
        policy.setMerchantId(new ObjectId(merchantId));
        policy.setPolicyType("RETURN");
        policy.setTitle(title);
        policy.setDescription("Test description");
        policy.setCreatedAt(Instant.now());
        policy.setUpdatedAt(Instant.now());
        return policy;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize in test helper", e);
        }
    }

    private FindAllMerchantRequest buildRequest(int page, int size, String search) {
        FindAllMerchantRequest req = new FindAllMerchantRequest();
        req.setPage(page);
        req.setPageSize(size);
        req.setSearch(search);
        return req;
    }

    @Nested
    @DisplayName("findAll tests")
    class FindAllTests {

        @Test
        @DisplayName("cache miss - fetch from DB and cache result")
        void cacheMiss_fetchesFromDb() {
            FindAllMerchantRequest req = buildRequest(1, 10, "");

            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantPolicyQueryRepository.findMerchantPolicies(any(FindAllMerchantRequest.class)))
                    .thenReturn(Uni.createFrom().item(
                            new PagedResult<>(List.of(createTestPolicy(ID_1, MERCHANT_ID, "Policy1")), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<MerchantPoliciesResponse>> result = service.findAll(req)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.message()).isEqualTo("Merchant policies retrieved successfully");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getTitle()).isEqualTo("Policy1");
        }

        @Test
        @DisplayName("cache hit - return cached response without DB call")
        void cacheHit_returnsCached() {
            FindAllMerchantRequest req = buildRequest(1, 10, "");
            MerchantPoliciesResponse cachedData = MerchantPoliciesResponse.from(createTestPolicy(ID_1, MERCHANT_ID, "CachedPolicy"));
            ApiResponsePagination<List<MerchantPoliciesResponse>> cachedResponse =
                    new ApiResponsePagination<>("success", "Merchant policies retrieved successfully",
                            List.of(cachedData), null);

            when(redisService.getReactive(anyString()))
                    .thenReturn(Uni.createFrom().item(toJson(cachedResponse)));

            ApiResponsePagination<List<MerchantPoliciesResponse>> result = service.findAll(req)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getTitle()).isEqualTo("CachedPolicy");
        }

        @Test
        @DisplayName("cache miss with search parameter")
        void cacheMiss_withSearch() {
            FindAllMerchantRequest req = buildRequest(1, 10, "RETURN");

            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantPolicyQueryRepository.findMerchantPolicies(any(FindAllMerchantRequest.class)))
                    .thenReturn(Uni.createFrom().item(new PagedResult<>(List.of(), 0)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<MerchantPoliciesResponse>> result = service.findAll(req)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByActive tests")
    class FindByActiveTests {

        @Test
        @DisplayName("cache miss - fetch from DB and cache result")
        void cacheMiss_fetchesFromDb() {
            FindAllMerchantRequest req = buildRequest(1, 10, "");

            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantPolicyQueryRepository.findActiveMerchantPolicies(any(FindAllMerchantRequest.class)))
                    .thenReturn(Uni.createFrom().item(
                            new PagedResult<>(List.of(createTestPolicy(ID_1, MERCHANT_ID, "ActivePolicy")), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<MerchantPoliciesResponseDeleteAt>> result = service.findByActive(req)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.message()).isEqualTo("Active merchant policies retrieved successfully");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        @DisplayName("cache hit - return cached response")
        void cacheHit_returnsCached() {
            FindAllMerchantRequest req = buildRequest(1, 10, "");
            MerchantPoliciesResponseDeleteAt cachedData =
                    MerchantPoliciesResponseDeleteAt.from(createTestPolicy(ID_1, MERCHANT_ID, "CachedActive"));
            ApiResponsePagination<List<MerchantPoliciesResponseDeleteAt>> cachedResponse =
                    new ApiResponsePagination<>("success", "Active merchant policies retrieved successfully",
                            List.of(cachedData), null);

            when(redisService.getReactive(anyString()))
                    .thenReturn(Uni.createFrom().item(toJson(cachedResponse)));

            ApiResponsePagination<List<MerchantPoliciesResponseDeleteAt>> result = service.findByActive(req)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findByTrashed tests")
    class FindByTrashedTests {

        @Test
        @DisplayName("cache miss - fetch from DB and cache result")
        void cacheMiss_fetchesFromDb() {
            FindAllMerchantRequest req = buildRequest(1, 10, "");

            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantPolicyQueryRepository.findTrashedMerchantPolicies(any(FindAllMerchantRequest.class)))
                    .thenReturn(Uni.createFrom().item(
                            new PagedResult<>(List.of(createTestPolicy(ID_1, MERCHANT_ID, "TrashedPolicy")), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<MerchantPoliciesResponseDeleteAt>> result = service.findByTrashed(req)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.message()).isEqualTo("Trashed merchant policies retrieved successfully");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        @DisplayName("cache hit - return cached response")
        void cacheHit_returnsCached() {
            FindAllMerchantRequest req = buildRequest(1, 10, "");
            MerchantPoliciesResponseDeleteAt cachedData =
                    MerchantPoliciesResponseDeleteAt.from(createTestPolicy(ID_1, MERCHANT_ID, "CachedTrashed"));
            ApiResponsePagination<List<MerchantPoliciesResponseDeleteAt>> cachedResponse =
                    new ApiResponsePagination<>("success", "Trashed merchant policies retrieved successfully",
                            List.of(cachedData), null);

            when(redisService.getReactive(anyString()))
                    .thenReturn(Uni.createFrom().item(toJson(cachedResponse)));

            ApiResponsePagination<List<MerchantPoliciesResponseDeleteAt>> result = service.findByTrashed(req)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findById tests")
    class FindByIdTests {

        @Test
        @DisplayName("cache miss - fetch from DB and cache result")
        void cacheMiss_fetchesFromDb() {
            String id = ID_1;

            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantPolicyQueryRepository.findById(id))
                    .thenReturn(Uni.createFrom().item(createTestPolicy(id, MERCHANT_ID, "SinglePolicy")));
            when(redisService.setReactive(anyString(), anyString()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponse<MerchantPoliciesResponse> result = service.findById(id)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.message()).isEqualTo("Merchant policy retrieved successfully");
            assertThat(result.data().getTitle()).isEqualTo("SinglePolicy");
        }

        @Test
        @DisplayName("cache hit - return cached response")
        void cacheHit_returnsCached() {
            String id = ID_1;
            MerchantPoliciesResponse cached = MerchantPoliciesResponse.from(createTestPolicy(id, MERCHANT_ID, "CachedSingle"));

            when(redisService.getReactive("merchantpolicy:id:" + id))
                    .thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<MerchantPoliciesResponse> result = service.findById(id)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data().getTitle()).isEqualTo("CachedSingle");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void notFound_throwsException() {
            String id = ID_999;

            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantPolicyQueryRepository.findById(id))
                    .thenReturn(Uni.createFrom().nullItem());

            assertThatThrownBy(() -> service.findById(id).await().indefinitely())
                    .isInstanceOf(com.sanedge.common.exception.ResourceNotFoundException.class)
                    .hasMessageContaining("Merchant policy not found with id=" + ID_999);
        }
    }
}
