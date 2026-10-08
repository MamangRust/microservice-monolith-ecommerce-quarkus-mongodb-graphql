package com.sanedge.transaction.entity;

import org.bson.types.ObjectId;

import com.sanedge.common.entity.BaseModel;
import com.sanedge.transaction.enums.PaymentStatus;

import io.quarkus.mongodb.panache.common.MongoEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@MongoEntity(collection = "transactions")
public class Transaction extends BaseModel {

    private ObjectId orderId;

    private ObjectId merchantId;

    @NotBlank
    @Size(max = 50)
    private String paymentMethod;

    @NotNull
    private Integer amount;

    private PaymentStatus status = PaymentStatus.PENDING;
}
