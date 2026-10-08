package com.sanedge.cart.repository;

import java.util.List;

import org.bson.types.ObjectId;

import com.sanedge.cart.entity.Cart;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CartCommandRepository implements ReactivePanacheMongoRepository<Cart> {

    public Uni<Cart> findById(String cartId) {
        return find("_id", new ObjectId(cartId)).firstResult();
    }

    public Uni<Boolean> deleteCartById(String cartId) {
        return delete("{ '_id': ?1 }", new ObjectId(cartId)).map(count -> count > 0);
    }

    public Uni<Boolean> deleteCartsByIds(List<String> cartIds) {
        List<ObjectId> objectIds = cartIds.stream().map(ObjectId::new).toList();
        return delete("{ '_id': { '$in': ?1 } }", objectIds).map(count -> count > 0);
    }
}
