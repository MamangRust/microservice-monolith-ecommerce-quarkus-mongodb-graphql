package com.sanedge.merchant_award.repository;

import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.merchant_award.entity.MerchantCertificationAndAward;
import com.sanedge.merchant_award.domain.requests.FindAllMerchantRequest;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantAwardQueryRepository implements ReactivePanacheMongoRepository<MerchantCertificationAndAward> {

    public Uni<PagedResult<MerchantCertificationAndAward>> findMerchantAwards(FindAllMerchantRequest req) {
        return query(req, false);
    }

    public Uni<PagedResult<MerchantCertificationAndAward>> findActiveMerchantAwards(FindAllMerchantRequest req) {
        return query(req, false);
    }

    public Uni<PagedResult<MerchantCertificationAndAward>> findTrashedMerchantAwards(FindAllMerchantRequest req) {
        return query(req, true);
    }

    private Uni<PagedResult<MerchantCertificationAndAward>> query(FindAllMerchantRequest req, boolean trashed) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<MerchantCertificationAndAward> panacheQuery;
        if (trashed) {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': { '$ne': null } }")
                    : find("{ 'deletedAt': { '$ne': null }, '$or': [ "
                            + "{ 'title': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'description': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'issuedBy': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'certificateUrl': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        } else {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': null }")
                    : find("{ 'deletedAt': null, '$or': [ "
                            + "{ 'title': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'description': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'issuedBy': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'certificateUrl': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<MerchantCertificationAndAward> findMerchantAwardById(String merchantCertificationId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(merchantCertificationId)).firstResult();
    }

    public Uni<MerchantCertificationAndAward> findById(String merchantCertificationId) {
        return find("_id", new ObjectId(merchantCertificationId)).firstResult();
    }
}
