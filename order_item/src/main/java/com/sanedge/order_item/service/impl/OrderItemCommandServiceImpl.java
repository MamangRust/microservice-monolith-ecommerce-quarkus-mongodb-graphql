package com.sanedge.order_item.service.impl;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order_item.domain.requests.CreateOrderItemRequest;
import com.sanedge.order_item.domain.requests.UpdateOrderItemRequest;
import com.sanedge.order_item.domain.response.OrderItemResponse;
import com.sanedge.order_item.domain.response.OrderItemResponseDeleteAt;
import com.sanedge.order_item.entity.OrderItem;
import com.sanedge.order_item.repository.OrderItemRepository;
import com.sanedge.order_item.service.OrderItemCommandService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrderItemCommandServiceImpl implements OrderItemCommandService {
    private static final Logger logger = LoggerFactory.getLogger(OrderItemCommandServiceImpl.class);

    private final OrderItemRepository orderItemRepository;
    private final TracingMetrics tracingMetrics;

    @Inject
    public OrderItemCommandServiceImpl(OrderItemRepository orderItemRepository, TracingMetrics tracingMetrics) {
        this.orderItemRepository = orderItemRepository;
        this.tracingMetrics = tracingMetrics;
    }

    @Override
    public Uni<ApiResponse<OrderItemResponse>> create(CreateOrderItemRequest request) {
        logger.info("Creating order item: {}", request);

        OrderItem item = new OrderItem();
        item.setOrderId(new ObjectId(request.getOrderId()));
        item.setProductId(new ObjectId(request.getProductId()));
        item.setQuantity(request.getQuantity());
        item.setPrice(request.getPrice());
        Instant now = Instant.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        return tracingMetrics.traceAndMeasure("createOrderItem", "create_order_item",
                () -> orderItemRepository.persist(item)
                        .map(saved -> {
                            logger.info("Successfully created order item with ID: {}", saved.id);
                            return ApiResponse.success("Order item created successfully",
                                    OrderItemResponse.from(saved));
                        })
                        .onFailure().invoke(e -> logger.error("Failed to create order item", e)));
    }

    @Override
    public Uni<ApiResponse<OrderItemResponse>> update(UpdateOrderItemRequest request) {
        logger.info("Updating order item: {}", request);

        return tracingMetrics.traceAndMeasure("updateOrderItem", "update_order_item",
                Attributes.builder().put("order_item.id", request.getOrderItemId().toString()).build(),
                () -> orderItemRepository.findById(request.getOrderItemId())
                        .chain(item -> {
                            if (item == null) {
                                logger.warn("Order item not found with ID: {}", request.getOrderItemId());
                                throw new ResourceNotFoundException("Order item not found");
                            }
                            item.setQuantity(request.getQuantity());
                            item.setPrice(request.getPrice());
                            item.setUpdatedAt(Instant.now());
                            return orderItemRepository.update(item);
                        })
                        .map(saved -> {
                            logger.info("Successfully updated order item with ID: {}", saved.id);
                            return ApiResponse.success("Order item updated successfully",
                                    OrderItemResponse.from(saved));
                        })
                        .onFailure()
                        .invoke(e -> logger.error("Failed to update order item ID: {}", request.getOrderItemId(), e)));
    }

    @Override
    public Uni<ApiResponse<OrderItemResponseDeleteAt>> trash(String id) {
        logger.info("Trashing order item: {}", id);

        return tracingMetrics.traceAndMeasure("trashOrderItem", "trash_order_item",
                Attributes.builder().put("order_item.id", id.toString()).build(), () -> orderItemRepository.trash(id)
                        .map(item -> {
                            if (item == null) {
                                logger.warn("Failed to trash order item - not found or already trashed with ID: {}",
                                        id);
                                throw new ResourceNotFoundException("Order item not found");
                            }
                            logger.info("Successfully trashed order item with ID: {}", id);
                            return ApiResponse.success("Order item trashed successfully",
                                    OrderItemResponseDeleteAt.from(item));
                        })
                        .onFailure().invoke(e -> logger.error("Failed to trash order item ID: {}", id, e)));
    }

    @Override
    public Uni<ApiResponse<OrderItemResponseDeleteAt>> restore(String id) {
        logger.info("Restoring order item: {}", id);

        return tracingMetrics.traceAndMeasure("restoreOrderItem", "restore_order_item",
                Attributes.builder().put("order_item.id", id.toString()).build(), () -> orderItemRepository.restore(id)
                        .map(item -> {
                            if (item == null) {
                                logger.warn("Failed to restore order item - not found or not trashed with ID: {}", id);
                                throw new ResourceNotFoundException("Order item not found in trash");
                            }
                            logger.info("Successfully restored order item with ID: {}", id);
                            return ApiResponse.success("Order item restored successfully",
                                    OrderItemResponseDeleteAt.from(item));
                        })
                        .onFailure().invoke(e -> logger.error("Failed to restore order item ID: {}", id, e)));
    }

    @Override
    public Uni<ApiResponse<Void>> deletePermanent(String id) {
        Attributes attrs = Attributes.builder().put("order_item.id", id.toString()).build();
        logger.warn("Permanently deleting order item: {}", id);

        return tracingMetrics.traceAndMeasure("deleteOrderItemPermanent", "delete_order_item_permanent", attrs, () -> {
            return orderItemRepository.deletePermanent(id)
                    .map(deletedItem -> {
                        if (deletedItem == null) {
                            logger.warn("Permanent delete failed - order item not found or must be trashed before permanent deletion with id: {}", id);
                            throw new InvalidRequestException("Order item not found or must be trashed before permanent deletion");
                        }
                        logger.info("Successfully permanently deleted order item with ID: {}", id);
                        return ApiResponse.<Void>success("Order item permanently deleted");
                    });
        });
    }

    @Override
    public Uni<ApiResponse<Void>> restoreAll() {
        logger.info("Restoring all trashed order items");

        return tracingMetrics.traceAndMeasure("restoreAllOrderItems", "restore_all_order_items", () -> {
            return orderItemRepository.restoreAllDeleted()
                    .map(success -> {
                        if (!success) {
                            throw new ResourceNotFoundException("No trashed order items found");
                        }
                        logger.info("Successfully restored all trashed order items");
                        return ApiResponse.<Void>success("All order items restored");
                    });
        });
    }

    @Override
    public Uni<ApiResponse<Void>> deleteAll() {
        logger.warn("Permanently deleting all trashed order items");

        return tracingMetrics.traceAndMeasure("deleteAllOrderItemsPermanent", "delete_all_order_items_permanent", () -> {
            return orderItemRepository.deleteAllDeleted()
                    .map(success -> {
                        if (!success) {
                            throw new ResourceNotFoundException("No trashed order items found");
                        }
                        logger.info("Successfully permanently deleted all trashed order items");
                        return ApiResponse.<Void>success("All trashed order items permanently deleted");
                    });
        });
    }

    @Override
    public Uni<ApiResponse<Void>> deleteByOrderPermanent(String orderId) {
        logger.warn("Permanently deleting all order items for order ID: {}", orderId);

        return tracingMetrics.traceAndMeasure("deleteByOrderPermanent", "delete_order_items_by_order_permanent",
                Attributes.builder().put("order.id", orderId.toString()).build(),
                () -> orderItemRepository.deleteByOrderPermanent(orderId)
                        .map(success -> {
                            if (!success) {
                                throw new ResourceNotFoundException("No order items found for order id: " + orderId);
                            }
                            logger.info("Successfully permanently deleted order items for order ID: {}", orderId);
                            return ApiResponse.<Void>success("Order items permanently deleted for order id: " + orderId);
                        })
                        .onFailure().invoke(e -> logger
                                .error("Failed to permanently delete order items for order ID: {}", orderId, e)));
    }

    @Override
    public Uni<ApiResponse<Void>> deleteByOrderRollback(String orderId) {
        return tracingMetrics.traceAndMeasure("deleteByOrderRollback", "delete_order_items_by_order_rollback",
                Attributes.builder().put("order.id", orderId.toString()).build(),
                () -> orderItemRepository.deleteByOrderRollback(orderId)
                        .map(deleted -> ApiResponse.<Void>success("Order items rolled back")));
    }

    @Override
    public Uni<ApiResponse<Integer>> calculateTotalPrice(String orderId) {
        logger.info("Calculating total price for order ID: {}", orderId);

        return tracingMetrics.traceAndMeasure("calculateTotalPrice", "calculate_total_price",
                Attributes.builder().put("order.id", orderId.toString()).build(),
                () -> orderItemRepository.findOrderItemByOrder(orderId)
                        .map(items -> {
                            int total = items.stream()
                                    .mapToInt(item -> item.getPrice() * item.getQuantity())
                                    .sum();
                            logger.info("Successfully calculated total price for order ID: {}. Total: {}", orderId,
                                    total);
                            return ApiResponse.success("Total price calculated", total);
                        })
                        .onFailure()
                        .invoke(e -> logger.error("Failed to calculate total price for order ID: {}", orderId, e)));
    }
}
