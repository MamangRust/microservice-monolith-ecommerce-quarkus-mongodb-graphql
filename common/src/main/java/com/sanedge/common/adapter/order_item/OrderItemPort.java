package com.sanedge.common.adapter.order_item;

import java.util.List;

import com.sanedge.common.adapter.model.OrderItem;
import com.sanedge.common.adapter.support.Paged;

import io.smallrye.mutiny.Uni;

/**
 * Port order item: baca + tulis + enumerasi. Setara
 * {@code order_item.QueryRepository}/{@code CommandRepository}/{@code BulkRepository}
 * di Go.
 */
public interface OrderItemPort {

    Uni<List<OrderItem>> findOrderItemByOrder(String orderId);

    Uni<Integer> calculateTotalPrice(String orderId);

    Uni<OrderItem> create(CreateData data);

    Uni<OrderItem> update(UpdateData data);

    Uni<OrderItem> trash(String orderItemId);

    Uni<OrderItem> restore(String orderItemId);

    Uni<Boolean> deletePermanent(String orderItemId);

    Uni<Boolean> deleteByOrderIdPermanent(String orderId);

    /** Rollback kompensasi: hapus item milik order (dipakai saat create order gagal). */
    Uni<Boolean> deleteByOrderIdRollback(String orderId);

    Uni<Boolean> restoreAll();

    Uni<Boolean> deleteAll();

    Uni<Paged<OrderItem>> findAll(int page, int pageSize);

    record CreateData(String orderId, String productId, int quantity, int price) {
    }

    record UpdateData(String orderItemId, int quantity, int price) {
    }
}
