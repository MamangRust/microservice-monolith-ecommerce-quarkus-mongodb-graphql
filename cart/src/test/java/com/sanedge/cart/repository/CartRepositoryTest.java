package com.sanedge.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.cart.domain.requests.FindAllCartsRequest;
import com.sanedge.cart.entity.Cart;
import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class CartRepositoryTest extends MongoTestBase {

    private static final String USER_1 = "507f1f77bcf86cd799439011";
    private static final String USER_2 = "507f1f77bcf86cd799439012";

    @Inject
    CartQueryRepository cartQueryRepo;

    @Inject
    CartCommandRepository cartCommandRepo;

    private Uni<Cart> createAndPersistCart(String userId, String name, Integer price) {
        Cart cart = new Cart();
        cart.setUserId(new ObjectId(userId));
        cart.setName(name);
        cart.setPrice(price);

        return cartQueryRepo.persist(cart).replaceWith(cart);
    }

    private FindAllCartsRequest buildRequest(String userId, int page, int pageSize, String search) {
        FindAllCartsRequest req = new FindAllCartsRequest();
        req.setUserId(userId);
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setSearch(search);
        return req;
    }

    @Test
    void testFindCartsByUser_Success() {
        Uni.join().all(
                createAndPersistCart(USER_1, "Laptop", 15000000),
                createAndPersistCart(USER_1, "Mouse", 500000))
                .andCollectFailures()
                .chain(ignored -> cartQueryRepo.findCartsByUser(buildRequest(USER_1, 1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindCartsByUser_FiltersByUserId() {
        Uni.join().all(
                createAndPersistCart(USER_1, "Item A", 1000),
                createAndPersistCart(USER_2, "Item B", 2000))
                .andCollectFailures()
                .chain(ignored -> cartQueryRepo.findCartsByUser(buildRequest(USER_1, 1, 10, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Item A");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindCartsByUser_WithSearchKeywordByName() {
        Uni.join().all(
                createAndPersistCart(USER_1, "Gaming Laptop", 15000000),
                createAndPersistCart(USER_1, "Office Laptop", 10000000),
                createAndPersistCart(USER_1, "Gaming Mouse", 1500000))
                .andCollectFailures()
                .chain(ignored -> cartQueryRepo.findCartsByUser(buildRequest(USER_1, 1, 10, "Gaming")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(2);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindCartsByUser_WithSearchKeywordByPrice() {
        Uni.join().all(
                createAndPersistCart(USER_1, "Item 15K", 15000),
                createAndPersistCart(USER_1, "Item 20K", 20000))
                .andCollectFailures()
                .chain(ignored -> cartQueryRepo.findCartsByUser(buildRequest(USER_1, 1, 10, "15000")))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(1);
                    assertThat(result.getData().get(0).getName()).isEqualTo("Item 15K");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindCartsByUser_WithPagination() {
        Uni.join().all(
                createAndPersistCart(USER_1, "Item 1", 1000),
                createAndPersistCart(USER_1, "Item 2", 2000),
                createAndPersistCart(USER_1, "Item 3", 3000))
                .andCollectFailures()
                .chain(ignored -> cartQueryRepo.findCartsByUser(buildRequest(USER_1, 1, 2, null)))
                .invoke(result -> {
                    assertThat(result.getData()).hasSize(2);
                    assertThat(result.getTotalRecords()).isEqualTo(3);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindCartsByUser_ReturnsEmptyWhenNoMatch() {
        createAndPersistCart(USER_1, "Laptop", 15000000)
                .chain(ignored -> cartQueryRepo.findCartsByUser(buildRequest(USER_1, 1, 10, "Mouse")))
                .invoke(result -> {
                    assertThat(result.getData()).isEmpty();
                    assertThat(result.getTotalRecords()).isZero();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteCartById_Success() {
        createAndPersistCart(USER_1, "To Delete", 1000)
                .chain(saved -> cartCommandRepo.deleteCartById(saved.id.toHexString())
                        .invoke(deleted -> assertThat(deleted).isTrue())
                        .chain(ignored -> cartQueryRepo.findById(saved.id.toHexString())))
                .invoke(checkDb -> assertThat(checkDb).isNull())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteCartById_ReturnsFalseIfNotFound() {
        cartCommandRepo.deleteCartById("507f1f77bcf86cd799439099")
                .invoke(deleted -> assertThat(deleted).isFalse())
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteCartsByIds_Success() {
        Uni.join().all(
                createAndPersistCart(USER_1, "Bulk 1", 1000),
                createAndPersistCart(USER_1, "Bulk 2", 2000),
                createAndPersistCart(USER_1, "Bulk 3", 3000))
                .andCollectFailures()
                .chain(carts -> {
                    List<String> idsToDelete = List.of(carts.get(0).id.toHexString(), carts.get(1).id.toHexString());
                    return cartCommandRepo.deleteCartsByIds(idsToDelete)
                            .invoke(deleted -> assertThat(deleted).isTrue())
                            .replaceWith(carts.get(2).id.toHexString());
                })
                .chain(remainingId -> cartQueryRepo.findById(remainingId))
                .invoke(remainingCart -> {
                    assertThat(remainingCart).isNotNull();
                    assertThat(remainingCart.getName()).isEqualTo("Bulk 3");
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testDeleteCartsByIds_ReturnsFalseIfNoneFound() {
        cartCommandRepo.deleteCartsByIds(List.of("507f1f77bcf86cd799439098", "507f1f77bcf86cd799439099"))
                .invoke(deleted -> assertThat(deleted).isFalse())
                .replaceWithVoid().await().indefinitely();
    }
}
