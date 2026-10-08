package com.sanedge.merchant.entity;

import org.bson.types.ObjectId;

import com.sanedge.common.entity.BaseModel;

import io.quarkus.mongodb.panache.common.MongoEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@MongoEntity(collection = "merchant_documents")
public class MerchantDocument extends BaseModel {

    private ObjectId merchantId;

    @NotBlank
    private String documentType;

    @NotBlank
    private String documentUrl;

    @NotBlank
    private String status = "PENDING";

    private String note;

    public String getDocumentId() {
        return this.id != null ? this.id.toHexString() : null;
    }

    public void setDocumentId(String documentId) {
        this.id = documentId != null ? new ObjectId(documentId) : null;
    }
}
