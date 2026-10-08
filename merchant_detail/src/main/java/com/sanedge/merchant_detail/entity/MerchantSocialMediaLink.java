package com.sanedge.merchant_detail.entity;

import com.sanedge.common.entity.BaseModel;

import org.bson.types.ObjectId;

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
@MongoEntity(collection = "merchant_social_media_links")
public class MerchantSocialMediaLink extends BaseModel {

    @NotNull
    private ObjectId merchantDetailId;

    @NotBlank
    @Size(max = 100)
    private String platform;

    @NotBlank
    private String url;
}
