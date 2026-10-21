package com.sanedge.order.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sanedge.common.adapter.merchant.MerchantPort;
import com.sanedge.common.adapter.model.OrderItem;
import com.sanedge.common.adapter.model.Product;
import com.sanedge.common.adapter.model.ShippingAddress;
import com.sanedge.common.adapter.order_item.OrderItemPort;
import com.sanedge.common.adapter.product.ProductPort;
import com.sanedge.common.adapter.shipping_address.ShippingAddressPort;
import com.sanedge.common.adapter.transaction.TransactionPort;
import com.sanedge.common.adapter.user.UserPort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.exception.ForbiddenException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.order.domain.requests.CreateOrderItemRequest;
import com.sanedge.order.domain.requests.CreateOrderRequest;
import com.sanedge.order.domain.requests.UpdateOrderItemRequest;
import com.sanedge.order.domain.requests.UpdateOrderRequest;
import com.sanedge.order.domain.response.OrderResponse;
import com.sanedge.order.domain.response.OrderResponseDeleteAt;
import com.sanedge.order.entity.Order;
import com.sanedge.order.entity.OrderOutbox;
import com.sanedge.order.repository.OrderCommandRepository;
import com.sanedge.order.repository.OrderOutboxRepository;
import com.sanedge.order.repository.OrderQueryRepository;
import com.sanedge.order.service.OrderCommandService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

@ApplicationScoped
public class OrderCommandServiceImpl implements OrderCommandService {
    private static final Logger logger = LoggerFactory.getLogger(OrderCommandServiceImpl.class);

    /** Kafka topic carrying the order stats event (consumed by stats-writer). */
    static final String ORDER_STATS_TOPIC = "order.event";
    /** Kafka topic carrying the order item stats event (consumed by stats-writer). */
    static final String ORDER_ITEM_STATS_TOPIC = "order_item.event";

    @Inject
    MerchantPort merchantPort;

    @Inject
    UserPort userPort;

    @Inject
    ProductPort productPort;

    @Inject
    OrderItemPort orderItemPort;

    @Inject
    TransactionPort transactionPort;

    @Inject
    ShippingAddressPort shippingAddressPort;

    @Inject
    OrderOutboxRepository orderOutboxRepository;

    private final OrderQueryRepository orderQueryRepository;
    private final OrderCommandRepository orderCommandRepository;
    private final Validator validator;
    private final RedisService redisService;
    private final TracingMetrics tracingMetrics;

    @Inject
    public OrderCommandServiceImpl(OrderQueryRepository orderQueryRepository,
            OrderCommandRepository orderCommandRepository,
            Validator validator,
            RedisService redisService,
            TracingMetrics tracingMetrics) {
        this.orderQueryRepository = orderQueryRepository;
        this.orderCommandRepository = orderCommandRepository;
        this.validator = validator;
        this.redisService = redisService;
        this.tracingMetrics = tracingMetrics;
    }

    private <T> void validateRequest(T req) {
        Set<ConstraintViolation<T>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ConstraintViolation<T> violation : violations) {
                sb.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("; ");
            }
            logger.error("Validation failed: {}", sb);
            throw new ConstraintViolationException("Validation failed: " + sb, violations);
        }
    }

    private Uni<Void> invalidateCache(String orderId) {
        if (orderId != null) {
            return Uni.combine().all().unis(
                    redisService.deleteReactive("order:id:" + orderId),
                    redisService.deleteReactive("order:relation:" + orderId)).discardItems();
        }
        return Uni.createFrom().voidItem();
    }

    Uni<Integer> processCreateOrderItems(List<CreateOrderItemRequest> items, Order order, int index,
            int currentTotalPrice) {
        List<CreateOrderItemRequest> reserved = new ArrayList<>();
        return processCreateOrderItemsInternal(items, order, index, currentTotalPrice, reserved)
                .onFailure().recoverWithUni(error -> preserveFailure(error, rollbackCreatedOrder(order.id.toHexString(), reserved)));
    }

    private Uni<Integer> processCreateOrderItemsInternal(List<CreateOrderItemRequest> items, Order order, int index,
            int currentTotalPrice, List<CreateOrderItemRequest> reserved) {
        if (index >= items.size()) {
            return Uni.createFrom().item(currentTotalPrice);
        }

        CreateOrderItemRequest itemReq = items.get(index);
        return productPort
                .findById(itemReq.getProductId())
                .chain(product -> {
                    if (itemReq.getQuantity() <= 0 || product.countInStock() < itemReq.getQuantity()) {
                        throw new InvalidRequestException(
                                "Insufficient stock or invalid quantity for product id=" + itemReq.getProductId());
                    }
                    int authoritativePrice = product.price();
                    var createReq = new OrderItemPort.CreateData(order.id.toHexString(), itemReq.getProductId(),
                            itemReq.getQuantity(), authoritativePrice);

                    return adjustStock(itemReq.getProductId(), -itemReq.getQuantity())
                            .chain(stockResponse -> {
                                reserved.add(itemReq);
                                return orderItemPort.create(createReq).replaceWithVoid();
                            })
                            .chain(() -> processCreateOrderItemsInternal(items, order, index + 1,
                                    currentTotalPrice + (itemReq.getQuantity() * authoritativePrice), reserved));
                });
    }

    private Uni<Void> rollbackCreatedOrder(String orderId, List<CreateOrderItemRequest> reserved) {
        return compensateStock(reserved)
                .chain(() -> orderItemPort.deleteByOrderIdRollback(orderId))
                .replaceWithVoid();
    }

    private Uni<Void> rollbackOrderAfterPersistenceFailure(String orderId, List<CreateOrderItemRequest> reserved) {
        return rollbackCreatedOrder(orderId, reserved)
                .chain(() -> orderCommandRepository.deleteCreated(orderId).replaceWithVoid());
    }

    private <T> Uni<T> preserveFailure(Throwable original, Uni<Void> compensation) {
        return compensation
                .onFailure().invoke(error -> logger.error("Commerce compensation failed; preserving original failure",
                        error))
                .onFailure().recoverWithItem((Void) null)
                .chain(() -> Uni.createFrom().failure(original));
    }

    private Uni<Product> adjustStock(String productId, int delta) {
        return productPort.adjustStock(productId, delta);
    }

    private Uni<Void> compensateStock(List<CreateOrderItemRequest> reserved) {
        if (reserved == null || reserved.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        Uni<Void> compensation = Uni.createFrom().voidItem();
        for (int i = reserved.size() - 1; i >= 0; i--) {
            CreateOrderItemRequest item = reserved.get(i);
            compensation = compensation.chain(() -> adjustStock(item.getProductId(), item.getQuantity()).replaceWithVoid());
        }
        return compensation
                .invoke(() -> recordStockCompensation("success", reserved.size()))
                .onFailure().invoke(error -> recordStockCompensation("failure", reserved.size()));
    }

    private void recordStockCompensation(String result, int items) {
        if (tracingMetrics != null) {
            tracingMetrics.recordStockCompensation(result, items);
        }
    }


    private Uni<Void> ensureOrderDeletable(String orderId) {
        return transactionPort.findByOrderId(orderId)
                .chain(tx -> {
                    if (tx != null && "success".equalsIgnoreCase(tx.paymentStatus())) {
                        throw new InvalidRequestException("Paid order cannot be permanently deleted");
                    }
                    return Uni.createFrom().voidItem();
                });
    }

    private Uni<Void> adjustOrderStock(String orderId, int direction) {
        return orderItemPort.findOrderItemByOrder(orderId)
                .chain(items -> {
                    List<CreateOrderItemRequest> adjusted = new ArrayList<>();
                    return adjustOrderStock(items, 0, direction, adjusted)
                            .onFailure().call(error -> {
                                Uni<Void> rollback = Uni.createFrom().voidItem();
                                for (int i = adjusted.size() - 1; i >= 0; i--) {
                                    var item = adjusted.get(i);
                                    rollback = rollback.chain(() -> adjustStock(
                                            item.getProductId(), -direction * item.getQuantity()).replaceWithVoid());
                                }
                                return rollback;
                            });
                });
    }

    private Uni<Void> adjustOrderStock(List<OrderItem> items, int index, int direction,
            List<CreateOrderItemRequest> adjusted) {
        if (index >= items.size()) {
            return Uni.createFrom().voidItem();
        }
        var item = items.get(index);
        return adjustStock(item.productId(), direction * item.quantity())
                .invoke(() -> {
                    CreateOrderItemRequest marker = new CreateOrderItemRequest();
                    marker.setProductId(item.productId());
                    marker.setQuantity(item.quantity());
                    adjusted.add(marker);
                })
                .replaceWithVoid()
                .chain(() -> adjustOrderStock(items, index + 1, direction, adjusted));
    }

    private record UpdatedOrderItems(int totalPrice, List<CreateOrderItemRequest> adjustments) {
    }

    Uni<UpdatedOrderItems> processUpdateOrderItems(List<UpdateOrderItemRequest> items, Order order, int index,
            int currentTotalPrice) {
        if (items == null || items.isEmpty()) {
            return Uni.createFrom().item(new UpdatedOrderItems(currentTotalPrice, new ArrayList<>()));
        }
        List<CreateOrderItemRequest> adjustments = new ArrayList<>();
        Uni<List<OrderItem>> existingItems = orderItemPort.findOrderItemByOrder(order.id.toHexString());
        Uni<UpdatedOrderItems> operation = existingItems.chain(response -> processUpdateOrderItemsInternal(items, order,
                index, currentTotalPrice, response, adjustments));
        return operation.onFailure().recoverWithUni(error -> preserveFailure(error,
                compensateAdjustments(adjustments)));
    }


    private Uni<UpdatedOrderItems> processUpdateOrderItemsInternal(List<UpdateOrderItemRequest> items, Order order,
            int index, int currentTotalPrice, List<OrderItem> existingItems,
            List<CreateOrderItemRequest> adjustments) {
        if (index >= items.size()) {
            return Uni.createFrom().item(new UpdatedOrderItems(currentTotalPrice, adjustments));
        }

        UpdateOrderItemRequest itemReq = items.get(index);
        return productPort
                .findById(itemReq.getProductId())
                .chain(product -> {
                    int stockDelta;
                    int oldQuantity = 0;
                    if (itemReq.getOrderItemId() != null && !itemReq.getOrderItemId().isEmpty()) {
                        var existingItem = existingItems.stream()
                                .filter(existing -> existing.id().equals(itemReq.getOrderItemId()))
                                .findFirst().orElseThrow(() -> new ResourceNotFoundException(
                                        "Order item not found with id=" + itemReq.getOrderItemId()));
                        if (!existingItem.productId().equals(itemReq.getProductId())) {
                            throw new InvalidRequestException("Order item product cannot be changed");
                        }
                        oldQuantity = existingItem.quantity();
                        stockDelta = oldQuantity - itemReq.getQuantity();
                    } else {
                        stockDelta = -itemReq.getQuantity();
                    }
                    if (itemReq.getQuantity() <= 0) {
                        throw new InvalidRequestException("Order item quantity must be positive");
                    }

                    Uni<Void> persistUnit = (stockDelta == 0
                            ? Uni.createFrom().voidItem()
                            : adjustStock(itemReq.getProductId(), stockDelta).invoke(() -> {
                                CreateOrderItemRequest marker = new CreateOrderItemRequest();
                                marker.setProductId(itemReq.getProductId());
                                marker.setQuantity(stockDelta);
                                adjustments.add(marker);
                            }).replaceWithVoid())
                            .chain(() -> {
                                if (itemReq.getOrderItemId() != null && !itemReq.getOrderItemId().isEmpty()) {
                                    return orderItemPort.update(new OrderItemPort.UpdateData(itemReq.getOrderItemId(),
                                            itemReq.getQuantity(), product.price())).replaceWithVoid();
                                }
                                return orderItemPort.create(new OrderItemPort.CreateData(order.id.toHexString(),
                                        itemReq.getProductId(), itemReq.getQuantity(), product.price())).replaceWithVoid();
                            });
                    return persistUnit
                            .chain(() -> processUpdateOrderItemsInternal(items, order, index + 1,
                                    currentTotalPrice + (itemReq.getQuantity() * product.price()), existingItems,
                                    adjustments));
                });
    }

    private Uni<Void> compensateAdjustments(List<CreateOrderItemRequest> adjustments) {
        if (adjustments == null || adjustments.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        Uni<Void> compensation = Uni.createFrom().voidItem();
        for (int i = adjustments.size() - 1; i >= 0; i--) {
            CreateOrderItemRequest adjustment = adjustments.get(i);
            compensation = compensation.chain(() -> adjustStock(adjustment.getProductId(), -adjustment.getQuantity())
                    .replaceWithVoid());
        }
        return compensation
                .invoke(() -> recordStockCompensation("success", adjustments.size()))
                .onFailure().invoke(error -> recordStockCompensation("failure", adjustments.size()));
    }

    @Override
    public Uni<ApiResponse<OrderResponse>> create(CreateOrderRequest request) {
        logger.info("Creating new order for merchantId={} and userId={}", request.getMerchantId(),
                request.getUserId());

        try {
            validateRequest(request);
        } catch (Exception e) {
            logger.error("Validation failed for create order", e);
            return Uni.createFrom().failure(e);
        }

        Attributes attributes = Attributes.builder()
                .put("merchant.id", request.getMerchantId() != null ? request.getMerchantId().toString() : "null")
                .put("user.id", request.getUserId() != null ? request.getUserId().toString() : "null")
                .build();

        return tracingMetrics.traceAndMeasure("createOrder", "create_order", attributes, () -> merchantPort
                .findById(request.getMerchantId())
                .chain(merchant -> userPort.findById(request.getUserId()))
                .chain(user -> {
                    Order order = new Order();
                    order.setMerchantId(new ObjectId(request.getMerchantId()));
                    order.setUserId(new ObjectId(request.getUserId()));
                    order.setTotalPrice(0);
                    Instant now = Instant.now();
                    order.setCreatedAt(now);
                    order.setUpdatedAt(now);

                    return orderCommandRepository.persistNew(order);
                })
                .chain(savedOrder -> {
                    List<CreateOrderItemRequest> reserved = new ArrayList<>();
                    return processCreateOrderItemsInternal(request.getItems(), savedOrder, 0, 0, reserved)
                            .chain(totalPrice -> {
                                var createShippingReq = new ShippingAddressPort.CreateData(
                                        savedOrder.id.toHexString(),
                                        request.getShippingAddress().getAlamat() == null ? ""
                                                : request.getShippingAddress().getAlamat(),
                                        request.getShippingAddress().getProvinsi() == null ? ""
                                                : request.getShippingAddress().getProvinsi(),
                                        request.getShippingAddress().getKota() == null ? ""
                                                : request.getShippingAddress().getKota(),
                                        request.getShippingAddress().getNegara() == null ? ""
                                                : request.getShippingAddress().getNegara(),
                                        request.getShippingAddress().getCourier() == null ? ""
                                                : request.getShippingAddress().getCourier(),
                                        request.getShippingAddress().getShippingMethod() == null ? ""
                                                : request.getShippingAddress().getShippingMethod(),
                                        request.getShippingAddress().getShippingCost() != null
                                                ? request.getShippingAddress().getShippingCost() : 0);

                                int shippingCost = request.getShippingAddress().getShippingCost() != null
                                        ? request.getShippingAddress().getShippingCost() : 0;
                                int subtotalWithShipping = totalPrice + shippingCost;
                                savedOrder.setTotalPrice(subtotalWithShipping + (subtotalWithShipping * 11 / 100));
                                return Uni.combine().all().unis(
                                        shippingAddressPort.create(createShippingReq),
                                        orderCommandRepository.updateTotalPrice(savedOrder.id.toHexString(),
                                                savedOrder.getTotalPrice())
                                                .chain(updated -> updated == 1
                                                        ? Uni.createFrom().item(updated)
                                                        : Uni.createFrom().failure(new ResourceNotFoundException(
                                                                "Order disappeared before total-price update"))))
                                        .asTuple().chain(tuple -> {
                                            OrderResponse response = OrderResponse.from(savedOrder);

                                            return invalidateCache(savedOrder.id.toHexString())
                                                    .chain(v -> sendOrderStats(savedOrder)
                                                            .onFailure().recoverWithItem((Void) null))
                                                    .map(v -> {
                                                        logger.info("Successfully created order with ID: {}",
                                                                savedOrder.id);
                                                        return ApiResponse.success("Order created successfully",
                                                                response);
                                                    });
                                        });
                            })
                            .onFailure().recoverWithUni(error -> preserveFailure(error,
                                    rollbackOrderAfterPersistenceFailure(savedOrder.id.toHexString(), reserved)));
                })
                .onFailure().invoke(e -> logger.error("Failed to create order for user: {}", request.getUserId(), e)));
    }

    @Override
    public Uni<ApiResponse<OrderResponse>> update(UpdateOrderRequest request) {
        if (request == null) {
            return Uni.createFrom().failure(new InvalidRequestException("Update request cannot be null"));
        }
        if (request.getOrderId() == null) {
            logger.error("OrderId is required for update");
            return Uni.createFrom().failure(new ResourceNotFoundException("OrderId is required"));
        }
        try {
            validateRequest(request);
        } catch (Exception e) {
            logger.error("Validation failed for update order", e);
            return Uni.createFrom().failure(e);
        }
        logger.info("Updating order id={}", request.getOrderId());

        return tracingMetrics.traceAndMeasure("updateOrder", "update_order",
                Attributes.builder().put("order.id", request.getOrderId()).build(),
                () -> orderQueryRepository.findOrderById(request.getOrderId())
                        .<Order>chain(optOrder -> {
                            if (optOrder.isEmpty()) {
                                throw new ResourceNotFoundException("Order not found");
                            }
                            return userPort.findById(request.getUserId())
                                    .map(user -> {
                                        Order existingOrder = optOrder.get();
                                        if (!request.getUserId().equals(existingOrder.getUserId().toHexString())) {
                                            throw new ForbiddenException("You are not allowed to update this order");
                                        }
                                        return existingOrder;
                                    });
                        })
                        .<ApiResponse<OrderResponse>>chain(existingOrder -> {
                            return processUpdateOrderItems(request.getItems(), existingOrder, 0, 0)
                                    .<ApiResponse<OrderResponse>>chain(updatedItems -> {
                                        int totalPrice = updatedItems.totalPrice();
                                        Uni<io.smallrye.mutiny.tuples.Tuple2<ShippingAddress, Order>> businessUpdate =
                                                shippingAddressPort.findById(request.getShippingAddress().getShippingId())
                                                        .chain(shipping -> {
                                                            if (shipping == null || shipping.id().isEmpty()) {
                                                                throw new ResourceNotFoundException(
                                                                        "Shipping address not found");
                                                            }

                                                            var updateShippingReq = new ShippingAddressPort.UpdateData(
                                                                    request.getShippingAddress().getShippingId(),
                                                                    request.getShippingAddress().getAlamat() == null ? ""
                                                                            : request.getShippingAddress().getAlamat(),
                                                                    request.getShippingAddress().getProvinsi() == null ? ""
                                                                            : request.getShippingAddress().getProvinsi(),
                                                                    request.getShippingAddress().getKota() == null ? ""
                                                                            : request.getShippingAddress().getKota(),
                                                                    request.getShippingAddress().getNegara() == null ? ""
                                                                            : request.getShippingAddress().getNegara(),
                                                                    request.getShippingAddress().getCourier() == null ? ""
                                                                            : request.getShippingAddress().getCourier(),
                                                                    request.getShippingAddress().getShippingMethod() == null
                                                                            ? ""
                                                                            : request.getShippingAddress()
                                                                                    .getShippingMethod(),
                                                                    request.getShippingAddress().getShippingCost() != null
                                                                            ? request.getShippingAddress().getShippingCost() : 0);

                                                            int shippingCost = request.getShippingAddress()
                                                                    .getShippingCost() != null
                                                                            ? request.getShippingAddress().getShippingCost() : 0;
                                                            int subtotalWithShipping = totalPrice + shippingCost;
                                                            existingOrder.setTotalPrice(subtotalWithShipping
                                                                    + (subtotalWithShipping * 11 / 100));
                                                            existingOrder.setUpdatedAt(Instant.now());

                                                            return Uni.combine().all().unis(
                                                                    shippingAddressPort.update(updateShippingReq),
                                                                    orderCommandRepository.update(existingOrder)).asTuple();
                                                        });

                                        return businessUpdate
                                                .onFailure().recoverWithUni(error -> preserveFailure(error,
                                                        compensateAdjustments(updatedItems.adjustments())))
                                                .chain(tuple -> {
                                                    OrderResponse response = OrderResponse.from(existingOrder);
                                                    return invalidateCache(existingOrder.id.toHexString())
                                                            .map(v -> {
                                                                logger.info("Successfully updated order with ID: {}",
                                                                        existingOrder.id);
                                                                return ApiResponse.success("Order updated successfully",
                                                                        response);
                                                            });
                                                });
                                    });
                        })
                        .onFailure()
                        .invoke(e -> logger.error("Failed to update order ID: {}", request.getOrderId(), e)));
    }

    @Override
    public Uni<ApiResponse<OrderResponseDeleteAt>> trash(String id) {
        logger.info("Trashing order id={}", id);

        return tracingMetrics.traceAndMeasure("trashOrder", "trash_order",
                Attributes.builder().put("order.id", id).build(), () -> orderCommandRepository.trashed(id)
                        .chain(order -> {
                            if (order == null) {
                                logger.warn("Failed to trash order - not found or already trashed with ID: {}", id);
                                throw new ResourceNotFoundException("Order not found or already trashed");
                            }
                                    return adjustOrderStock(id, 1)
                                    .onFailure().recoverWithUni(error -> preserveFailure(error,
                                            orderCommandRepository.restore(id).replaceWithVoid()))
                                    .replaceWith(order);
                        })
                        .chain(order -> {
                            OrderResponseDeleteAt response = OrderResponseDeleteAt.from(order);
                            return invalidateCache(id).map(v -> {
                                logger.info("Successfully trashed order with ID: {}", id);
                                return ApiResponse.success("Order trashed successfully!", response);
                            });
                        })
                        .onFailure().invoke(e -> logger.error("Failed to trash order ID: {}", id, e)));
    }

    @Override
    public Uni<ApiResponse<OrderResponseDeleteAt>> restore(String id) {
        logger.info("Restoring order id={}", id);

        return tracingMetrics.traceAndMeasure("restoreOrder", "restore_order",
                Attributes.builder().put("order.id", id).build(), () -> orderCommandRepository.restore(id)
                        .chain(order -> {
                            if (order == null) {
                                logger.warn("Failed to restore order - not found or not trashed with ID: {}", id);
                                throw new ResourceNotFoundException("Order not found or not trashed");
                            }
                            return adjustOrderStock(id, -1)
                                    .onFailure().recoverWithUni(error -> preserveFailure(error,
                                            orderCommandRepository.trashed(id).replaceWithVoid()))
                                    .replaceWith(order);
                        })
                        .chain(order -> {
                            OrderResponseDeleteAt response = OrderResponseDeleteAt.from(order);
                            return invalidateCache(id).map(v -> {
                                logger.info("Successfully restored order with ID: {}", id);
                                return ApiResponse.success("Order restored successfully!", response);
                            });
                        })
                        .onFailure().invoke(e -> logger.error("Failed to restore order ID: {}", id, e)));
    }

    @Override
    public Uni<ApiResponse<Void>> delete(String id) {
        Attributes attrs = Attributes.builder().put("order.id", id).build();
        logger.warn("Permanently deleting order id={}", id);

        return tracingMetrics.traceAndMeasure("deleteOrderPermanent", "delete_order_permanent", attrs, () -> {
            return ensureOrderDeletable(id)
                    .chain(() -> orderCommandRepository.deletePermanent(id))
                    .chain(deletedOrder -> {
                        if (deletedOrder == null) {
                            logger.warn(
                                    "Permanent delete failed - order not found or must be trashed before permanent deletion with id: {}",
                                    id);
                            throw new InvalidRequestException(
                                    "Order not found or must be trashed before permanent deletion");
                        }

                        return invalidateCache(id)
                                .map(v2 -> {
                                    logger.info("Successfully permanently deleted order with ID: {}", id);
                                    return ApiResponse.success("Order permanently deleted!");
                                });
                    });
        });
    }

    @Override
    public Uni<ApiResponse<Void>> restoreAll() {
        logger.info("Restoring ALL trashed orders");

        return tracingMetrics.traceAndMeasure("restoreAllOrders", "restore_all_orders", () ->
                orderCommandRepository.findAllDeleted()
                        .chain(orders -> {
                            if (orders.isEmpty()) {
                                throw new ResourceNotFoundException("No trashed orders found");
                            }
                            return restoreOrdersSequentially(orders, 0)
                                    .map(v -> ApiResponse.<Void>success("All orders restored successfully!"));
                        }));
    }

    private Uni<Void> restoreOrdersSequentially(List<Order> orders, int index) {
        return restoreOrdersSequentially(orders, index, new ArrayList<>());
    }

    private Uni<Void> restoreOrdersSequentially(List<Order> orders, int index, List<Order> restored) {
        if (index >= orders.size()) {
            return Uni.createFrom().voidItem();
        }
        Order order = orders.get(index);
        return orderCommandRepository.restore(order.id.toHexString())
                .chain(current -> {
                    if (current == null) {
                        throw new ResourceNotFoundException("Order could not be restored: " + order.id);
                    }
                    return adjustOrderStock(order.id.toHexString(), -1)
                            .onFailure().recoverWithUni(error -> preserveFailure(error,
                                    orderCommandRepository.trashed(order.id.toHexString()).replaceWithVoid()))
                            .invoke(() -> restored.add(order))
                            .chain(() -> restoreOrdersSequentially(orders, index + 1, restored));
                })
                .onFailure().recoverWithUni(error -> preserveFailure(error, rollbackRestoredOrders(restored)));
    }

    private Uni<Void> rollbackRestoredOrders(List<Order> restored) {
        Uni<Void> rollback = Uni.createFrom().voidItem();
        for (int i = restored.size() - 1; i >= 0; i--) {
            Order order = restored.get(i);
            rollback = rollback
                    .chain(() -> adjustOrderStock(order.id.toHexString(), 1))
                    .chain(() -> orderCommandRepository.trashed(order.id.toHexString()).replaceWithVoid());
        }
        return rollback;
    }

    @Override
    public Uni<ApiResponse<Void>> deleteAll() {
        logger.warn("Permanently deleting ALL trashed orders");

        return tracingMetrics.traceAndMeasure("deleteAllOrdersPermanent", "delete_all_orders_permanent", () -> {
            return orderCommandRepository.deleteAllDeleted()
                    .map(success -> {
                        if (!success) {
                            throw new ResourceNotFoundException("No trashed orders found");
                        }
                        logger.info("Successfully permanently deleted all trashed orders");
                        return ApiResponse.success("All orders permanently deleted!");
                    });
        });
    }


    /**
     * Queues the order + order_item stats events in the transactional outbox.
     * <p>
     * Topics: {@code order.event} and {@code order_item.event} — the only two
     * topics this service writes to for the stats pipeline.
     * <p>
     * The rows are written in the same request as the order, so a broker outage
     * no longer loses stats events: {@link com.sanedge.order.service.OrderOutboxPublisher}
     * retries them with backoff until Kafka accepts them.
     */
    Uni<Void> sendOrderStats(Order order) {
        String orderId = order.id != null ? order.id.toHexString() : null;
        String merchantId = order.getMerchantId() != null ? order.getMerchantId().toHexString() : null;
        Instant occurredAt = Instant.now();

        List<OrderOutbox> events = new ArrayList<>();

        JsonObject orderPayload = new JsonObject()
                .put("order_id", orderId)
                .put("merchant_id", merchantId)
                .put("status", "CREATED")
                .put("total_amount", order.getTotalPrice() != null ? order.getTotalPrice().doubleValue() : 0.0);
        events.add(statsEvent(ORDER_STATS_TOPIC, orderId, orderPayload, occurredAt));

        return orderItemPort.findOrderItemByOrder(orderId)
                .chain(items -> {
                    for (var item : items) {
                        JsonObject itemPayload = new JsonObject()
                                .put("order_item_id", item.id())
                                .put("order_id", item.orderId())
                                .put("merchant_id", merchantId)
                                .put("category_id", "")
                                .put("product_id", item.productId())
                                .put("quantity", item.quantity())
                                .put("unit_price", (double) item.price())
                                .put("subtotal", item.price() * item.quantity() * 1.0);
                        events.add(statsEvent(ORDER_ITEM_STATS_TOPIC, item.id(), itemPayload, occurredAt));
                    }
                    return orderOutboxRepository.persistPending(events);
                });
    }

    /**
     * Builds one outbox row. The payload is stored already enveloped
     * ({@code event_id}/{@code event_type}/{@code occurred_at}/payload) and is
     * sent verbatim by the publisher via {@code sendExistingEvent}, so the
     * envelope is never applied twice.
     */
    private static OrderOutbox statsEvent(String topic, String key, JsonObject payload, Instant occurredAt) {
        String eventId = UUID.randomUUID().toString();
        JsonObject envelope = new JsonObject()
                .put("event_id", eventId)
                .put("event_type", topic)
                .put("occurred_at", occurredAt.toString())
                .put("payload", payload);
        OrderOutbox event = new OrderOutbox();
        event.setEventId(eventId);
        event.setTopic(topic);
        event.setEventKey(key);
        event.setPayload(envelope.encode());
        return event;
    }
}
