package com.sanedge.order.repository;

import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.order.entity.Order;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderQueryRepository implements ReactivePanacheMongoRepository<Order> {

    /**
     * Replicates the previous SQL semantics of searching the string form of
     * id / totalPrice with a LIKE '%keyword%'.
     */
    private static final String ID_SEARCH_OR = "'$or': ["
            + "{ '$expr': { '$regexMatch': { 'input': { '$toString': '$_id' }, 'regex': ?1 } } }, "
            + "{ '$expr': { '$regexMatch': { 'input': { '$toString': '$totalPrice' }, 'regex': ?1 } } }"
            + "]";

    public Uni<PagedResult<Order>> findOrders(com.sanedge.order.domain.requests.FindAllOrderRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Order> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ }");
        } else {
            panacheQuery = find("{ " + ID_SEARCH_OR + " }", Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Order>> findActiveOrders(com.sanedge.order.domain.requests.FindAllOrderRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Order> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null }");
        } else {
            panacheQuery = find("{ 'deletedAt': null, " + ID_SEARCH_OR + " }", Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Order>> findTrashedOrders(com.sanedge.order.domain.requests.FindAllOrderRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Order> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': { '$ne': null } }");
        } else {
            panacheQuery = find("{ 'deletedAt': { '$ne': null }, " + ID_SEARCH_OR + " }", Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Order>> findOrdersByMerchant(
            com.sanedge.order.domain.requests.FindAllOrderByMerchantRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;
        String merchantId = req.getMerchantId();
        ObjectId merchantObjectId = merchantId != null && !merchantId.isBlank() ? new ObjectId(merchantId) : null;

        ReactivePanacheQuery<Order> panacheQuery;
        if (keyword == null && merchantObjectId == null) {
            panacheQuery = find("{ 'deletedAt': null }");
        } else if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null, 'merchantId': ?1 }", merchantObjectId);
        } else if (merchantObjectId == null) {
            panacheQuery = find("{ 'deletedAt': null, " + ID_SEARCH_OR + " }", Pattern.quote(keyword));
        } else {
            panacheQuery = find("{ 'deletedAt': null, 'merchantId': ?2, " + ID_SEARCH_OR + " }",
                    Pattern.quote(keyword), merchantObjectId);
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<Optional<Order>> findOrderById(String orderId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(orderId)).firstResult()
                .map(Optional::ofNullable);
    }

    public Uni<Order> findById(String orderId) {
        return find("_id", new ObjectId(orderId)).firstResult();
    }
}
