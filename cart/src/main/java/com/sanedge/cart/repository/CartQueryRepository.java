package com.sanedge.cart.repository;

import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.cart.entity.Cart;
import com.sanedge.cart.domain.requests.FindAllCartsRequest;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CartQueryRepository implements ReactivePanacheMongoRepository<Cart> {

    public Uni<PagedResult<Cart>> findCartsByUser(FindAllCartsRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;
        ObjectId userId = new ObjectId(req.getUserId());

        ReactivePanacheQuery<Cart> panacheQuery;
        if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null, 'userId': ?1 }", userId);
        } else {
            panacheQuery = find(
                    "{ 'deletedAt': null, 'userId': ?1, '$or': [ "
                            + "{ 'name': { '$regex': ?2, '$options': 'i' } }, "
                            + "{ '$expr': { '$regexMatch': { 'input': { '$toString': '$price' }, 'regex': ?2, 'options': 'i' } } } "
                            + "] }",
                    userId, Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<Cart> findById(String cartId) {
        return find("_id", new ObjectId(cartId)).firstResult();
    }
}
