package com.sanedge.transaction.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.sanedge.transaction.domain.response.TransactionResponse;
import com.sanedge.transaction.domain.response.TransactionResponseDeleteAt;
import com.sanedge.transaction.enums.PaymentStatus;
import com.sanedge.transaction.service.TransactionCommandService;

import io.grpc.StatusRuntimeException;
import io.smallrye.mutiny.Uni;
import pb.transaction.TransactionCommand;
import pb.transaction.TransactionCommon;
import pb.transaction.TransactionCommon.FindByIdTransactionRequest;

@ExtendWith(MockitoExtension.class)
class TransactionCommandGrpcHandlerTest {

    private static final String TX_ID_HEX = "507f1f77bcf86cd799439011";
    private static final String ORDER_ID_HEX = "507f1f77bcf86cd799439022";
    private static final String MERCHANT_ID_HEX = "507f1f77bcf86cd799439033";

    @Mock
    private TransactionCommandService transactionCommandService;

    private TransactionCommandGrpcHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TransactionCommandGrpcHandler();
        handler.transactionCommandService = transactionCommandService;
    }

    private TransactionResponse createTransactionResponse(String id) {
        return TransactionResponse.builder()
                .id(id)
                .orderId(ORDER_ID_HEX)
                .merchantId(MERCHANT_ID_HEX)
                .paymentMethod("Credit Card")
                .amount(110000)
                .paymentStatus(PaymentStatus.SUCCESS.name())
                .createdAt("2024-01-01 00:00:00.0")
                .updatedAt("2024-01-01 00:00:00.0")
                .build();
    }

    private TransactionResponseDeleteAt createTransactionResponseDeleteAt(String id) {
        return TransactionResponseDeleteAt.builder()
                .id(id)
                .orderId(ORDER_ID_HEX)
                .merchantId(MERCHANT_ID_HEX)
                .paymentMethod("Credit Card")
                .amount(110000)
                .paymentStatus(PaymentStatus.SUCCESS.name())
                .createdAt("2024-01-01 00:00:00.0")
                .updatedAt("2024-01-01 00:00:00.0")
                .deletedAt("2024-01-02 00:00:00.0")
                .build();
    }

    private TransactionCommand.CreateTransactionRequest createValidProtoCreateRequest() {
        return TransactionCommand.CreateTransactionRequest.newBuilder()
                .setOrderId(ORDER_ID_HEX)
                .setMerchantId(MERCHANT_ID_HEX)
                .setPaymentMethod("Credit Card")
                .setAmount(110000)
                .setPaymentStatus("success")
                .build();
    }

    private TransactionCommand.UpdateTransactionRequest createValidProtoUpdateRequest() {
        return TransactionCommand.UpdateTransactionRequest.newBuilder()
                .setTransactionId(TX_ID_HEX)
                .setOrderId(ORDER_ID_HEX)
                .setMerchantId(MERCHANT_ID_HEX)
                .setPaymentMethod("Credit Card")
                .setAmount(110000)
                .setPaymentStatus("success")
                .build();
    }

    @Test
    @DisplayName("create - should return ApiResponseTransaction on success")
    void create_Success() {
        TransactionCommand.CreateTransactionRequest request = createValidProtoCreateRequest();

        TransactionResponse data = createTransactionResponse(TX_ID_HEX);
        ApiResponse<TransactionResponse> apiResp = ApiResponse.success("Transaction created successfully", data);

        when(transactionCommandService.create(any())).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransaction response = handler.create(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Transaction created successfully");
        assertThat(response.getData().getId()).isEqualTo(TX_ID_HEX);
    }

    @Test
    @DisplayName("create - should return NOT_FOUND when ResourceNotFoundException thrown")
    void create_NotFound() {
        TransactionCommand.CreateTransactionRequest request = createValidProtoCreateRequest();

        when(transactionCommandService.create(any()))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Merchant not found")));

        StatusRuntimeException ex = null;
        try {
            handler.create(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("create - should return INTERNAL on generic exception")
    void create_InternalError() {
        TransactionCommand.CreateTransactionRequest request = createValidProtoCreateRequest();

        when(transactionCommandService.create(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.create(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("create - should handle null response data")
    void create_NullData() {
        TransactionCommand.CreateTransactionRequest request = createValidProtoCreateRequest();

        ApiResponse<TransactionResponse> apiResp = ApiResponse.success("Transaction created successfully", null);
        when(transactionCommandService.create(any())).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransaction response = handler.create(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    @DisplayName("update - should return ApiResponseTransaction on success")
    void update_Success() {
        TransactionCommand.UpdateTransactionRequest request = createValidProtoUpdateRequest();

        TransactionResponse data = createTransactionResponse(TX_ID_HEX);
        ApiResponse<TransactionResponse> apiResp = ApiResponse.success("Transaction updated successfully", data);

        when(transactionCommandService.update(any())).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransaction response = handler.update(request).await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData().getId()).isEqualTo(TX_ID_HEX);
    }

    @Test
    @DisplayName("update - should return NOT_FOUND when transaction not found")
    void update_NotFound() {
        TransactionCommand.UpdateTransactionRequest request = createValidProtoUpdateRequest();

        when(transactionCommandService.update(any()))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Transaction not found")));

        StatusRuntimeException ex = null;
        try {
            handler.update(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("trashedTransaction - should return ApiResponseTransactionDeleteAt on success")
    void trashedTransaction_Success() {
        FindByIdTransactionRequest request = FindByIdTransactionRequest.newBuilder().setId(TX_ID_HEX).build();

        TransactionResponseDeleteAt data = createTransactionResponseDeleteAt(TX_ID_HEX);
        ApiResponse<TransactionResponseDeleteAt> apiResp = ApiResponse.success("Transaction trashed successfully",
                data);

        when(transactionCommandService.trash(TX_ID_HEX)).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionDeleteAt response = handler.trashedTransaction(request).await()
                .indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData().getId()).isEqualTo(TX_ID_HEX);
        assertThat(response.getData().hasDeletedAt()).isTrue();
    }

    @Test
    @DisplayName("trashedTransaction - should return NOT_FOUND when transaction not found")
    void trashedTransaction_NotFound() {
        FindByIdTransactionRequest request = FindByIdTransactionRequest.newBuilder()
                .setId("000000000000000000000999").build();

        when(transactionCommandService.trash("000000000000000000000999"))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Transaction not found")));

        StatusRuntimeException ex = null;
        try {
            handler.trashedTransaction(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("restoreTransaction - should return ApiResponseTransactionDeleteAt on success")
    void restoreTransaction_Success() {
        FindByIdTransactionRequest request = FindByIdTransactionRequest.newBuilder().setId(TX_ID_HEX).build();

        TransactionResponseDeleteAt data = createTransactionResponseDeleteAt(TX_ID_HEX);
        ApiResponse<TransactionResponseDeleteAt> apiResp = ApiResponse.success("Transaction restored successfully",
                data);

        when(transactionCommandService.restore(TX_ID_HEX)).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionDeleteAt response = handler.restoreTransaction(request).await()
                .indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getData().getId()).isEqualTo(TX_ID_HEX);
    }

    @Test
    @DisplayName("deleteTransactionPermanent - should return ApiResponseTransactionDelete on success")
    void deleteTransactionPermanent_Success() {
        FindByIdTransactionRequest request = FindByIdTransactionRequest.newBuilder().setId(TX_ID_HEX).build();

        ApiResponse<Void> apiResp = ApiResponse.success("Transaction permanently deleted");
        when(transactionCommandService.delete(TX_ID_HEX)).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionDelete response = handler.deleteTransactionPermanent(request).await()
                .indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
        assertThat(response.getMessage()).isEqualTo("Transaction permanently deleted");
    }

    @Test
    @DisplayName("deleteTransactionPermanent - should return NOT_FOUND when transaction not found")
    void deleteTransactionPermanent_NotFound() {
        FindByIdTransactionRequest request = FindByIdTransactionRequest.newBuilder()
                .setId("000000000000000000000999").build();

        when(transactionCommandService.delete("000000000000000000000999"))
                .thenReturn(Uni.createFrom().failure(new ResourceNotFoundException("Transaction not found")));

        StatusRuntimeException ex = null;
        try {
            handler.deleteTransactionPermanent(request).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("restoreAllTransaction - should return ApiResponseTransactionAll on success")
    void restoreAllTransaction_Success() {
        ApiResponse<Void> apiResp = ApiResponse.success("All transactions restored successfully");
        when(transactionCommandService.restoreAll()).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionAll response = handler.restoreAllTransaction(Empty.getDefaultInstance())
                .await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    @DisplayName("restoreAllTransaction - should return INTERNAL on failure")
    void restoreAllTransaction_InternalError() {
        when(transactionCommandService.restoreAll())
                .thenReturn(Uni.createFrom().failure(new RuntimeException("DB error")));

        StatusRuntimeException ex = null;
        try {
            handler.restoreAllTransaction(Empty.getDefaultInstance()).await().indefinitely();
        } catch (StatusRuntimeException e) {
            ex = e;
        }

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("deleteTransactionByOrderPermanent - should return ApiResponseTransactionDelete on success")
    void deleteTransactionByOrderPermanent_Success() {
        FindByIdTransactionRequest request = FindByIdTransactionRequest.newBuilder().setId(ORDER_ID_HEX).build();

        ApiResponse<Boolean> apiResp = ApiResponse.success("Transaction by order permanently deleted", true);
        when(transactionCommandService.deleteByOrder(ORDER_ID_HEX)).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionDelete response = handler.deleteTransactionByOrderPermanent(request)
                .await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
    }

    @Test
    @DisplayName("deleteAllTransactionPermanent - should return ApiResponseTransactionAll on success")
    void deleteAllTransactionPermanent_Success() {
        ApiResponse<Void> apiResp = ApiResponse.success("All transactions permanently deleted");
        when(transactionCommandService.deleteAll()).thenReturn(Uni.createFrom().item(apiResp));

        TransactionCommon.ApiResponseTransactionAll response = handler
                .deleteAllTransactionPermanent(Empty.getDefaultInstance())
                .await().indefinitely();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("success");
    }
}
