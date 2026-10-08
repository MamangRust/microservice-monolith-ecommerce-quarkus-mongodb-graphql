package com.sanedge.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.order.domain.requests.FindAllOrderByMerchantRequest;
import com.sanedge.order.domain.requests.FindAllOrderRequest;
import com.sanedge.order.entity.Order;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class OrderRepositoryTest extends MongoTestBase {

    private static final ObjectId MERCHANT_1 = new ObjectId("507f1f77bcf86cd7994390a1");
    private static final ObjectId MERCHANT_2 = new ObjectId("507f1f77bcf86cd7994390a2");
    private static final String ID_999 = "507f1f77bcf86cd799439099";

    @Inject
    OrderQueryRepository orderQueryRepo;

    @Inject
    OrderCommandRepository orderCommandRepo;

    private Uni<Order> persistOrder(int totalPrice, ObjectId merchantId) {
        Order order = new Order();
        order.setTotalPrice(totalPrice);
        order.setMerchantId(merchantId);
        return orderQueryRepo.persist(order).replaceWith(order);
    }

    private FindAllOrderRequest findAllReq(int page, int size, String search) {
        FindAllOrderRequest r = new FindAllOrderRequest();
        r.setPage(page);
        r.setPageSize(size);
        r.setSearch(search == null ? "" : search);
        return r;
    }

    private FindAllOrderByMerchantRequest findAllByMerchantReq(String merchantId, int page, int size, String search) {
        FindAllOrderByMerchantRequest r = new FindAllOrderByMerchantRequest();
        r.setMerchantId(merchantId);
        r.setPage(page);
        r.setPageSize(size);
        r.setSearch(search == null ? "" : search);
        return r;
    }

    @Test
    void testCreateAndFindById() {
        persistOrder(100000, MERCHANT_1)
                .chain(saved -> orderQueryRepo.findById(saved.id.toHexString()))
                .invoke(found -> {
                    assertThat(found).isNotNull();
                    assertThat(found.getTotalPrice()).isEqualTo(100000);
                    assertThat(found.getMerchantId()).isEqualTo(MERCHANT_1);
                    assertThat(found.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindOrderById() {
        persistOrder(200000, MERCHANT_2)
                .chain(saved -> orderQueryRepo.findOrderById(saved.id.toHexString()))
                .invoke(opt -> {
                    assertThat(opt).isPresent();
                    assertThat(opt.get().getTotalPrice()).isEqualTo(200000);
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testFindOrderByIdReturnsEmptyIfNotFound() {
        orderQueryRepo.findOrderById(ID_999)
                .invoke(opt -> assertThat(opt).isEmpty())
                .await().indefinitely();
    }

    @Test
    void testFindOrderByIdReturnsEmptyIfTrashed() {
        persistOrder(300000, MERCHANT_1)
                .chain(saved -> orderCommandRepo.trashed(saved.id.toHexString())
                        .chain(() -> orderQueryRepo.findOrderById(saved.id.toHexString())))
                .invoke(opt -> assertThat(opt).isEmpty())
                .await().indefinitely();
    }

    @Test
    void testFindByIdReturnsNullWhenNotFound() {
        orderQueryRepo.findById(ID_999)
                .invoke(n -> assertThat(n).isNull())
                .await().indefinitely();
    }

    @Test
    void testTrashOrder() {
        persistOrder(400000, MERCHANT_1)
                .chain(saved -> orderCommandRepo.trashed(saved.id.toHexString()))
                .invoke(t -> {
                    assertThat(t).isNotNull();
                    assertThat(t.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testTrashOrderReturnsNullIfAlreadyTrashed() {
        persistOrder(500000, MERCHANT_1)
                .chain(saved -> orderCommandRepo.trashed(saved.id.toHexString())
                        .chain(() -> orderCommandRepo.trashed(saved.id.toHexString())))
                .invoke(t -> assertThat(t).isNull())
                .await().indefinitely();
    }

    @Test
    void testTrashOrderReturnsNullIfNotFound() {
        orderCommandRepo.trashed(ID_999)
                .invoke(t -> assertThat(t).isNull())
                .await().indefinitely();
    }

    @Test
    void testRestoreOrder() {
        persistOrder(600000, MERCHANT_1)
                .chain(saved -> orderCommandRepo.trashed(saved.id.toHexString())
                        .chain(() -> orderCommandRepo.restore(saved.id.toHexString())))
                .invoke(r -> {
                    assertThat(r).isNotNull();
                    assertThat(r.getDeletedAt()).isNull();
                })
                .replaceWithVoid().await().indefinitely();
    }

    @Test
    void testRestoreOrderReturnsNullIfNotTrashed() {
        persistOrder(700000, MERCHANT_1)
                .chain(saved -> orderCommandRepo.restore(saved.id.toHexString()))
                .invoke(r -> assertThat(r).isNull())
                .await().indefinitely();
    }

    @Test
    void testRestoreOrderReturnsNullIfNotFound() {
        orderCommandRepo.restore(ID_999)
                .invoke(r -> assertThat(r).isNull())
                .await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        persistOrder(800000, MERCHANT_1)
                .chain(saved -> orderCommandRepo.trashed(saved.id.toHexString())
                        .chain(() -> orderCommandRepo.deletePermanent(saved.id.toHexString()))
                        .chain(() -> orderQueryRepo.findOrderById(saved.id.toHexString())))
                .invoke(opt -> assertThat(opt).isEmpty())
                .await().indefinitely();
    }

    @Test
    void testDeletePermanentFailsIfNotTrashed() {
        persistOrder(900000, MERCHANT_1)
                .chain(saved -> orderCommandRepo.deletePermanent(saved.id.toHexString()))
                .invoke(d -> assertThat(d).isNull())
                .await().indefinitely();
    }

    @Test
    void testRestoreAllDeleted() {
        persistOrder(1000000, MERCHANT_1)
                .chain(saved -> orderCommandRepo.trashed(saved.id.toHexString()).replaceWithVoid())
                .chain(() -> persistOrder(1100000, MERCHANT_1)
                        .chain(saved -> orderCommandRepo.trashed(saved.id.toHexString()).replaceWithVoid()))
                .chain(() -> orderCommandRepo.restoreAllDeleted())
                .invoke(r -> assertThat(r).isTrue())
                .await().indefinitely();
    }

    @Test
    void testDeleteAllDeleted() {
        persistOrder(1200000, MERCHANT_1)
                .chain(saved -> orderCommandRepo.trashed(saved.id.toHexString()).replaceWithVoid())
                .chain(() -> persistOrder(1300000, MERCHANT_1)
                        .chain(saved -> orderCommandRepo.trashed(saved.id.toHexString()).replaceWithVoid()))
                .chain(() -> persistOrder(1400000, MERCHANT_1)) // active
                .chain(() -> orderCommandRepo.deleteAllDeleted())
                .invoke(r -> assertThat(r).isTrue())
                .await().indefinitely();
    }

    @Test
    void testFindActiveOrders() {
        persistOrder(1500000, MERCHANT_1)
                .chain(() -> persistOrder(1600000, MERCHANT_1))
                .chain(() -> orderQueryRepo.findActiveOrders(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isEqualTo(2))
                .await().indefinitely();
    }

    @Test
    void testFindTrashedOrdersEmpty() {
        persistOrder(1700000, MERCHANT_1)
                .chain(() -> persistOrder(1800000, MERCHANT_1))
                .chain(() -> orderQueryRepo.findTrashedOrders(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isZero())
                .await().indefinitely();
    }

    @Test
    void testFindTrashedOrders() {
        persistOrder(1900000, MERCHANT_1)
                .chain(saved -> orderCommandRepo.trashed(saved.id.toHexString()).replaceWithVoid())
                .chain(() -> persistOrder(2000000, MERCHANT_1)
                        .chain(saved -> orderCommandRepo.trashed(saved.id.toHexString()).replaceWithVoid()))
                .chain(() -> orderQueryRepo.findTrashedOrders(findAllReq(1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isEqualTo(2))
                .await().indefinitely();
    }

    @Test
    void testFindOrdersWithSearchKeyword() {
        persistOrder(100000, MERCHANT_1)
                .chain(() -> persistOrder(200000, MERCHANT_1))
                .chain(() -> persistOrder(300000, MERCHANT_1))
                .chain(() -> orderQueryRepo.findOrders(findAllReq(1, 10, "200")))
                .invoke(r -> {
                    assertThat(r.getTotalRecords()).isEqualTo(1);
                    assertThat(r.getData().get(0).getTotalPrice()).isEqualTo(200000);
                })
                .await().indefinitely();
    }

    @Test
    void testFindOrdersWithPagination() {
        persistOrder(10, MERCHANT_1)
                .chain(() -> persistOrder(20, MERCHANT_1))
                .chain(() -> persistOrder(30, MERCHANT_1))
                .chain(() -> persistOrder(40, MERCHANT_1))
                .chain(() -> persistOrder(50, MERCHANT_1))
                .chain(() -> orderQueryRepo.findOrders(findAllReq(1, 2, "")))
                .map(page1 -> {
                    assertThat(page1.getData()).hasSize(2);
                    assertThat(page1.getTotalRecords()).isEqualTo(5);
                    return page1;
                })
                .chain(page1 -> orderQueryRepo.findOrders(findAllReq(2, 2, "")))
                .invoke(page2 -> assertThat(page2.getData()).hasSize(2))
                .await().indefinitely();
    }

    @Test
    void testFindOrdersByMerchant() {
        persistOrder(100, MERCHANT_1)
                .chain(() -> persistOrder(200, MERCHANT_2))
                .chain(() -> persistOrder(300, MERCHANT_1))
                .chain(() -> orderQueryRepo.findOrdersByMerchant(findAllByMerchantReq(MERCHANT_1.toHexString(), 1, 10, "")))
                .invoke(r -> assertThat(r.getTotalRecords()).isEqualTo(2))
                .await().indefinitely();
    }

    @Test
    void testFindOrdersByMerchantWithSearch() {
        persistOrder(1000, MERCHANT_1)
                .chain(() -> persistOrder(2000, MERCHANT_1))
                .chain(() -> persistOrder(2000, MERCHANT_2))
                .chain(() -> orderQueryRepo.findOrdersByMerchant(
                        findAllByMerchantReq(MERCHANT_1.toHexString(), 1, 10, "2000")))
                .invoke(r -> {
                    assertThat(r.getTotalRecords()).isEqualTo(1);
                    assertThat(r.getData().get(0).getMerchantId()).isEqualTo(MERCHANT_1);
                    assertThat(r.getData().get(0).getTotalPrice()).isEqualTo(2000);
                })
                .await().indefinitely();
    }
}
