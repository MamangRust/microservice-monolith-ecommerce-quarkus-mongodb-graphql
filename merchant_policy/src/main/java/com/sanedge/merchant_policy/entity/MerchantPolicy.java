package com.sanedge.merchant_policy.entity;

import com.sanedge.common.entity.BaseModel;

import org.bson.types.ObjectId;

import com.sanedge.merchant_policy.domain.requests.CreateMerchantPolicyRequest;
import com.sanedge.merchant_policy.domain.requests.UpdateMerchantPolicyRequest;

import io.quarkus.mongodb.panache.common.MongoEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@MongoEntity(collection = "merchant_policies")
public class MerchantPolicy extends BaseModel {

    private ObjectId merchantId;

    @NotBlank
    @Size(max = 100)
    private String policyType;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    private String description;

    public static MerchantPolicy fromCreateRequest(CreateMerchantPolicyRequest req) {
        MerchantPolicy entity = new MerchantPolicy();
        entity.setMerchantId(req.getMerchantId() != null ? new ObjectId(req.getMerchantId()) : null);
        entity.setPolicyType(req.getPolicyType());
        entity.setTitle(req.getTitle());
        entity.setDescription(req.getDescription());
        return entity;
    }

    public void updateFromRequest(UpdateMerchantPolicyRequest req) {
        this.setPolicyType(req.getPolicyType());
        this.setTitle(req.getTitle());
        this.setDescription(req.getDescription());
    }
}
