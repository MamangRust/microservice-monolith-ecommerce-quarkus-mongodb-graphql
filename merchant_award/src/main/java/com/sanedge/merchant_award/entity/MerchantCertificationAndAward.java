package com.sanedge.merchant_award.entity;

import com.sanedge.common.entity.BaseModel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.bson.types.ObjectId;

import com.sanedge.merchant_award.domain.requests.CreateMerchantAwardRequest;
import com.sanedge.merchant_award.domain.requests.UpdateMerchantAwardRequest;

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
@MongoEntity(collection = "merchant_certifications_and_awards")
public class MerchantCertificationAndAward extends BaseModel {

    private ObjectId merchantId;

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @Size(max = 255)
    private String issuedBy;

    private Instant issueDate;

    private Instant expiryDate;

    @Size(max = 255)
    private String certificateUrl;

    public static MerchantCertificationAndAward fromCreateRequest(CreateMerchantAwardRequest req) {
        MerchantCertificationAndAward award = new MerchantCertificationAndAward();
        award.setMerchantId(req.getMerchantId() != null ? new ObjectId(req.getMerchantId()) : null);
        award.setTitle(req.getTitle());
        award.setDescription(req.getDescription());
        award.setIssuedBy(req.getIssuedBy());
        award.setIssueDate(parseDate(req.getIssueDate()));
        award.setExpiryDate(parseDate(req.getExpiryDate()));
        award.setCertificateUrl(req.getCertificateUrl());
        return award;
    }

    public void updateFromRequest(UpdateMerchantAwardRequest req) {
        this.title = req.getTitle();
        this.description = req.getDescription();
        this.issuedBy = req.getIssuedBy();
        this.issueDate = parseDate(req.getIssueDate());
        this.expiryDate = parseDate(req.getExpiryDate());
        this.certificateUrl = req.getCertificateUrl();
    }

    private static Instant parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank())
            return null;
        return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
