package com.sanedge.merchant.entity;

import org.bson.types.ObjectId;

import com.sanedge.common.entity.BaseModel;
import com.sanedge.common.enums.Status;

import io.quarkus.mongodb.panache.common.MongoEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@MongoEntity(collection = "merchants")
public class Merchant extends BaseModel {

    private ObjectId userId;

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;

    private String address;

    @Size(max = 100)
    private String contactEmail;

    @Size(max = 20)
    private String contactPhone;

    private Status status = Status.PENDING;

    public String getMerchantId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setMerchantId(String merchantId) {
        this.id = merchantId != null ? new ObjectId(merchantId) : null;
    }
}
