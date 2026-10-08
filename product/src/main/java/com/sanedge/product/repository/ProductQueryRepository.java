package com.sanedge.product.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.product.domain.requests.FindAllProductByCategoryIdRequest;
import com.sanedge.product.domain.requests.FindAllProductByCategoryRequest;
import com.sanedge.product.domain.requests.FindAllProductByMerchantRequest;
import com.sanedge.product.domain.requests.FindAllProductRequest;
import com.sanedge.product.entity.Product;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductQueryRepository implements ReactivePanacheMongoRepository<Product> {

    private static final String CATEGORY_COLLECTION = "categories";

    public Uni<PagedResult<Product>> findProducts(FindAllProductRequest req) {
        return findProductsInternal(req, false);
    }

    public Uni<PagedResult<Product>> findActiveProducts(FindAllProductRequest req) {
        return findProductsInternal(req, false);
    }

    public Uni<PagedResult<Product>> findTrashedProducts(FindAllProductRequest req) {
        return findProductsInternal(req, true);
    }

    private Uni<PagedResult<Product>> findProductsInternal(FindAllProductRequest req, boolean trashed) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Product> panacheQuery;
        if (trashed) {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': { '$ne': null } }")
                    : find("{ 'deletedAt': { '$ne': null }, '$or': [ "
                            + "{ 'name': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'description': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'slugProduct': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        } else {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': null }")
                    : find("{ 'deletedAt': null, '$or': [ "
                            + "{ 'name': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'description': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'slugProduct': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Product>> findByMerchant(FindAllProductByMerchantRequest req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;

        List<Bson> conditions = new ArrayList<>();
        conditions.add(Filters.eq("deletedAt", null));
        conditions.add(Filters.eq("merchantId", new ObjectId(req.getMerchantId())));
        if (req.getCategoryId() != null && !req.getCategoryId().isBlank()) {
            conditions.add(Filters.eq("categoryId", new ObjectId(req.getCategoryId())));
        }
        addKeywordAndPrice(conditions, req.getSearch(), req.getMinPrice(), req.getMaxPrice());

        ReactivePanacheQuery<Product> panacheQuery = find(Filters.and(conditions));
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Product>> findByCategory(FindAllProductByCategoryIdRequest req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;

        List<Bson> conditions = new ArrayList<>();
        conditions.add(Filters.eq("deletedAt", null));
        conditions.add(Filters.eq("categoryId", new ObjectId(req.getCategoryId())));
        if (req.getSearch() != null && !req.getSearch().trim().isEmpty()) {
            String regex = Pattern.quote(req.getSearch().trim());
            conditions.add(Filters.or(
                    Filters.regex("name", regex, "i"),
                    Filters.regex("description", regex, "i"),
                    Filters.regex("slugProduct", regex, "i")));
        }

        ReactivePanacheQuery<Product> panacheQuery = find(Filters.and(conditions));
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<Optional<Product>> findProductById(String productId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(productId)).firstResult()
                .map(Optional::ofNullable);
    }

    public Uni<Product> findById(String productId) {
        return find("_id", new ObjectId(productId)).firstResult();
    }

    /**
     * Products for a merchant with optional category, keyword and price filters.
     * Rewritten from the PostgreSQL native join as a MongoDB aggregation pipeline
     * (the join is unnecessary here because every predicate lives on the product
     * document itself).
     */
    public Uni<PagedResult<Product>> findProductsByMerchantNative(FindAllProductByMerchantRequest req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;

        List<Bson> conditions = new ArrayList<>();
        conditions.add(Filters.eq("deletedAt", null));
        if (req.getMerchantId() != null && !req.getMerchantId().isBlank()) {
            conditions.add(Filters.eq("merchantId", new ObjectId(req.getMerchantId())));
        }
        if (req.getCategoryId() != null && !req.getCategoryId().isBlank()) {
            conditions.add(Filters.eq("categoryId", new ObjectId(req.getCategoryId())));
        }
        addKeywordAndPrice(conditions, req.getSearch(), req.getMinPrice(), req.getMaxPrice());
        Bson filter = Filters.and(conditions);

        List<Bson> pipeline = List.of(
                Aggregates.match(filter),
                Aggregates.sort(Sorts.descending("createdAt")),
                Aggregates.skip(page * size),
                Aggregates.limit(size));

        return Uni.combine().all().unis(
                mongoCollection().aggregate(pipeline).collect().asList(),
                mongoCollection().countDocuments(filter))
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    /**
     * Products whose category name matches, with optional keyword and price
     * filters. The PostgreSQL native join to {@code categories} is rewritten as a
     * {@code $lookup} + {@code $unwind} aggregation over the {@code categories}
     * collection.
     */
    public Uni<PagedResult<Product>> findProductsByCategoryNameNative(FindAllProductByCategoryRequest req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;

        List<Bson> conditions = new ArrayList<>();
        conditions.add(Filters.eq("deletedAt", null));
        conditions.add(Filters.eq("category.name", req.getCategoryName()));
        addKeywordAndPrice(conditions, req.getSearch(), req.getMinPrice(), req.getMaxPrice());
        Bson filter = Filters.and(conditions);

        Bson lookup = Aggregates.lookup(CATEGORY_COLLECTION, "categoryId", "_id", "category");
        Bson unwind = Aggregates.unwind("$category");

        List<Bson> dataPipeline = List.of(
                lookup,
                unwind,
                Aggregates.match(filter),
                Aggregates.sort(Sorts.descending("createdAt")),
                Aggregates.skip(page * size),
                Aggregates.limit(size),
                Aggregates.project(Projections.exclude("category")));

        List<Bson> countPipeline = List.of(
                lookup,
                unwind,
                Aggregates.match(filter),
                Aggregates.count("total"));

        Uni<List<Product>> dataUni = mongoCollection().aggregate(dataPipeline).collect().asList();
        Uni<Long> countUni = mongoCollection().aggregate(countPipeline, Document.class).collect().asList()
                .map(list -> list.isEmpty() ? 0L : ((Number) list.get(0).get("total")).longValue());

        return Uni.combine().all().unis(dataUni, countUni)
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    private void addKeywordAndPrice(List<Bson> conditions, String search, Integer minPrice, Integer maxPrice) {
        if (search != null && !search.trim().isEmpty()) {
            String regex = Pattern.quote(search.trim());
            conditions.add(Filters.or(
                    Filters.regex("name", regex, "i"),
                    Filters.regex("description", regex, "i"),
                    Filters.regex("slugProduct", regex, "i")));
        }
        int min = minPrice != null ? minPrice : 0;
        int max = maxPrice != null ? maxPrice : 999999999;
        conditions.add(Filters.gte("price", min));
        conditions.add(Filters.lte("price", max));
    }
}
