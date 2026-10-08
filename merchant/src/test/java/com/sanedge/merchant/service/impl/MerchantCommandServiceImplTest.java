package com.sanedge.merchant.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.sanedge.common.adapter.model.User;
import com.sanedge.common.adapter.user.UserPort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.enums.Status;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.exception.ResourceAlreadyExistsException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.merchant.domain.requests.CreateMerchantRequest;
import com.sanedge.merchant.domain.requests.UpdateMerchantRequest;
import com.sanedge.merchant.domain.response.MerchantResponse;
import com.sanedge.merchant.domain.response.MerchantResponseDeleteAt;
import com.sanedge.merchant.entity.Merchant;
import com.sanedge.merchant.repository.MerchantCommandRepository;
import com.sanedge.merchant.repository.MerchantQueryRepository;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;

@ExtendWith(MockitoExtension.class)
class MerchantCommandServiceImplTest {

    private static final String ID_1 = "507f1f77bcf86cd799439011";
    private static final String ID_999 = "507f1f77bcf86cd799439099";

    @Mock
    private UserPort userPort;

    @Mock
    private MerchantQueryRepository merchantQueryRepository;

    @Mock
    private MerchantCommandRepository merchantCommandRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private TracingMetrics tracingMetrics;

    private MerchantCommandServiceImpl merchantCommandService;

    @BeforeEach
    void setUp() {
        merchantCommandService = new MerchantCommandServiceImpl(
                userPort,
                merchantQueryRepository,
                merchantCommandRepository,
                redisService,
                tracingMetrics);
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics).traceAndMeasure(anyString(), anyString(), any());
        lenient().doAnswer(invokeSupplier())
                .when(tracingMetrics)
                        .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

        lenient().when(redisService.deleteReactive(anyString()))
                .thenReturn(Uni.createFrom().voidItem());
    }

    private Merchant createMockMerchant(String userId, String name, Status status) {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(ID_1);
        merchant.setUserId(userId != null ? new ObjectId(userId) : null);
        merchant.setName(name);
        merchant.setDescription("Test Description");
        merchant.setAddress("Test Address");
        merchant.setContactEmail("test@merchant.com");
        merchant.setContactPhone("081234567890");
        merchant.setStatus(status);
        Instant now = Instant.now();
        merchant.setCreatedAt(now);
        merchant.setUpdatedAt(now);
        return merchant;
    }

    private User createMockUser() {
        Instant now = Instant.now();
        return new User(ID_1, "John", "Doe", "john.doe@example.com", null, now, now);
    }

    @Test
    void createMerchant_success_createsNewMerchant() {
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setName("New Merchant");
        request.setUserId(ID_1);
        request.setDescription("Test Description");
        request.setAddress("Test Address");
        request.setContactEmail("test@merchant.com");
        request.setContactPhone("081234567890");
        request.setStatus("PENDING");

        lenient().when(userPort.findById(ID_1))
                .thenReturn(Uni.createFrom().item(createMockUser()));
        lenient().when(merchantQueryRepository.existsByName("New Merchant"))
                .thenReturn(Uni.createFrom().item(false));
        lenient().when(merchantCommandRepository.persist(any(Merchant.class)))
                .thenAnswer(invocation -> {
                    Merchant m = invocation.getArgument(0);
                    m.setMerchantId(ID_1);
                    return Uni.createFrom().item(m);
                });

        ApiResponse<MerchantResponse> response = merchantCommandService.createMerchant(request).await()
                .indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("Merchant created successfully");
        assertThat(response.data()).isNotNull();
        assertThat(response.data().getName()).isEqualTo("New Merchant");
    }

    @Test
    void createMerchant_alreadyExists_throwsResourceAlreadyExistsException() {
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setName("Existing Merchant");
        request.setUserId(ID_1);
        request.setDescription("Test Description");
        request.setAddress("Test Address");
        request.setContactEmail("test@merchant.com");
        request.setContactPhone("081234567890");
        request.setStatus("PENDING");

        lenient().when(userPort.findById(ID_1))
                .thenReturn(Uni.createFrom().item(createMockUser()));
        when(merchantQueryRepository.existsByName("Existing Merchant"))
                .thenReturn(Uni.createFrom().item(true));

        try {
            merchantCommandService.createMerchant(request).await().indefinitely();
            Assertions.fail("Expected ResourceAlreadyExistsException");
        } catch (ResourceAlreadyExistsException e) {
            assertThat(e.getMessage()).contains("already taken");
        }
    }

    @Test
    void createMerchant_userNotFound_throwsResourceNotFoundException() {
        CreateMerchantRequest request = new CreateMerchantRequest();
        request.setName("New Merchant");
        request.setUserId(ID_999);
        request.setDescription("Test Description");
        request.setAddress("Test Address");
        request.setContactEmail("test@merchant.com");
        request.setContactPhone("081234567890");
        request.setStatus("PENDING");

        when(userPort.findById(ID_999))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("User not found")));

        try {
            merchantCommandService.createMerchant(request).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("User not found");
        }
    }

    @Test
    void updateMerchant_success_updatesMerchant() {
        UpdateMerchantRequest request = new UpdateMerchantRequest();
        request.setMerchantId(ID_1);
        request.setName("Updated Merchant");
        request.setUserId(ID_1);
        request.setDescription("Updated Description");
        request.setAddress("Updated Address");
        request.setContactEmail("updated@merchant.com");
        request.setContactPhone("089876543210");
        request.setStatus("SUCCESS");

        Merchant existingMerchant = createMockMerchant(ID_1, "Old Merchant", Status.PENDING);

        lenient().when(merchantQueryRepository.findMerchantById(ID_1))
                .thenReturn(Uni.createFrom().item(existingMerchant));
        lenient().when(userPort.findById(ID_1))
                .thenReturn(Uni.createFrom().item(createMockUser()));
        lenient().when(merchantCommandRepository.update(any(Merchant.class)))
                .thenReturn(Uni.createFrom().item(existingMerchant));

        ApiResponse<MerchantResponse> response = merchantCommandService.updateMerchant(request).await()
                .indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("Merchant updated successfully");
        assertThat(response.data()).isNotNull();
        assertThat(response.data().getName()).isEqualTo("Updated Merchant");
    }

    @Test
    void updateMerchant_notFound_throwsResourceNotFoundException() {
        UpdateMerchantRequest request = new UpdateMerchantRequest();
        request.setMerchantId(ID_999);
        request.setName("Updated Merchant");
        request.setUserId(ID_1);
        request.setDescription("Updated Description");
        request.setAddress("Updated Address");
        request.setContactEmail("updated@merchant.com");
        request.setContactPhone("089876543210");
        request.setStatus("SUCCESS");

        when(merchantQueryRepository.findMerchantById(ID_999))
                .thenReturn(Uni.createFrom().nullItem());

        try {
            merchantCommandService.updateMerchant(request).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("Merchant not found");
        }
    }

    @Test
    void trashMerchant_success_trashesMerchant() {
        Merchant merchant = createMockMerchant(ID_1, "Merchant to Trash", Status.PENDING);
        merchant.setDeletedAt(Instant.now());

        lenient().when(merchantCommandRepository.trashed(ID_1))
                .thenReturn(Uni.createFrom().item(merchant));

        ApiResponse<MerchantResponseDeleteAt> response = merchantCommandService.trashMerchant(ID_1).await()
                .indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("Merchant trashed successfully");
        assertThat(response.data()).isNotNull();
        assertThat(response.data().getName()).isEqualTo("Merchant to Trash");
    }

    @Test
    void trashMerchant_notFound_throwsResourceNotFoundException() {
        when(merchantCommandRepository.trashed(ID_999))
                .thenReturn(Uni.createFrom().nullItem());

        try {
            merchantCommandService.trashMerchant(ID_999).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("Merchant not found");
        }
    }

    @Test
    void restoreMerchant_success_restoresMerchant() {
        Merchant restoredMerchant = createMockMerchant(ID_1, "Restored Merchant", Status.PENDING);
        restoredMerchant.setDeletedAt(null);

        lenient().when(merchantCommandRepository.restore(ID_1))
                .thenReturn(Uni.createFrom().item(restoredMerchant));

        ApiResponse<MerchantResponseDeleteAt> response = merchantCommandService.restoreMerchant(ID_1).await()
                .indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("Merchant restored successfully");
        assertThat(response.data()).isNotNull();
        assertThat(response.data().getName()).isEqualTo("Restored Merchant");
    }

    @Test
    void restoreMerchant_notFound_throwsResourceNotFoundException() {
        when(merchantCommandRepository.restore(ID_999))
                .thenReturn(Uni.createFrom().nullItem());

        try {
            merchantCommandService.restoreMerchant(ID_999).await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("Merchant not found");
        }
    }

    @Test
    void deleteMerchant_success_deletesMerchant() {
        Merchant deletedMerchant = createMockMerchant(ID_1, "Deleted Merchant", Status.PENDING);
        deletedMerchant.setDeletedAt(Instant.now());

        lenient().when(merchantCommandRepository.deletePermanent(ID_1))
                .thenReturn(Uni.createFrom().item(deletedMerchant));

        ApiResponse<Void> response = merchantCommandService.deleteMerchant(ID_1).await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).isEqualTo("Merchant permanently deleted");
    }

    @Test
    void deleteMerchant_notFound_throwsInvalidRequestException() {
        when(merchantCommandRepository.deletePermanent(ID_999))
                .thenReturn(Uni.createFrom().nullItem());

        try {
            merchantCommandService.deleteMerchant(ID_999).await().indefinitely();
            Assertions.fail("Expected InvalidRequestException");
        } catch (InvalidRequestException e) {
            assertThat(e.getMessage()).contains("not found or must be trashed");
        }
    }

    @Test
    void restoreAll_success_restoresAll() {
        lenient().when(merchantCommandRepository.restoreAllDeleted())
                .thenReturn(Uni.createFrom().item(true));

        ApiResponse<Void> response = merchantCommandService.restoreAll().await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).contains("Restored all trashed merchants");
    }

    @Test
    void restoreAll_noneFound_throwsResourceNotFoundException() {
        when(merchantCommandRepository.restoreAllDeleted())
                .thenReturn(Uni.createFrom().item(false));

        try {
            merchantCommandService.restoreAll().await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("No trashed merchants found");
        }
    }

    @Test
    void deleteAll_success_deletesAll() {
        lenient().when(merchantCommandRepository.deleteAllDeleted())
                .thenReturn(Uni.createFrom().item(true));

        ApiResponse<Void> response = merchantCommandService.deleteAll().await().indefinitely();

        assertThat(response.status()).isEqualTo("success");
        assertThat(response.message()).contains("Deleted all trashed merchants");
    }

    @Test
    void deleteAll_noneFound_throwsResourceNotFoundException() {
        when(merchantCommandRepository.deleteAllDeleted())
                .thenReturn(Uni.createFrom().item(false));

        try {
            merchantCommandService.deleteAll().await().indefinitely();
            Assertions.fail("Expected ResourceNotFoundException");
        } catch (ResourceNotFoundException e) {
            assertThat(e.getMessage()).contains("No trashed merchants found");
        }
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
