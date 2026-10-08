package com.sanedge.order_item.repository;

import java.time.Instant;
import java.util.List;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.order_item.domain.requests.FindAllOrderItemRequest;
import com.sanedge.order_item.entity.OrderItem;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderItemRepository implements ReactivePanacheMongoRepository<OrderItem> {

    public Uni<OrderItem> findById(String orderItemId) {
        return find("_id", new ObjectId(orderItemId)).firstResult();
    }

    public Uni<List<OrderItem>> findOrderItemByOrder(String orderId) {
        return find("{ 'orderId': ?1, 'deletedAt': null }", new ObjectId(orderId)).list();
    }

    private Uni<PagedResult<OrderItem>> paged(String deletedFilter, FindAllOrderItemRequest req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() != null && req.getPageSize() > 0 ? req.getPageSize() : 10;

        ReactivePanacheQuery<OrderItem> panacheQuery = find("{ " + deletedFilter + " }");
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<OrderItem>> findOrderItems(FindAllOrderItemRequest req) {
        return paged("'deletedAt': null", req);
    }

    public Uni<PagedResult<OrderItem>> findActiveOrderItems(FindAllOrderItemRequest req) {
        return paged("'deletedAt': null", req);
    }

    public Uni<PagedResult<OrderItem>> findTrashedOrderItems(FindAllOrderItemRequest req) {
        return paged("'deletedAt': { '$ne': null }", req);
    }

    public Uni<OrderItem> trash(String orderItemId) {
        return find("_id", new ObjectId(orderItemId)).firstResult()
                .chain(item -> {
                    if (item != null && item.getDeletedAt() == null) {
                        item.setDeletedAt(Instant.now());
                        return item.update().map(v -> item);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<OrderItem> restore(String orderItemId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(orderItemId)).firstResult()
                .chain(item -> {
                    if (item != null) {
                        item.setDeletedAt(null);
                        return item.update().map(v -> item);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<OrderItem> deletePermanent(String orderItemId) {
        return find("{ '_id': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(orderItemId)).firstResult()
                .chain(item -> {
                    if (item != null) {
                        return item.delete().map(v -> item);
                    }
                    return Uni.createFrom().nullItem();
                });
    }

    public Uni<Boolean> restoreAllDeleted() {
        return update("{ '$set': { 'deletedAt': null } }")
                .where("{ 'deletedAt': { '$ne': null } }")
                .map(updatedCount -> updatedCount > 0);
    }

    public Uni<Boolean> deleteAllDeleted() {
        return delete("{ 'deletedAt': { '$ne': null } }")
                .map(deletedCount -> deletedCount > 0);
    }

    public Uni<Boolean> deleteByOrderPermanent(String orderId) {
        return delete("{ 'orderId': ?1, 'deletedAt': { '$ne': null } }", new ObjectId(orderId))
                .map(count -> count > 0);
    }

    public Uni<Boolean> deleteByOrderRollback(String orderId) {
        return delete("{ 'orderId': ?1 }", new ObjectId(orderId))
                .map(count -> count > 0);
    }
}
