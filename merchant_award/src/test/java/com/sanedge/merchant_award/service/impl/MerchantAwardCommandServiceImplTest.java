package com.sanedge.merchant_award.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.merchant_award.domain.requests.CreateMerchantAwardRequest;
import com.sanedge.merchant_award.domain.requests.UpdateMerchantAwardRequest;
import com.sanedge.merchant_award.domain.response.MerchantAwardResponse;
import com.sanedge.merchant_award.domain.response.MerchantAwardResponseDeleteAt;
import com.sanedge.merchant_award.entity.MerchantCertificationAndAward;
import com.sanedge.merchant_award.repository.MerchantAwardCommandRepository;

import com.sanedge.common.adapter.merchant.MerchantPort;
import com.sanedge.common.adapter.model.Merchant;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class MerchantAwardCommandServiceImplTest {

    private static final String ID_1 = "507f1f77bcf86cd799439011";
    private static final String ID_999 = "507f1f77bcf86cd799439099";
    private static final String MERCHANT_ID = "507f1f77bcf86cd799439021";

    @Mock
    private MerchantAwardCommandRepository merchantAwardCommandRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    @Mock
    private MerchantPort merchantPort;

    private MerchantAwardCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MerchantAwardCommandServiceImpl(
                merchantAwardCommandRepository,
                redisService,
                tracingMetrics,
                merchantPort);

        lenient().when(redisService.deleteReactive(anyString()))
                .thenReturn(Uni.createFrom().voidItem());

        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(tracingMetrics)
                .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any(Supplier.class));

        lenient().doAnswer(invocation -> {
            Supplier<Uni<?>> supplier = invocation.getArgument(2);
            return supplier.get();
        }).when(tracingMetrics)
                .traceAndMeasure(anyString(), anyString(), any(Supplier.class));

        lenient().when(merchantAwardCommandRepository.restoreAllDeleted())
                .thenReturn(Uni.createFrom().item(true));
        lenient().when(merchantAwardCommandRepository.deleteAllDeleted())
                .thenReturn(Uni.createFrom().item(true));
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

    private CreateMerchantAwardRequest createValidCreateRequest() {
        CreateMerchantAwardRequest req = new CreateMerchantAwardRequest();
        req.setMerchantId(MERCHANT_ID);
        req.setTitle("Best Merchant");
        req.setDescription("Award description");
        req.setIssuedBy("ISO Organization");
        req.setIssueDate("2024-06-15");
        req.setExpiryDate("2025-06-15");
        req.setCertificateUrl("http://example.com/award.pdf");
        return req;
    }

    private UpdateMerchantAwardRequest createValidUpdateRequest() {
        UpdateMerchantAwardRequest req = new UpdateMerchantAwardRequest();
        req.setMerchantCertificationId(ID_1);
        req.setTitle("Updated Award");
        req.setDescription("Updated description");
        req.setIssuedBy("Updated Issuer");
        req.setIssueDate("2024-07-01");
        req.setExpiryDate("2025-07-01");
        req.setCertificateUrl("http://example.com/updated.pdf");
        return req;
    }

    private void mockMerchantExists(String merchantId) {
        when(merchantPort.findById(anyString()))
                .thenReturn(Uni.createFrom().item(new Merchant(merchantId, null, "Merchant", null, null,
                        null, null, null, Instant.now(), Instant.now())));
    }

    private void mockMerchantNotFound() {
        when(merchantPort.findById(anyString()))
                .thenReturn(Uni.createFrom().failure(
                        new ResourceNotFoundException("Merchant not found with id " + MERCHANT_ID)));
    }

    @Nested
    @DisplayName("create merchant award tests")
    class CreateTests {

        @Test
        @DisplayName("should successfully create merchant award on happy path")
        void create_Success() {
            CreateMerchantAwardRequest req = createValidCreateRequest();
            mockMerchantExists(req.getMerchantId());

            when(merchantAwardCommandRepository.persist(any(MerchantCertificationAndAward.class)))
                    .thenAnswer(inv -> {
                        MerchantCertificationAndAward a = inv.getArgument(0);
                        a.id = new ObjectId(ID_1);
                        return Uni.createFrom().item(a);
                    });

            ApiResponse<MerchantAwardResponse> response = service.createMerchantAward(req)
                    .await().indefinitely();

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("Merchant award created successfully");
            assertThat(response.data()).isNotNull();
            assertThat(response.data().getId()).isEqualTo(ID_1);

            verify(redisService).deleteReactive("merchantawards:id:" + ID_1);
        }

        @Test
        @DisplayName("should fail when merchant not found")
        void create_MerchantNotFound() {
            CreateMerchantAwardRequest req = createValidCreateRequest();
            mockMerchantNotFound();

            assertThatThrownBy(() -> service.createMerchantAward(req).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Merchant not found with id " + req.getMerchantId());
        }
    }

    @Nested
    @DisplayName("update merchant award tests")
    class UpdateTests {

        @Test
        @DisplayName("should successfully update merchant award on happy path")
        void update_Success() {
            UpdateMerchantAwardRequest req = createValidUpdateRequest();
            MerchantCertificationAndAward existing = createTestAward(ID_1, MERCHANT_ID, "Old Award");

            when(merchantAwardCommandRepository.findById(ID_1))
                    .thenReturn(Uni.createFrom().item(existing));

            when(merchantAwardCommandRepository.update(any(MerchantCertificationAndAward.class)))
                    .thenAnswer(inv -> {
                        MerchantCertificationAndAward award = inv.getArgument(0);
                        return Uni.createFrom().item((Object) award);
                    });

            ApiResponse<MerchantAwardResponse> response = service.updateMerchantAward(req)
                    .await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("Merchant award updated successfully");
            assertThat(response.data().getId()).isEqualTo(ID_1);
            assertThat(response.data().getTitle()).isEqualTo("Updated Award");

            verify(redisService).deleteReactive("merchantawards:id:" + ID_1);
        }

        @Test
        @DisplayName("should fail when award not found")
        void update_NotFound() {
            UpdateMerchantAwardRequest req = createValidUpdateRequest();

            when(merchantAwardCommandRepository.findById(ID_1))
                    .thenReturn(Uni.createFrom().nullItem());

            assertThatThrownBy(() -> service.updateMerchantAward(req).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Merchant award not found with id " + req.getMerchantCertificationId());
        }

        @Test
        @DisplayName("should fail when merchantCertificationId is null")
        void update_NullId() {
            UpdateMerchantAwardRequest req = new UpdateMerchantAwardRequest();

            assertThatThrownBy(() -> service.updateMerchantAward(req).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("MerchantCertificationId is required");
        }
    }

    @Nested
    @DisplayName("trash merchant award tests")
    class TrashTests {

        @Test
        @DisplayName("should successfully trash existing merchant award")
        void trash_Success() {
            MerchantCertificationAndAward award = createTestAward(ID_1, MERCHANT_ID, "Award to trash");

            when(merchantAwardCommandRepository.trashed(ID_1))
                    .thenReturn(Uni.createFrom().item(award));

            ApiResponse<MerchantAwardResponseDeleteAt> response = service.trashedMerchantAward(ID_1)
                    .await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("Merchant award trashed successfully");
            assertThat(response.data().getId()).isEqualTo(ID_1);

            verify(redisService).deleteReactive("merchantawards:id:" + ID_1);
        }

        @Test
        @DisplayName("should fail when award not found for trash")
        void trash_NotFound() {
            when(merchantAwardCommandRepository.trashed(ID_999))
                    .thenReturn(Uni.createFrom().nullItem());

            assertThatThrownBy(() -> service.trashedMerchantAward(ID_999).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Merchant award not found or already trashed");
        }
    }

    @Nested
    @DisplayName("restore merchant award tests")
    class RestoreTests {

        @Test
        @DisplayName("should successfully restore trashed merchant award")
        void restore_Success() {
            MerchantCertificationAndAward award = createTestAward(ID_1, MERCHANT_ID, "Restored Award");

            when(merchantAwardCommandRepository.restore(ID_1))
                    .thenReturn(Uni.createFrom().item(award));

            ApiResponse<MerchantAwardResponseDeleteAt> response = service.restoreMerchantAward(ID_1)
                    .await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("Merchant award restored successfully");
            assertThat(response.data().getId()).isEqualTo(ID_1);

            verify(redisService).deleteReactive("merchantawards:id:" + ID_1);
        }

        @Test
        @DisplayName("should fail when award is not trashed")
        void restore_NotTrashed() {
            when(merchantAwardCommandRepository.restore(ID_1))
                    .thenReturn(Uni.createFrom().nullItem());

            assertThatThrownBy(() -> service.restoreMerchantAward(ID_1).await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Merchant award not found or not trashed");
        }
    }

    @Nested
    @DisplayName("delete merchant award permanent tests")
    class DeletePermanentTests {

        @Test
        @DisplayName("should successfully permanently delete trashed award")
        void deletePermanent_Success() {
            MerchantCertificationAndAward award = createTestAward(ID_1, MERCHANT_ID, "To be deleted");
            award.setDeletedAt(Instant.now());

            when(merchantAwardCommandRepository.deletePermanent(ID_1))
                    .thenReturn(Uni.createFrom().item(award));

            ApiResponse<Void> response = service.deleteMerchantAwardPermanent(ID_1)
                    .await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("Merchant award permanently deleted");

            verify(redisService).deleteReactive("merchantawards:id:" + ID_1);
        }

        @Test
        @DisplayName("should fail when award not found or not trashed")
        void deletePermanent_NotFound() {
            when(merchantAwardCommandRepository.deletePermanent(ID_999))
                    .thenReturn(Uni.createFrom().nullItem());

            assertThatThrownBy(() -> service.deleteMerchantAwardPermanent(ID_999).await().indefinitely())
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Merchant award not found or must be trashed before permanent deletion");
        }
    }

    @Nested
    @DisplayName("restore all trashed awards tests")
    class RestoreAllTests {

        @Test
        @DisplayName("should successfully restore all trashed awards")
        void restoreAll_Success() {
            ApiResponse<Void> response = service.restoreAllMerchantAward()
                    .await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("All trashed merchant awards restored");
        }

        @Test
        @DisplayName("should fail when no trashed awards to restore")
        void restoreAll_NoTrashed() {
            when(merchantAwardCommandRepository.restoreAllDeleted())
                    .thenReturn(Uni.createFrom().item(false));

            assertThatThrownBy(() -> service.restoreAllMerchantAward().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No trashed merchant awards found");
        }
    }

    @Nested
    @DisplayName("delete all trashed awards permanent tests")
    class DeleteAllTests {

        @Test
        @DisplayName("should successfully delete all trashed awards")
        void deleteAll_Success() {
            ApiResponse<Void> response = service.deleteAllMerchantAwardPermanent()
                    .await().indefinitely();

            assertThat(response.status()).isEqualTo("success");
            assertThat(response.message()).isEqualTo("All trashed merchant awards permanently deleted");
        }

        @Test
        @DisplayName("should fail when no trashed awards to delete")
        void deleteAll_NoTrashed() {
            when(merchantAwardCommandRepository.deleteAllDeleted())
                    .thenReturn(Uni.createFrom().item(false));

            assertThatThrownBy(() -> service.deleteAllMerchantAwardPermanent().await().indefinitely())
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No trashed merchant awards found");
        }
    }
}
