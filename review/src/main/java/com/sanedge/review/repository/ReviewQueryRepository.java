package com.sanedge.review.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.review.domain.requests.FindAllReview;
import com.sanedge.review.domain.requests.FindAllReviewByMerchant;
import com.sanedge.review.domain.requests.FindAllReviewByProduct;
import com.sanedge.review.entity.Review;
import com.sanedge.review.entity.ReviewDetail;
import com.sanedge.review.entity.ReviewRelationsDetail;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReviewQueryRepository implements ReactivePanacheMongoRepository<Review> {

    private static final String PRODUCT_COLLECTION = "products";
    private static final String REVIEW_DETAIL_COLLECTION = "review_details";

    public Uni<PagedResult<Review>> findReviews(FindAllReview req) {
        return findReviewsInternal(req, false);
    }

    public Uni<PagedResult<Review>> findActiveReviews(FindAllReview req) {
        return findReviewsInternal(req, false);
    }

    public Uni<PagedResult<Review>> findTrashedReviews(FindAllReview req) {
        return findReviewsInternal(req, true);
    }

    private Uni<PagedResult<Review>> findReviewsInternal(FindAllReview req, boolean trashed) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Review> panacheQuery;
        if (trashed) {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': { '$ne': null } }")
                    : find("{ 'deletedAt': { '$ne': null }, '$or': [ "
                            + "{ 'name': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'comment': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        } else {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': null }")
                    : find("{ 'deletedAt': null, '$or': [ "
                            + "{ 'name': { '$regex': ?1, '$options': 'i' } }, "
                            + "{ 'comment': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<Optional<Review>> findReviewById(String reviewId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(reviewId)).firstResult()
                .map(Optional::ofNullable);
    }

    public Uni<Review> findById(String reviewId) {
        return find("_id", new ObjectId(reviewId)).firstResult();
    }

    /**
     * Reviews for a product, each enriched with its {@code review_details}
     * documents. Rewritten from the PostgreSQL native query as a MongoDB
     * aggregation pipeline: the correlated {@code json_agg} sub-select becomes a
     * {@code $lookup} on {@code review_details.reviewId -> reviews._id}.
     */
    public Uni<PagedResult<ReviewRelationsDetail>> findByProductId(FindAllReviewByProduct req) {
        String productId = req.getProductId();
        int rating = req.getRating() == null ? 0 : req.getRating();
        String search = req.getSearch() == null ? "" : req.getSearch().trim();
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;

        List<Bson> conditions = new ArrayList<>();
        conditions.add(Filters.eq("deletedAt", null));
        conditions.add(Filters.eq("productId", new ObjectId(productId)));
        if (rating != 0) {
            conditions.add(Filters.eq("rating", rating));
        }
        addSearch(conditions, search);
        Bson match = Filters.and(conditions);

        List<Bson> dataPipeline = List.of(
                Aggregates.match(match),
                Aggregates.lookup(REVIEW_DETAIL_COLLECTION, "_id", "reviewId", "reviewDetail"),
                Aggregates.sort(Sorts.descending("createdAt")),
                Aggregates.skip(page * size),
                Aggregates.limit(size));

        List<Bson> countPipeline = List.of(
                Aggregates.match(match),
                Aggregates.count("total"));

        return pagedRelations(dataPipeline, countPipeline);
    }

    /**
     * Reviews for every product belonging to a merchant. Rewritten from the
     * PostgreSQL native {@code JOIN products} as a {@code $lookup} +
     * {@code $unwind} over the {@code products} collection (the unwind mirrors the
     * inner join semantics), followed by a {@code $lookup} for the review details.
     */
    public Uni<PagedResult<ReviewRelationsDetail>> findByMerchantId(FindAllReviewByMerchant req) {
        String merchantId = req.getMerchantId();
        int rating = req.getRating() == null ? 0 : req.getRating();
        String search = req.getSearch() == null ? "" : req.getSearch().trim();
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;

        List<Bson> conditions = new ArrayList<>();
        conditions.add(Filters.eq("deletedAt", null));
        conditions.add(Filters.eq("product.merchantId", new ObjectId(merchantId)));
        if (rating != 0) {
            conditions.add(Filters.eq("rating", rating));
        }
        addSearch(conditions, search);
        Bson match = Filters.and(conditions);

        Bson lookupProduct = Aggregates.lookup(PRODUCT_COLLECTION, "productId", "_id", "product");
        Bson unwindProduct = Aggregates.unwind("$product");

        List<Bson> dataPipeline = List.of(
                lookupProduct,
                unwindProduct,
                Aggregates.match(match),
                Aggregates.lookup(REVIEW_DETAIL_COLLECTION, "_id", "reviewId", "reviewDetail"),
                Aggregates.sort(Sorts.descending("createdAt")),
                Aggregates.skip(page * size),
                Aggregates.limit(size));

        List<Bson> countPipeline = List.of(
                lookupProduct,
                unwindProduct,
                Aggregates.match(match),
                Aggregates.count("total"));

        return pagedRelations(dataPipeline, countPipeline);
    }

    private void addSearch(List<Bson> conditions, String search) {
        if (search != null && !search.isEmpty()) {
            String regex = Pattern.quote(search);
            conditions.add(Filters.or(
                    Filters.regex("name", regex, "i"),
                    Filters.regex("comment", regex, "i")));
        }
    }

    private Uni<PagedResult<ReviewRelationsDetail>> pagedRelations(List<Bson> dataPipeline, List<Bson> countPipeline) {
        Uni<List<ReviewRelationsDetail>> dataUni = mongoCollection()
                .aggregate(dataPipeline, Document.class)
                .collect().asList()
                .map(this::mapRelations);
        Uni<Long> countUni = mongoCollection()
                .aggregate(countPipeline, Document.class)
                .collect().asList()
                .map(list -> list.isEmpty() ? 0L : ((Number) list.get(0).get("total")).longValue());

        return Uni.combine().all().unis(dataUni, countUni)
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    private List<ReviewRelationsDetail> mapRelations(List<Document> documents) {
        List<ReviewRelationsDetail> list = new ArrayList<>();
        for (Document doc : documents) {
            ReviewRelationsDetail dto = new ReviewRelationsDetail();
            dto.setId(toHex(doc.get("_id")));
            dto.setUserId(toHex(doc.get("userId")));
            dto.setProductId(toHex(doc.get("productId")));
            dto.setName(doc.getString("name"));
            dto.setComment(doc.getString("comment"));
            Object rating = doc.get("rating");
            dto.setRating(rating != null ? ((Number) rating).intValue() : null);
            dto.setCreatedAt(toInstantString(doc.get("createdAt")));
            dto.setUpdatedAt(toInstantString(doc.get("updatedAt")));
            dto.setDeletedAt(toInstantString(doc.get("deletedAt")));
            dto.setReviewDetail(mapDetails(doc.getList("reviewDetail", Document.class)));
            list.add(dto);
        }
        return list;
    }

    private List<ReviewDetail> mapDetails(List<Document> documents) {
        List<ReviewDetail> details = new ArrayList<>();
        if (documents == null) {
            return details;
        }
        for (Document doc : documents) {
            ReviewDetail detail = new ReviewDetail();
            detail.id = asObjectId(doc.get("_id"));
            detail.setReviewId(asObjectId(doc.get("reviewId")));
            detail.setType(doc.getString("type"));
            detail.setUrl(doc.getString("url"));
            detail.setCaption(doc.getString("caption"));
            detail.setCreatedAt(toInstant(doc.get("createdAt")));
            detail.setUpdatedAt(toInstant(doc.get("updatedAt")));
            detail.setDeletedAt(toInstant(doc.get("deletedAt")));
            details.add(detail);
        }
        return details;
    }

    private ObjectId asObjectId(Object value) {
        return value instanceof ObjectId objectId ? objectId : null;
    }

    private String toHex(Object value) {
        if (value instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        return value != null ? value.toString() : null;
    }

    private String toInstantString(Object value) {
        Instant instant = toInstant(value);
        return instant != null ? instant.toString() : null;
    }

    private Instant toInstant(Object value) {
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }
}
