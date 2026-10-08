package com.sanedge.merchant_detail.entity;

import com.sanedge.common.entity.BaseModel;

import org.bson.types.ObjectId;

import io.quarkus.mongodb.panache.common.MongoEntity;
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
@MongoEntity(collection = "merchant_details")
public class MerchantDetail extends BaseModel {

    @NotNull
    private ObjectId merchantId;

    @Size(max = 255)
    private String displayName;

    @Size(max = 255)
    private String coverImageUrl;

    @Size(max = 255)
    private String logoUrl;

    private String shortDescription;

    @Size(max = 255)
    private String websiteUrl;
}
