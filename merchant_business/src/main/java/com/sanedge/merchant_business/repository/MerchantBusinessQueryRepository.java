package com.sanedge.merchant_business.repository;

import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.merchant_business.entity.MerchantBusinessInformation;
import com.sanedge.merchant_business.domain.requests.FindAllMerchantRequest;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantBusinessQueryRepository implements ReactivePanacheMongoRepository<MerchantBusinessInformation> {

    public Uni<PagedResult<MerchantBusinessInformation>> findMerchantBusinessInformation(FindAllMerchantRequest req) {
        return query(req, false);
    }

    public Uni<PagedResult<MerchantBusinessInformation>> findActiveMerchantBusinessInformation(FindAllMerchantRequest req) {
        return query(req, false);
    }

    public Uni<PagedResult<MerchantBusinessInformation>> findTrashedMerchantBusinessInformation(FindAllMerchantRequest req) {
        return query(req, true);
    }

    private Uni<PagedResult<MerchantBusinessInformation>> query(FindAllMerchantRequest req, boolean trashed) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<MerchantBusinessInformation> panacheQuery;
        if (trashed) {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': { '$ne': null } }")
                    : find("{ 'deletedAt': { '$ne': null }, '$or': [ "
                            + "{ 'businessType': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'taxId': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'websiteUrl': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        } else {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': null }")
                    : find("{ 'deletedAt': null, '$or': [ "
                            + "{ 'businessType': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'taxId': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'websiteUrl': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<MerchantBusinessInformation> findMerchantBusinessInformationById(String merchantBusinessInfoId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(merchantBusinessInfoId)).firstResult();
    }

    public Uni<MerchantBusinessInformation> findById(String merchantBusinessInfoId) {
        return find("_id", new ObjectId(merchantBusinessInfoId)).firstResult();
    }
}
