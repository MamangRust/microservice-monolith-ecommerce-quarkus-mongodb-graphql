package com.sanedge.merchant_business.entity;

import com.sanedge.common.entity.BaseModel;

import org.bson.types.ObjectId;

import com.sanedge.merchant_business.domain.requests.CreateMerchantBusinessRequest;
import com.sanedge.merchant_business.domain.requests.UpdateMerchantBusinessRequest;

import io.quarkus.mongodb.panache.common.MongoEntity;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@MongoEntity(collection = "merchant_business_information")
public class MerchantBusinessInformation extends BaseModel {

    private ObjectId merchantId;

    @Size(max = 100)
    private String businessType;

    @Size(max = 50)
    private String taxId;

    private Integer establishedYear;

    private Integer numberOfEmployees;

    @Size(max = 255)
    private String websiteUrl;

    public static MerchantBusinessInformation fromCreateRequest(CreateMerchantBusinessRequest req) {
        MerchantBusinessInformation mbi = new MerchantBusinessInformation();
        mbi.setMerchantId(req.getMerchantId() != null ? new ObjectId(req.getMerchantId()) : null);
        mbi.setBusinessType(req.getBusinessType());
        mbi.setTaxId(req.getTaxId());
        mbi.setEstablishedYear(req.getEstablishedYear());
        mbi.setNumberOfEmployees(req.getNumberOfEmployees());
        mbi.setWebsiteUrl(req.getWebsiteUrl());
        return mbi;
    }

    public static MerchantBusinessInformation fromUpdateRequest(UpdateMerchantBusinessRequest req) {
        MerchantBusinessInformation mbi = new MerchantBusinessInformation();
        mbi.id = req.getMerchantBusinessInfoId() != null ? new ObjectId(req.getMerchantBusinessInfoId()) : null;
        mbi.setBusinessType(req.getBusinessType());
        mbi.setTaxId(req.getTaxId());
        mbi.setEstablishedYear(req.getEstablishedYear());
        mbi.setNumberOfEmployees(req.getNumberOfEmployees());
        mbi.setWebsiteUrl(req.getWebsiteUrl());
        return mbi;
    }
}
