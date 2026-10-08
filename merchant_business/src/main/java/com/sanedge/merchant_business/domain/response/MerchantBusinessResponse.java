package com.sanedge.merchant_business.domain.response;

import com.sanedge.merchant_business.entity.MerchantBusinessInformation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantBusinessResponse {
    private String id;
    private String merchantId;
    private String businessType;
    private String taxId;
    private Integer establishedYear;
    private Integer numberOfEmployees;
    private String websiteUrl;
    private String createdAt;
    private String updatedAt;

    public static MerchantBusinessResponse from(MerchantBusinessInformation entity) {
        return MerchantBusinessResponse.builder()
                .id(entity.id != null ? entity.id.toHexString() : null)
                .merchantId(entity.getMerchantId() != null ? entity.getMerchantId().toHexString() : null)
                .businessType(entity.getBusinessType())
                .taxId(entity.getTaxId())
                .establishedYear(entity.getEstablishedYear())
                .numberOfEmployees(entity.getNumberOfEmployees())
                .websiteUrl(entity.getWebsiteUrl())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }
}