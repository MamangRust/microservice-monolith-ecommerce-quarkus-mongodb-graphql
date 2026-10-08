package com.sanedge.transaction.domain.response;

import com.sanedge.transaction.entity.Transaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponseDeleteAt {
    private String id;
    private String orderId;
    private String merchantId;
    private String paymentMethod;
    private Integer amount;
    private String paymentStatus;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;

    public static TransactionResponseDeleteAt from(Transaction entity) {
        if (entity == null) {
            return null;
        }
        return TransactionResponseDeleteAt.builder()
                .id(entity.id != null ? entity.id.toHexString() : null)
                .orderId(entity.getOrderId() != null ? entity.getOrderId().toHexString() : null)
                .merchantId(entity.getMerchantId() != null ? entity.getMerchantId().toHexString() : null)
                .paymentMethod(entity.getPaymentMethod())
                .amount(entity.getAmount())
                .paymentStatus(entity.getStatus() != null ? entity.getStatus().name() : null)
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .deletedAt(entity.getDeletedAt() != null ? entity.getDeletedAt().toString() : null)
                .build();
    }
}
