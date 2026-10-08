package com.sanedge.order_item.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import com.sanedge.common.test.MongoDbResource;
import com.sanedge.common.test.MongoTestBase;
import com.sanedge.order_item.domain.requests.FindAllOrderItemRequest;
import com.sanedge.order_item.entity.OrderItem;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;

@QuarkusTest
@QuarkusTestResource(MongoDbResource.class)
class OrderItemRepositoryTest extends MongoTestBase {

    private static final ObjectId ORDER_ID_100 = new ObjectId("507f1f77bcf86cd7994390a1");
    private static final ObjectId ORDER_ID_200 = new ObjectId("507f1f77bcf86cd7994390a2");
    private static final ObjectId PRODUCT_ID_1 = new ObjectId("507f1f77bcf86cd7994390b1");
    private static final ObjectId PRODUCT_ID_2 = new ObjectId("507f1f77bcf86cd7994390b2");
    private static final ObjectId PRODUCT_ID_3 = new ObjectId("507f1f77bcf86cd7994390b3");

    @Inject
    OrderItemRepository orderItemRepository;

    private Uni<OrderItem> createAndPersistItem(ObjectId orderId, ObjectId productId) {
        OrderItem o = new OrderItem();
        o.setOrderId(orderId);
        o.setProductId(productId);
        o.setPrice(15000);
        o.setQuantity(2);
        o.setCreatedAt(Instant.now());
        o.setUpdatedAt(Instant.now());
        return orderItemRepository.persist(o).replaceWith(o);
    }

    @Test
    void testCreateAndFindById() {
        createAndPersistItem(ORDER_ID_100, PRODUCT_ID_1)
                .invoke(saved -> {
                    assertThat(saved).isNotNull();
                    assertThat(saved.id).isNotNull();
                })
                .chain(saved -> orderItemRepository.findById(saved.id.toHexString()))
                .invoke(found -> assertThat(found).isNotNull())
                .replaceWithVoid()
                .await().indefinitely();
    }

    @Test
    void testFindByOrderId() {
        Uni.join().all(
                createAndPersistItem(ORDER_ID_100, PRODUCT_ID_1),
                createAndPersistItem(ORDER_ID_100, PRODUCT_ID_2),
                createAndPersistItem(ORDER_ID_200, PRODUCT_ID_3))
                .andCollectFailures()
                .chain(ignored -> orderItemRepository.findOrderItemByOrder(ORDER_ID_100.toHexString()))
                .invoke(result -> assertThat(result).hasSize(2))
                .replaceWithVoid()
                .await().indefinitely();
    }

    @Test
    void testFindAllItemsPaged() {
        Uni.join().all(
                createAndPersistItem(ORDER_ID_100, PRODUCT_ID_1),
                createAndPersistItem(ORDER_ID_100, PRODUCT_ID_2),
                createAndPersistItem(ORDER_ID_100, PRODUCT_ID_3),
                createAndPersistItem(ORDER_ID_100, new ObjectId("507f1f77bcf86cd7994390b4")),
                createAndPersistItem(ORDER_ID_100, new ObjectId("507f1f77bcf86cd7994390b5")))
                .andCollectFailures()
                .chain(ignored -> {
                    FindAllOrderItemRequest req = new FindAllOrderItemRequest();
                    req.setPage(1);
                    req.setPageSize(10);
                    return orderItemRepository.findOrderItems(req);
                })
                .invoke(result -> assertThat(result.getData()).hasSize(5))
                .replaceWithVoid()
                .await().indefinitely();
    }

    @Test
    void testTrashOrderItem() {
        createAndPersistItem(ORDER_ID_100, PRODUCT_ID_1)
                .chain(saved -> orderItemRepository.trash(saved.id.toHexString()))
                .invoke(trashed -> {
                    assertThat(trashed).isNotNull();
                    assertThat(trashed.getDeletedAt()).isNotNull();
                })
                .replaceWithVoid()
                .await().indefinitely();
    }

    @Test
    void testRestoreOrderItem() {
        createAndPersistItem(ORDER_ID_100, PRODUCT_ID_1)
                .chain(saved -> orderItemRepository.trash(saved.id.toHexString())
                        .chain(ignored -> orderItemRepository.restore(saved.id.toHexString())))
                .invoke(restored -> {
                    assertThat(restored).isNotNull();
                    assertThat(restored.getDeletedAt()).isNull();
                })
                .replaceWithVoid()
                .await().indefinitely();
    }

    @Test
    void testDeletePermanent() {
        createAndPersistItem(ORDER_ID_100, PRODUCT_ID_1)
                .chain(saved -> orderItemRepository.trash(saved.id.toHexString())
                        .chain(ignored -> orderItemRepository.deletePermanent(saved.id.toHexString())))
                .invoke(deleted -> assertThat(deleted).isNotNull())
                .replaceWithVoid()
                .await().indefinitely();
    }
}
