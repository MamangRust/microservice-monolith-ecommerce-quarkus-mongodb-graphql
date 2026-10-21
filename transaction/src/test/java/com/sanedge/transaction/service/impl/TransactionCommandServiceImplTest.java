package com.sanedge.transaction.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sanedge.common.adapter.merchant.MerchantPort;
import com.sanedge.common.adapter.model.Merchant;
import com.sanedge.common.adapter.model.Order;
import com.sanedge.common.adapter.model.OrderItem;
import com.sanedge.common.adapter.model.ShippingAddress;
import com.sanedge.common.adapter.model.User;
import com.sanedge.common.adapter.order.OrderPort;
import com.sanedge.common.adapter.order_item.OrderItemPort;
import com.sanedge.common.adapter.shipping_address.ShippingAddressPort;
import com.sanedge.common.adapter.user.UserPort;
import com.sanedge.common.config.RedisService;
import com.sanedge.common.domain.response.ApiResponse;
import com.sanedge.common.exception.InvalidRequestException;
import com.sanedge.common.exception.ResourceNotFoundException;
import com.sanedge.common.observability.TracingMetrics;
import com.sanedge.transaction.domain.requests.CreateTransactionRequest;
import com.sanedge.transaction.domain.requests.UpdateTransactionRequest;
import com.sanedge.transaction.domain.response.TransactionResponse;
import com.sanedge.transaction.domain.response.TransactionResponseDeleteAt;
import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.entity.TransactionOutbox;
import com.sanedge.transaction.enums.PaymentStatus;
import com.sanedge.transaction.repository.TransactionCommandRepository;
import com.sanedge.transaction.repository.TransactionOutboxRepository;
import com.sanedge.transaction.repository.TransactionQueryRepository;
import com.sanedge.transaction.service.KafkaService;

import io.opentelemetry.api.common.Attributes;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class TransactionCommandServiceImplTest {

        private static final String TX_ID_HEX = "507f1f77bcf86cd799439011";
        private static final String ORDER_ID_HEX = "507f1f77bcf86cd799439022";
        private static final String MERCHANT_ID_HEX = "507f1f77bcf86cd799439033";
        private static final String USER_ID_HEX = "507f1f77bcf86cd799439044";
        private static final String SHIPPING_ID_HEX = "507f1f77bcf86cd799439055";

        @Mock
        private TransactionQueryRepository transactionQueryRepo;

        @Mock
        private TransactionCommandRepository transactionCommandRepo;

        @Mock
        private MerchantPort merchantPort;

        @Mock
        private OrderPort orderPort;

        @Mock
        private OrderItemPort orderItemPort;

        @Mock
        private ShippingAddressPort shippingAddressPort;

        @Mock
        private UserPort userPort;

        @Mock
        private RedisService redisService;

        @Mock
        private KafkaService kafkaService;

        @Mock
        private TransactionOutboxRepository transactionOutboxRepository;

        @Mock
        private TracingMetrics tracingMetrics;

        private TransactionCommandServiceImpl service;

        @BeforeEach
        void setUp() {
                service = new TransactionCommandServiceImpl(
                                transactionQueryRepo,
                                transactionCommandRepo,
                                redisService,
                                tracingMetrics);

                service.merchantPort = merchantPort;
                service.orderPort = orderPort;
                service.orderItemPort = orderItemPort;
                service.shippingAddressPort = shippingAddressPort;
                service.userPort = userPort;
                service.kafkaService = kafkaService;
                service.transactionOutboxRepository = transactionOutboxRepository;

                lenient().doAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Supplier<Uni<?>> supplier = invocation.getArgument(3);
                        return supplier.get();
                }).when(tracingMetrics)
                                .traceAndMeasure(anyString(), anyString(), any(Attributes.class), any());

                lenient().doAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Supplier<Uni<?>> supplier = invocation.getArgument(2);
                        return supplier.get();
                }).when(tracingMetrics)
                                .traceAndMeasure(anyString(), anyString(), any());

                lenient().when(redisService.deleteReactive(anyString()))
                                .thenReturn(Uni.createFrom().voidItem());

                lenient().when(transactionCommandRepo.deleteAllDeleted())
                                .thenReturn(Uni.createFrom().item(true));
                lenient().when(transactionCommandRepo.restoreAllDeleted())
                                .thenReturn(Uni.createFrom().item(true));
        }

        private Transaction createTestTransaction(ObjectId id, ObjectId orderId, ObjectId merchantId, int amount,
                        PaymentStatus status) {
                Transaction t = new Transaction();
                t.id = id;
                t.setOrderId(orderId);
                t.setMerchantId(merchantId);
                t.setAmount(amount);
                t.setPaymentMethod("CREDIT");
                t.setStatus(status);
                t.setCreatedAt(Instant.now());
                t.setUpdatedAt(Instant.now());
                return t;
        }

        private Transaction createTestTransaction(int id, int orderId, int merchantId, int amount,
                        PaymentStatus status) {
                return createTestTransaction(new ObjectId(hex(id)), new ObjectId(hex(orderId)),
                                new ObjectId(hex(merchantId)), amount, status);
        }

        private static String hex(int n) {
                return String.format("%024x", n);
        }

        private CreateTransactionRequest createValidCreateRequest() {
                CreateTransactionRequest req = new CreateTransactionRequest();
                req.setOrderID(ORDER_ID_HEX);
                req.setMerchantID(MERCHANT_ID_HEX);
                req.setAmount(200000);
                req.setPaymentMethod("CREDIT");
                return req;
        }

        private UpdateTransactionRequest createValidUpdateRequest() {
                UpdateTransactionRequest req = new UpdateTransactionRequest();
                req.setTransactionID(TX_ID_HEX);
                req.setOrderID(ORDER_ID_HEX);
                req.setMerchantID(MERCHANT_ID_HEX);
                req.setAmount(250000);
                req.setPaymentMethod("DEBIT");
                return req;
        }

        private void mockPortsForOrder(int itemQuantity) {
                when(merchantPort.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(new Merchant(MERCHANT_ID_HEX, USER_ID_HEX, "Merchant",
                                                null, null, null, null, "ACTIVE", Instant.now(), Instant.now())));

                when(orderPort.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(new Order(ORDER_ID_HEX, MERCHANT_ID_HEX,
                                                USER_ID_HEX, 0, Instant.now(), Instant.now())));

                when(orderItemPort.findOrderItemByOrder(anyString()))
                                .thenReturn(Uni.createFrom().item(List.of(new OrderItem("oi-1", ORDER_ID_HEX,
                                                "p1", itemQuantity, 50000, Instant.now(), Instant.now()))));

                when(shippingAddressPort.findByOrder(anyString()))
                                .thenReturn(Uni.createFrom().item(new ShippingAddress(SHIPPING_ID_HEX, ORDER_ID_HEX,
                                                "123 Test Street", "Test Province", "Indonesia", "Test City",
                                                "REG", 10000, Instant.now(), Instant.now())));

                lenient().when(userPort.findById(anyString()))
                                .thenReturn(Uni.createFrom().item(new User(USER_ID_HEX, "Test", "User",
                                                "test@test.com", null, Instant.now(), Instant.now())));

                lenient().when(kafkaService.sendMessage(anyString(), anyString(), any()))
                                .thenReturn(Uni.createFrom().voidItem());
                lenient().when(kafkaService.sendExistingEvent(anyString(), anyString(), any()))
                                .thenReturn(Uni.createFrom().voidItem());
                lenient().when(transactionOutboxRepository.persist(any(TransactionOutbox.class)))
                                .thenAnswer(inv -> Uni.createFrom().item((TransactionOutbox) inv.getArgument(0)));
        }

        private void mockCreateDependencies() {
                mockPortsForOrder(3);
        }

        @Nested
        @DisplayName("create transaction tests")
        class CreateTransactionTests {

                @Test
                @DisplayName("should successfully create transaction on happy path")
                void create_Success() {
                        CreateTransactionRequest req = createValidCreateRequest();
                        mockCreateDependencies();

                        when(transactionCommandRepo.persist(any(Transaction.class)))
                                        .thenAnswer(inv -> {
                                                Transaction t = inv.getArgument(0);
                                                t.id = new ObjectId(TX_ID_HEX);
                                                return Uni.createFrom().item(t);
                                        });

                        ApiResponse<TransactionResponse> response = service.create(req)
                                        .await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Transaction created successfully");
                        assertThat(response.data()).isNotNull();
                        assertThat(response.data().getId()).isEqualTo(TX_ID_HEX);

                        verify(redisService, atLeastOnce()).deleteReactive(anyString());
                }

                @Test
                @DisplayName("should publish the transaction stats event to the transactions.event topic")
                void create_PublishesStatsEvent() {
                        CreateTransactionRequest req = createValidCreateRequest();
                        mockCreateDependencies();

                        when(transactionCommandRepo.persist(any(Transaction.class)))
                                        .thenAnswer(inv -> {
                                                Transaction t = inv.getArgument(0);
                                                t.id = new ObjectId(TX_ID_HEX);
                                                return Uni.createFrom().item(t);
                                        });

                        service.create(req).await().indefinitely();

                        // The email event is offloaded to the same outbox, so select the stats row.
                        ArgumentCaptor<TransactionOutbox> captor = ArgumentCaptor.forClass(TransactionOutbox.class);
                        verify(transactionOutboxRepository, atLeastOnce()).persist(captor.capture());

                        TransactionOutbox queued = captor.getAllValues().stream()
                                        .filter(e -> "transactions.event".equals(e.getTopic()))
                                        .findFirst()
                                        .orElseThrow();
                        assertThat(queued.getTopic()).isEqualTo("transactions.event");
                        assertThat(queued.getEventKey()).isEqualTo(ORDER_ID_HEX);
                        assertThat(queued.getEventId()).isNotBlank();
                        // Payload is stored already enveloped — the publisher sends it verbatim.
                        JsonObject envelope = new JsonObject(queued.getPayload());
                        assertThat(envelope.getString("event_id")).isEqualTo(queued.getEventId());
                        assertThat(envelope.getString("event_type")).isEqualTo("transactions.event");
                        assertThat(envelope.getJsonObject("payload").getString("order_id")).isEqualTo(ORDER_ID_HEX);
                }
        }

        @Nested
        @DisplayName("update transaction tests")
        class UpdateTransactionTests {

                @Test
                @DisplayName("should fail when transaction not found")
                void update_NotFound() {
                        UpdateTransactionRequest req = createValidUpdateRequest();

                        when(transactionQueryRepo.findTransactionById(TX_ID_HEX))
                                        .thenReturn(Uni.createFrom().item(Optional.empty()));

                        assertThatThrownBy(() -> service.update(req).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Transaction not found");
                }

                @Test
                @DisplayName("should successfully update transaction on happy path")
                void update_Success() {
                        UpdateTransactionRequest req = createValidUpdateRequest();
                        Transaction existing = createTestTransaction(new ObjectId(TX_ID_HEX),
                                        new ObjectId(ORDER_ID_HEX), new ObjectId(MERCHANT_ID_HEX), 150000,
                                        PaymentStatus.PENDING);

                        when(transactionQueryRepo.findTransactionById(TX_ID_HEX))
                                        .thenReturn(Uni.createFrom().item(Optional.of(existing)));

                        mockPortsForOrder(4);

                        when(transactionCommandRepo.update(any(Transaction.class)))
                                        .thenAnswer(inv -> {
                                                Transaction t = inv.getArgument(0);
                                                return Uni.createFrom().item(t);
                                        });

                        ApiResponse<TransactionResponse> response = service.update(req)
                                        .await().indefinitely();

                        assertThat(response).isNotNull();
                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Transaction updated successfully");
                        assertThat(response.data().getId()).isEqualTo(TX_ID_HEX);

                        verify(redisService, atLeastOnce()).deleteReactive(anyString());
                }
        }

        @Nested
        @DisplayName("trash transaction tests")
        class TrashTransactionTests {

                @Test
                @DisplayName("should successfully trash existing transaction")
                void trash_Success() {
                        String id = hex(1);
                        Transaction trashed = createTestTransaction(1, 1, 1, 150000, PaymentStatus.PENDING);

                        when(transactionCommandRepo.trashed(id))
                                        .thenReturn(Uni.createFrom().item(trashed));

                        ApiResponse<TransactionResponseDeleteAt> response = service.trash(id)
                                        .await().indefinitely();

                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Transaction trashed successfully");
                        assertThat(response.data().getId()).isEqualTo(id);
                }

                @Test
                @DisplayName("should fail when transaction not found for trash")
                void trash_NotFound() {
                        when(transactionCommandRepo.trashed(hex(999)))
                                        .thenReturn(Uni.createFrom().nullItem());

                        assertThatThrownBy(() -> service.trash(hex(999)).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Transaction not found or already trashed");
                }
        }

        @Nested
        @DisplayName("restore transaction tests")
        class RestoreTransactionTests {

                @Test
                @DisplayName("should successfully restore trashed transaction")
                void restore_Success() {
                        String id = hex(1);
                        Transaction restored = createTestTransaction(1, 1, 1, 150000, PaymentStatus.PENDING);

                        when(transactionCommandRepo.restore(id))
                                        .thenReturn(Uni.createFrom().item(restored));

                        ApiResponse<TransactionResponseDeleteAt> response = service.restore(id)
                                        .await().indefinitely();

                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Transaction restored successfully");
                        assertThat(response.data().getId()).isEqualTo(id);
                }

                @Test
                @DisplayName("should fail when transaction is not trashed")
                void restore_NotTrashed() {
                        when(transactionCommandRepo.restore(hex(1)))
                                        .thenReturn(Uni.createFrom().nullItem());

                        assertThatThrownBy(() -> service.restore(hex(1)).await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Transaction not found or not trashed");
                }
        }

        @Nested
        @DisplayName("delete by id tests")
        class DeleteByIdTests {

                @Test
                @DisplayName("should successfully permanently delete trashed transaction")
                void deletePermanent_Success() {
                        String id = hex(1);
                        Transaction trashed = createTestTransaction(1, 1, 1, 150000, PaymentStatus.PENDING);
                        trashed.setDeletedAt(Instant.now());

                        when(transactionCommandRepo.deletePermanent(id))
                                        .thenReturn(Uni.createFrom().item(trashed));

                        ApiResponse<Void> response = service.delete(id).await().indefinitely();

                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("Transaction permanently deleted");
                }

                @Test
                @DisplayName("should fail when transaction not found")
                void deletePermanent_NotFound() {
                        when(transactionCommandRepo.deletePermanent(hex(999)))
                                        .thenReturn(Uni.createFrom().nullItem());

                        assertThatThrownBy(() -> service.delete(hex(999)).await().indefinitely())
                                        .isInstanceOf(InvalidRequestException.class)
                                        .hasMessageContaining("Transaction not found or must be trashed before permanent deletion");
                }
        }

        @Nested
        @DisplayName("delete all trashed tests")
        class DeleteAllTests {

                @Test
                @DisplayName("should successfully delete all trashed transactions")
                void deleteAll_Success() {
                        ApiResponse<Void> response = service.deleteAll().await().indefinitely();

                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("All transactions permanently deleted");
                }

                @Test
                @DisplayName("should fail when no trashed transactions to delete")
                void deleteAll_NoTrashed() {
                        when(transactionCommandRepo.deleteAllDeleted())
                                        .thenReturn(Uni.createFrom().item(false));

                        assertThatThrownBy(() -> service.deleteAll().await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("No trashed transactions found");
                }
        }

        @Nested
        @DisplayName("restore all trashed tests")
        class RestoreAllTests {

                @Test
                @DisplayName("should successfully restore all trashed transactions")
                void restoreAll_Success() {
                        ApiResponse<Void> response = service.restoreAll().await().indefinitely();

                        assertThat(response.status()).isEqualTo("success");
                        assertThat(response.message()).isEqualTo("All transactions restored successfully");
                }

                @Test
                @DisplayName("should fail when no trashed transactions to restore")
                void restoreAll_NoTrashed() {
                        when(transactionCommandRepo.restoreAllDeleted())
                                        .thenReturn(Uni.createFrom().item(false));

                        assertThatThrownBy(() -> service.restoreAll().await().indefinitely())
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("No trashed transactions found");
                }
        }
}
