package com.sanedge.banner.repository;

import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.banner.entity.Banner;
import com.sanedge.banner.domain.requests.FindAllBannerRequest;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BannerQueryRepository implements ReactivePanacheMongoRepository<Banner> {

    public Uni<PagedResult<Banner>> findBanners(FindAllBannerRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Banner> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null }");
        } else {
            panacheQuery = find("{ 'deletedAt': null, 'name': { '$regex': ?1, '$options': 'i' } }",
                    Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Banner>> findActiveBanners(FindAllBannerRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Banner> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null, 'isActive': true }");
        } else {
            panacheQuery = find(
                    "{ 'deletedAt': null, 'isActive': true, 'name': { '$regex': ?1, '$options': 'i' } }",
                    Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<Banner>> findTrashedBanners(FindAllBannerRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Banner> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': { '$ne': null } }");
        } else {
            panacheQuery = find("{ 'deletedAt': { '$ne': null }, 'name': { '$regex': ?1, '$options': 'i' } }",
                    Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<Banner> findByName(String name) {
        return find("{ 'name': { '$regex': ?1, '$options': 'i' }, 'deletedAt': null }",
                "^" + Pattern.quote(name) + "$").firstResult();
    }

    public Uni<Banner> findById(String bannerId) {
        return find("_id", new ObjectId(bannerId)).firstResult();
    }
}
