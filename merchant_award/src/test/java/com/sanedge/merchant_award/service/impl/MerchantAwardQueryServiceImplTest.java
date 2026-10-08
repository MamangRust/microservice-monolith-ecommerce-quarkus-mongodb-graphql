package com.sanedge.merchant_award.service.impl;

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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.domain.response.ApiResponsePagination;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.merchant_award.domain.requests.FindAllMerchantRequest;
import com.sanedge.merchant_award.domain.response.MerchantAwardResponse;
import com.sanedge.merchant_award.domain.response.MerchantAwardResponseDeleteAt;
import com.sanedge.merchant_award.entity.MerchantCertificationAndAward;
import com.sanedge.merchant_award.repository.MerchantAwardQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.NotFoundException;

@ExtendWith(MockitoExtension.class)
class MerchantAwardQueryServiceImplTest {

    private static final String ID_1 = "507f1f77bcf86cd799439011";
    private static final String ID_999 = "507f1f77bcf86cd799439099";
    private static final String MERCHANT_ID = "507f1f77bcf86cd799439021";

    @Mock
    private MerchantAwardQueryRepository merchantAwardQueryRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private MerchantAwardQueryServiceImpl service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new MerchantAwardQueryServiceImpl(
                merchantAwardQueryRepository,
                redisService,
                objectMapper,
                tracingMetrics);

        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics)
                .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(2);
            return supplier.get();
        }).when(tracingMetrics)
                .traceAndMeasure(anyString(), anyString(), any());
    }

    private MerchantCertificationAndAward createTestAward(String id, String merchantId, String title) {
        MerchantCertificationAndAward award = new MerchantCertificationAndAward();

        award.id = new ObjectId(id);
        award.setMerchantId(new ObjectId(merchantId));
        award.setTitle(title);
        award.setDescription("Test description");
        award.setIssuedBy("Issuer");
        award.setIssueDate(Instant.parse("2024-01-01T00:00:00Z"));
        award.setExpiryDate(Instant.parse("2025-01-01T00:00:00Z"));
        award.setCertificateUrl("http://example.com/file.pdf");
        award.setCreatedAt(Instant.now());
        award.setUpdatedAt(Instant.now());
        return award;
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
            when(merchantAwardQueryRepository.findMerchantAwards(any(FindAllMerchantRequest.class)))
                    .thenReturn(Uni.createFrom().item(
                            new PagedResult<>(List.of(createTestAward(ID_1, MERCHANT_ID, "Award 1")), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<MerchantAwardResponse>> result = service.findAll(req)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.message()).isEqualTo("Merchant awards retrieved successfully");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getTitle()).isEqualTo("Award 1");
        }

        @Test
        @DisplayName("cache hit - return cached response without DB call")
        void cacheHit_returnsCached() {
            FindAllMerchantRequest req = buildRequest(1, 10, "");
            MerchantAwardResponse cachedData = MerchantAwardResponse.from(createTestAward(ID_1, MERCHANT_ID, "Cached Award"));
            ApiResponsePagination<List<MerchantAwardResponse>> cachedResponse =
                    new ApiResponsePagination<>("success", "Merchant awards retrieved successfully",
                            List.of(cachedData), null);

            when(redisService.getReactive(anyString()))
                    .thenReturn(Uni.createFrom().item(toJson(cachedResponse)));

            ApiResponsePagination<List<MerchantAwardResponse>> result = service.findAll(req)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data()).hasSize(1);
            assertThat(result.data().get(0).getTitle()).isEqualTo("Cached Award");
        }

        @Test
        @DisplayName("cache miss with search parameter")
        void cacheMiss_withSearch() {
            FindAllMerchantRequest req = buildRequest(1, 10, "ISO");

            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantAwardQueryRepository.findMerchantAwards(any(FindAllMerchantRequest.class)))
                    .thenReturn(Uni.createFrom().item(new PagedResult<>(List.of(), 0)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<MerchantAwardResponse>> result = service.findAll(req)
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
            when(merchantAwardQueryRepository.findActiveMerchantAwards(any(FindAllMerchantRequest.class)))
                    .thenReturn(Uni.createFrom().item(
                            new PagedResult<>(List.of(createTestAward(ID_1, MERCHANT_ID, "Active Award")), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<MerchantAwardResponseDeleteAt>> result = service.findByActive(req)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.message()).isEqualTo("Active merchant awards retrieved successfully");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        @DisplayName("cache hit - return cached response")
        void cacheHit_returnsCached() {
            FindAllMerchantRequest req = buildRequest(1, 10, "");
            MerchantAwardResponseDeleteAt cachedData =
                    MerchantAwardResponseDeleteAt.from(createTestAward(ID_1, MERCHANT_ID, "Cached Active"));
            ApiResponsePagination<List<MerchantAwardResponseDeleteAt>> cachedResponse =
                    new ApiResponsePagination<>("success", "Active merchant awards retrieved successfully",
                            List.of(cachedData), null);

            when(redisService.getReactive(anyString()))
                    .thenReturn(Uni.createFrom().item(toJson(cachedResponse)));

            ApiResponsePagination<List<MerchantAwardResponseDeleteAt>> result = service.findByActive(req)
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
            when(merchantAwardQueryRepository.findTrashedMerchantAwards(any(FindAllMerchantRequest.class)))
                    .thenReturn(Uni.createFrom().item(
                            new PagedResult<>(List.of(createTestAward(ID_1, MERCHANT_ID, "Trashed Award")), 1)));
            when(redisService.setWithExpirationReactive(anyString(), anyString(), anyLong()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponsePagination<List<MerchantAwardResponseDeleteAt>> result = service.findByTrashed(req)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.message()).isEqualTo("Trashed merchant awards retrieved successfully");
            assertThat(result.data()).hasSize(1);
        }

        @Test
        @DisplayName("cache hit - return cached response")
        void cacheHit_returnsCached() {
            FindAllMerchantRequest req = buildRequest(1, 10, "");
            MerchantAwardResponseDeleteAt cachedData =
                    MerchantAwardResponseDeleteAt.from(createTestAward(ID_1, MERCHANT_ID, "Cached Trashed"));
            ApiResponsePagination<List<MerchantAwardResponseDeleteAt>> cachedResponse =
                    new ApiResponsePagination<>("success", "Trashed merchant awards retrieved successfully",
                            List.of(cachedData), null);

            when(redisService.getReactive(anyString()))
                    .thenReturn(Uni.createFrom().item(toJson(cachedResponse)));

            ApiResponsePagination<List<MerchantAwardResponseDeleteAt>> result = service.findByTrashed(req)
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
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantAwardQueryRepository.findMerchantAwardById(ID_1))
                    .thenReturn(Uni.createFrom().item(createTestAward(ID_1, MERCHANT_ID, "Single Award")));
            when(redisService.setReactive(anyString(), anyString()))
                    .thenReturn(Uni.createFrom().voidItem());

            ApiResponse<MerchantAwardResponse> result = service.findById(ID_1)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.message()).isEqualTo("Merchant award retrieved successfully");
            assertThat(result.data().getId()).isEqualTo(ID_1);
            assertThat(result.data().getTitle()).isEqualTo("Single Award");
        }

        @Test
        @DisplayName("cache hit - return cached award")
        void cacheHit_returnsCached() {
            MerchantAwardResponse cached = MerchantAwardResponse.from(createTestAward(ID_1, MERCHANT_ID, "Cached Single"));

            when(redisService.getReactive("merchantawards:id:" + ID_1))
                    .thenReturn(Uni.createFrom().item(toJson(cached)));

            ApiResponse<MerchantAwardResponse> result = service.findById(ID_1)
                    .await().indefinitely();

            assertThat(result.status()).isEqualTo("success");
            assertThat(result.data().getId()).isEqualTo(ID_1);
            assertThat(result.data().getTitle()).isEqualTo("Cached Single");
        }

        @Test
        @DisplayName("should throw NotFoundException when award not found")
        void notFound_throwsException() {
            when(redisService.getReactive(anyString())).thenReturn(Uni.createFrom().nullItem());
            when(merchantAwardQueryRepository.findMerchantAwardById(ID_999))
                    .thenReturn(Uni.createFrom().nullItem());

            assertThatThrownBy(() -> service.findById(ID_999).await().indefinitely())
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Merchant award not found with id: " + ID_999);
        }
    }
}
