package com.sanedge.order.repository;

import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.order.entity.ShippingAddress;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShippingAddressQueryRepository implements ReactivePanacheMongoRepository<ShippingAddress> {

    private static final String SEARCH_OR = "'$or': [ "
            + "{ 'kota': { '$regex': ?1, '$options': 'i' } }, "
            + "{ 'provinsi': { '$regex': ?1, '$options': 'i' } }, "
            + "{ 'negara': { '$regex': ?1, '$options': 'i' } } ]";

    private Uni<PagedResult<ShippingAddress>> paged(String deletedFilter, String keyword, int page, int size) {
        String quoted = Pattern.quote(keyword == null ? "" : keyword);
        String filter = deletedFilter + ", " + SEARCH_OR;
        ReactivePanacheQuery<ShippingAddress> panacheQuery = find("{ " + filter + " }", quoted).page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<PagedResult<ShippingAddress>> findShippingAddresses(String keyword, int page, int size) {
        return paged("'deletedAt': null", keyword, page, size);
    }

    public Uni<PagedResult<ShippingAddress>> findActiveShippingAddresses(String keyword, int page, int size) {
        return paged("'deletedAt': null", keyword, page, size);
    }

    public Uni<PagedResult<ShippingAddress>> findTrashedShippingAddresses(String keyword, int page, int size) {
        return paged("'deletedAt': { '$ne': null }", keyword, page, size);
    }

    public Uni<Optional<ShippingAddress>> findByIdNative(String id) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(id)).firstResult().map(Optional::ofNullable);
    }

    public Uni<Optional<ShippingAddress>> findByOrderId(String orderId) {
        return find("{ 'orderId': ?1, 'deletedAt': null }", new ObjectId(orderId)).firstResult()
                .map(Optional::ofNullable);
    }
}
