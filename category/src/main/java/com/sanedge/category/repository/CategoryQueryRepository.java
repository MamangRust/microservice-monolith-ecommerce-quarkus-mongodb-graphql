package com.sanedge.category.repository;

import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.category.entity.Category;
import com.sanedge.category.domain.requests.FindAllCategoryRequest;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryQueryRepository implements ReactivePanacheMongoRepository<Category> {

    public Uni<PagedResult<Category>> findCategories(FindAllCategoryRequest req) {
        return findCategoriesInternal(req, false, false);
    }

    public Uni<PagedResult<Category>> findActiveCategories(FindAllCategoryRequest req) {
        return findCategoriesInternal(req, false, false);
    }

    public Uni<PagedResult<Category>> findTrashedCategories(FindAllCategoryRequest req) {
        return findCategoriesInternal(req, true, true);
    }

    private Uni<PagedResult<Category>> findCategoriesInternal(FindAllCategoryRequest req, boolean trashed,
            boolean descending) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        Sort sort = descending ? Sort.descending("_id") : Sort.ascending("_id");

        ReactivePanacheQuery<Category> panacheQuery;
        if (trashed) {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': { '$ne': null } }", sort)
                    : find("{ 'deletedAt': { '$ne': null }, '$or': [ "
                            + "{ 'name': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'description': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'slugCategory': { '$regex': ?1, '$options': 'i' } } ] }",
                            sort, Pattern.quote(keyword));
        } else {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': null }", sort)
                    : find("{ 'deletedAt': null, '$or': [ "
                            + "{ 'name': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'description': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'slugCategory': { '$regex': ?1, '$options': 'i' } } ] }",
                            sort, Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<Category> findCategoryById(String categoryId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(categoryId)).firstResult();
    }

    public Uni<Category> findById(String categoryId) {
        return find("_id", new ObjectId(categoryId)).firstResult();
    }
}
