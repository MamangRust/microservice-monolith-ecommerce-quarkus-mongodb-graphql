package com.sanedge.shipping_address.repository;

import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.shipping_address.domain.requests.FindAllShippingAddress;
import com.sanedge.shipping_address.entity.ShippingAddress;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ShippingAddressQueryRepository implements ReactivePanacheMongoRepository<ShippingAddress> {

    public Uni<PagedResult<ShippingAddress>> findShippingAddresses(FindAllShippingAddress req) {
        return findShippingAddressesInternal(req, false);
    }

    public Uni<PagedResult<ShippingAddress>> findActiveShippingAddresses(FindAllShippingAddress req) {
        return findShippingAddressesInternal(req, false);
    }

    public Uni<PagedResult<ShippingAddress>> findTrashedShippingAddresses(FindAllShippingAddress req) {
        return findShippingAddressesInternal(req, true);
    }

    private Uni<PagedResult<ShippingAddress>> findShippingAddressesInternal(FindAllShippingAddress req, boolean trashed) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<ShippingAddress> panacheQuery;
        if (trashed) {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': { '$ne': null } }")
                    : find("{ 'deletedAt': { '$ne': null }, '$or': [ { 'kota': { '$regex': ?1, '$options': 'i' } }, { 'provinsi': { '$regex': ?1, '$options': 'i' } }, { 'negara': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        } else {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': null }")
                    : find("{ 'deletedAt': null, '$or': [ { 'kota': { '$regex': ?1, '$options': 'i' } }, { 'provinsi': { '$regex': ?1, '$options': 'i' } }, { 'negara': { '$regex': ?1, '$options': 'i' } } ] }",
                            Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<Optional<ShippingAddress>> findByIdNative(String id) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(id)).firstResult().map(Optional::ofNullable);
    }

    public Uni<Optional<ShippingAddress>> findByOrderId(String orderId) {
        return find("{ 'orderId': ?1, 'deletedAt': null }", new ObjectId(orderId)).firstResult()
                .map(Optional::ofNullable);
    }
}
