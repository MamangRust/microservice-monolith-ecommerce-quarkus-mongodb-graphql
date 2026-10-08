package com.sanedge.merchant_detail.domain.response;

import com.sanedge.merchant_detail.entity.MerchantSocialMediaLink;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSocialMediaLinkResponseDeleteAt {
    private String id;
    private String platform;
    private String url;
    private String createdAt;
    private String updatedAt;
    private String deletedAt;

    public static MerchantSocialMediaLinkResponseDeleteAt from(MerchantSocialMediaLink entity) {
        return MerchantSocialMediaLinkResponseDeleteAt.builder()
                .id(entity.id != null ? entity.id.toHexString() : null)
                .platform(entity.getPlatform())
                .url(entity.getUrl())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .deletedAt(entity.getDeletedAt() != null ? entity.getDeletedAt().toString() : null)
                .build();
    }
}
