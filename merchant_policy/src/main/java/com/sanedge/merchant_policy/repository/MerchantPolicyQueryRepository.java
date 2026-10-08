package com.sanedge.merchant_policy.repository;

import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.merchant_policy.entity.MerchantPolicy;
import com.sanedge.merchant_policy.domain.requests.FindAllMerchantRequest;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantPolicyQueryRepository implements ReactivePanacheMongoRepository<MerchantPolicy> {

    public Uni<PagedResult<MerchantPolicy>> findMerchantPolicies(FindAllMerchantRequest req) {
        return query(req, false);
    }

    public Uni<PagedResult<MerchantPolicy>> findActiveMerchantPolicies(FindAllMerchantRequest req) {
        return query(req, false);
    }

    public Uni<PagedResult<MerchantPolicy>> findTrashedMerchantPolicies(FindAllMerchantRequest req) {
        return query(req, true);
    }

    private Uni<PagedResult<MerchantPolicy>> query(FindAllMerchantRequest req, boolean trashed) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<MerchantPolicy> panacheQuery;
        if (trashed) {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': { '$ne': null } }")
                    : find("{ 'deletedAt': { '$ne': null }, '$or': [ "
                            + "{ 'policyType': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'title': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'description': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        } else {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': null }")
                    : find("{ 'deletedAt': null, '$or': [ "
                            + "{ 'policyType': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'title': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'description': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<MerchantPolicy> findMerchantPolicyById(String merchantPolicyId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(merchantPolicyId)).firstResult();
    }

    public Uni<MerchantPolicy> findById(String merchantPolicyId) {
        return find("_id", new ObjectId(merchantPolicyId)).firstResult();
    }
}
